package health.kino.sdk.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun KinoProcessingScreen(
    progress: Float,
    statusLabel: String,
    brandColor: Color = MaterialTheme.colorScheme.primary
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "scan_progress"
    )

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Circular progress arc
            ProgressArc(
                progress = animatedProgress,
                brandColor = brandColor,
                modifier = Modifier.size(200.dp)
            )

            Spacer(Modifier.height(28.dp))

            // Status label
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(36.dp))

            // Step dots
            StepDots(progress = animatedProgress, brandColor = brandColor)
        }
    }
}

@Composable
private fun ProgressArc(
    progress: Float,
    brandColor: Color,
    modifier: Modifier = Modifier
) {
    val trackColor = brandColor.copy(alpha = 0.15f)
    val sweepAngle = 360f * progress
    val percentText = "${(progress * 100).toInt()}%"

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 16.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(
                width = size.width - strokeWidth,
                height = size.height - strokeWidth
            )
            val topLeft = Offset(inset, inset)

            // Track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress arc
            drawArc(
                color = brandColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Percentage in center
        Text(
            text = percentText,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StepDots(
    progress: Float,
    brandColor: Color
) {
    // Four steps: Uploading (0–0.25), Queued (0.25–0.5), Analyzing (0.5–0.85), Finishing (0.85–1.0)
    val stepThresholds = listOf(0.1f, 0.35f, 0.65f, 0.9f)
    val stepLabels = listOf("Upload", "Queue", "Analyze", "Finish")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            stepThresholds.forEachIndexed { index, threshold ->
                val isReached = progress >= threshold
                Surface(
                    shape = CircleShape,
                    modifier = Modifier.size(10.dp),
                    color = if (isReached) brandColor else brandColor.copy(alpha = 0.2f)
                ) {}
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            stepLabels.forEachIndexed { index, label ->
                val isReached = progress >= stepThresholds[index]
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isReached)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.width(52.dp),
                    maxLines = 1
                )
            }
        }
    }
}
