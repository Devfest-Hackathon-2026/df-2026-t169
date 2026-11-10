package com.example.gdgoc_app.data.diagnosis

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class DiagnosisDraft(val questionSetVersion: String = DIAGNOSIS_VERSION,
    val answers: Map<String, Int> = emptyMap(), val page: Int = 0,
    val idempotencyKey: String = UUID.randomUUID().toString(), val submitted: Boolean = false, val questionIndex: Int? = null) {
    // Old drafts store a five-question page. Missing questionIndex preserves that format.
    val currentQuestionIndex: Int get() = questionIndex ?: page * 5
    fun validate() {
        require(questionSetVersion == DIAGNOSIS_VERSION && page in 0..3 && (questionIndex == null || questionIndex in 0..19))
        require(answers.keys.all { it in QUESTION_IDS } && answers.values.all { it in 1..5 })
        UUID.fromString(idempotencyKey)
    }
}
interface DiagnosisDraftStore {
    fun load(uid: String, preview: Boolean): DiagnosisDraft?
    fun save(uid: String, preview: Boolean, draft: DiagnosisDraft)
    fun clear(uid: String, preview: Boolean)
}
/** App-private, excluded from Android backup. Preview and real drafts have separate account partitions. */
class FileDiagnosisDraftStore(context: Context) : DiagnosisDraftStore {
    private val directory = File(context.noBackupFilesDir, "diagnosis-drafts")
    private val gson = Gson()
    private fun file(uid: String, preview: Boolean): AtomicFile {
        check(directory.isDirectory || directory.mkdirs())
        val hash = MessageDigest.getInstance("SHA-256").digest("$preview:$uid:$DIAGNOSIS_VERSION".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return AtomicFile(File(directory, "$hash.json"))
    }
    override fun load(uid: String, preview: Boolean): DiagnosisDraft? {
        val f = file(uid, preview)
        if (!f.baseFile.exists()) return null
        return gson.fromJson(String(f.readFully(), Charsets.UTF_8), DiagnosisDraft::class.java).also { it.validate() }
    }
    override fun save(uid: String, preview: Boolean, draft: DiagnosisDraft) {
        draft.validate()
        val f = file(uid, preview)
        val stream = f.startWrite()
        try { stream.write(gson.toJson(draft).toByteArray(Charsets.UTF_8)); f.finishWrite(stream) }
        catch (e: Exception) { f.failWrite(stream); throw e }
    }
    override fun clear(uid: String, preview: Boolean) { file(uid, preview).delete() }
}
