package com.ff9.poweliftjudge.ui.settings

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import kotlinx.coroutines.launch
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
                onCalibrate = { onCalibrate("Squat") }
            )

            // Bench
            ThresholdCard(
                title = stringResource(R.string.bench_target),
                value = state.benchThreshold,
                onValueChange = { viewModel.setBenchThreshold(it) },
                onCalibrate = { onCalibrate("Bench Press") }
            )

            // Bench Hold Duration
            SettingsCard(title = stringResource(R.string.bench_hold_duration)) {
                val displayValue = if (state.benchHoldDuration == 0f) {
                    stringResource(R.string.hold_disabled)
                } else {
                    String.format("%.1fs", state.benchHoldDuration)
                }
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = state.benchHoldDuration,
                    onValueChange = { viewModel.setBenchHoldDuration(it) },
                    valueRange = 0f..3f,
                    steps = 29,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Deadlift
            ThresholdCard(
                title = stringResource(R.string.deadlift_range),
                value = state.deadliftThreshold,
                onValueChange = { viewModel.setDeadliftThreshold(it) },
                onCalibrate = { onCalibrate("Deadlift") }
            )

            // Sumo
            ThresholdCard(
                title = stringResource(R.string.sumo_range),
                value = state.sumoThreshold,
                onValueChange = { viewModel.setSumoThreshold(it) },
                onCalibrate = { onCalibrate("Sumo Deadlift") }
            )

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
    onCalibrate: () -> Unit
) {
    SettingsCard(title = title) {
        Text(
            text = "$value°",
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
    }
}
