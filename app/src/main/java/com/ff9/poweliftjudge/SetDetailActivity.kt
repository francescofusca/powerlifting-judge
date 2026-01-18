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
import java.text.SimpleDateFormat
import java.util.*

class SetDetailActivity : AppCompatActivity() {

    private lateinit var db: LiftDatabase
    private var liftId: Int = 0
    private var currentLift: Lift? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_detail)

        db = LiftDatabase.getDatabase(this)
        liftId = intent.getIntExtra("LIFT_ID", 0)

        loadLiftDetails()
    }

    private fun loadLiftDetails() {
        CoroutineScope(Dispatchers.IO).launch {
            val lift = db.liftDao().getAll().find { it.id == liftId }
            withContext(Dispatchers.Main) {
                currentLift = lift
                if (lift != null) {
                    displayLiftDetails(lift)
                } else {
                    finish()
                }
            }
        }
    }

    private fun displayLiftDetails(lift: Lift) {
        val tvLiftType: TextView = findViewById(R.id.tvLiftTypeDetail)
        val tvDate: TextView = findViewById(R.id.tvDateDetail)
        val tvTotalRepsDetail: TextView = findViewById(R.id.tvTotalRepsDetail)
        val tvTotalTimeDetail: TextView = findViewById(R.id.tvTotalTimeDetail)
        val tvAvgTimeDetail: TextView = findViewById(R.id.tvAvgTimeDetail)
        val tvNotesDetail: TextView = findViewById(R.id.tvNotesDetail)
        val recyclerRepTimesDetail: RecyclerView = findViewById(R.id.recyclerRepTimesDetail)
        val btnEditReps: Button = findViewById(R.id.btnEditRepsDetail)
        val btnClose: Button = findViewById(R.id.btnCloseDetail)

        tvLiftType.text = lift.type.uppercase()

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        tvDate.text = dateFormat.format(Date(lift.date))

        tvTotalRepsDetail.text = lift.reps.toString()
        tvTotalTimeDetail.text = formatTime(lift.totalTime)

        val repTimes = parseRepTimes(lift.repTimes)
        val avgTime = if (repTimes.isNotEmpty()) repTimes.average() else 0.0
        tvAvgTimeDetail.text = formatTime((avgTime * 1000).toLong())

        tvNotesDetail.text = if (lift.notes.isNotEmpty()) lift.notes else getString(R.string.no_notes)

        // Parsing delle statistiche dettagliate
        val repStats = parseRepStats(lift.repTimes)
        recyclerRepTimesDetail.layoutManager = LinearLayoutManager(this)
        recyclerRepTimesDetail.adapter = RepStatsAdapter(repStats)

        btnEditReps.setOnClickListener {
            showEditRepsDialog(lift)
        }

        btnClose.setOnClickListener {
            finish()
        }
    }

    private fun showEditRepsDialog(lift: Lift) {
        val input = EditText(this)
        input.setText(lift.reps.toString())
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        AlertDialog.Builder(this)
            .setTitle(R.string.modify_reps)
            .setMessage(R.string.enter_correct_reps)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val newReps = input.text.toString().toIntOrNull()
                if (newReps != null && newReps > 0) {
                    updateReps(lift.id, newReps)
                } else {
                    android.widget.Toast.makeText(this, R.string.invalid_number, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateReps(liftId: Int, newReps: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            db.liftDao().updateReps(liftId, newReps)
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(this@SetDetailActivity, R.string.reps_updated, android.widget.Toast.LENGTH_SHORT).show()
                loadLiftDetails()
            }
        }
    }

    private fun parseRepTimes(json: String): List<Double> {
        return try {
            val jsonArray = JSONArray(json)
            // Try to parse as old format (simple array of times)
            try {
                List(jsonArray.length()) { i -> jsonArray.getDouble(i) / 1000.0 }
            } catch (e: Exception) {
                // If it fails, try new format (object with stats)
                List(jsonArray.length()) { i ->
                    val obj = jsonArray.getJSONObject(i)
                    obj.getLong("totalTime") / 1000.0
                }
            }
        } catch (e: Exception) {
            emptyList()
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

    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = LocaleHelper.getLanguage(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }
}
