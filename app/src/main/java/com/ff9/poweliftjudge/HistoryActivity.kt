package com.ff9.poweliftjudge

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
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

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LiftAdapter
    private lateinit var db: LiftDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_history)

            recyclerView = findViewById(R.id.recyclerView)
            recyclerView.layoutManager = LinearLayoutManager(this)

            db = LiftDatabase.getDatabase(this)

            adapter = LiftAdapter(
                lifts = listOf(),
                onItemClick = { lift -> openSetDetail(lift) },
                onEditClick = { lift -> showEditDialog(lift) },
                onDeleteClick = { lift -> showDeleteDialog(lift) }
            )
            recyclerView.adapter = adapter

            loadLifts()
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(this, getString(R.string.error, e.message), android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun loadLifts() {
        CoroutineScope(Dispatchers.IO).launch {
            val lifts = db.liftDao().getAll()
            withContext(Dispatchers.Main) {
                adapter.updateData(lifts)
                val tvEmpty: android.widget.TextView = findViewById(R.id.tvEmpty)
                tvEmpty.visibility = if (lifts.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    private fun showEditDialog(lift: Lift) {
        val input = EditText(this)
        input.setText(lift.reps.toString())
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        AlertDialog.Builder(this)
            .setTitle(R.string.modify_reps)
            .setMessage(R.string.enter_correct_reps)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val newReps = input.text.toString().toIntOrNull() ?: lift.reps
                updateReps(lift.id, newReps)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteDialog(lift: Lift) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_lift)
            .setMessage(R.string.delete_lift_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteLift(lift.id)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateReps(liftId: Int, newReps: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            db.liftDao().updateReps(liftId, newReps)
            loadLifts()
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(this@HistoryActivity, R.string.reps_updated, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteLift(liftId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            db.liftDao().deleteLift(liftId)
            loadLifts()
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(this@HistoryActivity, R.string.lift_deleted, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openSetDetail(lift: Lift) {
        val intent = Intent(this, SetDetailActivity::class.java)
        intent.putExtra("LIFT_ID", lift.id)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadLifts()
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = LocaleHelper.getLanguage(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }
}
