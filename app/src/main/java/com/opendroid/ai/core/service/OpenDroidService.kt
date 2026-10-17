package com.opendroid.ai.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.opendroid.ai.core.agent.AgentLoop
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.core.voice.SpeechRecognitionEngine
import com.opendroid.ai.core.voice.TextToSpeechEngine
import com.opendroid.ai.core.voice.VoiceApprovalIntent
import com.opendroid.ai.core.voice.VoiceApprovalParser
import com.opendroid.ai.core.voice.WakeWordDetector
import com.opendroid.ai.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OpenDroidService : Service() {

    @Inject
    lateinit var agentLoop: AgentLoop

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var mcpServer: McpServer

    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var speechRecognitionEngine: SpeechRecognitionEngine
    private lateinit var textToSpeechEngine: TextToSpeechEngine

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var showFloatingButton = false
    @Volatile private var pendingApprovalListen = false

    companion object {
        const val ACTION_TRIGGER_RECORD = "com.opendroid.ai.action.TRIGGER_RECORD"
        private const val CHANNEL_ID = "opendroid_channel"
        private const val NOTIFICATION_ID = 2024
        private var instance: OpenDroidService? = null
        
        fun start(context: Context) {
            val intent = Intent(context, OpenDroidService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OpenDroidService::class.java)
            context.stopService(intent)
        }

        fun pauseWakeWord() {
            instance?.wakeWordDetector?.stopListening()
        }

        fun resumeWakeWord() {
            instance?.startWakeWordDetection()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize engines
        wakeWordDetector = WakeWordDetector(this)
        speechRecognitionEngine = SpeechRecognitionEngine(this)
        textToSpeechEngine = TextToSpeechEngine(this, settingsRepository)

        // Bind Agent Loop TTS
        agentLoop.onSpeakCallback = { text ->
            textToSpeechEngine.speak(text)
        }

        // Set TTS completion listener to transition back to Idle
        textToSpeechEngine.onCompletionListener = {
            // Only Speaking resets to Idle - speech that happens to play while a
            // plan sits in PlanProposed must not knock the approval gate over.
            if (agentLoop.agentState.value is AgentState.Speaking) {
                agentLoop.setAgentState(AgentState.Idle)
            }
            if (pendingApprovalListen) {
                pendingApprovalListen = false
                startListeningForApproval()
            }
        }

        // Start Foreground Notification
        createNotificationChannel()
        startForegroundCompat()
        mcpServer.start()

        // Monitor config and keep wake word detection active
        serviceScope.launch {
            settingsRepository.llmConfig.collectLatest { config ->
                showFloatingButton = config.showFloatingButton
                startWakeWordDetection()
            }
        }

        // Hands-free plan approval (voice-driven):
        // speak the prompt once per proposed plan and auto-listen for the reply.
        serviceScope.launch {
            var promptedPlanId: String? = null
            agentLoop.agentState.collectLatest { state ->
                if (state is AgentState.PlanProposed && state.plan.planId != promptedPlanId) {
                    promptedPlanId = state.plan.planId
                    pendingApprovalListen = true
                    textToSpeechEngine.speak(
                        "I've planned: ${state.plan.goal}, ${state.plan.estimatedSteps} steps. " +
                        "Say approve to run, or cancel."
                    )
                }
            }
        }
    }

    private fun startForegroundCompat() {
        val notification = createNotification()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Starting a microphone-type FGS without RECORD_AUDIO granted throws a
            // SecurityException on Android 14+, and starting one from BOOT_COMPLETED is
            // prohibited on Android 15 even with the permission. Fall back to specialUse
            // until the microphone is actually usable.
            val micGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val type = if (micGranted) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            try {
                androidx.core.app.ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            } catch (e: SecurityException) {
                androidx.core.app.ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // FOREGROUND_SERVICE_TYPE_MICROPHONE is an API 30 constant; API 29 devices
            // fall through to plain startForeground, which uses the manifest-declared types.
            androidx.core.app.ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startWakeWordDetection() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        wakeWordDetector.startListening {
            textToSpeechEngine.speak("Saathi AI online.")
            startListeningForQuery()
        }
    }

    private fun startListeningForQuery() {
        // Temporarily pause wake word to avoid hearing itself
        wakeWordDetector.stopListening()

        // Set agent state to Listening
        agentLoop.setAgentState(AgentState.Listening)

        // Give audio hardware 250ms to cleanly release wake word session before opening query session
        serviceScope.launch {
            kotlinx.coroutines.delay(250)
            speechRecognitionEngine.startListening(
                onResult = { query ->
                    agentLoop.processQuery(query, this@OpenDroidService)
                    // Resume wake word detection
                    startWakeWordDetection()
                },
                onError = { err ->
                    android.util.Log.w("OpenDroidService", "Speech listening error in service query: $err")
                    agentLoop.setAgentState(AgentState.Idle)
                    // Resume wake word detection
                    startWakeWordDetection()
                }
            )
        }
    }

    /**
     * One-shot reply capture after the spoken approval prompt. Unrecognized
     * speech (or an error) is a deliberate no-op: the plan stays in
     * PlanProposed with the visual modal still on screen. Never a grant path -
     * nothing spoken may widen the allowlist.
     */
    private fun startListeningForApproval() {
        wakeWordDetector.stopListening()
        serviceScope.launch {
            kotlinx.coroutines.delay(200)
            speechRecognitionEngine.startListening(
                onResult = { utterance ->
                    when (VoiceApprovalParser.parse(utterance)) {
                        VoiceApprovalIntent.APPROVE -> agentLoop.approveProposedPlan(this@OpenDroidService)
                        VoiceApprovalIntent.REJECT -> agentLoop.rejectProposedPlan()
                        VoiceApprovalIntent.NONE -> Unit
                    }
                    startWakeWordDetection()
                },
                onError = { _ ->
                    startWakeWordDetection()
                }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForegroundCompat()
        if (intent?.action == ACTION_TRIGGER_RECORD) {
            startListeningForQuery()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Ensure foreground service stays pinned and active when app is swiped away from recents
        createNotificationChannel()
        startForegroundCompat()

        // Set up watchdog resurrection intent via AlarmManager
        try {
            val restartIntent = Intent(applicationContext, OpenDroidService::class.java).also {
                it.setPackage(packageName)
            }
            val pendingIntent = android.app.PendingIntent.getService(
                applicationContext,
                1,
                restartIntent,
                android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
            alarmManager?.set(
                android.app.AlarmManager.ELAPSED_REALTIME,
                android.os.SystemClock.elapsedRealtime() + 1000,
                pendingIntent
            )
        } catch (e: Exception) {
            android.util.Log.e("OpenDroidService", "Failed to schedule restart watchdog", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        mcpServer.stop()
        wakeWordDetector.destroy()
        speechRecognitionEngine.destroy()
        textToSpeechEngine.destroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Saathi AI Agent Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Saathi AI background agent alive"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Saathi AI Active")
            .setContentText("Listening for wake word 'Saathi AI'")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }
}
