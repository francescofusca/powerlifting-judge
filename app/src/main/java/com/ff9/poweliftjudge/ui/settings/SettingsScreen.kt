package com.ff9.poweliftjudge.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.ff9.poweliftjudge.LocaleHelper
import com.ff9.poweliftjudge.R
import com.ff9.poweliftjudge.data.backup.BackupManager
import com.ff9.poweliftjudge.model.HoldPoint
import com.ff9.poweliftjudge.ui.theme.PrimaryRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCalibrate: (String) -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSoundDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val restartMessage = stringResource(R.string.restart_for_theme)
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val backupSuccessMsg = stringResource(R.string.backup_success)
    val backupErrorMsg = stringResource(R.string.error, "Export failed")
    val restoreSuccessMsg = stringResource(R.string.restore_success)

    // Export launcher: create a .json file
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup { json ->
                if (json != null) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(json.toByteArray())
                        }
                        scope.launch { snackbarHostState.showSnackbar(backupSuccessMsg) }
                    } catch (_: Exception) {
                        scope.launch { snackbarHostState.showSnackbar(backupErrorMsg) }
                    }
                } else {
                    scope.launch { snackbarHostState.showSnackbar(backupErrorMsg) }
                }
            }
        }
    }

    // Import launcher: pick a .json file
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreConfirm = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Countdown Timer
            SettingsCard(title = stringResource(R.string.countdown_timer)) {
                val timerText = if (state.countdownTimer == 1) {
                    "1 ${stringResource(R.string.second)}"
                } else {
                    "${state.countdownTimer} ${stringResource(R.string.seconds)}"
                }
                Text(
                    text = timerText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = state.countdownTimer.toFloat(),
                    onValueChange = { viewModel.setCountdownTimer(it.roundToInt()) },
                    valueRange = 1f..120f,
                    steps = 118,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Squat
            ThresholdCard(
                title = stringResource(R.string.squat_target),
                value = state.squatThreshold,
                onValueChange = { viewModel.setSquatThreshold(it) },
                onCalibrate = { onCalibrate("Squat") },
                holdPoints = state.holdPointsMap["threshold_squat"] ?: emptyList(),
                maxAngle = state.squatThreshold,
                onAddHoldPoint = { viewModel.addHoldPoint("threshold_squat", it) },
                onRemoveHoldPoint = { viewModel.removeHoldPoint("threshold_squat", it) }
            )

            // Bench
            ThresholdCard(
                title = stringResource(R.string.bench_target),
                value = state.benchThreshold,
                onValueChange = { viewModel.setBenchThreshold(it) },
                onCalibrate = { onCalibrate("Bench Press") },
                holdPoints = state.holdPointsMap["threshold_bench"] ?: emptyList(),
                maxAngle = state.benchThreshold,
                onAddHoldPoint = { viewModel.addHoldPoint("threshold_bench", it) },
                onRemoveHoldPoint = { viewModel.removeHoldPoint("threshold_bench", it) }
            )

            // Deadlift
            ThresholdCard(
                title = stringResource(R.string.deadlift_range),
                value = state.deadliftThreshold,
                onValueChange = { viewModel.setDeadliftThreshold(it) },
                onCalibrate = { onCalibrate("Deadlift") },
                holdPoints = state.holdPointsMap["threshold_deadlift"] ?: emptyList(),
                maxAngle = state.deadliftThreshold,
                onAddHoldPoint = { viewModel.addHoldPoint("threshold_deadlift", it) },
                onRemoveHoldPoint = { viewModel.removeHoldPoint("threshold_deadlift", it) }
            )

            // Sumo
            ThresholdCard(
                title = stringResource(R.string.sumo_range),
                value = state.sumoThreshold,
                onValueChange = { viewModel.setSumoThreshold(it) },
                onCalibrate = { onCalibrate("Sumo Deadlift") },
                holdPoints = state.holdPointsMap["threshold_sumo"] ?: emptyList(),
                maxAngle = state.sumoThreshold,
                onAddHoldPoint = { viewModel.addHoldPoint("threshold_sumo", it) },
                onRemoveHoldPoint = { viewModel.removeHoldPoint("threshold_sumo", it) }
            )

            // Custom Exercises
            if (state.customExercises.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.custom_exercises_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                var deleteTarget by remember { mutableStateOf<String?>(null) }

                state.customExercises.forEach { exercise ->
                    val threshold = state.customThresholds[exercise.name] ?: exercise.defaultThreshold
                    CustomThresholdCard(
                        title = exercise.name.uppercase(),
                        value = threshold,
                        onValueChange = { viewModel.setCustomThreshold(exercise.name, it) },
                        onCalibrate = { onCalibrate(exercise.name) },
                        onDelete = { deleteTarget = exercise.name },
                        holdPoints = state.holdPointsMap[exercise.prefsKey] ?: emptyList(),
                        maxAngle = threshold,
                        onAddHoldPoint = { viewModel.addHoldPoint(exercise.prefsKey, it) },
                        onRemoveHoldPoint = { viewModel.removeHoldPoint(exercise.prefsKey, it) }
                    )
                }

                // Delete confirmation dialog
                deleteTarget?.let { name ->
                    AlertDialog(
                        onDismissRequest = { deleteTarget = null },
                        title = { Text(stringResource(R.string.delete_exercise)) },
                        text = { Text(stringResource(R.string.delete_exercise_message, name)) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.deleteCustomExercise(name)
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
            }

            // Sound Selection
            SettingsCard(title = stringResource(R.string.start_sound)) {
                Text(
                    text = stringResource(R.string.current_sound, "${state.startSound}.mp3"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { showSoundDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.change_sound))
                }
            }

            // Language
            SettingsCard(title = stringResource(R.string.language_setting)) {
                val langName = LocaleHelper.getLanguageName(context, state.language)
                Text(
                    text = stringResource(R.string.current_language, langName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { showLanguageDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.change_language))
                }
            }

            // Weight Unit
            SettingsCard(title = stringResource(R.string.weight_unit)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.weightUnit.uppercase(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = state.weightUnit == "lb",
                        onCheckedChange = {
                            viewModel.setWeightUnit(if (it) "lb" else "kg")
                        }
                    )
                    Text(
                        text = if (state.weightUnit == "lb") stringResource(R.string.lb) else stringResource(R.string.kg),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // Body Weight
            SettingsCard(title = stringResource(R.string.body_weight_setting)) {
                OutlinedTextField(
                    value = state.bodyWeight,
                    onValueChange = { viewModel.setBodyWeight(it) },
                    label = { Text(stringResource(R.string.enter_body_weight)) },
                    suffix = { Text(state.weightUnit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Dark mode toggle
            SettingsCard(title = stringResource(R.string.dark_mode_title)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.darkMode) stringResource(R.string.on) else stringResource(R.string.off),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = state.darkMode,
                        onCheckedChange = {
                            viewModel.setDarkMode(it)
                            scope.launch {
                                snackbarHostState.showSnackbar(restartMessage)
                            }
                        }
                    )
                }
            }

            // Backup / Restore
            SettingsCard(title = stringResource(R.string.backup_restore)) {
                FilledTonalButton(
                    onClick = {
                        val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        exportLauncher.launch("pljudge_backup_$date.json")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.backup_data))
                }
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.restore_data))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.by_ff9),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Restore confirmation dialog
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirm = false
                pendingRestoreUri = null
            },
            title = { Text(stringResource(R.string.restore_data)) },
            text = { Text(stringResource(R.string.restore_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirm = false
                        val uri = pendingRestoreUri ?: return@Button
                        pendingRestoreUri = null
                        try {
                            val jsonString = context.contentResolver.openInputStream(uri)
                                ?.bufferedReader()?.use { it.readText() } ?: return@Button
                            viewModel.importBackup(jsonString) { result ->
                                scope.launch {
                                    when (result) {
                                        is BackupManager.ImportResult.Success ->
                                            snackbarHostState.showSnackbar(restoreSuccessMsg)
                                        is BackupManager.ImportResult.Error ->
                                            snackbarHostState.showSnackbar(result.message)
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            scope.launch { snackbarHostState.showSnackbar("Restore failed") }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
                ) {
                    Text(stringResource(R.string.confirm_restore))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    pendingRestoreUri = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Sound dialog
    if (showSoundDialog) {
        val soundOptions = listOf("start" to "Start.mp3", "race_start" to "Race Start.mp3", "beep_short" to "Beep Short.mp3")
        AlertDialog(
            onDismissRequest = { showSoundDialog = false },
            title = { Text(stringResource(R.string.select_start_sound)) },
            text = {
                Column {
                    soundOptions.forEach { (key, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.startSound == key,
                                onClick = {
                                    viewModel.setStartSound(key)
                                    showSoundDialog = false
                                }
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSoundDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Language dialog
    if (showLanguageDialog) {
        val languages = listOf(
            "en" to R.string.lang_english,
            "it" to R.string.lang_italian,
            "es" to R.string.lang_spanish,
            "ru" to R.string.lang_russian,
            "pt" to R.string.lang_portuguese,
            "de" to R.string.lang_german,
            "fr" to R.string.lang_french,
            "ja" to R.string.lang_japanese
        )
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.select_language)) },
            text = {
                Column {
                    languages.forEach { (code, nameRes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.language == code,
                                onClick = {
                                    viewModel.setLanguage(code)
                                    LocaleHelper.setLocale(context, code)
                                    showLanguageDialog = false
                                    (context as? Activity)?.recreate()
                                }
                            )
                            Text(
                                text = stringResource(nameRes),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
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
private fun ThresholdCard(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    onCalibrate: () -> Unit,
    holdPoints: List<HoldPoint>,
    maxAngle: Int,
    onAddHoldPoint: (HoldPoint) -> Unit,
    onRemoveHoldPoint: (Int) -> Unit
) {
    SettingsCard(title = title) {
        Text(
            text = "$value\u00B0",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..130f,
            steps = 129,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        FilledTonalButton(
            onClick = onCalibrate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.calibrate))
        }
        HoldPointsSection(
            holdPoints = holdPoints,
            maxAngle = maxAngle,
            onAdd = onAddHoldPoint,
            onRemove = onRemoveHoldPoint
        )
    }
}

@Composable
private fun CustomThresholdCard(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    onCalibrate: () -> Unit,
    onDelete: () -> Unit,
    holdPoints: List<HoldPoint>,
    maxAngle: Int,
    onAddHoldPoint: (HoldPoint) -> Unit,
    onRemoveHoldPoint: (Int) -> Unit
) {
    SettingsCard(title = title) {
        Text(
            text = "$value\u00B0",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..130f,
            steps = 129,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onCalibrate,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.calibrate))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = PrimaryRed,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        HoldPointsSection(
            holdPoints = holdPoints,
            maxAngle = maxAngle,
            onAdd = onAddHoldPoint,
            onRemove = onRemoveHoldPoint
        )
    }
}

@Composable
private fun HoldPointsSection(
    holdPoints: List<HoldPoint>,
    maxAngle: Int,
    onAdd: (HoldPoint) -> Unit,
    onRemove: (Int) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    Text(
        text = stringResource(R.string.hold_points),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    Spacer(modifier = Modifier.height(4.dp))

    if (holdPoints.isEmpty()) {
        Text(
            text = stringResource(R.string.no_hold_points),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        holdPoints.forEachIndexed { index, hp ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        R.string.hold_at_angle,
                        hp.angleDegrees,
                        String.format("%.1fs", hp.durationMs / 1000f)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = PrimaryRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    TextButton(
        onClick = { showAddDialog = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.add_hold_point))
    }

    if (showAddDialog) {
        var angle by remember { mutableIntStateOf(maxAngle.coerceAtLeast(1) / 2) }
        var duration by remember { mutableFloatStateOf(1.0f) }
        val effectiveMax = maxAngle.coerceAtLeast(1)

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.add_hold_point)) },
            text = {
                Column {
                    Text(
                        text = "${stringResource(R.string.hold_angle)}: $angle\u00B0",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = angle.toFloat(),
                        onValueChange = { angle = it.roundToInt() },
                        valueRange = 1f..effectiveMax.toFloat(),
                        steps = (effectiveMax - 2).coerceAtLeast(0),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${stringResource(R.string.hold_duration)}: ${String.format("%.1fs", duration)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = duration,
                        onValueChange = { duration = (it * 10).roundToInt() / 10f },
                        valueRange = 0.1f..5f,
                        steps = 48,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAdd(HoldPoint(angle, (duration * 1000).toLong()))
                    showAddDialog = false
                }) {
                    Text(stringResource(R.string.add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
