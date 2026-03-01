package com.ff9.poweliftjudge.ui.history

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ff9.poweliftjudge.PLJudgeApp
import com.ff9.poweliftjudge.database.Lift
import com.ff9.poweliftjudge.model.LiftType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryUiState(
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<Int> = emptySet(),
    val selectedTab: LiftType? = null,
    val exportResult: ExportResult? = null
)

enum class ExportResult { SUCCESS, EMPTY }

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as PLJudgeApp).container.repository

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _selectedTab = MutableStateFlow<LiftType?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val lifts: StateFlow<List<Lift>> = _selectedTab.flatMapLatest { tab ->
        if (tab == null) repository.getAllLiftsFlow()
        else repository.getLiftsByTypeFlow(tab.displayName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(type: LiftType?) {
        _selectedTab.value = type
        _uiState.update { it.copy(selectedTab = type, isSelectionMode = false, selectedIds = emptySet()) }
    }

    fun enterSelectionMode(liftId: Int) {
        _uiState.update { it.copy(isSelectionMode = true, selectedIds = setOf(liftId)) }
    }

    fun toggleSelection(liftId: Int) {
        _uiState.update { state ->
            val newIds = if (liftId in state.selectedIds) {
                state.selectedIds - liftId
            } else {
                state.selectedIds + liftId
            }
            if (newIds.isEmpty()) {
                state.copy(isSelectionMode = false, selectedIds = emptySet())
            } else {
                state.copy(selectedIds = newIds)
            }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(isSelectionMode = false, selectedIds = emptySet()) }
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteLifts(ids)
            _uiState.update { it.copy(isSelectionMode = false, selectedIds = emptySet()) }
        }
    }

    fun deleteLift(liftId: Int) {
        viewModelScope.launch {
            repository.deleteLift(liftId)
        }
    }

    fun updateReps(liftId: Int, newReps: Int) {
        viewModelScope.launch {
            repository.updateReps(liftId, newReps)
        }
    }

    fun updateNotes(liftId: Int, newNotes: String) {
        viewModelScope.launch {
            repository.updateNotes(liftId, newNotes)
        }
    }

    fun updateWeight(liftId: Int, weight: Double, weightUnit: String) {
        viewModelScope.launch {
            repository.updateWeight(liftId, weight, weightUnit)
        }
    }

    fun updateRpe(liftId: Int, rpe: Int) {
        viewModelScope.launch {
            repository.updateRpe(liftId, rpe)
        }
    }

    fun exportCsv(context: Context) {
        viewModelScope.launch {
            val currentLifts = lifts.value
            if (currentLifts.isEmpty()) {
                _uiState.update { it.copy(exportResult = ExportResult.EMPTY) }
                return@launch
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val csv = buildString {
                appendLine("Date,Type,Reps,Weight,Unit,TotalTime,Valid,Notes")
                currentLifts.forEach { lift ->
                    val date = dateFormat.format(Date(lift.date))
                    val notes = lift.notes.replace("\"", "\"\"")
                    appendLine("\"$date\",\"${lift.type}\",${lift.reps},${lift.weight},\"${lift.weightUnit}\",${lift.totalTime},${lift.valid},\"$notes\"")
                }
            }

            val file = File(context.cacheDir, "pl_judge_export.csv")
            file.writeText(csv)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export"))
            _uiState.update { it.copy(exportResult = ExportResult.SUCCESS) }
        }
    }

    fun clearExportResult() {
        _uiState.update { it.copy(exportResult = null) }
    }
}
