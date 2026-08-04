package health.kino.sdk.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

private enum class CameraStep {
    REQUESTING_PERMISSION,
    CAPTURING_FRONT,
    PREVIEW_FRONT,
    CAPTURING_BACK,
    DONE
}

@Composable
fun KinoCameraScreen(
    onComplete: (frontBytes: ByteArray, backBytes: ByteArray) -> Unit,
    onCancel: (() -> Unit)? = null,
    brandColor: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current

    var step by remember { mutableStateOf(
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) CameraStep.CAPTURING_FRONT
        else CameraStep.REQUESTING_PERMISSION
    ) }

    var permissionDeniedPermanently by remember { mutableStateOf(false) }
    var frontBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            step = CameraStep.CAPTURING_FRONT
        } else {
            permissionDeniedPermanently = true
        }
    }

    LaunchedEffect(Unit) {
        if (step == CameraStep.REQUESTING_PERMISSION) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    when (step) {
        CameraStep.REQUESTING_PERMISSION -> {
            PermissionScreen(
                deniedPermanently = permissionDeniedPermanently,
                brandColor = brandColor,
                onCancel = onCancel,
                onRequestAgain = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )
        }

        CameraStep.CAPTURING_FRONT -> {
            CaptureView(
                label = "Front photo",
                hint = "Face the camera — arms slightly away from your sides",
                brandColor = brandColor,
                isCapturing = isCapturing,
                onCapture = { imageCapture ->
                    isCapturing = true
                    captureImage(context, imageCapture) { bytes ->
                        frontBytes = bytes
                        isCapturing = false
                        step = CameraStep.PREVIEW_FRONT
                    }
                },
                onCancel = onCancel
            )
        }

        CameraStep.PREVIEW_FRONT -> {
            PhotoPreviewScreen(
                label = "Front photo — looks good?",
                brandColor = brandColor,
                onContinue = { step = CameraStep.CAPTURING_BACK },
                onRetake = { step = CameraStep.CAPTURING_FRONT }
            )
        }

        CameraStep.CAPTURING_BACK -> {
            CaptureView(
                label = "Back photo",
                hint = "Turn around — same posture, arms away from sides",
                brandColor = brandColor,
                isCapturing = isCapturing,
                onCapture = { imageCapture ->
                    isCapturing = true
                    captureImage(context, imageCapture) { bytes ->
                        val front = frontBytes ?: return@captureImage
                        isCapturing = false
                        step = CameraStep.DONE
                        onComplete(front, bytes)
                    }
                },
                onCancel = onCancel
            )
        }

        CameraStep.DONE -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = brandColor)
            }
        }
    }
}

// ── Camera capture view ───────────────────────────────────────────────────────

@Composable
private fun CaptureView(
    label: String,
    hint: String,
    brandColor: Color,
    isCapturing: Boolean,
    onCapture: (ImageCapture) -> Unit,
    onCancel: (() -> Unit)?
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // Dimmed overlay with oval cutout
        SilhouetteOverlay(modifier = Modifier.fillMaxSize())

        // Top controls
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            if (onCancel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = Color.White)
                    }
                }
            }

            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        // Shutter button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp)
        ) {
            ShutterButton(
                brandColor = brandColor,
                enabled = !isCapturing && imageCapture != null,
                onClick = { imageCapture?.let { onCapture(it) } }
            )
        }
    }
}

@Composable
private fun SilhouetteOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val ovalWidth = size.width * 0.55f
        val ovalHeight = size.height * 0.72f
        val left = (size.width - ovalWidth) / 2f
        val top = (size.height - ovalHeight) / 2f

        // Dark scrim
        drawRect(color = Color.Black.copy(alpha = 0.45f))

        // Cut out the oval (transparent)
        drawOval(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(ovalWidth, ovalHeight),
            blendMode = BlendMode.Clear
        )

        // Oval guide stroke
        drawOval(
            color = Color.White.copy(alpha = 0.7f),
            topLeft = Offset(left, top),
            size = Size(ovalWidth, ovalHeight),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
private fun ShutterButton(
    brandColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = brandColor),
        contentPadding = PaddingValues(0.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(Color.White, CircleShape)
        )
    }
}

// ── Photo preview ─────────────────────────────────────────────────────────────

@Composable
private fun PhotoPreviewScreen(
    label: String,
    brandColor: Color,
    onContinue: () -> Unit,
    onRetake: () -> Unit
) {
    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onRetake,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Retake")
                    }
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                    ) {
                        Text("Continue", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ── Permission screen ─────────────────────────────────────────────────────────

@Composable
private fun PermissionScreen(
    deniedPermanently: Boolean,
    brandColor: Color,
    onCancel: (() -> Unit)?,
    onRequestAgain: () -> Unit
) {
    val context = LocalContext.current

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Camera access needed",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (deniedPermanently)
                    "Camera permission was denied. Open Settings to enable it for this app."
                else
                    "Kino needs camera access to capture your front and back photos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(32.dp))

            if (deniedPermanently) {
                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = brandColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Open Settings", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onRequestAgain,
                    colors = ButtonDefaults.buttonColors(containerColor = brandColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Allow camera access", fontWeight = FontWeight.SemiBold)
                }
            }

            if (onCancel != null) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}

// ── Capture helper ────────────────────────────────────────────────────────────

private fun captureImage(
    context: Context,
    imageCapture: ImageCapture,
    onResult: (ByteArray) -> Unit
) {
    val executor = Executors.newSingleThreadExecutor()
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                ContextCompat.getMainExecutor(context).execute {
                    onResult(bytes)
                }
                executor.shutdown()
            }

            override fun onError(exception: ImageCaptureException) {
                executor.shutdown()
            }
        }
    )
}
