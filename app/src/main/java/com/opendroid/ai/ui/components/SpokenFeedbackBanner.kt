package com.opendroid.ai.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.ui.theme.*

/**
 * High-contrast visual status bar displayed prominently at the top of the chat screen.
 * Formatted with large fonts (16sp-18sp) and intuitive visual indicators for elderly users
 * to immediately understand what Saathi AI is currently doing.
 */
@Composable
fun SpokenFeedbackBanner(
    agentState: AgentState,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val theme = OpenDroidTheme.colors

    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Determine status text, icon, and colors based on current AgentState and listening state
    val (statusText, subText, statusColor, statusIcon, isAnimated) = when {
        isListening -> StatusInfo(
            primary = "Listening to you...",
            secondary = "Aapki baat sun raha hoon — Bolte rahiye",
            color = Color(0xFFEF4444),
            icon = Icons.Default.Mic,
            animated = true
        )
        agentState is AgentState.Thinking -> StatusInfo(
            primary = "Thinking & Planning...",
            secondary = "Soch raha hoon aur plan bana raha hoon",
            color = Color(0xFF38BDF8),
            icon = Icons.Default.Psychology,
            animated = true
        )
        agentState is AgentState.ExecutingPlan -> StatusInfo(
            primary = agentState.currentStepDesc.ifBlank { "Performing action..." },
            secondary = "Kaam kiya ja raha hai...",
            color = Color(0xFF38BDF8),
            icon = Icons.Default.PlayArrow,
            animated = true
        )
        agentState is AgentState.Speaking -> StatusInfo(
            primary = "Speaking aloud...",
            secondary = agentState.text.take(60) + if (agentState.text.length > 60) "..." else "",
            color = Color(0xFF007AFF),
            icon = Icons.Default.VolumeUp,
            animated = true
        )
        agentState is AgentState.PlanProposed -> StatusInfo(
            primary = "Action Plan Ready: ${agentState.plan.goal}",
            secondary = "${agentState.plan.estimatedSteps} steps prepared — Please confirm below",
            color = Color(0xFFFF9500),
            icon = Icons.Default.Assignment,
            animated = false
        )
        agentState is AgentState.Error -> StatusInfo(
            primary = "Attention Needed",
            secondary = agentState.message,
            color = Color(0xFFEF4444),
            icon = Icons.Default.Warning,
            animated = false
        )
        else -> StatusInfo(
            primary = "Saathi AI Ready",
            secondary = "साथी AI तैयार है — Bol kar aadesh dein",
            color = Color(0xFF10B981),
            icon = Icons.Default.CheckCircle,
            animated = false
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = theme.cardBackground,
        border = BorderStroke(
            width = if (isAnimated) 2.dp else 1.5.dp,
            color = if (isAnimated) statusColor.copy(alpha = pulseAlpha) else statusColor.copy(alpha = 0.5f)
        ),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // High-contrast status indicator circle
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.18f))
                    .border(
                        1.5.dp,
                        if (isAnimated) statusColor.copy(alpha = pulseAlpha) else statusColor,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Primary & Secondary Status Text
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = statusText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Live Activity Dot
            if (isAnimated) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = pulseAlpha))
                )
            }
        }
    }
}

private data class StatusInfo(
    val primary: String,
    val secondary: String,
    val color: Color,
    val icon: ImageVector,
    val animated: Boolean
)
