package health.kino.sdk.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class InstructionItem(val title: String, val body: String)

fun defaultInstructions(): List<InstructionItem> = listOf(
    InstructionItem(
        title = "Lighting",
        body = "Stand in a well-lit area with even, natural or indoor light. Avoid standing in front of a bright window or in shadows. Overhead lighting works well — harsh side lighting can create shadows that affect accuracy."
    ),
    InstructionItem(
        title = "Clothing",
        body = "Wear form-fitting clothing such as athletic shorts, a swimsuit, or compression wear. Loose or baggy clothes obscure body contours and reduce accuracy. Remove shoes, belts, and bulky accessories."
    ),
    InstructionItem(
        title = "Distance & framing",
        body = "Stand approximately 6–8 feet from the camera. Your full body should be visible from head to toe with a small margin on each side. Use the silhouette guide to align yourself before capturing."
    ),
    InstructionItem(
        title = "Posture",
        body = "Stand upright with your feet shoulder-width apart, arms slightly away from your sides, and palms facing inward. Look straight ahead. Relax your muscles — do not flex or hold your breath."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KinoInstructionsScreen(
    onStart: () -> Unit,
    brandColor: Color = MaterialTheme.colorScheme.primary,
    ctaLabel: String = "Start scan",
    title: String = "How to scan",
    instructions: List<InstructionItem> = defaultInstructions()
) {
    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                    ) {
                        Text(
                            text = ctaLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = 24.dp,
                bottom = innerPadding.calculateBottomPadding() + 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(instructions) { item ->
                InstructionCard(item = item)
            }
        }
    }
}

@Composable
private fun InstructionCard(item: InstructionItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
        }
    }
}
