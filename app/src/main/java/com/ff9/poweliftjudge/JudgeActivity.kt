package com.ff9.poweliftjudge

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ff9.poweliftjudge.database.Lift
import com.ff9.poweliftjudge.database.LiftDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class JudgeActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private lateinit var progressBar: ProgressBar
    private lateinit var angleText: TextView
    private lateinit var statusText: TextView
    private lateinit var btnStart: Button
    private lateinit var btnFinish: Button
    private lateinit var tvCountdown: TextView
    private lateinit var tvRepsCounter: TextView

    private lateinit var db: LiftDatabase
    private var mediaPlayerBip: MediaPlayer? = null
    private var mediaPlayerStart: MediaPlayer? = null
    private lateinit var vibrator: Vibrator

    private var targetAngle: Int = 90
    private var startPitch: Float? = null
    private var currentPitch: Float = 0f
    private var isLiftGood = false
    private var liftType: String = "Squat"
    private var repsCount: Int = 0
    private var countdownSeconds: Int = 3
    private var isCountdownRunning = false
    private var isLiftActive = false

    // Time tracking
    private var setStartTime: Long = 0
    private var lastRepTime: Long = 0
    private val repTimesList = mutableListOf<Long>() // Time between reps in milliseconds

    // Fase tracking per discesa/salita
    private var repStartTime: Long = 0
    private var descentStartTime: Long = 0
    private var ascentStartTime: Long = 0
    private var wasInTargetZone = false
    private val repStatsList = mutableListOf<RepStats>()

    // Throttle UI updates
    private var lastUiUpdate = 0L

    // For fallback sensor fusion
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_judge)

            progressBar = findViewById(R.id.progressBarDepth)
            angleText = findViewById(R.id.tvAngle)
            statusText = findViewById(R.id.tvStatus)
            btnStart = findViewById(R.id.btnStart)
            btnFinish = findViewById(R.id.btnFinish)
            tvCountdown = findViewById(R.id.tvCountdown)
            tvRepsCounter = findViewById(R.id.tvRepsCounter)

            val tvLiftType: TextView = findViewById(R.id.tvLiftType)
            val tvTarget: TextView = findViewById(R.id.tvTarget)
            val tvInstructions: TextView = findViewById(R.id.tvInstructions)

            liftType = intent.getStringExtra("LIFT_TYPE") ?: "Squat"
            tvLiftType.text = liftType.uppercase()

            // Load specific settings
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            countdownSeconds = prefs.getInt("countdown_timer", 3)

            when(liftType) {
                "Squat" -> {
                    targetAngle = prefs.getInt("threshold_squat", 90)
                    tvInstructions.text = getString(R.string.squat_instruction, targetAngle)
                }
                "Bench Press" -> {
                    targetAngle = prefs.getInt("threshold_bench", 90)
                    tvInstructions.text = getString(R.string.bench_instruction, targetAngle)
                }
                "Deadlift" -> {
                    targetAngle = prefs.getInt("threshold_deadlift", 60)
                    tvInstructions.text = getString(R.string.deadlift_instruction, targetAngle)
                }
                "Sumo Deadlift" -> {
                    targetAngle = prefs.getInt("threshold_sumo", 60)
                    tvInstructions.text = getString(R.string.sumo_instruction, targetAngle)
                }
            }
            tvTarget.text = "${getString(R.string.target)}: $targetAngle°"
            tvRepsCounter.text = "${getString(R.string.reps)}: 0"

            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (rotationSensor == null) {
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            }

            db = LiftDatabase.getDatabase(this)

            // Safe Media Player Init
            try {
                mediaPlayerBip = MediaPlayer.create(this, R.raw.bip)

                // Load start sound from settings
                val startSound = prefs.getString("start_sound", "start") ?: "start"
                val soundResId = when(startSound) {
                    "race_start" -> R.raw.race_start
                    "beep_short" -> R.raw.beep_short
                    else -> R.raw.start
                }
                mediaPlayerStart = MediaPlayer.create(this, soundResId)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibrator = vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            btnStart.setOnClickListener {
                if (!isCountdownRunning && !isLiftActive) {
                    startCountdown()
                }
            }

            btnFinish.setOnClickListener {
                if (isLiftActive) {
                    showFinishDialog()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: run {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        // Release media players to avoid crashes
        try {
            if (mediaPlayerBip?.isPlaying == true) {
                mediaPlayerBip?.stop()
            }
            if (mediaPlayerStart?.isPlaying == true) {
                mediaPlayerStart?.stop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayerBip?.release()
        mediaPlayerBip = null
        mediaPlayerStart?.release()
        mediaPlayerStart = null
    }

    private fun startCountdown() {
        isCountdownRunning = true
        repsCount = 0
        repTimesList.clear()
        repStatsList.clear()
        wasInTargetZone = false
        tvRepsCounter.text = "${getString(R.string.reps)}: 0"
        btnStart.isEnabled = false
        btnStart.alpha = 0.5f
        statusText.text = getString(R.string.preparing)
        statusText.setTextColor(ContextCompat.getColor(this, R.color.warning_yellow))

        tvCountdown.visibility = TextView.VISIBLE
        angleText.visibility = TextView.GONE

        var countdown = countdownSeconds
        val handler = android.os.Handler(mainLooper)

        val runnable = object : Runnable {
            override fun run() {
                if (countdown > 0) {
                    tvCountdown.text = countdown.toString()
                    tvCountdown.startAnimation(AnimationUtils.loadAnimation(this@JudgeActivity, R.anim.bounce))
                    countdown--
                    handler.postDelayed(this, 1000)
                } else {
                    // Countdown finished
                    tvCountdown.visibility = TextView.GONE
                    angleText.visibility = TextView.VISIBLE
                    isCountdownRunning = false
                    isLiftActive = true

                    // Show FINISCHI button
                    btnFinish.visibility = Button.VISIBLE
                    btnFinish.backgroundTintList = ContextCompat.getColorStateList(this@JudgeActivity, R.color.accent_red)

                    // Start time tracking
                    setStartTime = System.currentTimeMillis()
                    lastRepTime = setStartTime

                    // Auto-calibrate
                    startPitch = currentPitch

                    // Play start sound
                    try {
                        mediaPlayerStart?.start()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    statusText.text = getString(R.string.go)
                    statusText.setTextColor(ContextCompat.getColor(this@JudgeActivity, R.color.success_green))

                    // Vibrate to signal start
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(200)
                    }
                }
            }
        }
        handler.post(runnable)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            updateOrientation()
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            updateOrientationFallback()
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            updateOrientationFallback()
        }
    }

    private fun updateOrientationFallback() {
        if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
            updateOrientation()
        }
    }

    private fun updateOrientation() {
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        currentPitch = pitchDeg
        
        val now = System.currentTimeMillis()
        if (now - lastUiUpdate > 50) { // Update UI max 20 times per second
            processLiftLogic(pitchDeg)
            lastUiUpdate = now
        }
    }

    private fun processLiftLogic(pitch: Float) {
        if (pitch.isNaN()) return

        val base = startPitch

        if (base == null || !isLiftActive) {
            angleText.text = "${pitch.roundToInt()}°"
            return
        }

        // Logic valid for all: Delta from Start
        // Squat: Start 0 -> Go to 90 -> Delta 90
        // Deadlift: Start 60 -> Go to 0 -> Delta 60
        var delta = abs(pitch - base)
        if (delta.isNaN()) delta = 0f
        if (delta > 180) delta = 360 - delta

        val progress = ((delta / targetAngle) * 100).toInt().coerceIn(0, 100)

        progressBar.progress = progress
        angleText.text = "${delta.roundToInt()}°"

        // Tracciamento fasi discesa/salita
        val currentTime = System.currentTimeMillis()
        val threshold = targetAngle * 0.2f // Soglia per considerare "vicino a zero"

        if (isLiftActive) {
            if (delta < threshold && !wasInTargetZone && descentStartTime == 0L) {
                // Inizia discesa da posizione zero
                repStartTime = currentTime
                descentStartTime = currentTime
            } else if (delta >= targetAngle && !wasInTargetZone && descentStartTime > 0L) {
                // Raggiunto target - fine discesa, inizio salita
                ascentStartTime = currentTime
                wasInTargetZone = true
            }
        }

        if (delta >= targetAngle && !isLiftGood) {
            isLiftGood = true
            onGoodLift()
        } else if (delta < threshold && isLiftGood && wasInTargetZone) {
            // Tornato a zero - completa la rep con statistiche
            val repEndTime = currentTime
            if (repStartTime > 0 && descentStartTime > 0 && ascentStartTime > 0) {
                val descentTime = ascentStartTime - descentStartTime
                val ascentTime = repEndTime - ascentStartTime
                val totalTime = repEndTime - repStartTime

                repStatsList.add(RepStats(descentTime, ascentTime, totalTime))
            }

            // Reset per prossima rep
            isLiftGood = false
            wasInTargetZone = false
            repStartTime = 0
            descentStartTime = 0
            ascentStartTime = 0

            if (isLiftActive) {
                statusText.text = "PRONTO PER PROSSIMA REP"
                statusText.setTextColor(ContextCompat.getColor(this, R.color.neon_blue))
            }
            angleText.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            progressBar.progressDrawable = ContextCompat.getDrawable(this, R.drawable.circular_progress)
        }
    }

    private fun onGoodLift() {
        repsCount++
        tvRepsCounter.text = "${getString(R.string.reps)}: $repsCount"
        tvRepsCounter.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_pulse))

        // Track time between reps
        val currentTime = System.currentTimeMillis()
        val timeSinceLastRep = currentTime - lastRepTime
        repTimesList.add(timeSinceLastRep)
        lastRepTime = currentTime

        statusText.text = "GOOD LIFT!"
        statusText.setTextColor(ContextCompat.getColor(this, R.color.success_green))
        statusText.startAnimation(AnimationUtils.loadAnimation(this, R.anim.bounce))
        angleText.setTextColor(ContextCompat.getColor(this, R.color.success_green))
        angleText.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_pulse))

        // Visual Feedback
        // Audio - play bip for each rep
        try {
            if (mediaPlayerBip?.isPlaying == true) {
                mediaPlayerBip?.seekTo(0)
            } else {
                mediaPlayerBip?.start()
            }
        } catch (e: Exception) {
             // Ignore audio errors to prevent crash
        }

        // Haptic
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
             @Suppress("DEPRECATION")
             vibrator.vibrate(300)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun showFinishDialog() {
        // Creo un layout custom per avere tutti e 4 i bottoni
        val dialogView = layoutInflater.inflate(android.R.layout.select_dialog_item, null)
        val linearLayout = android.widget.LinearLayout(this)
        linearLayout.orientation = android.widget.LinearLayout.VERTICAL
        linearLayout.setPadding(48, 32, 48, 16)

        val messageText = android.widget.TextView(this)
        messageText.text = getString(R.string.completed_reps, repsCount)
        messageText.textSize = 16f
        messageText.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        messageText.setPadding(0, 0, 0, 24)
        linearLayout.addView(messageText)

        // Bottone SALVA
        val btnSave = android.widget.Button(this)
        btnSave.text = getString(R.string.save).uppercase()
        btnSave.setOnClickListener {
            finishSet()
        }
        btnSave.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_red))
        btnSave.setTextColor(ContextCompat.getColor(this, R.color.text_light))
        val params1 = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params1.setMargins(0, 0, 0, 12)
        btnSave.layoutParams = params1
        linearLayout.addView(btnSave)

        // Bottone MODIFICA
        val btnModify = android.widget.Button(this)
        btnModify.text = getString(R.string.modify).uppercase()
        btnModify.setOnClickListener {
            showModifyRepsDialog()
        }
        btnModify.setBackgroundColor(ContextCompat.getColor(this, R.color.secondary_bg))
        btnModify.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        val params2 = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params2.setMargins(0, 0, 0, 12)
        btnModify.layoutParams = params2
        linearLayout.addView(btnModify)

        // Bottone SCARTA
        val btnDiscard = android.widget.Button(this)
        btnDiscard.text = getString(R.string.discard).uppercase()
        btnDiscard.setOnClickListener {
            showDiscardConfirmDialog()
        }
        btnDiscard.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_red))
        btnDiscard.setTextColor(ContextCompat.getColor(this, R.color.text_light))
        val params3 = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params3.setMargins(0, 0, 0, 12)
        btnDiscard.layoutParams = params3
        linearLayout.addView(btnDiscard)

        // Bottone CONTINUA
        val btnContinue = android.widget.Button(this)
        btnContinue.text = getString(R.string.continue_lift).uppercase()
        btnContinue.setOnClickListener {
            // Chiude solo il dialog
        }
        btnContinue.setBackgroundColor(ContextCompat.getColor(this, R.color.text_secondary))
        btnContinue.setTextColor(ContextCompat.getColor(this, R.color.text_light))
        val params4 = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        btnContinue.layoutParams = params4
        linearLayout.addView(btnContinue)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.finish_set)
            .setView(linearLayout)
            .create()

        // Quando clicco su un bottone, chiudo il dialog
        btnSave.setOnClickListener { dialog.dismiss(); finishSet() }
        btnModify.setOnClickListener { dialog.dismiss(); showModifyRepsDialog() }
        btnDiscard.setOnClickListener { dialog.dismiss(); showDiscardConfirmDialog() }
        btnContinue.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showDiscardConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.discard_set_title)
            .setMessage(R.string.discard_set_message)
            .setPositiveButton(R.string.discard) { _, _ ->
                finish()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                showFinishDialog()
            }
            .show()
    }

    private fun showModifyRepsDialog() {
        val input = android.widget.EditText(this)
        input.setText(repsCount.toString())
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        AlertDialog.Builder(this)
            .setTitle(R.string.modify_reps)
            .setMessage(R.string.enter_correct_reps)
            .setView(input)
            .setPositiveButton(R.string.confirm_and_save) { _, _ ->
                val newReps = input.text.toString().toIntOrNull()
                if (newReps != null && newReps > 0) {
                    repsCount = newReps
                    finishSet()
                } else {
                    android.widget.Toast.makeText(this, R.string.invalid_number, android.widget.Toast.LENGTH_SHORT).show()
                    showFinishDialog()
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                showFinishDialog()
            }
            .show()
    }

    private fun finishSet() {
        isLiftActive = false
        val totalTime = System.currentTimeMillis() - setStartTime

        // Converti RepStats in JSON
        val statsJsonArray = org.json.JSONArray()
        for (stat in repStatsList) {
            val statObj = org.json.JSONObject()
            statObj.put("descentTime", stat.descentTime)
            statObj.put("ascentTime", stat.ascentTime)
            statObj.put("totalTime", stat.totalTime)
            statsJsonArray.put(statObj)
        }

        val intent = android.content.Intent(this, SetSummaryActivity::class.java)
        intent.putExtra("LIFT_TYPE", liftType)
        intent.putExtra("TOTAL_REPS", repsCount)
        intent.putExtra("REP_STATS", statsJsonArray.toString())
        intent.putExtra("TOTAL_TIME", totalTime)
        startActivity(intent)
        finish()
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getLanguage(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }
}
