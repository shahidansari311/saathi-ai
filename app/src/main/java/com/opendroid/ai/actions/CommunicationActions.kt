package com.opendroid.ai.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.opendroid.ai.accessibility.OpenDroidAccessibilityService
import com.opendroid.ai.accessibility.WhatsAppAutomator
import com.opendroid.ai.accessibility.TelegramAutomator
import com.opendroid.ai.accessibility.SmsAutomator
import com.opendroid.ai.actions.base.Action
import com.opendroid.ai.actions.base.ActionResult
import com.opendroid.ai.core.agent.ContactResolution
import com.opendroid.ai.core.agent.ContactResolver
import com.opendroid.ai.core.agent.maskPhone
import com.opendroid.ai.core.util.DeviceCapabilities
import android.provider.ContactsContract
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.net.URLEncoder
import com.opendroid.ai.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

internal enum class EmailComposeOutcome {
    COMPOSED,
    VERIFIED_SENT,
    UNAVAILABLE
}

internal fun interface EmailComposer {
    fun open(context: Context, to: String, subject: String, body: String): EmailComposeOutcome
}

private class AndroidEmailComposer : EmailComposer {
    override fun open(
        context: Context,
        to: String,
        subject: String,
        body: String
    ): EmailComposeOutcome {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            return EmailComposeOutcome.UNAVAILABLE
        }
        context.startActivity(intent)
        return EmailComposeOutcome.COMPOSED
    }
}

