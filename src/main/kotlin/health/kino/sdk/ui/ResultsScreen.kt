package health.kino.sdk.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import health.kino.sdk.ScanResult
import health.kino.sdk.ScanStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KinoResultsScreen(
    result: ScanResult,
    onDone: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    brandColor: Color = MaterialTheme.colorScheme.primary,
    doneLabel: String = "Done"
) {
    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (result.status == ScanStatus.FAILED || result.status == ScanStatus.REJECTED) {
                        if (onRetry != null) {
                            OutlinedButton(
                                onClick = onRetry,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Try again")
                            }
                        }
                    }

                    if (onDone != null) {
                        Button(
                            onClick = onDone,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                        ) {
                            Text(
                                text = doneLabel,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text = "Your results",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            when (result.status) {
                ScanStatus.COMPLETED -> CompletedResults(result = result, brandColor = brandColor)
                ScanStatus.FAILED    -> ErrorState(message = "Scan failed. Please try again.", brandColor = brandColor)
                ScanStatus.REJECTED  -> ErrorState(message = "Scan was rejected by quality checks. Please retake your photos in better lighting.", brandColor = brandColor)
                else                 -> ErrorState(message = "Unexpected scan status: ${result.status}", brandColor = brandColor)
            }
        }
    }
}

@Composable
private fun CompletedResults(result: ScanResult, brandColor: Color) {
    // Primary metric: body fat percentage
    val bodyFat = result.bodyFatPercentage ?: result.bodyFatPercentageSmoothed

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = brandColor.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Body fat",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            if (bodyFat != null) {
                Text(
                    text = "%.1f%%".format(bodyFat),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = brandColor
                )
                if (result.bodyFatPercentageSmoothed != null && result.bodyFatPercentage != null
                    && result.bodyFatPercentageSmoothed != result.bodyFatPercentage) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Smoothed: %.1f%%".format(result.bodyFatPercentageSmoothed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                Text(
                    text = "—",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }

    // Secondary metrics: muscle mass + bone mineral content
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricCard(
            label = "Muscle mass",
            value = result.estimatedMuscleMassLbs?.let { "%.1f lbs".format(it) },
            isDerived = result.derived?.contains("estimated_muscle_mass_lbs") == true,
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            label = "Bone mineral",
            value = result.boneMineralContentLbs?.let { "%.1f lbs".format(it) },
            isDerived = false,
            modifier = Modifier.weight(1f)
        )
    }

    // Derived-value disclosure
    if (!result.derived.isNullOrEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = "* Values marked with an asterisk are derived estimates, not directly measured.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    // Model info & scan ID
    result.modelVersion?.let { version ->
        Text(
            text = "Model v$version",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
    result.scanId?.let { id ->
        Text(
            text = "Scan: $id",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String?,
    isDerived: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = if (isDerived) "$label *" else label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value ?: "—",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, brandColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}
