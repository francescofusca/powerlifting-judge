package com.ff9.poweliftjudge.ui.visual

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.abs
import kotlin.math.roundToInt

private val GREEN  = Color(0xFF00E676)
private val YELLOW = Color(0xFFFFD600)
private val RED    = Color(0xFFFF5252)
private val PANEL_BG = Color(0xCC000000)   // 80% black

@OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun VisualJudgeScreen(
    liftName: String,
    onBack: () -> Unit,
    vm: VisualJudgeViewModel = viewModel()
) {
    val context         = LocalContext.current
    val lifecycleOwner  = LocalLifecycleOwner.current
    val state           by vm.uiState.collectAsStateWithLifecycle()

    // ── Permissions ───────────────────────────────────────────────────────────
    var camGranted by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res -> camGranted = res[Manifest.permission.CAMERA] == true }

    // ── MediaProjection screen record ─────────────────────────────────────────
    val mpManager = remember {
        context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    val screenRecordLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenRecordService.onSaved = { uri -> vm.onScreenRecordingSaved(uri) }
            @Suppress("DEPRECATION")
            val dm = DisplayMetrics().also {
                (context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager)
                    .defaultDisplay.getMetrics(it)
            }
            ContextCompat.startForegroundService(context, Intent(context, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_START
                putExtra(ScreenRecordService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenRecordService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenRecordService.EXTRA_WIDTH, dm.widthPixels)
                putExtra(ScreenRecordService.EXTRA_HEIGHT, dm.heightPixels)
                putExtra(ScreenRecordService.EXTRA_DENSITY, dm.densityDpi)
            })
            vm.onScreenRecordingStarted()
        }
    }

    fun toggleRecord() {
        if (state.isRecording) {
            context.startService(Intent(context, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP
            })
        } else {
            screenRecordLauncher.launch(mpManager.createScreenCaptureIntent())
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────
    var showWeightDialog by remember { mutableStateOf(true) }
    var selectedKg by remember { mutableStateOf(20f) }

    LaunchedEffect(Unit) {
        vm.initialize(liftName)
        permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    DisposableEffect(Unit) {
        onDispose {
            if (state.isRecording) context.startService(
                Intent(context, ScreenRecordService::class.java).apply { action = ScreenRecordService.ACTION_STOP }
            )
        }
    }

    // ── Weight dialog ─────────────────────────────────────────────────────────
    if (showWeightDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Peso bilanciere") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Seleziona il peso del bilanciere:")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10f, 15f, 20f, 25f).forEach { kg ->
                            FilterChip(
                                selected = selectedKg == kg,
                                onClick  = { selectedKg = kg },
                                label    = { Text("${kg.roundToInt()} kg") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { vm.setBarbellKg(selectedKg); showWeightDialog = false }) {
                    Text("Inizia")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWeightDialog = false; onBack() }) { Text("Annulla") }
            }
        )
        return
    }

    if (!camGranted) {
        Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Permesso camera necessario", color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                }) { Text("Concedi") }
            }
        }
        return
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN LAYOUT — fullscreen camera + lightweight overlays
    // ═══════════════════════════════════════════════════════════════════════════
    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // ── Camera preview ────────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { pv ->
                    pv.scaleType = PreviewView.ScaleType.FIT_CENTER
                    previewView = pv
                    vm.bindCamera(ctx, lifecycleOwner, pv.surfaceProvider)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Skeleton overlay (draws on top of camera) ─────────────────────────
        PoseOverlay(
            pose            = state.currentPose,
            imageWidth      = state.imageWidth,
            imageHeight     = state.imageHeight,
            rotationDegrees = state.rotationDegrees,
            isFrontCamera   = state.cameraFacing == CameraSelector.LENS_FACING_FRONT,
            analysis        = state.analysis,
            modifier        = Modifier.fillMaxSize()
        )

        // ── Top gradient bar ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
        )

        // ── Top navigation row ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(liftName.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${state.barbellKg.roundToInt()} kg", color = Color(0xFF80D8FF), fontSize = 11.sp)
            }

            IconButton(onClick = {
                previewView?.let { vm.flipCamera(context, lifecycleOwner, it.surfaceProvider) }
            }) {
                Icon(Icons.Default.FlipCameraAndroid, null, tint = Color.White)
            }
        }

        // ── Compact stats panel — top RIGHT, transparent, narrow ──────────────
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 60.dp, end = 10.dp)
                .width(96.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PANEL_BG)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Rep counter — big
            Text(
                text = "${state.analysis.repCount}",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp
            )
            Text("reps", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp,
                modifier = Modifier.offset(y = (-6).dp))

            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

            // Angles
            if (state.analysis.hipAngle > 0f)
                StatLine("Hip",  "${state.analysis.hipAngle.roundToInt()}°",
                    if (state.analysis.lockoutOk) GREEN else Color.White)
            if (state.analysis.kneeAngle > 0f)
                StatLine("Knee", "${state.analysis.kneeAngle.roundToInt()}°",
                    if (state.analysis.kneeAngle > 160f) GREEN else Color.White)

            // Depth indicator
            StatLine("Depth",
                if (state.analysis.depthOk) "OK" else "Low",
                if (state.analysis.depthOk) GREEN else YELLOW)

            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

            // Velocity with mini bar
            val vel = abs(state.currentVelocity)
            val velColor = when { vel >= 0.7f -> GREEN; vel >= 0.4f -> YELLOW; else -> Color.White }
            StatLine("Vel", "%.2f".format(vel), velColor)
            VeloBar(vel, velColor)

            // Power
            if (state.currentPowerW > 0f)
                StatLine("Pwr", "${state.currentPowerW.roundToInt()}W", velColor)

            // Back
            val bs = state.currentBackScore
            StatLine("Back", "${bs.roundToInt()}%",
                if (bs >= 80f) GREEN else if (bs >= 60f) YELLOW else RED)
        }

        // ── Feedback text — centered, top area, small ─────────────────────────
        if (state.analysis.feedback.isNotEmpty()) {
            val isGood = state.analysis.feedback.contains("GOOD", ignoreCase = true)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
            ) {
                Text(
                    text = state.analysis.feedback,
                    color = if (isGood) GREEN else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .background(PANEL_BG, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }
        }

        // ── Hold progress bar ─────────────────────────────────────────────────
        val hp = state.analysis.holdProgress
        if (hp in 0.01f..0.99f) {
            LinearProgressIndicator(
                progress = { hp },
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .align(Alignment.Center),
                color = GREEN,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }

        // ── REC badge ────────────────────────────────────────────────────────
        if (state.isRecording) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 62.dp, top = 10.dp)
                    .background(RED.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(Modifier.size(6.dp).background(Color.White, CircleShape))
                Text("REC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }

        // ── Bottom gradient ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
        )

        // ── Bottom controls ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reset
            SmallCircleButton(onClick = { vm.resetAnalyzer() }) {
                Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }

            // Start / Finish
            if (!state.isActive) {
                Button(
                    onClick = { vm.startSet() },
                    colors = ButtonDefaults.buttonColors(containerColor = GREEN),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("INIZIA", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            } else {
                Button(
                    onClick = { vm.finishSet() },
                    colors = ButtonDefaults.buttonColors(containerColor = RED),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("FINE", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }

            // Record
            SmallCircleButton(
                onClick = { toggleRecord() },
                containerColor = if (state.isRecording) RED else Color.White.copy(alpha = 0.15f)
            ) {
                Icon(
                    if (state.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    // ── Post-series sheet ─────────────────────────────────────────────────────
    if (state.showPostSeries) {
        PostSeriesSheet(
            liftName  = liftName,
            stats     = state.repStats,
            barbellKg = state.barbellKg,
            onDismiss = { vm.dismissPostSeries() }
        )
    }

    // ── Video saved ───────────────────────────────────────────────────────────
    val videoUri = state.savedVideoUri
    if (videoUri != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissSaveDialog() },
            title = { Text("Video salvato") },
            text  = { Text("Schermo registrato con overlay in galleria → PLJudge") },
            confirmButton = {
                Button(onClick = {
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, videoUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Condividi"
                    ))
                    vm.dismissSaveDialog()
                }) { Text("Condividi") }
            },
            dismissButton = { TextButton(onClick = { vm.dismissSaveDialog() }) { Text("OK") } }
        )
    }
}

// ── Reusable small widgets ─────────────────────────────────────────────────────

@Composable
private fun SmallCircleButton(
    onClick: () -> Unit,
    containerColor: Color = Color.White.copy(alpha = 0.15f),
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun StatLine(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun VeloBar(velocity: Float, color: Color) {
    val fraction = (velocity / 1.5f).coerceIn(0f, 1f)
    Canvas(modifier = Modifier.fillMaxWidth().height(3.dp).padding(vertical = 0.dp)) {
        drawRect(Color.White.copy(alpha = 0.15f), size = size)
        drawRect(color, size = Size(size.width * fraction, size.height))
    }
}
