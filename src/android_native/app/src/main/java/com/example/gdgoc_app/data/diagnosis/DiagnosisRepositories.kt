package com.example.gdgoc_app.data.diagnosis

import com.example.gdgoc_app.data.remote.ApiClient
import com.example.gdgoc_app.data.remote.ApiException
import com.example.gdgoc_app.data.remote.ApiErrorCode
import com.google.gson.JsonElement
import com.google.gson.JsonObject

interface DiagnosisWireAdapter {
    fun questions(json: JsonObject): DiagnosisQuestionSet
    fun result(json: JsonObject): DiagnosisResult
    fun detail(json: JsonObject): DiagnosisResult = result(json)
    fun latest(json: JsonObject): DiagnosisResult?
    fun history(json: JsonObject): List<DiagnosisResult>
}
/** Django's deployed wire format, mapped to the existing UI models without local scoring. */
class V21DiagnosisWireAdapter(private val spec: DiagnosisSpec) : DiagnosisWireAdapter {
    private fun invalid(field: String = "response"): Nothing = throw ApiException("診断APIの応答形式が正式仕様と一致しません（項目：$field）。")
    private fun string(json: JsonObject, key: String): String {
        val value = json[key] ?: invalid(key)
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) invalid(key)
        return value.asString
    }
    private fun integer(value: JsonElement): Int {
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) invalid()
        return try { value.asBigDecimal.intValueExact() } catch (_: ArithmeticException) { invalid() }
    }
    private fun integers(json: JsonObject, key: String, keys: Set<String>, range: IntRange): Map<String, Int> {
        val value = json[key] ?: invalid("$key:missing")
        if (!value.isJsonObject) invalid("$key:not_object")
        if (value.asJsonObject.keySet() != keys) {
            val safeKeys=value.asJsonObject.keySet().filter { it.matches(Regex("Q[0-9]{1,2}")) || it in STYLE_AXES || it in READINESS_AXES }
            invalid("$key:count=${value.asJsonObject.size()},fields=${safeKeys.joinToString()}")
        }
        return value.asJsonObject.entrySet().associate { (k,v) -> k to integer(v).also { if (it !in range) invalid() } }
    }
    private fun flags(json: JsonObject, key: String): Set<String> {
        val value = json[key] ?: invalid(key)
        if (!value.isJsonArray) invalid()
        val result = value.asJsonArray.map {
            if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString) invalid()
            it.asString.also { axis -> if (axis !in STYLE_AXES) invalid() }
        }
        if (result.distinct().size != result.size) invalid()
        return result.toSet()
    }
    override fun questions(json: JsonObject): DiagnosisQuestionSet {
        val version = string(json,"questionSetVersion")
        val values = json["questions"] ?: invalid()
        if (!values.isJsonArray || values.asJsonArray.size() != 20) invalid()
        val questions = values.asJsonArray.mapIndexed { index, element ->
            if (!element.isJsonObject) invalid()
            val q = element.asJsonObject
            val kind = string(q,"kind")
            val id = string(q,"id")
            val style = index < 12
            if (kind != if (style) "style" else "readiness") invalid()
            if (style) {
                if (string(q,"axis") != STYLE_AXES[index % 4]) invalid()
            } else {
                val indicator = when(index) { in 12..14 -> "V"; in 15..17 -> "O"; else -> "R" }
                if (string(q,"indicator") != indicator) invalid()
            }
            val scale = q["scale"] ?: invalid()
            if (!scale.isJsonArray || scale.asJsonArray.size() != 5) invalid()
            val labels = scale.asJsonArray.map { if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString) invalid(); it.asString }
            DiagnosisQuestion(id,string(q,"text"),if(style) "BIPOLAR" else "AGREEMENT",
                if(style) string(q,"left") else null, if(style) string(q,"right") else null,labels,index+1)
        }
        return try { spec.validateQuestions(DiagnosisQuestionSet(version,questions)) }
        catch (_: IllegalArgumentException) { invalid() }
    }
    override fun result(json: JsonObject): DiagnosisResult = parseResult(json, allowOldVersion = false)
    private fun parseResult(json: JsonObject, allowOldVersion: Boolean): DiagnosisResult {
        val version = string(json,"questionSetVersion")
        val directionsJson = json["styleDirectionCodes"] ?: invalid("styleDirectionCodes")
        if (!directionsJson.isJsonObject || directionsJson.asJsonObject.keySet() != STYLE_AXES.toSet()) invalid("styleDirectionCodes")
        val directions = STYLE_AXES.associateWith { axis ->
            string(directionsJson.asJsonObject,axis).also { if (it !in axis.map { c -> c.toString() }) invalid() }
        }
        val flagsJson = json["boundaryFlags"] ?: invalid("boundaryFlags")
        if (!flagsJson.isJsonObject) invalid()
        val result = DiagnosisResult(string(json,"id"),string(json,"typeCode"),string(json,"typeName"),string(json,"description"),
            integers(json,"styleAxisScores",STYLE_AXES.toSet(),0..100),
            BoundaryFlags(flags(flagsJson.asJsonObject,"exactTie"),flags(flagsJson.asJsonObject,"nearBoundary")),
            integers(json,"readinessScores",READINESS_AXES.toSet(),0..100),version,string(json,"completedAt"),
            integers(json,"styleAxisRawTotals",STYLE_AXES.toSet(),3..15),directions)
        if (result.id.isBlank() || result.completedAt.isBlank() || version.isBlank() || !result.typeCode.matches(Regex("[GE][IX][PF][SD]"))) invalid()
        if (!allowOldVersion || version == DIAGNOSIS_VERSION) {
            if (result.typeName.isBlank() || result.description.isBlank()) invalid("display_copy")
            try { return spec.validateResult(result) } catch (_: Exception) { invalid("scores_or_boundaryFlags") }
        }
        return result
    }
    override fun detail(json: JsonObject): DiagnosisResult = parseResult(json, allowOldVersion = true)
    override fun latest(json: JsonObject): DiagnosisResult? = result(json)
    override fun history(json: JsonObject): List<DiagnosisResult> {
        val values = json["diagnoses"] ?: invalid()
        if (!values.isJsonArray || values.asJsonArray.size() > 3) invalid()
        return values.asJsonArray.map { if (!it.isJsonObject) invalid(); parseResult(it.asJsonObject,true) }
    }
}
class ApiDiagnosisRepository(private val api: ApiClient, private val spec: DiagnosisSpec,
    override val isConnected: Boolean = false, private val adapter: DiagnosisWireAdapter = V21DiagnosisWireAdapter(spec)) : DiagnosisRepository {
    override val isPreview = false
    private fun requireConnected() { if (!isConnected) throw DiagnosisNotConnected() }
    override suspend fun questions(): DiagnosisQuestionSet = if (!isConnected) spec.questionSet() else
        adapter.questions(api.get("/diagnoses/questions", JsonObject::class.java, authenticated = true))
    override suspend fun submit(request: DiagnosisSubmission): DiagnosisResult {
        requireConnected(); request.validate()
        val response=api.postJson("/diagnoses", request, JsonObject::class.java, authenticated = true, successStatuses = setOf(200,201))
        return adapter.result(response)
    }
    override suspend fun latest(): DiagnosisResult? {
        requireConnected()
        return try { adapter.latest(api.get("/diagnoses/latest", JsonObject::class.java, authenticated = true)) }
        catch (e: ApiException) { if (e.statusCode == 404 && e.errorCode == ApiErrorCode.DiagnosisNotFound) null else throw e }
    }
    override suspend fun history(): List<DiagnosisResult> {
        requireConnected()
        return adapter.history(api.get("/diagnoses/history?limit=2", JsonObject::class.java, authenticated = true))
    }
    private suspend fun detailJson(id: String): JsonObject {
        require(id.isNotBlank() && id.length <= 256 && id !in setOf(".",".."))
        val encoded = java.net.URLEncoder.encode(id,"UTF-8").replace("+","%20")
        return api.get("/diagnoses/$encoded",JsonObject::class.java,authenticated=true)
    }
    override suspend fun detail(id: String): DiagnosisResult {
        requireConnected()
        return adapter.detail(detailJson(id)).also {
            if (it.id != id) throw ApiException("指定した診断結果と応答が一致しません。")
        }
    }
}
/** Fixed screen fixture, independent of the answers. No scoring and no network/storage writes. */
class PreviewDiagnosisRepository(private val spec: DiagnosisSpec) : DiagnosisRepository {
    override val isPreview = true
    override val isConnected = true
    override suspend fun questions() = spec.questionSet()
    fun fixture(): DiagnosisResult {
        val type = spec.types.single { it.typeCode == "GXPD" }
        return DiagnosisResult("preview-only", type.typeCode, type.typeName, type.description,
            mapOf("GE" to 83, "IX" to 17, "PF" to 67, "SD" to 33), BoundaryFlags(),
            mapOf("V" to 75, "O" to 50, "R" to 63), DIAGNOSIS_VERSION, "プレビュー")
    }
    override suspend fun submit(request: DiagnosisSubmission): DiagnosisResult { request.validate(); return fixture() }
    override suspend fun latest(): DiagnosisResult? = null
    override suspend fun history(): List<DiagnosisResult> = emptyList()
}
