package com.ff9.poweliftjudge.ui.visual

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

private val GREEN  = Color(0xFF00E676)
private val YELLOW = Color(0xFFFFD600)
private val RED    = Color(0xFFFF5252)
private val PANEL  = Color(0xFF1A1A2E)
private val CARD   = Color(0xFF16213E)

@Composable
fun PostSeriesSheet(
    liftName: String,
    stats: List<RepVisualStat>,
    barbellKg: Float,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PANEL)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = liftName.uppercase(),
                                color = GREEN,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp
                            )
                            Text(
                                text = "Analisi serie • ${stats.size} rep • ${barbellKg.roundToInt()} kg",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.White)
                        }
                    }
                }

                // Summary cards
                if (stats.isNotEmpty()) {
                    item {
                        val meanVel  = stats.map { it.meanVelocity }.average().toFloat()
                        val peakVel  = stats.maxOf { it.peakVelocity }
                        val meanRom  = stats.map { it.rangeOfMotionCm }.average().toFloat()
                        val meanPow  = stats.map { it.peakPowerW }.average().toFloat()
                        val meanBack = stats.map { it.backScore }.average().toFloat()
                        val meanEcc  = stats.map { it.eccentricTimeMs }.average().toLong()
                        val meanCon  = stats.map { it.concentricTimeMs }.average().toLong()

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("RIEPILOGO SERIE", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SummaryCard("Mean Velocity", "%.2f m/s".format(meanVel), velColor(meanVel), Modifier.weight(1f))
                                SummaryCard("Peak Velocity", "%.2f m/s".format(peakVel), velColor(peakVel), Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SummaryCard("Range of Motion", "%.1f cm".format(meanRom), Color.White, Modifier.weight(1f))
                                SummaryCard("Peak Power", "%.0f W".format(meanPow), if (meanPow > 500f) GREEN else YELLOW, Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SummaryCard("Eccentrico", "${meanEcc}ms", Color.White, Modifier.weight(1f))
                                SummaryCard("Concentrico", "${meanCon}ms", Color.White, Modifier.weight(1f))
                            }
                            SummaryCard("Back Shape", "%.0f / 100".format(meanBack),
                                if (meanBack >= 80f) GREEN else if (meanBack >= 60f) YELLOW else RED,
                                Modifier.fillMaxWidth())
                        }
                    }

                    // Rep-by-rep table header
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text("ANALISI REP PER REP", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        RepTableHeader()
                    }
                }

                // Rep rows
                items(stats) { rep ->
                    RepTableRow(rep)
                }

                // Velocity bar chart
                if (stats.size > 1) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("VELOCITÀ CONCENTRICA PER REP", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        VelocityBarChart(stats)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(CARD, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun RepTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CARD, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TableCell("Rep", Color.White.copy(alpha = 0.5f), Modifier.weight(0.5f))
        TableCell("Vel", Color.White.copy(alpha = 0.5f), Modifier.weight(1f))
        TableCell("Peak", Color.White.copy(alpha = 0.5f), Modifier.weight(1f))
        TableCell("RoM", Color.White.copy(alpha = 0.5f), Modifier.weight(1f))
        TableCell("Ecc", Color.White.copy(alpha = 0.5f), Modifier.weight(1f))
        TableCell("Back", Color.White.copy(alpha = 0.5f), Modifier.weight(1f))
    }
}

@Composable
private fun RepTableRow(rep: RepVisualStat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CARD, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TableCell("${rep.repNumber}", Color.White, Modifier.weight(0.5f))
        TableCell("%.2f".format(rep.meanVelocity), velColor(rep.meanVelocity), Modifier.weight(1f))
        TableCell("%.2f".format(rep.peakVelocity), velColor(rep.peakVelocity), Modifier.weight(1f))
        TableCell("%.0fcm".format(rep.rangeOfMotionCm), Color.White, Modifier.weight(1f))
        TableCell("${rep.eccentricTimeMs}ms", Color.White, Modifier.weight(1f))
        TableCell("%.0f".format(rep.backScore),
            if (rep.backScore >= 80f) GREEN else if (rep.backScore >= 60f) YELLOW else RED,
            Modifier.weight(1f))
    }
}

@Composable
private fun TableCell(text: String, color: Color, modifier: Modifier) {
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center, modifier = modifier)
}

@Composable
private fun VelocityBarChart(stats: List<RepVisualStat>) {
    val maxVel = stats.maxOf { it.meanVelocity }.coerceAtLeast(0.1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CARD, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            stats.forEach { rep ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    val barFraction = (rep.meanVelocity / maxVel).coerceIn(0.05f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(barFraction)
                            .background(velColor(rep.meanVelocity), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    )
                    Text("#${rep.repNumber}", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
                }
            }
        }
    }
}

private fun velColor(v: Float): Color = when {
    v >= 0.7f -> GREEN
    v >= 0.4f -> YELLOW
    else      -> RED
}
