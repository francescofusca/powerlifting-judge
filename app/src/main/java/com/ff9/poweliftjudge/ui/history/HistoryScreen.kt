package com.ff9.poweliftjudge.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ff9.poweliftjudge.R
import com.ff9.poweliftjudge.database.Lift
import com.ff9.poweliftjudge.model.LiftType
import com.ff9.poweliftjudge.ui.theme.PrimaryRed
import com.ff9.poweliftjudge.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onLiftClick: (Int) -> Unit,
    onStatsClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val lifts by viewModel.lifts.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<Lift?>(null) }
    var editTarget by remember { mutableStateOf<Lift?>(null) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Compute PR IDs: for each type, find the lift with max weight
    val prIds by remember(lifts) {
        derivedStateOf {
            lifts.filter { it.weight > 0 }
                .groupBy { it.type }
                .mapNotNull { (_, typeLifts) ->
                    val maxWeight = typeLifts.maxOf { it.weight }
                    typeLifts.firstOrNull { it.weight == maxWeight }?.id
                }
                .toSet()
        }
    }

    val builtInTabs = LiftType.entries.map { it.displayName }
    val customTabs = state.customExercises.map { it.name }
    val tabs = listOf<String?>(null) + builtInTabs + customTabs
    val selectedTabIndex = tabs.indexOf(state.selectedTab)

    Scaffold(
        topBar = {
            if (state.isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.n_selected, state.selectedIds.size))
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteSelectedDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selected), tint = PrimaryRed)
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.lift_history)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.exportCsv(context) }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.export_data))
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab row
            ScrollableTabRow(
                selectedTabIndex = if (selectedTabIndex >= 0) selectedTabIndex else 0,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, tabName ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { viewModel.selectTab(tabName) },
                        text = {
                            Text(
                                text = when (tabName) {
                                    null -> stringResource(R.string.all_lifts)
                                    LiftType.SQUAT.displayName -> stringResource(R.string.squat)
                                    LiftType.BENCH_PRESS.displayName -> stringResource(R.string.bench_press)
                                    LiftType.DEADLIFT.displayName -> stringResource(R.string.deadlift)
                                    LiftType.SUMO_DEADLIFT.displayName -> stringResource(R.string.sumo_deadlift)
                                    else -> tabName
                                }
                            )
                        }
                    )
                }
            }

            if (lifts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FitnessCenter,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.no_lifts),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Stats preview card when a specific lift type tab is selected
                    if (state.selectedTab != null) {
                        item {
                            StatsPreviewCard(
                                lifts = lifts,
                                onClick = { onStatsClick(state.selectedTab!!) }
                            )
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.history_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(lifts, key = { it.id }) { lift ->
                        LiftItem(
                            lift = lift,
                            isSelectionMode = state.isSelectionMode,
                            isSelected = lift.id in state.selectedIds,
                            isPR = lift.id in prIds,
                            onClick = {
                                if (state.isSelectionMode) {
                                    viewModel.toggleSelection(lift.id)
                                } else {
                                    onLiftClick(lift.id)
                                }
                            },
                            onLongClick = {
                                if (!state.isSelectionMode) {
                                    viewModel.enterSelectionMode(lift.id)
                                }
                            },
                            onEdit = { editTarget = lift },
                            onDelete = { deleteTarget = lift }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    // Delete single dialog
    deleteTarget?.let { lift ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_lift)) },
            text = { Text(stringResource(R.string.delete_lift_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLift(lift.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete selected dialog
    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text(stringResource(R.string.delete_selected)) },
            text = { Text(stringResource(R.string.delete_selected_message, state.selectedIds.size)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelected()
                        showDeleteSelectedDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Edit reps + weight + notes dialog
    editTarget?.let { lift ->
        var repsText by remember { mutableStateOf(lift.reps.toString()) }
        var weightText by remember { mutableStateOf(if (lift.weight > 0) lift.weight.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } else "") }
        var selectedUnit by remember { mutableStateOf(lift.weightUnit) }
        var rpeValue by remember { mutableStateOf(lift.rpe) }
        var notesText by remember { mutableStateOf(lift.notes) }
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text(stringResource(R.string.edit)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = repsText,
                        onValueChange = { repsText = it },
                        label = { Text(stringResource(R.string.enter_correct_reps)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = weightText,
                            onValueChange = { weightText = it },
                            label = { Text(stringResource(R.string.weight)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedUnit == "kg",
                                    onClick = { selectedUnit = "kg" }
                                )
                                Text("kg", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedUnit == "lb",
                                    onClick = { selectedUnit = "lb" }
                                )
                                Text("lb", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // RPE Slider
                    Text(
                        text = stringResource(R.string.rpe_label) + ": " +
                            if (rpeValue == 0) stringResource(R.string.no_rpe) else "$rpeValue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = rpeValue.toFloat(),
                        onValueChange = { rpeValue = it.roundToInt() },
                        valueRange = 0f..10f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text(stringResource(R.string.edit_notes)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newReps = repsText.toIntOrNull()
                    if (newReps != null && newReps > 0) {
                        viewModel.updateReps(lift.id, newReps)
                    }
                    val newWeight = weightText.toDoubleOrNull() ?: 0.0
                    viewModel.updateWeight(lift.id, newWeight, selectedUnit)
                    viewModel.updateRpe(lift.id, rpeValue)
                    viewModel.updateNotes(lift.id, notesText)
                    editTarget = null
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiftItem(
    lift: Lift,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isPR: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
            } else if (lift.valid) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lift.type.uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (isPR) {
                        Text(
                            text = " \uD83C\uDFC6",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
                Text(
                    text = dateFormat.format(Date(lift.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val repsText = if (lift.reps == 1) "1 rep" else "${lift.reps} reps"
                Text(
                    text = repsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (lift.weight > 0) {
                    Text(
                        text = "${lift.weight} ${lift.weightUnit}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (lift.rpe > 0) {
                    Text(
                        text = "RPE ${lift.rpe}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isSelectionMode) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = PrimaryRed
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsPreviewCard(
    lifts: List<Lift>,
    onClick: () -> Unit
) {
    if (lifts.isEmpty()) return

    val totalSets = lifts.size
    val totalReps = lifts.sumOf { it.reps }
    val liftsWithWeight = lifts.filter { it.weight > 0 }
    val prLift = liftsWithWeight.maxByOrNull { it.weight }
    val prText = if (prLift != null) "${prLift.weight.let { if (it == it.toLong().toDouble()) it.toLong().toString() else "%.1f".format(it) }} ${prLift.weightUnit}" else "-"

    // Estimated 1RM (Epley)
    var est1rm = 0.0
    var est1rmUnit = "kg"
    for (lift in liftsWithWeight) {
        val e = if (lift.reps == 1) lift.weight else lift.weight * (1 + lift.reps / 30.0)
        if (e > est1rm) {
            est1rm = e
            est1rmUnit = lift.weightUnit
        }
    }
    val est1rmText = if (est1rm > 0) "${if (est1rm == est1rm.toLong().toDouble()) est1rm.toLong().toString() else "%.1f".format(est1rm)} $est1rmUnit" else "-"

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.QueryStats,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Text(
                        text = stringResource(R.string.stats_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = stringResource(R.string.view_stats) + " \u203A",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MiniStat(stringResource(R.string.total_sets), "$totalSets")
                MiniStat(stringResource(R.string.total_reps), "$totalReps")
                MiniStat("PR", prText)
                MiniStat("1RM", est1rmText)
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
