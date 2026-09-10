package com.example.arcarcustomizer.fallback

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

/**
 * Fallback mode's real-world backdrop (tasks.md Phase 4): a raw, full-screen CameraX preview
 * that the non-AR SceneView layer composites its car on top of.
 */
@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                // PERFORMANCE (the default) backs the preview with a SurfaceView, which is
                // composited by SurfaceFlinger as its own layer BEHIND the app's normal window
                // surface — so the SceneView layer drawn "on top" of it in this same Box would
                // actually just paint over an opaque window background, hiding the camera
                // entirely. COMPATIBLE uses a TextureView instead, which composites inline with
                // normal View/Compose content, letting the transparent SceneView layer above it
                // actually show the camera feed through.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                        )
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "Failed to bind camera preview", e)
                    }
                },
                ContextCompat.getMainExecutor(ctx),
            )
            previewView
        },
    )
}
