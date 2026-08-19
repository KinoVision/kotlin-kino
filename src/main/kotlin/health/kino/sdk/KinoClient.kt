package health.kino.sdk

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class KinoClient(private val config: KinoConfig) {

    private val gson = Gson()

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun getUploadUrls(): UploadResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${config.baseUrl}/kino/uploads")
            .post("{}".toRequestBody(JSON))
            .build()
        executeAndParse<UploadResponse>(request)
    }

    suspend fun uploadImage(slot: UploadSlot, imageBytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)

        // Policy fields must come first per S3 presigned-post requirements
        slot.fields.forEach { (key, value) ->
            bodyBuilder.addFormDataPart(key, value)
        }

        // File part last
        bodyBuilder.addFormDataPart(
            name = "file",
            filename = "image.jpg",
            body = imageBytes.toRequestBody("image/jpeg".toMediaType())
        )

        val request = Request.Builder()
            .url(slot.url)
            .post(bodyBuilder.build())
            .build()

        try {
            val response = http.newCall(request).execute()
            response.use {
                if (!it.isSuccessful) {
                    throw KinoException.EndpointError
                }
            }
        } catch (e: IOException) {
            throw KinoException.NetworkError(e)
        }
    }

    suspend fun createScan(uploadId: String, scanConfig: ScanConfig): ScanResult = withContext(Dispatchers.IO) {
        val idempotencyKey = scanConfig.idempotencyKey ?: UUID.randomUUID().toString()

        val body = JsonObject().apply {
            addProperty("upload_id", uploadId)
            addProperty("subject_id", scanConfig.subjectId)
            addProperty("weight_lbs", scanConfig.weightLbs)
            addProperty("height_inches", scanConfig.heightInches)
            addProperty("sex", gson.toJsonTree(scanConfig.sex).asString)
            addProperty("age", scanConfig.age)
            add("consent", gson.toJsonTree(scanConfig.consent))
        }

        val request = Request.Builder()
            .url("${config.baseUrl}/kino/scans")
            .post(gson.toJson(body).toRequestBody(JSON))
            .header("Idempotency-Key", idempotencyKey)
            .build()

        executeAndParse<ScanResult>(request)
    }

    suspend fun getScan(scanId: String): ScanResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${config.baseUrl}/kino/scans/$scanId")
            .get()
            .build()
        executeAndParse<ScanResult>(request)
    }

    suspend fun pollScan(
        scanId: String,
        onProgress: ((Double) -> Unit)? = null
    ): ScanResult {
        val maxAttempts    = 21
        val firstDelayMs   = 20_000L // v3.0 typically settles around 20s
        val pollIntervalMs = 5_000L

        repeat(maxAttempts) { attempt ->
            // Delay before polling: 20s on the first attempt, 5s on subsequent
            delay(if (attempt == 0) firstDelayMs else pollIntervalMs)

            val result = getScan(scanId)

            when (result.status) {
                ScanStatus.COMPLETED -> {
                    onProgress?.invoke(1.0)
                    return result
                }
                ScanStatus.FAILED    -> throw KinoException.InternalError
                ScanStatus.REJECTED  -> throw KinoException.QcRejected
                else -> {
                    val progress = (attempt + 1).toDouble() / maxAttempts
                    onProgress?.invoke(progress)
                }
            }
        }

        throw KinoException.Timeout
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private inline fun <reified T> executeAndParse(request: Request): T {
        try {
            val response = http.newCall(request).execute()
            response.use { resp ->
                val bodyString = resp.body?.string() ?: ""

                if (!resp.isSuccessful) {
                    parseAndThrowApiError(bodyString, resp.code)
                }

                return gson.fromJson(bodyString, T::class.java)
                    ?: throw KinoException.InternalError
            }
        } catch (e: KinoException) {
            throw e
        } catch (e: IOException) {
            throw KinoException.NetworkError(e)
        }
    }

    private fun parseAndThrowApiError(body: String, httpCode: Int): Nothing {
        try {
            val root = JsonParser.parseString(body).asJsonObject
            val error = root.getAsJsonObject("error")
            val code = error?.get("code")?.asString ?: "unknown"
            val message = error?.get("message")?.asString ?: "Unknown error"

            // Surface rate-limit retry-after from HTTP header context if present
            if (code == "rate_limited") {
                throw KinoException.RateLimited(retryAfterSeconds = null)
            }

            throw KinoException.fromCode(code, message)
        } catch (e: KinoException) {
            throw e
        } catch (e: Exception) {
            // Could not parse error body — fall back on HTTP status
            throw when (httpCode) {
                401   -> KinoException.InvalidApiKey
                403   -> KinoException.InsufficientScope
                404   -> KinoException.ScanNotFound
                429   -> KinoException.RateLimited(retryAfterSeconds = null)
                in 500..599 -> KinoException.InternalError
                else  -> KinoException.Unknown(httpCode.toString(), "HTTP $httpCode")
            }
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
