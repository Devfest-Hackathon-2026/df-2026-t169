package com.example.gdgoc_app.ui.screen.diagnosis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gdgoc_app.data.diagnosis.*
import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.data.remote.ApiException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

enum class DiagnosisStage { Loading, Guide, Answering, Submitting, HandingOff, Result }
data class DiagnosisUiState(val stage: DiagnosisStage = DiagnosisStage.Loading,
    val questionSet: DiagnosisQuestionSet? = null, val draft: DiagnosisDraft = DiagnosisDraft(),
    val result: DiagnosisResult? = null, val history: List<DiagnosisResult> = emptyList(),
    val historyError: String? = null, val error: String? = null, val preview: Boolean = false, val lastStatus: Int? = null)

class DiagnosisViewModel(private val auth: StateFlow<User?>, private val repository: DiagnosisRepository,
    private val store: DiagnosisDraftStore, private val spec: DiagnosisSpec, private val showLatest: Boolean = false, private val detailId: String? = null,
    private val onFirstDiagnosisSaved: ((String, DiagnosisResult) -> Unit)? = null) : ViewModel() {
    private val _state = MutableStateFlow(DiagnosisUiState(preview = repository.isPreview))
    val state = _state.asStateFlow()
    private var owner: String? = null
    private var generation = 0L
    private var operation: Job? = null
    init {
        require(onFirstDiagnosisSaved == null || !repository.isPreview)
        viewModelScope.launch {
            auth.map { it?.id }.distinctUntilChanged().collect { uid ->
                generation++; operation?.cancel(); owner = uid
                _state.value = DiagnosisUiState(preview = repository.isPreview)
                if (uid != null) { if (showLatest || detailId != null) loadLatest() else load() }
            }
        }
    }
    private fun current(uid: String, revision: Long) = owner == uid && auth.value?.id == uid && generation == revision
    fun load() {
        if (operation?.isActive == true) return
        val uid = owner ?: return
        val revision = generation
        _state.update { it.copy(stage = DiagnosisStage.Loading, error = null) }
        operation = viewModelScope.launch {
            try {
                val questions = spec.validateQuestions(repository.questions())
                if (current(uid, revision)) _state.update { it.copy(questionSet = questions) }
                val draft = store.load(uid, repository.isPreview) ?: DiagnosisDraft()
                draft.validate()
                if (current(uid, revision)) _state.update { it.copy(stage = DiagnosisStage.Guide, questionSet = questions, draft = draft) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (current(uid, revision)) _state.update { it.copy(error = "設問または一時保存した回答を読み込めませんでした。再試行してください。") }
            }
        }
    }
    fun loadLatest() {
        if (operation?.isActive == true) return
        val uid = owner ?: return
        val revision = generation
        _state.update { it.copy(stage = DiagnosisStage.Loading, error = null) }
        operation = viewModelScope.launch {
            try {
                val result = if (detailId != null) repository.detail(detailId) else repository.latest() ?: error("No completed diagnosis")
                if (result.questionSetVersion == DIAGNOSIS_VERSION) spec.validateResult(result)
                if (!current(uid, revision)) return@launch
                _state.update { it.copy(stage = DiagnosisStage.Result, result = result,
                    questionSet = if (result.questionSetVersion == DIAGNOSIS_VERSION) spec.questionSet() else null,
                    draft = DiagnosisDraft()) }
                try {
                    val history = if (detailId == null) repository.history() else emptyList()
                    if (current(uid, revision)) _state.update { it.copy(history = history.filter { h -> h.id != result.id }.take(1),
                        historyError = if (detailId != null) "過去結果の詳細です。最新結果の画面から前回1回を比較できます。" else null) }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { if (current(uid, revision)) _state.update { it.copy(historyError = "過去結果を取得できませんでした。") } }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (current(uid, revision)) _state.update { it.copy(error = "診断結果を取得できませんでした。再試行してください。") }
            }
        }
    }
    fun retryLoad() { if (showLatest || detailId != null) loadLatest() else load() }
    private fun persist(draft: DiagnosisDraft): Boolean {
        val uid = owner ?: return false
        if (auth.value?.id != uid) return false
        return try { store.save(uid, repository.isPreview, draft); true }
        catch (_: Exception) { _state.update { it.copy(error = "回答を端末に一時保存できませんでした。空き容量を確認して再試行してください。") }; false }
    }
    fun begin() {
        if (_state.value.questionSet == null || _state.value.stage != DiagnosisStage.Guide) return
        if (persist(_state.value.draft)) _state.update { it.copy(stage = DiagnosisStage.Answering, error = null) }
    }
    fun restart() {
        if (_state.value.stage == DiagnosisStage.Submitting || _state.value.questionSet == null) return
        generation++; operation?.cancel()
        val draft = DiagnosisDraft()
        if (persist(draft)) _state.update { it.copy(stage = DiagnosisStage.Answering, draft = draft, result = null, history = emptyList(), error = null) }
    }
    fun answer(id: String, value: Int) {
        if (_state.value.stage != DiagnosisStage.Answering || id !in QUESTION_IDS || value !in 1..5) return
        val previous = _state.value.draft
        if (previous.answers[id] == value) return
        val draft = previous.copy(answers = previous.answers + (id to value),
            idempotencyKey = if (previous.submitted) UUID.randomUUID().toString() else previous.idempotencyKey, submitted = false)
        _state.update { it.copy(draft = draft, error = null, lastStatus = null) }
        persist(draft)
    }
    fun previousPage() { movePage(-1) }
    fun nextPage() { movePage(1) }
    private fun movePage(delta: Int) {
        val state = _state.value
        if (state.stage != DiagnosisStage.Answering) return
        if (delta > 0) {
            val missing = listOf(QUESTION_IDS[state.draft.currentQuestionIndex]).filter { it !in state.draft.answers }
            if (missing.isNotEmpty()) { _state.update { it.copy(error = "未回答の設問があります：${missing.joinToString()}。") }; return }
        }
        val draft = state.draft.copy(questionIndex = (state.draft.currentQuestionIndex + delta).coerceIn(0, 19))
        _state.update { it.copy(draft = draft, error = null) }; persist(draft)
    }
    fun submit() {
        val state = _state.value
        if (state.stage != DiagnosisStage.Answering || operation?.isActive == true) return
        if (state.draft.answers.keys != QUESTION_IDS.toSet()) {
            _state.update { it.copy(error = "全20問に回答してください。") }; return
        }
        val uid = owner ?: return
        if (auth.value?.id != uid) return
        val revision = generation
        val draft = state.draft.copy(submitted = true)
        if (!persist(draft)) return
        _state.update { it.copy(stage = DiagnosisStage.Submitting, draft = draft, error = null) }
        operation = viewModelScope.launch {
            try {
                val result = spec.validateResult(repository.submit(DiagnosisSubmission(DIAGNOSIS_VERSION, draft.idempotencyKey, draft.answers)))
                currentCoroutineContext().ensureActive()
                if (!current(uid, revision)) return@launch
                // Results contain no per-question answers. Release the completed input in memory.
                _state.update { it.copy(stage = if(onFirstDiagnosisSaved == null) DiagnosisStage.Result else DiagnosisStage.HandingOff,
                    result = if(onFirstDiagnosisSaved == null) result else null, draft = DiagnosisDraft()) }
                try { store.clear(uid, repository.isPreview) }
                catch (_: Exception) { _state.update { it.copy(error = "結果を受信しましたが、端末の一時回答を削除できませんでした。") } }
                if (onFirstDiagnosisSaved != null) {
                    onFirstDiagnosisSaved.invoke(uid,result)
                    return@launch
                }
                try {
                    val history = repository.history()
                    if (current(uid, revision)) _state.update { it.copy(history = history.filter { h -> h.id != result.id }.take(1)) }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { if (current(uid, revision)) _state.update { it.copy(historyError = "過去結果を取得できませんでした。") } }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (current(uid, revision)) _state.update { it.copy(stage = DiagnosisStage.Answering,
                    lastStatus = (e as? ApiException)?.statusCode,
                    error = if (e is DiagnosisNotConnected) e.message else (e as? ApiException)?.message ?: "診断を送信できませんでした。回答を保持しています。同じ内容で再試行できます。") }
            }
        }
    }
    fun renewConflictingSubmission() {
        val state = _state.value
        if (state.stage != DiagnosisStage.Answering || state.lastStatus != 409) return
        val draft = state.draft.copy(idempotencyKey = UUID.randomUUID().toString(), submitted = false)
        if (persist(draft)) _state.update { it.copy(draft = draft, lastStatus = null, error = null) }
    }
}
