package com.ff9.poweliftjudge.ui.judge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ff9.poweliftjudge.R
import com.ff9.poweliftjudge.model.LiftType
import com.ff9.poweliftjudge.ui.judge.components.AngleIndicator
import com.ff9.poweliftjudge.ui.judge.components.HoldProgressIndicator
import com.ff9.poweliftjudge.ui.theme.PrimaryRed
import com.ff9.poweliftjudge.ui.theme.SuccessGreen
import com.ff9.poweliftjudge.ui.theme.WarningYellow
import com.ff9.poweliftjudge.ui.theme.NeonBlue
import java.net.URLEncoder

@Composable
fun JudgeScreen(
    liftType: String,
    onFinishSaved: (String, Int, String, Long) -> Unit,
    onDiscard: () -> Unit,
    viewModel: JudgeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFinishDialog by remember { mutableStateOf(false) }
    var showModifyDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(liftType) {
        viewModel.initialize(liftType)
    }

    val instructionText = if (state.isCustomExercise) {
        stringResource(R.string.custom_exercise_instruction, state.targetAngle)
    } else {
        when (state.liftType) {
            LiftType.SQUAT -> stringResource(R.string.squat_instruction, state.targetAngle)
            LiftType.BENCH_PRESS -> stringResource(R.string.bench_instruction, state.targetAngle)
            LiftType.DEADLIFT -> stringResource(R.string.deadlift_instruction, state.targetAngle)
            LiftType.SUMO_DEADLIFT -> stringResource(R.string.sumo_instruction, state.targetAngle)
        }
    }

    val statusColor = when (state.statusColor) {
        StatusColor.DEFAULT -> MaterialTheme.colorScheme.onSurfaceVariant
        StatusColor.PREPARING -> WarningYellow
        StatusColor.GO -> SuccessGreen
        StatusColor.GOOD_LIFT -> SuccessGreen
        StatusColor.READY_NEXT -> NeonBlue
        StatusColor.FAILED -> PrimaryRed
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .displayCutoutPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Lift type title
            Text(
                text = if (state.isCustomExercise) state.exerciseName.uppercase()
                       else state.liftType.displayName.uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Target and reps
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${stringResource(R.string.target)}: ${state.targetAngle}°",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${stringResource(R.string.reps)}: ${state.repsCount}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Countdown overlay or Angle indicator
            AnimatedVisibility(
                visible = state.phase == JudgePhase.COUNTDOWN,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Text(
                    text = state.countdownValue.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 120.sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(
                visible = state.phase != JudgePhase.COUNTDOWN,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AngleIndicator(
                        angleDelta = if (state.phase == JudgePhase.ACTIVE) state.angleDelta else 0f,
                        progress = if (state.phase == JudgePhase.ACTIVE) state.progress else 0,
                        isGoodLift = state.isGoodLift
                    )

                    // Hold progress indicator (for any lift with hold points)
                    if (state.totalHoldPoints > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HoldProgressIndicator(
                            isActive = state.holdActive,
                            progress = state.holdProgress,
                            currentIndex = state.currentHoldIndex,
                            totalPoints = state.totalHoldPoints,
                            holdAngle = state.currentHoldAngle
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status text
            Text(
                text = state.statusText,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = statusColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Instructions (only in IDLE)
            if (state.phase == JudgePhase.IDLE) {
                Text(
                    text = instructionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Buttons
            when (state.phase) {
                JudgePhase.IDLE -> {
                    Button(
                        onClick = { viewModel.startCountdown() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.start),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                JudgePhase.COUNTDOWN -> {
                    // No buttons during countdown
                }
                JudgePhase.ACTIVE -> {
                    Button(
                        onClick = { showFinishDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryRed
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.finish),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }

    // Finish Dialog
    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text(stringResource(R.string.finish_set)) },
            text = {
                Text(stringResource(R.string.completed_reps, state.repsCount))
            },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            showFinishDialog = false
                            val data = viewModel.getFinishData()
                            val encoded = URLEncoder.encode(data.repStatsJson, "UTF-8")
                            onFinishSaved(data.liftType, data.repsCount, encoded, data.totalTime)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
                    ) {
                        Text(stringResource(R.string.save).uppercase())
                    }

                    FilledTonalButton(
                        onClick = {
                            showFinishDialog = false
                            showModifyDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.modify).uppercase())
                    }

                    OutlinedButton(
                        onClick = {
                            showFinishDialog = false
                            showDiscardDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.discard).uppercase())
                    }

                    TextButton(
                        onClick = { showFinishDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(stringResource(R.string.continue_lift).uppercase())
                    }
                }
            },
            dismissButton = {}
        )
    }

    // Modify Reps Dialog
    if (showModifyDialog) {
        var repsText by remember { mutableStateOf(state.repsCount.toString()) }
        AlertDialog(
            onDismissRequest = {
                showModifyDialog = false
                showFinishDialog = true
            },
            title = { Text(stringResource(R.string.modify_reps)) },
            text = {
                OutlinedTextField(
                    value = repsText,
                    onValueChange = { repsText = it },
                    label = { Text(stringResource(R.string.enter_correct_reps)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val newReps = repsText.toIntOrNull()
                    if (newReps != null && newReps > 0) {
                        viewModel.updateRepsCount(newReps)
                        showModifyDialog = false
                        val data = viewModel.getFinishData()
                        val encoded = URLEncoder.encode(data.repStatsJson, "UTF-8")
                        onFinishSaved(data.liftType, newReps, encoded, data.totalTime)
                    }
                }) {
                    Text(stringResource(R.string.confirm_and_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showModifyDialog = false
                    showFinishDialog = true
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Discard Confirm Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = {
                showDiscardDialog = false
                showFinishDialog = true
            },
            title = { Text(stringResource(R.string.discard_set_title)) },
            text = { Text(stringResource(R.string.discard_set_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onDiscard()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
                ) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    showFinishDialog = true
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
