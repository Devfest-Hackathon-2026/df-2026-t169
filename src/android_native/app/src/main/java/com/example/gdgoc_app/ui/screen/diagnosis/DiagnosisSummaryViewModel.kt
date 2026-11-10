package com.example.gdgoc_app.ui.screen.diagnosis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gdgoc_app.data.diagnosis.*
import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.data.remote.ApiException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DiagnosisSummaryState(val loading: Boolean = false, val result: DiagnosisResult? = null,
    val checked: Boolean = false, val error: String? = null, val history: List<DiagnosisResult> = emptyList(),
    val historyLoading: Boolean = false, val historyChecked: Boolean = false, val historyError: String? = null)
class DiagnosisSummaryViewModel(private val auth: StateFlow<User?>, private val repository: DiagnosisRepository) : ViewModel() {
    private val _state = MutableStateFlow(DiagnosisSummaryState())
    val state = _state.asStateFlow()
    private var owner: String? = null
    private var generation = 0L
    private var operation: Job? = null
    private var historyJob: Job? = null
    init {
        require(!repository.isPreview) { "Preview results must not be attached to profiles" }
        viewModelScope.launch {
            auth.map { it?.id }.distinctUntilChanged().collect { uid ->
                generation++; operation?.cancel(); historyJob?.cancel(); owner = uid; _state.value = DiagnosisSummaryState()
                if (uid != null) refresh()
            }
        }
    }
    fun refresh() {
        if (operation?.isActive == true) return
        val uid = owner ?: return
        val revision = generation
        if (!repository.isConnected) {
            _state.value = DiagnosisSummaryState(error = "診断結果の取得はAPI接続待ちです。")
            return
        }
        historyJob?.cancel()
        _state.value = DiagnosisSummaryState(loading = true)
        operation = viewModelScope.launch {
            try {
                val result = repository.latest()
                if (revision == generation && auth.value?.id == uid) _state.value = DiagnosisSummaryState(result = result, checked = true)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (revision == generation && auth.value?.id == uid) _state.value = DiagnosisSummaryState(error = (e as? ApiException)?.message ?: "診断結果を取得できませんでした。再試行してください。") }
        }
    }
    fun loadHistory() {
        if(historyJob?.isActive==true || _state.value.historyChecked || _state.value.result==null) return
        val uid=owner ?: return
        val revision=generation
        _state.update { it.copy(historyLoading=true,historyError=null) }
        historyJob=viewModelScope.launch {
            try {
                val history=repository.history()
                if(revision==generation && auth.value?.id==uid) _state.update { state -> state.copy(history=history.filter { it.id!=state.result?.id }.take(1),historyLoading=false,historyChecked=true) }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(revision==generation && auth.value?.id==uid) _state.update { it.copy(historyLoading=false,historyError="過去結果を取得できませんでした。") } }
        }
    }
    fun refreshAfterDiagnosis() {
        generation++
        operation?.cancel()
        refresh()
    }
    fun acceptServerResult(result: DiagnosisResult, uid: String) {
        if (auth.value?.id != uid || owner != uid) return
        generation++; operation?.cancel()
        _state.value = DiagnosisSummaryState(result = result, checked = true)
    }
}
