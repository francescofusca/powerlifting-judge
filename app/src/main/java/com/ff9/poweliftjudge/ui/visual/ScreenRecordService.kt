package com.ff9.poweliftjudge.ui.visual

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import java.io.File

class ScreenRecordService : Service() {

    companion object {
        const val ACTION_START = "pljudge.action.SCREEN_RECORD_START"
        const val ACTION_STOP  = "pljudge.action.SCREEN_RECORD_STOP"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_WIDTH       = "width"
        const val EXTRA_HEIGHT      = "height"
        const val EXTRA_DENSITY     = "density"

        // Callback invoked when recording is saved (main thread)
        var onSaved: ((Uri?) -> Unit)? = null
        var isRunning = false
    }

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder?    = null
    private var virtualDisplay: VirtualDisplay?  = null
    private var tmpFile: File? = null

    private val NOTIF_ID      = 9001
    private val CHANNEL_ID    = "pljudge_screen_record"

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture(intent)
            ACTION_STOP  -> stopCapture()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseAll()
        isRunning = false
    }

    // ── Start ─────────────────────────────────────────────────────────────────

    private fun startCapture(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val resultData: Intent? = intent.getParcelableExtra(EXTRA_RESULT_DATA)
        val width   = intent.getIntExtra(EXTRA_WIDTH, 1080)
        val height  = intent.getIntExtra(EXTRA_HEIGHT, 1920)
        val density = intent.getIntExtra(EXTRA_DENSITY, 480)

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        isRunning = true

        // Create temp file for recording
        tmpFile = File(cacheDir, "pljudge_screen_${System.currentTimeMillis()}.mp4")

        // Setup MediaRecorder
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoSize(width, height)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(6_000_000)
            setAudioSamplingRate(44100)
            setOutputFile(tmpFile!!.absolutePath)
            prepare()
        }
        mediaRecorder = recorder

        // Get MediaProjection and create VirtualDisplay
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, resultData!!)

        // From Android 14, MediaProjection.Callback needed to detect permission revocation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopCapture() }
            }, null)
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "PLJudgeScreen",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder.surface, null, null
        )

        recorder.start()
    }

    // ── Stop ──────────────────────────────────────────────────────────────────

    private fun stopCapture() {
        try { mediaRecorder?.stop() } catch (_: Exception) {}
        releaseAll()
        saveToGallery()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseAll() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { mediaProjection?.stop() }  catch (_: Exception) {}
        try { mediaRecorder?.release() } catch (_: Exception) {}
        virtualDisplay  = null
        mediaProjection = null
        mediaRecorder   = null
    }

    private fun saveToGallery() {
        val file = tmpFile ?: return onSaved?.invoke(null).let { }
        try {
            val name = "PLJudge_screen_${System.currentTimeMillis()}"
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/PLJudge")
                }
                contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)?.also { uri ->
                    contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                }
            } else {
                val dest = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "PLJudge/$name.mp4"
                )
                dest.parentFile?.mkdirs()
                file.copyTo(dest, overwrite = true)
                Uri.fromFile(dest)
            }
            file.delete()
            onSaved?.invoke(uri)
        } catch (_: Exception) {
            onSaved?.invoke(null)
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Registrazione schermo", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PL Judge — Registrazione schermo")
            .setContentText("Tocca STOP per terminare la registrazione")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }
}
