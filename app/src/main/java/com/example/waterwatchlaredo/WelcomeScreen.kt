package com.example.waterwatchlaredo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(
    onExplore: () -> Unit,
    onExploreDemo: () -> Unit,
    loadingDemo: Boolean = false,
    demoError: String = ""
) {
    val waterColor = MaterialTheme.colorScheme.primary
    val paleWater = MaterialTheme.colorScheme.primaryContainer
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text("WATERWATCH / LAREDO", color = waterColor,
                    style = MaterialTheme.typography.labelLarge)
                // A decorative water drop and ripples, not map data or a safety indicator.
                Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                    val drop = Path().apply {
                        moveTo(size.width * 0.5f, 0f)
                        cubicTo(size.width * 0.38f, size.height * 0.5f,
                            size.width * 0.40f, size.height * 0.78f,
                            size.width * 0.5f, size.height * 0.78f)
                        cubicTo(size.width * 0.60f, size.height * 0.78f,
                            size.width * 0.62f, size.height * 0.5f, size.width * 0.5f, 0f)
                        close()
                    }
                    drawOval(paleWater, topLeft = Offset(size.width * 0.15f, size.height * 0.75f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.7f, size.height * 0.2f))
                    drawPath(drop, waterColor)
                    drawLine(waterColor, Offset(size.width * 0.2f, size.height * 0.95f),
                        Offset(size.width * 0.8f, size.height * 0.95f), strokeWidth = 2.dp.toPx())
                }
                Text("Laredo, let's\nwatch our water.", fontSize = 40.sp, lineHeight = 44.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Text("Small observations. A clearer local picture.",
                    color = waterColor, style = MaterialTheme.typography.titleMedium)
                Text("Notice an unusual taste, smell, color, or low pressure? " +
                    "WaterWatch helps you document it and explore what has been reported nearby.")
                WelcomeDetail("Explore the map", "See report patterns and optional water-main reference lines.")
                WelcomeDetail("Add your observation", "Choose an issue, attach a location, and save a report.")
                WelcomeDetail("Check the Community board", "Tell resident observations apart from city-published updates.")
                Surface(color = paleWater, shape = MaterialTheme.shapes.medium) {
                    Text("Prototype: resident reports currently stay on this device. " +
                        "Demo reports are fictional. This app does not replace official city notices.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onExplore, enabled = !loadingDemo, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Explore the map")
            }
            OutlinedButton(
                onClick = onExploreDemo,
                enabled = !loadingDemo,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text(if (loadingDemo) "Preparing sample reports…" else "Explore demo · sample data") }
            if (demoError.isNotEmpty()) {
                Text(demoError, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            Text("No account needed to explore. Sign in when you're ready to report.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        }
    }
}

@Composable
private fun WelcomeDetail(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}
