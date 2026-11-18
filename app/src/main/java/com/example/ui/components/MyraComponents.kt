package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AssistantState
import com.example.data.ThemeAccent
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.CyanSoft
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassCardBg
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.RoyalPurpleDeep
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = GlassBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = GlassCardBg,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun FirebaseAiNoticeBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = RoyalPurpleDeep.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Gemini Firebase AI Status",
                tint = EmeraldPulse,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.ai_live_connected_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun MyraAvatarCore(
    assistantState: AssistantState,
    micRms: Float,
    mouthOpenness: Float,
    accent: ThemeAccent,
    coreSize: Dp = 188.dp,
    fullBodyMode: Boolean = true,
    onTapCore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryGlow = when (accent) {
        ThemeAccent.NEBULA_PURPLE -> ElectricViolet
        ThemeAccent.CYBER_CYAN -> LuminousCyan
        ThemeAccent.MIDNIGHT_ROYAL -> Color(0xFF6B8CFF)
    }
    val secondaryGlow = when (accent) {
        ThemeAccent.NEBULA_PURPLE -> LuminousCyan
        ThemeAccent.CYBER_CYAN -> EmeraldPulse
        ThemeAccent.MIDNIGHT_ROYAL -> ElectricViolet
    }

    val infiniteTransition = rememberInfiniteTransition(label = "myra_core_anim")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val animatedMouth by animateFloatAsState(
        targetValue = if (assistantState == AssistantState.SPEAKING) mouthOpenness else 0f,
        animationSpec = tween(durationMillis = 65),
        label = "lip_sync"
    )

    val liveEnergy = when (assistantState) {
        AssistantState.LISTENING -> 0.35f + micRms * 0.65f
        AssistantState.SPEAKING -> 0.30f + animatedMouth * 0.70f
        AssistantState.STANDBY_WAKE_WORD -> 0.22f + micRms * 0.45f
        AssistantState.THINKING -> 0.45f
        AssistantState.IDLE -> 0.10f
    }

    val ringSize = coreSize + 44.dp

    if (fullBodyMode) {
        val stageWidth = coreSize * 1.12f
        val stageHeight = coreSize * 1.44f
        Box(
            modifier = modifier
                .width(stageWidth)
                .height(stageHeight)
                .testTag("myra_avatar_core")
                .clickable(onClick = onTapCore),
            contentAlignment = Alignment.Center
        ) {
            // Holographic full-body aura + live bottom floor pulse
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height * 0.52f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = 0.24f + liveEnergy * 0.28f),
                            secondaryGlow.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.maxDimension * 0.56f
                    ),
                    radius = size.maxDimension * 0.56f,
                    center = center
                )

                // Holographic pedestal ring at her feet
                val pedestalRectSize = Size(size.width * 0.72f, size.height * 0.08f)
                val pedestalTopLeft = Offset((size.width - pedestalRectSize.width) / 2f, size.height * 0.89f)
                drawOval(
                    brush = Brush.horizontalGradient(listOf(primaryGlow, secondaryGlow, primaryGlow)),
                    topLeft = pedestalTopLeft,
                    size = pedestalRectSize,
                    style = Stroke(width = 3.5f + liveEnergy * 3f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .graphicsLayer {
                        val s = if (assistantState == AssistantState.LISTENING || assistantState == AssistantState.SPEAKING) {
                            pulseScale + liveEnergy * 0.018f
                        } else pulseScale
                        scaleX = s
                        scaleY = s
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        width = 1.6.dp,
                        brush = Brush.verticalGradient(listOf(secondaryGlow, primaryGlow.copy(alpha = 0.5f))),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .background(DeepNavyBg.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_myra_fullbody),
                    contentDescription = "MYRA AI Full Body Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = animatedMouth * 1.4f
                        }
                )

                // Lip movement overlay aligned to full-body MYRA face
                if (animatedMouth > 0.04f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val mouthCenterX = size.width * 0.50f
                        val mouthCenterY = size.height * 0.173f
                        val mouthWidth = size.width * (0.042f + animatedMouth * 0.008f)
                        val mouthHeight = size.height * (0.005f + animatedMouth * 0.015f)
                        drawRoundRect(
                            color = Color(0xFF280B10).copy(alpha = (0.65f + animatedMouth * 0.3f).coerceIn(0f, 0.95f)),
                            topLeft = Offset(mouthCenterX - mouthWidth / 2f, mouthCenterY - mouthHeight / 2f),
                            size = Size(mouthWidth, mouthHeight),
                            cornerRadius = CornerRadius(mouthHeight, mouthHeight)
                        )
                    }
                }
            }
        }
        return
    }

    Box(
        modifier = modifier
            .size(ringSize)
            .testTag("myra_avatar_core")
            .clickable(onClick = onTapCore),
        contentAlignment = Alignment.Center
    ) {
        // Animated Circular Voice Waveform + Futuristic Neural Halo
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) - 24f

            // Outer radial aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = 0.28f + liveEnergy * 0.25f),
                        secondaryGlow.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension / 2f
                ),
                radius = size.minDimension / 2f,
                center = center
            )

            // Rotating arc segments
            drawArc(
                brush = Brush.sweepGradient(listOf(primaryGlow, secondaryGlow, Color.Transparent, primaryGlow)),
                startAngle = orbitAngle,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                size = Size(baseRadius * 2f, baseRadius * 2f),
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )

            // Circular waveform bars reacting to real voice RMS & TTS mouth openness
            val barCount = 44
            for (i in 0 until barCount) {
                val theta = (i.toDouble() / barCount) * 2.0 * PI + Math.toRadians(orbitAngle.toDouble() * 0.4)
                val harmonic = sin(theta * 3.0 + orbitAngle * 0.08).toFloat()
                val amp = (6f + liveEnergy * 26f * ((harmonic + 1.2f) / 2.2f))
                val rInner = baseRadius - 6f
                val rOuter = rInner + amp
                val startPt = Offset(
                    center.x + (rInner * cos(theta)).toFloat(),
                    center.y + (rInner * sin(theta)).toFloat()
                )
                val endPt = Offset(
                    center.x + (rOuter * cos(theta)).toFloat(),
                    center.y + (rOuter * sin(theta)).toFloat()
                )
                drawLine(
                    color = if (i % 2 == 0) secondaryGlow else primaryGlow,
                    start = startPt,
                    end = endPt,
                    strokeWidth = 3.2f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Realistic Human-Like Female Portrait with Real-Time Lip Movement Overlay
        Box(
            modifier = Modifier
                .size(coreSize)
                .graphicsLayer {
                    val s = if (assistantState == AssistantState.LISTENING || assistantState == AssistantState.SPEAKING) {
                        pulseScale + liveEnergy * 0.025f
                    } else 1f
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .border(
                    width = 2.5.dp,
                    brush = Brush.linearGradient(listOf(secondaryGlow, primaryGlow)),
                    shape = CircleShape
                )
                .background(DeepNavyBg),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_myra_avatar),
                contentDescription = "MYRA AI Female Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Subtle natural head/jaw motion when speaking
                        translationY = animatedMouth * 2.2f
                    }
            )

            // Real-time animated mouth/lip movement overlay aligned to MYRA's lips
            if (animatedMouth > 0.04f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val mouthCenterX = size.width * 0.50f
                    val mouthCenterY = size.height * 0.594f
                    val mouthWidth = size.width * (0.118f + animatedMouth * 0.018f)
                    val mouthHeight = size.height * (0.012f + animatedMouth * 0.048f)

                    // Inner dark mouth opening
                    drawRoundRect(
                        color = Color(0xFF280B10).copy(alpha = (0.65f + animatedMouth * 0.3f).coerceIn(0f, 0.95f)),
                        topLeft = Offset(mouthCenterX - mouthWidth / 2f, mouthCenterY - mouthHeight / 2f),
                        size = Size(mouthWidth, mouthHeight),
                        cornerRadius = CornerRadius(mouthHeight, mouthHeight)
                    )

                    // Subtle upper teeth highlight during wide vowels
                    if (animatedMouth > 0.42f) {
                        val teethHeight = mouthHeight * 0.28f
                        drawRoundRect(
                            color = Color(0xFFF7EFEA).copy(alpha = 0.72f),
                            topLeft = Offset(mouthCenterX - mouthWidth * 0.34f, mouthCenterY - mouthHeight * 0.36f),
                            size = Size(mouthWidth * 0.68f, teethHeight),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }

                    // Moving lower lip contour
                    val lowerLipPath = Path().apply {
                        moveTo(mouthCenterX - mouthWidth * 0.55f, mouthCenterY + mouthHeight * 0.1f)
                        quadraticTo(
                            mouthCenterX,
                            mouthCenterY + mouthHeight * 0.95f,
                            mouthCenterX + mouthWidth * 0.55f,
                            mouthCenterY + mouthHeight * 0.1f
                        )
                    }
                    drawPath(
                        path = lowerLipPath,
                        color = Color(0xFFB56A64).copy(alpha = 0.85f),
                        style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingMyraOrb(
    assistantState: AssistantState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = assistantState == AssistantState.LISTENING || assistantState == AssistantState.SPEAKING
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .shadow(14.dp, CircleShape, ambientColor = LuminousCyan, spotColor = ElectricViolet)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag("floating_myra_orb"),
        shape = CircleShape,
        color = if (active) LuminousCyan else RoyalPurpleDeep,
        border = BorderStroke(1.5.dp, if (active) TextPrimary else LuminousCyan)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (active) Icons.Default.GraphicEq else Icons.Default.Mic,
                contentDescription = "Launch MYRA Voice Mode",
                tint = if (active) DeepNavyBg else CyanSoft,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (active) "MYRA Live" else "MYRA",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (active) DeepNavyBg else TextPrimary
            )
        }
    }
}