@Singleton
class CommunicationActions @Inject constructor(
    private val contactResolver: ContactResolver,
    private val callFlowExecutor: CallFlowExecutor,
    private val settingsRepository: SettingsRepository? = null
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun getActions(): List<Action> = listOf(
        EmergencySosAction(),
        MakeCallAction(),
        GetContactAction(),
        SendWhatsAppAction(),
        SendTelegramAction(),
        OpenTelegramAction(),
        SendSmsAction(),
        SendEmailAction(),
        SendWhatsAppGroupAction(),
        MakeVideoCallAction(),
        ReadMessagesAction(),
        ReadEmailsAction()
    )

    companion object {
        /**
         * Legacy static resolve for backward compat.
         * Used by actions that don't yet support disambiguation.
         */
        private fun resolveContactToPhoneNumber(context: Context, contact: String): String {
            if (contact.startsWith("$")) {
                throw IllegalArgumentException("Unresolved contact placeholder: $contact")
            }
            val cleaned = contact.replace(" ", "").replace("-", "")
            if (cleaned.startsWith("+") || (cleaned.isNotEmpty() && cleaned.all { it.isDigit() })) {
                return cleaned
            }
            val result = ContactResolver.resolve(context, contact)
            if (result is ContactResolver.ContactResult.Found) {
                return result.phoneNumber
            }
            throw IllegalArgumentException("Contact '$contact' not found in your contacts")
        }
    }

    /**
     * Build a NeedsInput with contact picker metadata.
     * Metadata stores match data as JSON so it survives serialization.
     */
    private fun buildContactPickerResult(
        contactQuery: String,
        matches: List<com.opendroid.ai.core.agent.Contact>,
        action: String,
        extraMeta: Map<String, String> = emptyMap()
    ): ActionResult.NeedsInput {
        val matchesJson = json.encodeToString(
            matches.map { mapOf("name" to it.name, "phone" to it.phoneNumber, "type" to it.type) }
        )
        return ActionResult.NeedsInput(
            question = "Which '$contactQuery' do you mean?",
            options = matches.mapIndexed { i, c ->
                "${i + 1}. ${c.name} (${c.type}: ${maskPhone(c.phoneNumber)})"
            },
            metadata = mapOf(
                "type" to "contact_picker",
                "query" to contactQuery,
                "action" to action,
                "matches" to matchesJson
            ) + extraMeta
        )
    }

    // ── Safe Sensor & Battery Helpers for SOS ────────────────

    private fun getBatteryPercentage(context: Context): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    private fun getLocationCoordinates(context: Context): Pair<Double, Double>? {
        return try {
            val hasFine = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasFine && !hasCoarse) return null

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null

            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if (!isGpsEnabled && !isNetworkEnabled) return null

            val gpsLoc = if (hasFine && isGpsEnabled) {
                try { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: SecurityException) { null }
            } else null

            val networkLoc = if (isNetworkEnabled) {
                try { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: SecurityException) { null }
            } else null

            val bestLoc = when {
                gpsLoc != null && networkLoc != null -> if (gpsLoc.time > networkLoc.time) gpsLoc else networkLoc
                gpsLoc != null -> gpsLoc
                else -> networkLoc
            }

            bestLoc?.let { Pair(it.latitude, it.longitude) }
        } catch (_: Exception) {
            null
        }
    }

    // ── EMERGENCY_SOS ────────────────────────────────────────

    private inner class EmergencySosAction : Action {
        override val name: String = "EMERGENCY_SOS"

        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val contactQuery = params["contact"] ?: params["to"] ?: "emergency"
            val shouldCall = params["makeCall"]?.lowercase() in listOf("yes", "true", "1")

            // 1. Grab current location coordinates and battery level
            val coords = getLocationCoordinates(context)
            val locationUrl = if (coords != null) {
                String.format(
                    java.util.Locale.US,
                    "https://maps.google.com/?q=%.5f,%.5f",
                    coords.first,
                    coords.second
                )
            } else {
                "Location unavailable"
            }

            val batteryLevel = getBatteryPercentage(context)
            val batteryText = if (batteryLevel >= 0) "$batteryLevel" else "Unknown"

            // 2. Compose urgent bilingual (Hindi & English) distress alert with live location, battery, and time
            val currentTime = java.text.SimpleDateFormat("hh:mm a, dd MMM", java.util.Locale.getDefault()).format(java.util.Date())
            val alertMessage = """
🚨 EMERGENCY / आपातकालीन सहायता:
I need emergency help! Mujhe turant madad ki zaroorat hai!
📍 Live Location / स्थान: $locationUrl
🔋 Battery / बैटरी: $batteryText%
⏰ Time / समय: $currentTime
(Sent automatically via Saathi AI Emergency SOS)
            """.trimIndent()

            // 3. Resolve target contacts (Multiple emergency contacts / address book / 112)
            val savedEmergencyPhones = try {
                settingsRepository?.llmConfig?.first()?.allEmergencyPhones()?.filter { it.isNotBlank() } ?: emptyList()
            } catch (e: Exception) { emptyList() }
            val savedEmergencyName = try {
                settingsRepository?.llmConfig?.first()?.emergencyContactName?.takeIf { it.isNotBlank() }
            } catch (e: Exception) { null }

            val targetPhones = mutableListOf<Pair<String, String>>() // (phone, label)

            if (savedEmergencyPhones.isNotEmpty() && (contactQuery.equals("emergency", ignoreCase = true) || contactQuery.equals("sos", ignoreCase = true))) {
                savedEmergencyPhones.forEachIndexed { index, phone ->
                    val label = if (index == 0 && savedEmergencyName != null) savedEmergencyName else "Emergency Contact ${index + 1}"
                    targetPhones.add(Pair(phone, label))
                }
            } else if (contactQuery.equals("emergency", ignoreCase = true) || contactQuery.equals("sos", ignoreCase = true)) {
                // Search for designated emergency/family contacts in address book
                val familyAliases = listOf("beta", "family", "emergency", "ice", "sos", "ghar", "doctor", "police")
                for (alias in familyAliases) {
                    when (val res = contactResolver.resolveWithDisambiguation(alias)) {
                        is ContactResolution.Found -> {
                            targetPhones.add(Pair(res.contact.phoneNumber, res.contact.name))
                            break
                        }
                        is ContactResolution.Ambiguous -> {
                            val top = res.matches.first()
                            targetPhones.add(Pair(top.phoneNumber, top.name))
                            break
                        }
                        is ContactResolution.NotFound -> {}
                    }
                }
                if (targetPhones.isEmpty()) {
                    targetPhones.add(Pair("112", "Emergency Services (112)"))
                }
            } else {
                when (val resolved = contactResolver.resolveWithDisambiguation(contactQuery)) {
                    is ContactResolution.Found -> {
                        targetPhones.add(Pair(resolved.contact.phoneNumber, resolved.contact.name))
                    }
                    is ContactResolution.Ambiguous -> {
                        val top = resolved.matches.first()
                        targetPhones.add(Pair(top.phoneNumber, top.name))
                    }
                    is ContactResolution.NotFound -> {
                        val numeric = contactQuery.filter { it.isDigit() || it == '+' }
                        if (numeric.length >= 3) {
                            targetPhones.add(Pair(numeric, contactQuery))
                        } else {
                            targetPhones.add(Pair("112", "Emergency Services (112)"))
                        }
                    }
                }
            }

            // 4. Broadcast distress SMS or WhatsApp to ALL target emergency contacts
            for ((phone, label) in targetPhones) {
                val smsResult = executeSms(phone, label, alertMessage, context)
                if (!smsResult.success) {
                    // Try WhatsApp as backup if SMS couldn't auto-send
                    executeWhatsApp(phone, label, alertMessage, context)
                }
            }

            // 5. If requested, initiate direct phone call to primary emergency contact
            if (shouldCall && targetPhones.isNotEmpty()) {
                val primaryPhone = targetPhones.first().first
                executeCall(primaryPhone, context)
            }

            return ActionResult(
                true,
                "Emergency distress alert sent to ${targetPhones.size} contact(s).",
                null
            )
        }
    }

    // ── MAKE_CALL with disambiguation ────────────────────────

    private inner class MakeCallAction : Action {
        override val name: String = "MAKE_CALL"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val contact = params["contact"]
                ?: params["number"]
                ?: params["phone"]
                ?: params["phoneNumber"]
                ?: return ActionResult(false, null, "contact or number parameter missing")

            val savedQuickCall = try {
                settingsRepository?.llmConfig?.first()?.quickCallPhone?.takeIf { it.isNotBlank() }
            } catch (e: Exception) { null }
            val savedQuickCallName = try {
                settingsRepository?.llmConfig?.first()?.quickCallName?.takeIf { it.isNotBlank() }
            } catch (e: Exception) { null }

            if (savedQuickCall != null && (
                contact.equals("beta", ignoreCase = true) ||
                contact.equals("family", ignoreCase = true) ||
                contact.equals("ghar", ignoreCase = true) ||
                (savedQuickCallName != null && contact.equals(savedQuickCallName, ignoreCase = true))
            )) {
                return executeCall(savedQuickCall, context)
            }

            return when (val resolved = contactResolver.resolveWithDisambiguation(contact)) {
                is ContactResolution.Found -> executeCall(resolved.contact.phoneNumber, context)
                is ContactResolution.Ambiguous -> buildContactPickerResult(contact, resolved.matches, "MAKE_CALL")
                is ContactResolution.NotFound -> ActionResult.NeedsInput(
                    question = "I couldn't find '$contact' in your contacts. What's their number?",
                    metadata = mapOf("param" to "contact")
                )
            }
        }
    }

    // ── GET_CONTACT ──────────────────────────────────────────

    private inner class GetContactAction : Action {
        override val name: String = "GET_CONTACT"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val contactName = params["name"] ?: params["contact"] ?: params["query"]
                ?: return ActionResult(false, null, "contact name is missing")
            return when (val resolved = contactResolver.resolveWithDisambiguation(contactName)) {
                is ContactResolution.Found -> {
                    ActionResult(true, "${resolved.contact.name}: ${resolved.contact.phoneNumber}", null)
                }
                is ContactResolution.Ambiguous -> {
                    val top = resolved.matches.first()
                    ActionResult(true, "${top.name}: ${top.phoneNumber}", null)
                }
                is ContactResolution.NotFound -> {
                    ActionResult(false, null, "Could not find contact '$contactName'")
                }
            }
        }
    }

    // ── SEND_WHATSAPP with disambiguation ────────────────────

    private inner class SendWhatsAppAction : Action {
        override val name: String = "SEND_WHATSAPP"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val contact = params["contact"] ?: return ActionResult(false, null, "contact is missing")
            val message = params["message"] ?: return ActionResult(false, null, "message is missing")

            return when (val resolved = contactResolver.resolveWithDisambiguation(contact)) {
                is ContactResolution.Found -> executeWhatsApp(resolved.contact.phoneNumber, contact, message, context)
                is ContactResolution.Ambiguous -> buildContactPickerResult(
                    contact, resolved.matches, "SEND_WHATSAPP",
                    mapOf("message" to message)
                )
                is ContactResolution.NotFound -> ActionResult.NeedsInput(
                    question = "I couldn't find '$contact'. What's their WhatsApp number?",
                    metadata = mapOf("param" to "contact")
                )
            }
        }
    }

    // ── SEND_TELEGRAM with username / phone / disambiguation ───

    private inner class SendTelegramAction : Action {
        override val name: String = "SEND_TELEGRAM"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val contact = params["contact"]
                ?: params["to"]
                ?: params["recipient"]
                ?: params["username"]
                ?: return ActionResult(false, null, "contact is missing")
            val message = params["message"]
                ?: params["text"]
                ?: params["body"]
                ?: return ActionResult(false, null, "message is missing")

            val trimmed = contact.trim()
            if (trimmed.startsWith("@") || (trimmed.isNotEmpty() && !trimmed.contains(" ") && !trimmed.all { it.isDigit() })) {
                // Direct Telegram username/handle
                return executeTelegram(trimmed.removePrefix("@"), contact, message, context, isUsername = true)
            }

            if (trimmed.startsWith("+") || (trimmed.isNotEmpty() && trimmed.all { it.isDigit() })) {
                // Direct phone number
                return executeTelegram(trimmed, contact, message, context, isUsername = false)
            }

            return when (val resolved = contactResolver.resolveWithDisambiguation(contact)) {
                is ContactResolution.Found -> executeTelegram(resolved.contact.phoneNumber, contact, message, context, isUsername = false)
                is ContactResolution.Ambiguous -> buildContactPickerResult(
                    contact, resolved.matches, "SEND_TELEGRAM",
                    mapOf("message" to message)
                )
                is ContactResolution.NotFound -> {
                    // If not found in device contacts, treat as Telegram username
                    executeTelegram(trimmed.removePrefix("@"), contact, message, context, isUsername = true)
                }
            }
        }
    }

    private inner class OpenTelegramAction : Action {
        override val name: String = "OPEN_TELEGRAM"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val contact = params["contact"] ?: params["username"] ?: params["channel"]
            return try {
                if (!contact.isNullOrBlank()) {
                    val domain = contact.trim().removePrefix("@")
                    val tgUri = "tg://resolve?domain=$domain".toUri()
                    val intent = Intent(Intent.ACTION_VIEW, tgUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        return ActionResult(true, "Opened Telegram chat with $contact!", null)
                    }
                    val webIntent = Intent(Intent.ACTION_VIEW, "https://t.me/$domain".toUri()).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(webIntent)
                    return ActionResult(true, "Opened Telegram with $contact!", null)
                } else {
                    val pm = context.packageManager
                    val launchIntent = pm.getLaunchIntentForPackage("org.telegram.messenger")
                        ?: pm.getLaunchIntentForPackage("org.telegram.messenger.web")
                        ?: pm.getLaunchIntentForPackage("org.telegram.plus")
                        ?: pm.getLaunchIntentForPackage("nekox.messenger")
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        ActionResult(true, "Telegram is open!", null)
                    } else {
                        val webIntent = Intent(Intent.ACTION_VIEW, "https://web.telegram.org".toUri()).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(webIntent)
                        ActionResult(true, "Opened Telegram Web in browser.", null)
                    }
                }
            } catch (e: Exception) {
                Log.e("OpenTelegram", "Failed to open Telegram: ${e.localizedMessage}")
                ActionResult(false, null, "Couldn't open Telegram right now.")
            }
        }
    }

    // ── SEND_SMS with disambiguation ─────────────────────────

    private inner class SendSmsAction : Action {
        override val name: String = "SEND_SMS"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val contact = params["contact"]
                ?: params["to"]
                ?: params["recipient"]
                ?: return ActionResult(false, null, "contact parameter missing")
            val message = params["message"]
                ?: params["text"]
                ?: params["body"]
                ?: return ActionResult(false, null, "message parameter missing")

            return when (val resolved = contactResolver.resolveWithDisambiguation(contact)) {
                is ContactResolution.Found -> executeSms(resolved.contact.phoneNumber, contact, message, context)
                is ContactResolution.Ambiguous -> buildContactPickerResult(
                    contact, resolved.matches, "SEND_SMS",
                    mapOf("message" to message)
                )
                is ContactResolution.NotFound -> ActionResult.NeedsInput(
                    question = "I couldn't find '$contact'. What's their phone number?",
                    metadata = mapOf("param" to "contact")
                )
            }
        }
    }

    // ── Execution helpers ────────────────────────────────────

    private suspend fun executeCall(phone: String, context: Context): ActionResult =
        callFlowExecutor.execute(phone, context)

    private suspend fun executeWhatsApp(phone: String, contactLabel: String, message: String, context: Context): ActionResult {
        return try {
            val encodedMsg = URLEncoder.encode(message, "UTF-8")
            val whatsappUri = if (phone.matches(Regex("\\+?[0-9]+"))) {
                "https://api.whatsapp.com/send?phone=$phone&text=$encodedMsg".toUri()
            } else {
                "whatsapp://send?text=$encodedMsg".toUri()
            }
            val intent = Intent(Intent.ACTION_VIEW, whatsappUri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            val service = OpenDroidAccessibilityService.getInstance()
            if (service != null) {
                val autoSent = WhatsAppAutomator.automateSend(message)
                if (autoSent) {
                    return ActionResult(true, "Sent your message to $contactLabel!", null)
                }
                // First attempt didn't confirm — try one more time with a longer wait
                kotlinx.coroutines.delay(2000)
                val retryClicked = service.findAndClickById("com.whatsapp:id/send") ||
                                   service.findAndClick("Send") ||
                                   service.findAndClick("send")
                if (retryClicked) {
                    // We clicked something — check if it actually sent
                    kotlinx.coroutines.delay(500)
                    val verifyResult = verifySendCompleted(service)
                    if (verifyResult != false) {
                        // Verified or inconclusive → trust the click
                        return ActionResult(true, "Message sent to $contactLabel!", null)
                    }
                    // Verification says input field still has text — send didn't work
                    return ActionResult(false, null, "I tapped send but the message is still in the input field. Please send it manually.", true)
                }
            }

            // No accessibility service or couldn't click send — chat is open with message pre-filled
            ActionResult(false, null, "I've opened the chat with $contactLabel on WhatsApp with your message ready — just tap send!", true)
        } catch (e: Exception) {
            Log.e("SendWhatsApp", "WhatsApp failed: ${e.localizedMessage}")
            ActionResult(false, null, "WhatsApp didn't work. ${e.localizedMessage ?: "Please try again."}", true)
        }
    }

    /**
     * After clicking send, verify the message was actually dispatched by checking
     * if the WhatsApp input field is now empty or shows the placeholder text.
     * 
     * Returns:
     *   true  = verified sent (input field is empty/placeholder)
     *   false = verified NOT sent (input field still has message text)
     *   null  = inconclusive (couldn't find input field to check)
     */
    private fun verifySendCompleted(service: OpenDroidAccessibilityService): Boolean? {
        try {
            val rootNode = service.rootInActiveWindow ?: return null // inconclusive
            val inputIds = listOf("com.whatsapp:id/entry", "com.whatsapp:id/text_entry")
            for (id in inputIds) {
                val inputNodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                for (node in inputNodes) {
                    val text = node.text?.toString() ?: ""
                    // If input field is empty or shows placeholder, message was sent
                    if (text.isBlank() || text == "Type a message" || text == "Message") {
                        return true
                    }
                    // Input field still has content — message wasn't sent
                    return false
                }
            }
            // Couldn't find input field — inconclusive, don't assume failure
            return null
        } catch (e: Exception) {
            return null // inconclusive
        }
    }

    private suspend fun executeTelegram(
        identifier: String,
        contactLabel: String,
        message: String,
        context: Context,
        isUsername: Boolean
    ): ActionResult {
        return try {
            val encodedMsg = URLEncoder.encode(message, "UTF-8")
            val tgUri = if (isUsername) {
                "tg://resolve?domain=$identifier&text=$encodedMsg".toUri()
            } else {
                "tg://msg?to=$identifier&text=$encodedMsg".toUri()
            }

            val intent = Intent(Intent.ACTION_VIEW, tgUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val pm = context.packageManager
            val tgPackages = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus", "nekox.messenger")
            val installedTgPkg = tgPackages.firstOrNull { pkg ->
                try {
                    pm.getPackageInfo(pkg, 0)
                    true
                } catch (e: Exception) {
                    false
                }
            }

            if (installedTgPkg != null) {
                intent.setPackage(installedTgPkg)
            }

            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
            } else {
                val fallbackUri = if (isUsername) {
                    "https://t.me/$identifier".toUri()
                } else {
                    "https://t.me/share/url?url=&text=$encodedMsg".toUri()
                }
                val webIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }

            val service = OpenDroidAccessibilityService.getInstance()
            if (service != null) {
                val autoSent = TelegramAutomator.automateSend(message)
                if (autoSent) {
                    return ActionResult(true, "Message sent to $contactLabel on Telegram!", null)
                }
            }

            ActionResult(true, "Opened Telegram chat with $contactLabel with your message ready!", null)
        } catch (e: Exception) {
            Log.e("SendTelegram", "Telegram failed: ${e.localizedMessage}")
            ActionResult(false, null, "Telegram didn't work. ${e.localizedMessage ?: "Please try again."}", true)
        }
    }

    private suspend fun executeSms(phone: String, contactLabel: String, message: String, context: Context): ActionResult {
        return try {
            if (DeviceCapabilities.canSendSms(context) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val smsManager = context.getSystemService(SmsManager::class.java)
                    if (smsManager != null) {
                        val parts = smsManager.divideMessage(message)
                        if (parts.size > 1) {
                            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                        } else {
                            smsManager.sendTextMessage(phone, null, message, null, null)
                        }
                        return ActionResult(true, "Text sent to $contactLabel!", null)
                    }
                } catch (_: Exception) {
                    // SmsManager failed — fall through to intent
                }
            }
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = "smsto:$phone".toUri()
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Telephony is optional: a device with no radio and no messaging app
            // would throw ActivityNotFoundException here.
            if (intent.resolveActivity(context.packageManager) == null) {
                return ActionResult(false, null, "This device can't send text messages — there's no messaging app or phone hardware. Want me to use WhatsApp?")
            }
            context.startActivity(intent)

            val service = OpenDroidAccessibilityService.getInstance()
            if (service != null) {
                val autoSent = SmsAutomator.automateSend()
                if (autoSent) {
                    return ActionResult(true, "Sent SMS to $contactLabel!", null)
                }
            }

            // Couldn't auto-send — be honest
            ActionResult(false, null, "I've opened your message to $contactLabel but couldn't tap send automatically. Please tap send.", true)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = "sms:$phone".toUri()
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)

                val service = OpenDroidAccessibilityService.getInstance()
                if (service != null) {
                    val autoSent = SmsAutomator.automateSend()
                    if (autoSent) {
                        return ActionResult(true, "Sent SMS to $contactLabel!", null)
                    }
                }

                ActionResult(false, null, "Messaging app is open for $contactLabel but needs you to tap send.", true)
            } catch (e2: Exception) {
                Log.e("SendSMS", "SMS failed: ${e2.localizedMessage}")
                ActionResult(false, null, "Couldn't open messaging. Try again?")
            }
        }
    }

    // ── Non-disambiguated actions (unchanged) ────────────────

    internal class SendEmailAction(
        private val emailComposer: EmailComposer = AndroidEmailComposer()
    ) : Action {
        override val name: String = "SEND_EMAIL"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val to = params["to"] ?: return ActionResult(false, null, "to email is missing")
            val subject = params["subject"] ?: ""
            val body = params["body"] ?: ""
            return try {
                when (emailComposer.open(context, to, subject, body)) {
                    EmailComposeOutcome.VERIFIED_SENT ->
                        ActionResult(true, "Email sent successfully.", null)
                    EmailComposeOutcome.COMPOSED ->
                        ActionResult.UserActionRequired(
                            "Email draft opened. Review it and tap Send; sending was not verified."
                        )
                    EmailComposeOutcome.UNAVAILABLE ->
                        ActionResult(false, null, "Couldn't open the email app. Is one installed?")
                }
            } catch (e: Exception) {
                Log.e("SendEmail", "Email compose launch failed")
                ActionResult(false, null, "Couldn't open the email app. Is one installed?")
            }
        }
    }

    private class SendWhatsAppGroupAction : Action {
        override val name: String = "SEND_WHATSAPP_GROUP"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val groupName = params["groupName"] ?: return ActionResult(false, null, "groupName parameter missing")
            val message = params["message"] ?: return ActionResult(false, null, "message parameter missing")
            return try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult(true, "WhatsApp is open — find the '$groupName' group and send your message!", null)
            } catch (e: Exception) {
                Log.e("WhatsAppGroup", "Group message failed: ${e.localizedMessage}")
                ActionResult(false, null, "Couldn't open WhatsApp. Is it installed?")
            }
        }
    }

    private inner class MakeVideoCallAction : Action {
        override val name: String = "MAKE_VIDEO_CALL"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val contact = params["contact"] ?: return ActionResult(false, null, "contact parameter missing")
            val phone = resolveContactToPhoneNumber(context, contact)
            val app = params["app"] ?: "whatsapp"
            return try {
                when (app.lowercase()) {
                    "whatsapp" -> {
                        val intent = Intent(Intent.ACTION_VIEW, "https://api.whatsapp.com/send?phone=$phone".toUri()).apply {
                            setPackage("com.whatsapp")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                    else -> {
                        val pm = context.packageManager
                        val launchIntent = pm.getLaunchIntentForPackage("com.google.android.apps.meetings")
                            ?: pm.getLaunchIntentForPackage("com.google.android.apps.tachyon")
                            ?: pm.getLaunchIntentForPackage("us.zoom.videomeetings")
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                        } else {
                            val dialIntent = Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            // Telephony is optional — a device with no radio and no
                            // dialer app has nothing left to fall back to.
                            if (dialIntent.resolveActivity(context.packageManager) == null) {
                                return ActionResult(false, null, "No video call app is installed, and this device can't place phone calls. Try WhatsApp?")
                            }
                            context.startActivity(dialIntent)
                        }
                    }
                }
                ActionResult(true, "Video call to $contact is starting!", null)
            } catch (e: Exception) {
                Log.e("VideoCall", "Video call failed: ${e.localizedMessage}")
                ActionResult(false, null, "Couldn't start the video call. Try again?")
            }
        }
    }

    private class ReadMessagesAction : Action {
        override val name: String = "READ_MESSAGES"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val app = params["app"] ?: "sms"
            return try {
                val intent = when (app.lowercase()) {
                    "whatsapp" -> context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                    "telegram" -> context.packageManager.getLaunchIntentForPackage("org.telegram.messenger")
                        ?: context.packageManager.getLaunchIntentForPackage("org.telegram.messenger.web")
                        ?: context.packageManager.getLaunchIntentForPackage("org.telegram.plus")
                    else -> Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_MESSAGING)
                    }
                }
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    ActionResult(true, "Here are your messages!", null)
                } else {
                    ActionResult(false, null, "Couldn't open the messaging app.")
                }
            } catch (e: Exception) {
                Log.e("ReadMessages", "Failed: ${e.localizedMessage}")
                ActionResult(false, null, "Couldn't open your messages right now.")
            }
        }
    }

    private class ReadEmailsAction : Action {
        override val name: String = "READ_EMAILS"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            return try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_EMAIL)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult(true, "Your email is open!", null)
            } catch (e: Exception) {
                Log.e("ReadEmails", "Failed: ${e.localizedMessage}")
                ActionResult(false, null, "Couldn't open the email app.")
            }
        }
    }
}
