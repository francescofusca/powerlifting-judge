package com.ff9.poweliftjudge

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ff9.poweliftjudge.database.Lift
import com.ff9.poweliftjudge.database.LiftDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class SetSummaryActivity : AppCompatActivity() {

    private lateinit var db: LiftDatabase
    private var liftType: String = ""
    private var totalReps: Int = 0
    private var repStatsJson: String = ""
    private var totalTimeMs: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_summary)

        db = LiftDatabase.getDatabase(this)

        // Get data from intent
        liftType = intent.getStringExtra("LIFT_TYPE") ?: "Unknown"
        totalReps = intent.getIntExtra("TOTAL_REPS", 0)
        repStatsJson = intent.getStringExtra("REP_STATS") ?: "[]"
        totalTimeMs = intent.getLongExtra("TOTAL_TIME", 0)

        setupUI()
    }

    private fun setupUI() {
        val tvLiftType: TextView = findViewById(R.id.tvLiftTypeSummary)
        val tvTotalReps: TextView = findViewById(R.id.tvTotalReps)
        val tvTotalTime: TextView = findViewById(R.id.tvTotalTime)
        val tvAvgTime: TextView = findViewById(R.id.tvAvgTime)
        val recyclerRepTimes: RecyclerView = findViewById(R.id.recyclerRepTimes)
        val etNotes: EditText = findViewById(R.id.etNotes)
        val btnSave: Button = findViewById(R.id.btnSaveSummary)
        val btnDiscard: Button = findViewById(R.id.btnDiscardSet)

        tvLiftType.text = liftType.uppercase()
        tvTotalReps.text = totalReps.toString()
        tvTotalTime.text = formatTime(totalTimeMs)

        // Parse rep stats
        val repStats = parseRepStats(repStatsJson)
        val avgTime = if (repStats.isNotEmpty()) repStats.map { it.totalTime }.average() else 0.0
        tvAvgTime.text = formatTime(avgTime.toLong())

        // Setup RecyclerView con le nuove statistiche
        recyclerRepTimes.layoutManager = LinearLayoutManager(this)
        recyclerRepTimes.adapter = RepStatsAdapter(repStats)

        btnSave.setOnClickListener {
            val notes = etNotes.text.toString()
            saveSetToDatabase(notes)
        }

        btnDiscard.setOnClickListener {
            showDiscardDialog()
        }
    }

    private fun parseRepStats(json: String): List<RepStats> {
        return try {
            val jsonArray = JSONArray(json)
            List(jsonArray.length()) { i ->
                val obj = jsonArray.getJSONObject(i)
                RepStats(
                    descentTime = obj.getLong("descentTime"),
                    ascentTime = obj.getLong("ascentTime"),
                    totalTime = obj.getLong("totalTime")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000.0
        return String.format("%.1fs", seconds)
    }

    private fun saveSetToDatabase(notes: String) {
        CoroutineScope(Dispatchers.IO).launch {
            db.liftDao().insertLift(
                Lift(
                    id = 0,
                    type = liftType,
                    date = System.currentTimeMillis(),
                    valid = true,
                    reps = totalReps,
                    repTimes = repStatsJson,
                    totalTime = totalTimeMs,
                    notes = notes
                )
            )

            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    this@SetSummaryActivity,
                    R.string.set_saved,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun showDiscardDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.discard_set_title)
            .setMessage(R.string.discard_set_message)
            .setPositiveButton(R.string.discard) { _, _ ->
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = LocaleHelper.getLanguage(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }
}
