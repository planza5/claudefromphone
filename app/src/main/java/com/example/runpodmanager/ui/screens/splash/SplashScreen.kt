package com.example.runpodmanager.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(
    viewModel: SplashViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToPods: () -> Unit
) {
    val hasApiKey by viewModel.hasApiKey.collectAsState()
    val isReady by viewModel.isReady.collectAsState()

    val scale = remember { Animatable(0.3f) }
    val alpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val glowScale = remember { Animatable(1f) }

    // Runpod brand colors
    val runpodPurple = Color(0xFF7B2CBF)
    val runpodPurpleDark = Color(0xFF5A189A)
    val runpodPurpleLight = Color(0xFF9D4EDD)
    val runpodPink = Color(0xFFE040FB)

    // Infinite glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Particles rotation
    val particleRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    LaunchedEffect(Unit) {
        // Fade in
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(600, easing = FastOutSlowInEasing)
        )

        // Zoom in effect
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        )

        // Show text with bounce
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        )

        // Small pulse
        scale.animateTo(
            targetValue = 1.05f,
            animationSpec = tween(200)
        )
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(200)
        )

        // Wait for dramatic effect
        delay(800)

        // Epic zoom out
        scale.animateTo(
            targetValue = 20f,
            animationSpec = tween(500, easing = FastOutSlowInEasing)
        )
    }

    // Navigate when animation is done and we know if API key exists
    LaunchedEffect(isReady, scale.value) {
        if (isReady && scale.value >= 18f) {
            if (hasApiKey) {
                onNavigateToPods()
            } else {
                onNavigateToSettings()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        runpodPurple,
                        runpodPurpleDark,
                        Color(0xFF3C096C),
                        Color(0xFF240046)
                    ),
                    radius = 1500f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Animated particles background
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha.value * 0.6f)
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2

            // Draw orbiting particles
            for (i in 0 until 12) {
                val angle = (i * 30f + particleRotation) * (Math.PI / 180f)
                val radius = 200f + (i % 3) * 80f
                val x = centerX + (radius * cos(angle)).toFloat()
                val y = centerY + (radius * sin(angle)).toFloat()

                drawCircle(
                    color = if (i % 2 == 0) runpodPink.copy(alpha = 0.6f) else runpodPurpleLight.copy(alpha = 0.5f),
                    radius = 8f + (i % 4) * 3f,
                    center = Offset(x, y)
                )
            }

            // Inner particles
            for (i in 0 until 8) {
                val angle = (i * 45f - particleRotation * 0.5f) * (Math.PI / 180f)
                val radius = 120f
                val x = centerX + (radius * cos(angle)).toFloat()
                val y = centerY + (radius * sin(angle)).toFloat()

                drawCircle(
                    color = Color.White.copy(alpha = 0.4f),
                    radius = 4f,
                    center = Offset(x, y)
                )
            }
        }

        // Glow effect behind logo
        Box(
            modifier = Modifier
                .size(200.dp)
                .scale(scale.value * 1.2f)
                .alpha(glowAlpha * alpha.value)
                .blur(40.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            runpodPink.copy(alpha = 0.8f),
                            runpodPurple.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Main content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value)
        ) {
            // Logo container with glow
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                runpodPurpleLight,
                                runpodPurple,
                                runpodPink
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Inner circle
                Box(
                    modifier = Modifier
                        .size(85.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF240046),
                                    Color(0xFF3C096C)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Lightning bolt / Pod symbol
                    Text(
                        text = "⚡",
                        fontSize = 44.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Brand text
            Text(
                text = "RUNPOD",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 10.sp,
                modifier = Modifier.alpha(textAlpha.value)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "M A N A G E R",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = runpodPink,
                letterSpacing = 8.sp,
                modifier = Modifier.alpha(textAlpha.value)
            )
        }
    }
}
