package com.flexi.jarvis.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flexi.jarvis.R
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun JarvisAnimatedSplash(onFinished: () -> Unit) {
    var targetProgress by remember { mutableFloatStateOf(0f) }

    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(3000, easing = LinearEasing),
        label = "splashProgress"
    )

    LaunchedEffect(Unit) {
        targetProgress = 1f
        delay(3200)
        onFinished()
    }

    val starAnimation = rememberInfiniteTransition(label = "starfield")

    val starMotion by starAnimation.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "starMotion"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030405))
    ) {
        val screenHeight = maxHeight

        // Animated galaxy, drifting stars and silver planet.
        Canvas(Modifier.fillMaxSize()) {
            val stars = listOf(
                0.04f to 0.04f, 0.16f to 0.11f, 0.29f to 0.05f,
                0.48f to 0.09f, 0.72f to 0.04f, 0.91f to 0.12f,
                0.08f to 0.23f, 0.24f to 0.30f, 0.82f to 0.25f,
                0.94f to 0.36f, 0.12f to 0.46f, 0.36f to 0.39f,
                0.68f to 0.43f, 0.89f to 0.52f, 0.05f to 0.61f,
                0.25f to 0.68f, 0.78f to 0.70f, 0.94f to 0.82f,
                0.12f to 0.87f, 0.42f to 0.78f, 0.61f to 0.91f,
                0.32f to 0.17f, 0.57f to 0.31f, 0.74f to 0.16f
            )

            stars.forEachIndexed { index, point ->
                val direction = if (index % 2 == 0) 1f else -1f
                val x = (point.first + starMotion * 0.045f * direction + 1f) % 1f
                val y = point.second +
                    sin((starMotion * 2f * PI + index).toFloat()) * 0.008f

                val twinkle =
                    0.22f + 0.65f *
                    ((sin((starMotion * 2f * PI + index * 1.8f).toFloat()) + 1f) / 2f)

                drawCircle(
                    color = Color.White.copy(alpha = twinkle),
                    radius = if (index % 5 == 0) 1.7.dp.toPx() else 0.8.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(
                        x * size.width,
                        y * size.height
                    )
                )
            }

            // Very faint constellation lines.
            val lineColor = Color.White.copy(alpha = 0.10f)
            val links = listOf(
                0.08f to 0.23f, 0.16f to 0.11f,
                0.16f to 0.11f, 0.29f to 0.05f,
                0.82f to 0.25f, 0.91f to 0.12f,
                0.89f to 0.52f, 0.94f to 0.36f
            )

            links.chunked(2).forEach { pair ->
                if (pair.size == 2) {
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(
                            pair[0].first * size.width,
                            pair[0].second * size.height
                        ),
                        end = androidx.compose.ui.geometry.Offset(
                            pair[1].first * size.width,
                            pair[1].second * size.height
                        ),
                        strokeWidth = 0.6.dp.toPx()
                    )
                }
            }

            // Large monochrome planet rising from the bottom.
            val planetCenter = androidx.compose.ui.geometry.Offset(
                size.width / 2f,
                size.height * 1.12f
            )
            val planetRadius = size.width * 0.57f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF777777),
                        Color(0xFF333333),
                        Color(0xFF08090A),
                        Color.Black
                    ),
                    center = androidx.compose.ui.geometry.Offset(
                        planetCenter.x - planetRadius * 0.28f,
                        planetCenter.y - planetRadius * 0.85f
                    ),
                    radius = planetRadius * 2.2f
                ),
                radius = planetRadius,
                center = planetCenter
            )

            // Fine silver atmospheric rim.
            drawArc(
                color = Color.White.copy(alpha = 0.65f),
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(
                    planetCenter.x - planetRadius * 1.05f,
                    planetCenter.y - planetRadius * 1.05f
                ),
                size = androidx.compose.ui.geometry.Size(
                    planetRadius * 2.1f,
                    planetRadius * 2.1f
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.2.dp.toPx()
                )
            )

            drawArc(
                color = Color.White.copy(alpha = 0.22f),
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(
                    planetCenter.x - planetRadius * 1.12f,
                    planetCenter.y - planetRadius * 1.12f
                ),
                size = androidx.compose.ui.geometry.Size(
                    planetRadius * 2.24f,
                    planetRadius * 2.24f
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx()
                )
            )
        }

        // Actual JARVIS emblem and wordmark from the existing project assets.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.79f)
                .align(Alignment.TopCenter)
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(screenHeight * 0.17f))

            Image(
                painter = painterResource(R.drawable.jarvis_mark),
                contentDescription = "JARVIS emblem",
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(screenHeight * 0.25f),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.height(8.dp))

            Image(
                painter = painterResource(R.drawable.jarvis_wordmark),
                contentDescription = "JARVIS AI",
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(screenHeight * 0.12f),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.height(screenHeight * 0.045f))

            // Live progress bar.
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.32f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF777777),
                                    Color(0xFFE4E4E4),
                                    Color.White
                                )
                            )
                        )
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Your Smart Learning Assistant",
                color = Color(0xFFD0D0D0),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp
            )
        }
    }
}
