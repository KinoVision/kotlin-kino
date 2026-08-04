# Kino Android SDK

Body composition scanning for Android. Wraps the Kino API with a Kotlin coroutines client and four Jetpack Compose screens.

---

## 1. Install

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven.kino.health/android") }
    }
}

// build.gradle.kts (app module)
dependencies {
    implementation("health.kino:android-sdk:1.0.0")
}
```

---

## 2. Setup

```kotlin
val kino = KinoClient(
    KinoConfig(
        baseUrl = "https://api.yourapp.com",  // your backend proxy
        timeoutMs = 30_000
    )
)
```

Your backend proxies to Kino and holds the Kino API key. The SDK never holds credentials.

---

## 3. Quickstart

A complete NavHost wiring all four screens with a shared ViewModel:

```kotlin
// ScanViewModel.kt
class ScanViewModel(private val kino: KinoClient) : ViewModel() {
    var frontBytes: ByteArray? = null
    var backBytes: ByteArray? = null
    var scanResult: ScanResult? = null
    var progress by mutableFloatStateOf(0f)
    var statusLabel by mutableStateOf("Starting…")

    fun runScan(scanConfig: ScanConfig) {
        viewModelScope.launch {
            try {
                statusLabel = "Uploading photos…"
                val urls = kino.getUploadUrls()
                kino.uploadImage(urls.front, frontBytes!!)
                kino.uploadImage(urls.back, backBytes!!)

                statusLabel = "Analyzing…"
                val initial = kino.createScan(urls.uploadId, scanConfig)

                scanResult = kino.pollScan(initial.scanId!!) { p ->
                    progress = p.toFloat()
                    statusLabel = when {
                        p < 0.35 -> "In queue…"
                        p < 0.85 -> "Analyzing body composition…"
                        else     -> "Finishing up…"
                    }
                }
            } catch (e: KinoException) {
                // handle error
            }
        }
    }
}

// NavGraph.kt
@Composable
fun KinoNavGraph(vm: ScanViewModel, navController: NavHostController) {
    NavHost(navController, startDestination = "instructions") {
        composable("instructions") {
            KinoInstructionsScreen(
                onStart = { navController.navigate("camera") }
            )
        }
        composable("camera") {
            KinoCameraScreen(
                onComplete = { front, back ->
                    vm.frontBytes = front
                    vm.backBytes = back
                    navController.navigate("processing")
                    vm.runScan(yourScanConfig)
                },
                onCancel = { navController.popBackStack() }
            )
        }
        composable("processing") {
            KinoProcessingScreen(
                progress = vm.progress,
                statusLabel = vm.statusLabel
            )
            LaunchedEffect(vm.scanResult) {
                if (vm.scanResult != null) navController.navigate("results")
            }
        }
        composable("results") {
            vm.scanResult?.let { result ->
                KinoResultsScreen(
                    result = result,
                    onDone = { navController.popBackStack("instructions", inclusive = false) },
                    onRetry = { navController.popBackStack("camera", inclusive = false) }
                )
            }
        }
    }
}
```

---

## 4. Camera permission

Add to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

`KinoCameraScreen` handles the runtime permission request and settings redirect internally. You do not need to request the permission before navigating to the screen.

---

## 5. API client direct usage

```kotlin
val kino = KinoClient(KinoConfig(baseUrl = "https://api.yourapp.com"))

// In a coroutine scope:
val urls = kino.getUploadUrls()                          // POST /kino/uploads

kino.uploadImage(urls.front, frontJpegBytes)             // PUT → S3 presigned URL
kino.uploadImage(urls.back, backJpegBytes)

val scanConfig = ScanConfig(
    subjectId    = "usr_abc123",
    weightLbs    = 185.0,
    heightInches = 71.0,
    sex          = Sex.MALE,
    age          = 32,
    consent      = ConsentRecord(
        obtained      = true,
        capturedAt    = "2026-08-03T12:00:00Z",
        policyVersion = "1.0"
    )
)

val initial = kino.createScan(urls.uploadId, scanConfig) // POST /kino/scans

val result = kino.pollScan(initial.scanId!!) { progress ->
    println("Progress: ${(progress * 100).toInt()}%")
}
// GET /kino/scans/{id} — polls every 2 s, max 30 attempts

println("Body fat: ${result.bodyFatPercentage}%")
```

---

## 6. Error handling

| `KinoException` subclass | When it fires | What to do |
|---|---|---|
| `InvalidApiKey` | Backend rejected the API key | Check your proxy's key configuration |
| `InsufficientScope` | Key lacks required permissions | Upgrade key scope on the backend |
| `InvalidSubjectId` | `subjectId` format or policy mismatch | Validate ID format before calling |
| `InvalidField` | A request field is malformed | Check field values in `ScanConfig` |
| `ValueOutOfRange` | Weight, height, or age outside bounds | Validate before calling (age ≥ 18, weight > 0) |
| `ImageNotFound` | Upload wasn't found when creating scan | Re-upload images before calling `createScan` |
| `ScanNotFound` | `scanId` doesn't exist | Check the ID; don't retry blindly |
| `QcRejected` | Images failed quality checks | Ask user to retake in better conditions |
| `AgeBelowMinimum` | Age under the platform minimum | Block scan in UI before calling |
| `RateLimited(retryAfterSeconds)` | Too many requests | Back off; use `retryAfterSeconds` if set |
| `EndpointError` | Unexpected HTTP error from backend | Retry once; then surface to user |
| `InternalError` | 5xx from Kino | Retry with exponential backoff |
| `Timeout` | `pollScan` exceeded 30 attempts (60 s) | Offer retry; scan may still complete server-side |
| `NetworkError(cause)` | No connectivity / socket error | Check connectivity; retry when online |
| `Unknown(code, message)` | Unrecognized error code | Log `code` and report to support |

---

## 7. What is NOT in this SDK

- **Server-side proxy** — you must implement a backend that holds the Kino API key and exposes `/kino/uploads`, `/kino/scans`, and `/kino/scans/{id}`. See the Kino server docs.
- **Pose detection / real-time feedback** — the camera screen captures stills only. Live pose guidance requires a separate ML library (e.g., ML Kit Pose Detection).
- **Image preprocessing** — no auto-crop, rotation normalization, or compression is applied. Pass JPEG bytes at full capture resolution.
- **Auth / user management** — the SDK is stateless. You handle authentication between your app and your backend.

See `DEPENDENCIES.md` for the full third-party dependency tree.
