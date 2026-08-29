package com.example.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance, 120 FPS Intro Animation matching the Google AI Studio neon glow video.
 * Features:
 * - Neon Rainbow Border Tracer
 * - Glowing Multi-gradient Studio S-Ribbon expansion
 * - Vibrant Google Ai Studio typography with subtle sparkle
 * - One-tap skip to immediately access the app
 */
@Composable
fun IntroSplashScreen(
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animation progress controllers
    val borderProgress = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.6f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textTranslateY = remember { Animatable(20f) }
    val sparkleAlpha = remember { Animatable(0f) }
    val overallAlpha = remember { Animatable(1f) }

    // Continuous ambient breathing glow
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_glow")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    LaunchedEffect(Unit) {
        // Step 1: Animate the glowing neon rounded border trace (0ms - 800ms)
        launch {
            borderProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
            )
        }

        // Step 2: Animate the Google AI Studio Ribbon Logo popping in (500ms - 1300ms)
        delay(400)
        launch {
            logoAlpha.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
        }

        // Step 3: Animate the Google Ai Studio text & Sparkle (1000ms - 1700ms)
        delay(600)
        launch {
            textAlpha.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
            textTranslateY.animateTo(0f, animationSpec = tween(500, easing = FastOutSlowInEasing))
        }
        launch {
            sparkleAlpha.animateTo(1f, animationSpec = tween(600, easing = FastOutSlowInEasing))
        }

        // Hold frame for user to experience the vibrant intro branding
        delay(1200)

        // Step 4: Gracefully fade out into the main application
        overallAlpha.animateTo(0f, animationSpec = tween(400, easing = LinearEasing))
        onAnimationFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050608))
            .alpha(overallAlpha.value)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tap to skip intro immediately
                onAnimationFinished()
            }
            .testTag("intro_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Main animated container
        Box(
            modifier = Modifier
                .size(330.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            // 1. Neon Glowing Border Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidthPx = 5.dp.toPx()
                val cornerRadiusPx = 52.dp.toPx()
                val padding = strokeWidthPx / 2 + 4.dp.toPx()
                val rectSize = Size(size.width - padding * 2, size.height - padding * 2)

                val fullPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = androidx.compose.ui.geometry.Rect(
                                offset = Offset(padding, padding),
                                size = rectSize
                            ),
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                        )
                    )
                }

                val pathMeasure = PathMeasure()
                pathMeasure.setPath(fullPath, false)
                val totalLength = pathMeasure.length

                val currentLength = totalLength * borderProgress.value

                val drawnPath = Path()
                pathMeasure.getSegment(0f, currentLength, drawnPath, true)

                // Neon Multi-color Rainbow Gradient Brush
                val rainbowBrush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF00E676), // Neon Green
                        Color(0xFF76FF03), // Lime
                        Color(0xFFFFD600), // Yellow
                        Color(0xFFFF3D00), // Sunset Orange / Red
                        Color(0xFFD500F9), // Purple / Magenta
                        Color(0xFF2979FF), // Neon Blue
                        Color(0xFF00E5FF), // Cyan
                        Color(0xFF00E676)  // Wrap around
                    ),
                    center = Offset(size.width / 2, size.height / 2)
                )

                // Outer Soft Bloom Layer
                if (borderProgress.value > 0.05f) {
                    drawPath(
                        path = drawnPath,
                        brush = rainbowBrush,
                        style = Stroke(
                            width = strokeWidthPx * 2.8f * pulseGlow,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        ),
                        alpha = 0.35f
                    )
                }

                // Core Crisp Neon Stroke
                drawPath(
                    path = drawnPath,
                    brush = rainbowBrush,
                    style = Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    ),
                    alpha = 0.95f
                )

                // Inner Bright White Glow Core
                drawPath(
                    path = drawnPath,
                    color = Color.White.copy(alpha = 0.6f),
                    style = Stroke(
                        width = strokeWidthPx * 0.35f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 2. Animated Center S-Ribbon Logo and Typography
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
            ) {
                // S-Ribbon Icon with Glowing Capsules
                Canvas(
                    modifier = Modifier
                        .size(150.dp, 120.dp)
                ) {
                    val w = size.width
                    val h = size.height

                    // Top Capsule (Vibrant Green -> Cyan -> Royal Blue)
                    val topPillPath = Path().apply {
                        val pillHeight = 44.dp.toPx()
                        val r = pillHeight / 2
                        val leftX = 14.dp.toPx()
                        val rightX = w - 14.dp.toPx()
                        val topY = 12.dp.toPx()
                        val bottomY = topY + pillHeight

                        // Slanted rounded capsule
                        moveTo(leftX + r, topY)
                        lineTo(rightX - r, topY - 8.dp.toPx())
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(
                                left = rightX - pillHeight,
                                top = topY - 8.dp.toPx(),
                                right = rightX,
                                bottom = bottomY - 8.dp.toPx()
                            ),
                            startAngleDegrees = -90f,
                            sweepAngleDegrees = 180f,
                            forceMoveTo = false
                        )
                        lineTo(leftX + r, bottomY)
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(
                                left = leftX,
                                top = topY,
                                right = leftX + pillHeight,
                                bottom = bottomY
                            ),
                            startAngleDegrees = 90f,
                            sweepAngleDegrees = 180f,
                            forceMoveTo = false
                        )
                        close()
                    }

                    // Bottom Capsule (Royal Blue -> Amber -> Neon Orange / Red)
                    val bottomPillPath = Path().apply {
                        val pillHeight = 44.dp.toPx()
                        val r = pillHeight / 2
                        val leftX = 10.dp.toPx()
                        val rightX = w - 18.dp.toPx()
                        val topY = h - pillHeight - 6.dp.toPx()
                        val bottomY = h - 6.dp.toPx()

                        // Slanted rounded capsule
                        moveTo(leftX + r, topY + 8.dp.toPx())
                        lineTo(rightX - r, topY)
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(
                                left = rightX - pillHeight,
                                top = topY,
                                right = rightX,
                                bottom = bottomY
                            ),
                            startAngleDegrees = -90f,
                            sweepAngleDegrees = 180f,
                            forceMoveTo = false
                        )
                        lineTo(leftX + r, bottomY + 8.dp.toPx())
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(
                                left = leftX,
                                top = topY + 8.dp.toPx(),
                                right = leftX + pillHeight,
                                bottom = bottomY + 8.dp.toPx()
                            ),
                            startAngleDegrees = 90f,
                            sweepAngleDegrees = 180f,
                            forceMoveTo = false
                        )
                        close()
                    }

                    // Top Pill Brush
                    val topBrush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF00E676), // Neon Green
                            Color(0xFF00B0FF), // Bright Sky Blue
                            Color(0xFF2979FF)  // Royal Blue
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h * 0.5f)
                    )

                    // Bottom Pill Brush
                    val bottomBrush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF2979FF), // Royal Blue
                            Color(0xFFFFD600), // Amber Yellow
                            Color(0xFFFF3D00)  // Sunset Red-Orange
                        ),
                        start = Offset(0f, h * 0.4f),
                        end = Offset(w, h)
                    )

                    // Draw Top Capsule with ambient glow
                    drawPath(path = topPillPath, brush = topBrush)
                    // Draw Bottom Capsule
                    drawPath(path = bottomPillPath, brush = bottomBrush)
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Typography: "Google Ai Studio"
                Box(
                    modifier = Modifier
                        .alpha(textAlpha.value)
                        .padding(top = textTranslateY.value.dp)
                ) {
                    val annotatedString = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color(0xFF4285F4))) { append("G") }
                        withStyle(SpanStyle(color = Color(0xFFEA4335))) { append("o") }
                        withStyle(SpanStyle(color = Color(0xFFFBBC05))) { append("o") }
                        withStyle(SpanStyle(color = Color(0xFF4285F4))) { append("g") }
                        withStyle(SpanStyle(color = Color(0xFF34A853))) { append("l") }
                        withStyle(SpanStyle(color = Color(0xFFEA4335))) { append("e") }
                        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) {
                            append(" Ai Studio")
                        }
                    }

                    Text(
                        text = annotatedString,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 21.sp,
                            letterSpacing = 0.4.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // 3. Subtle bottom sparkle icon
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFF8AB4F8).copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp)
                    .size(16.dp)
                    .alpha(sparkleAlpha.value)
            )
        }
    }
}
