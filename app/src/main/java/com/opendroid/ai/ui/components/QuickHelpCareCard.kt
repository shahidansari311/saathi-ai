package com.opendroid.ai.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.ai.ui.theme.OpenDroidTheme

/**
 * Quick-Help Care Card for elderly and non-tech-savvy users.
 *
 * Features:
 *  1. 🚨 1-Tap Emergency SOS: Large high-contrast red button for instant distress alerts.
 *  2. 📞 Call Family ("Beta"): One-tap direct dial for emergency/family contact.
 *  3. 🎙️ Big Voice Mic Orb: Prominent, high-contrast pulsing microphone button
 *     inviting the user to speak naturally without needing to type on a keyboard.
 */
@Composable
fun QuickHelpCareCard(
    isListening: Boolean,
    transcriptionText: String = "",
    onMicClick: () -> Unit,
    onEmergencySosClick: () -> Unit,
    onCallFamilyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = OpenDroidTheme.colors

    // Pulse animation for the big voice mic orb when active
    val infiniteTransition = rememberInfiniteTransition(label = "micOrbTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringAlpha"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = theme.cardBackground),
        border = BorderStroke(1.5.dp, theme.borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterCenter
        ) {
            // ── Section 1: Big Voice Mic Orb ────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Expanding ripple ring when listening
                if (isListening) {
                    Box(
                        modifier = Modifier
                            .size(105.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = ringAlpha))
                    )
                }

                // Main Touch Target Orb
                val orbColor = if (isListening) Color(0xFFDC2626) else Color(0xFF0284C7)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(if (isListening) pulseScale else 1.0f)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    orbColor.copy(alpha = 0.95f),
                                    orbColor
                                )
                            )
                        )
                        .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                        .clickable { onMicClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isListening) "Stop listening" else "Start voice command",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Big Voice Invitation Prompt
            Text(
                text = if (isListening) "Listening... Tap to Stop" else "Tap to Speak (बोलें)",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = if (isListening) Color(0xFFEF4444) else theme.textPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isListening && transcriptionText.isNotBlank()) {
                    "\"$transcriptionText\""
                } else {
                    "No typing needed — just say what you want"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isListening && transcriptionText.isNotBlank()) Color(0xFF38BDF8) else theme.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Section 2: Large 1-Tap Quick Action Buttons ────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 🚨 1-Tap Emergency SOS Button
                Button(
                    onClick = onEmergencySosClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 58.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626), // High-contrast Alert Red
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Emergency SOS",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "EMERGENCY",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "SOS (मदद)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                // 📞 Call Family ("Beta") Button
                Button(
                    onClick = onCallFamilyClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 58.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF16A34A), // High-contrast Forest Green
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call Beta",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "CALL BETA",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Phone (बेटा)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}
