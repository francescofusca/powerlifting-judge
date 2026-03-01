package com.ff9.poweliftjudge.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ff9.poweliftjudge.R
import com.ff9.poweliftjudge.model.LiftType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiftStatsScreen(
    liftType: String,
    onBack: () -> Unit,
    viewModel: LiftStatsViewModel = viewModel()
) {
    val statsFlow = remember(liftType) { viewModel.getStatsFlow(liftType) }
    val state by statsFlow.collectAsStateWithLifecycle(initialValue = LiftStatsUiState())
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    val displayName = when (LiftType.fromDisplayName(liftType)) {
        LiftType.SQUAT -> stringResource(R.string.squat)
        LiftType.BENCH_PRESS -> stringResource(R.string.bench_press)
        LiftType.DEADLIFT -> stringResource(R.string.deadlift)
        LiftType.SUMO_DEADLIFT -> stringResource(R.string.sumo_deadlift)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$displayName - ${stringResource(R.string.stats_title)}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (!state.hasData) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_stats),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Summary card
                StatsCard(stringResource(R.string.stats_title)) {
                    StatsRow(stringResource(R.string.total_sets), "${state.totalSets}")
                    StatsRow(stringResource(R.string.total_reps), "${state.totalReps}")
                    if (state.prWeight > 0) {
                        StatsRow(
                            "PR",
                            "${formatWeight(state.prWeight)} ${state.prWeightUnit} (${dateFormat.format(Date(state.prDate))})"
                        )
                    }
                    if (state.totalVolume > 0) {
                        StatsRow(
                            stringResource(R.string.total_volume),
                            "${formatWeight(state.totalVolume)} ${state.volumeUnit}"
                        )
                    }
                    if (state.hasRpeData) {
                        StatsRow(
                            stringResource(R.string.avg_rpe),
                            "%.1f".format(state.avgRpe)
                        )
                    }
                }

                // Estimated 1RM
                if (state.est1rm > 0) {
                    StatsCard(stringResource(R.string.estimated_1rm)) {
                        Text(
                            text = "${formatWeight(state.est1rm)} ${state.est1rmUnit}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Epley: weight × (1 + reps/30)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Average rep times
                if (state.hasTimeData) {
                    StatsCard(stringResource(R.string.avg_rep_time)) {
                        StatsRow(stringResource(R.string.avg_descent), formatMs(state.avgDescent))
                        StatsRow(stringResource(R.string.avg_ascent), formatMs(state.avgAscent))
                        StatsRow(stringResource(R.string.avg_rep_time), formatMs(state.avgRepTime))
                    }
                }

                // Best set
                if (state.bestSetWeight > 0) {
                    StatsCard(stringResource(R.string.best_set)) {
                        Text(
                            text = "${state.bestSetReps} reps @ ${formatWeight(state.bestSetWeight)} ${state.bestSetUnit}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = dateFormat.format(Date(state.bestSetDate)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Weight progression
                if (state.recentSets.isNotEmpty()) {
                    StatsCard(stringResource(R.string.weight_progression)) {
                        state.recentSets.forEach { set ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${formatWeight(set.weight)} ${set.weightUnit} × ${set.reps}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = dateFormat.format(Date(set.date)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Training frequency
                StatsCard(stringResource(R.string.training_frequency)) {
                    StatsRow(stringResource(R.string.last_week), "${state.setsLastWeek} sets")
                    StatsRow(stringResource(R.string.last_month), "${state.setsLastMonth} sets")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun StatsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatWeight(w: Double): String {
    return if (w == w.toLong().toDouble()) w.toLong().toString() else "%.1f".format(w)
}

private fun formatMs(ms: Long): String {
    return if (ms < 1000) "${ms}ms"
    else "%.2fs".format(ms / 1000.0)
}
