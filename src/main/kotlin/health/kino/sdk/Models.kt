package health.kino.sdk

import com.google.gson.annotations.SerializedName

// ── Config ──────────────────────────────────────────────────────────────────

data class KinoConfig(
    val baseUrl: String,
    val timeoutMs: Long = 30_000
)

// ── Scan input ───────────────────────────────────────────────────────────────

data class ScanConfig(
    val subjectId: String,
    val weightLbs: Double,        // 80–500 lbs
    val heightInches: Double,     // 48–84 in; required
    val sex: Sex,
    val age: Int,
    val consent: ConsentRecord,
    val idempotencyKey: String? = null
)

enum class Sex {
    @SerializedName("male")   MALE,
    @SerializedName("female") FEMALE
}

data class ConsentRecord(
    @SerializedName("terms_accepted") val termsAccepted: Boolean
)

// ── Upload ───────────────────────────────────────────────────────────────────

data class UploadSlot(
    @SerializedName("url")    val url: String,
    @SerializedName("fields") val fields: Map<String, String>,
    @SerializedName("s3_key") val s3Key: String
)

data class UploadResponse(
    @SerializedName("upload_id") val uploadId: String,
    @SerializedName("front")     val front: UploadSlot,
    @SerializedName("back")      val back: UploadSlot
)

// ── Scan result ──────────────────────────────────────────────────────────────

enum class ScanStatus {
    @SerializedName("pending")    PENDING,
    @SerializedName("processing") PROCESSING,
    @SerializedName("completed")  COMPLETED,
    @SerializedName("failed")     FAILED,
    @SerializedName("rejected")   REJECTED
}

data class SegmentalLeanMass(
    @SerializedName("arm_left_lean_lbs")  val armLeftLeanLbs: Double?,
    @SerializedName("arm_right_lean_lbs") val armRightLeanLbs: Double?,
    @SerializedName("leg_left_lean_lbs")  val legLeftLeanLbs: Double?,
    @SerializedName("leg_right_lean_lbs") val legRightLeanLbs: Double?
)

data class ScanResult(
    @SerializedName("scan_id")                           val scanId: String?,
    @SerializedName("subject_id")                        val subjectId: String?,
    @SerializedName("status")                            val status: ScanStatus?,
    @SerializedName("mode")                              val mode: String?,
    @SerializedName("degraded_reason")                   val degradedReason: String?,
    @SerializedName("model_version")                     val modelVersion: String?,
    @SerializedName("created_at")                        val createdAt: String?,
    @SerializedName("body_fat_percentage")               val bodyFatPercentage: Double?,
    @SerializedName("body_fat_percentage_smoothed")      val bodyFatPercentageSmoothed: Double?,
    @SerializedName("estimated_muscle_mass_percentage")  val estimatedMuscleMassPercentage: Double?,
    @SerializedName("estimated_muscle_mass_lbs")         val estimatedMuscleMassLbs: Double?,
    @SerializedName("estimated_muscle_mass_lbs_smoothed") val estimatedMuscleMassLbsSmoothed: Double?,
    @SerializedName("bone_mineral_content_lbs")          val boneMineralContentLbs: Double?,
    @SerializedName("visceral_fat_lbs")                  val visceralFatLbs: Double?,
    @SerializedName("segmental_lean_mass")               val segmentalLeanMass: SegmentalLeanMass?,
    @SerializedName("derived")                           val derived: List<String>?,
    @SerializedName("processing_time_ms")                val processingTimeMs: Long?,
    @SerializedName("request_id")                        val requestId: String?
)

// ── Exceptions ───────────────────────────────────────────────────────────────

sealed class KinoException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    object InvalidApiKey             : KinoException("Invalid or missing API key")
    object InsufficientScope         : KinoException("API key lacks required scope")
    object InvalidSubjectId          : KinoException("subject_id is invalid or does not match policy")
    object InvalidField              : KinoException("One or more request fields are invalid")
    object ValueOutOfRange           : KinoException("A numeric value is outside the accepted range")
    object UnsupportedModelVersion   : KinoException("Requested model_version is not available")
    object ImageNotFound             : KinoException("Referenced image upload was not found")
    object ScanNotFound              : KinoException("Scan ID not found")
    object QcRejected                : KinoException("Scan rejected by quality-control checks")
    object AgeBelowMinimum           : KinoException("Subject age is below the minimum allowed")
    object SandboxCreditsExhausted   : KinoException("Sandbox inference credits exhausted — contact Kino for more")
    object EndpointError             : KinoException("The API endpoint returned an unexpected error")
    object InternalError             : KinoException("Internal server error")
    object Timeout                   : KinoException("Scan polling timed out before completion")

    data class RateLimited(val retryAfterSeconds: Int?) :
        KinoException("Rate limit exceeded" + if (retryAfterSeconds != null) "; retry after ${retryAfterSeconds}s" else "")

    data class NetworkError(val networkCause: Throwable) :
        KinoException("Network error: ${networkCause.message}", networkCause)

    data class Unknown(val code: String, override val message: String) :
        KinoException("Unknown error [$code]: $message")

    companion object {
        fun fromCode(code: String, message: String): KinoException = when (code) {
            "invalid_api_key"           -> InvalidApiKey
            "insufficient_scope"        -> InsufficientScope
            "invalid_subject_id"        -> InvalidSubjectId
            "invalid_field"             -> InvalidField
            "value_out_of_range"        -> ValueOutOfRange
            "unsupported_model_version" -> UnsupportedModelVersion
            "image_not_found"           -> ImageNotFound
            "scan_not_found"            -> ScanNotFound
            "qc_rejected"               -> QcRejected
            "age_below_minimum"         -> AgeBelowMinimum
            "sandbox_credits_exhausted" -> SandboxCreditsExhausted
            "rate_limited"              -> RateLimited(retryAfterSeconds = null)
            "endpoint_error"            -> EndpointError
            "internal_error"            -> InternalError
            else                        -> Unknown(code, message)
        }
    }
}
