package com.ff9.poweliftjudge.ui.total

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ff9.poweliftjudge.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerliftingTotalScreen(
    onBack: () -> Unit,
    viewModel: PowerliftingTotalViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.powerlifting_total)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (!state.hasData) {
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = stringResource(R.string.no_total_data),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Current Total card
                TotalCard(title = stringResource(R.string.current_total)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MiniStat("SQ", formatWeight(state.squatPR.weight))
                        Text("+", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MiniStat("BP", formatWeight(state.benchPR.weight))
                        Text("+", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MiniStat("DL", formatWeight(state.deadliftPR.weight))
                        Text("=", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MiniStat(formatWeight(state.currentTotal), state.weightUnit, highlight = true)
                    }
                }

                // Personal Records card
                TotalCard(title = stringResource(R.string.personal_records)) {
                    PRRow("SQ", state.squatPR, dateFormat)
                    PRRow("BP", state.benchPR, dateFormat)
                    PRRow("DL", state.deadliftPR, dateFormat)
                }

                // Best Session Total
                if (state.sessionTotals.isNotEmpty()) {
                    TotalCard(title = stringResource(R.string.best_session_total)) {
                        Text(
                            text = "${formatWeight(state.bestTotal)} ${state.weightUnit}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (state.bestTotalDate > 0) {
                            Text(
                                text = dateFormat.format(Date(state.bestTotalDate)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Relative Strength
                if (state.bodyWeight > 0 && state.relativeStrength > 0) {
                    TotalCard(title = stringResource(R.string.relative_strength)) {
                        Text(
                            text = "%.2f".format(state.relativeStrength),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${formatWeight(state.currentTotal)} / ${formatWeight(state.bodyWeight.toDouble())} ${state.weightUnit}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Session History
                if (state.sessionTotals.isNotEmpty()) {
                    TotalCard(title = stringResource(R.string.session_history)) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            HeaderText("Date", Modifier.weight(1.2f))
                            HeaderText("SQ", Modifier.weight(0.7f))
                            HeaderText("BP", Modifier.weight(0.7f))
                            HeaderText("DL", Modifier.weight(0.7f))
                            HeaderText("Total", Modifier.weight(0.9f))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        state.sessionTotals.forEach { session ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                CellText(dateFormat.format(Date(session.date)), Modifier.weight(1.2f))
                                CellText(formatWeight(session.squatBest), Modifier.weight(0.7f))
                                CellText(formatWeight(session.benchBest), Modifier.weight(0.7f))
                                CellText(formatWeight(session.deadliftBest), Modifier.weight(0.7f))
                                Text(
                                    text = formatWeight(session.total),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(0.9f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TotalCard(title: String, content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
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
private fun MiniStat(label: String, value: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PRRow(label: String, pr: LiftPR, dateFormat: SimpleDateFormat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (pr.weight > 0) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${formatWeight(pr.weight)} ${pr.weightUnit}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                if (pr.date > 0) {
                    Text(
                        text = dateFormat.format(Date(pr.date)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                text = "-",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeaderText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun CellText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

private fun formatWeight(w: Double): String {
    return if (w == 0.0) "-"
    else if (w == w.toLong().toDouble()) w.toLong().toString()
    else "%.1f".format(w)
}
