package com.example.arcarcustomizer.fallback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.arcarcustomizer.customization.CarModelConfig
import com.example.arcarcustomizer.customization.CustomizableCar
import com.example.arcarcustomizer.customization.CustomizationControls
import com.example.arcarcustomizer.customization.DefaultCarPaints
import com.example.arcarcustomizer.customization.DefaultWheelStyles
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.ContactShadowContext
import io.github.sceneview.node.Node as NodeImpl
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Fallback mode (tasks.md Phase 4): the same car rendered in a non-AR SceneView over a live
 * CameraX preview, rotated by the device's gyroscope, with a soft contact shadow.
 *
 * Reuses [CustomizableCar] — the identical shared module AR mode uses — so paint and wheel
 * swap behave the same way in both modes.
 */
@Composable
fun FallbackScreen() {
    var paint by remember { mutableStateOf(DefaultCarPaints.first()) }
    var wheelStyle by remember { mutableStateOf(DefaultWheelStyles.first()) }
    var rigNode by remember { mutableStateOf<NodeImpl?>(null) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val carModel = rememberModelInstance(modelLoader, CarModelConfig.CAR_ASSET_PATH)

    RegisterDeviceRotationListener { rotation -> rigNode?.rotation = rotation }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(modifier = Modifier.fillMaxSize())

        // TextureSurface + isOpaque = false so this layer composites over the camera preview
        // beneath it instead of covering it.
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            surfaceType = SurfaceType.TextureSurface,
            isOpaque = false,
        ) {
            carModel?.let { model ->
                // Placeholder placement in front of the default camera — retune once the real
                // car .glb's actual size is known.
                Node(
                    position = Position(y = -0.3f, z = -1.5f),
                    apply = { rigNode = this },
                ) {
                    CustomizableCar(
                        carModel = model,
                        wheelModelLoader = modelLoader,
                        paint = paint,
                        wheelStyle = wheelStyle,
                    )
                }
            }

            ContactShadow(
                size = Size(1.2f, 1.2f, 0f),
                context = ContactShadowContext.Floor,
                position = Position(y = -0.8f, z = -1.5f),
            )
        }

        CustomizationControls(
            paints = DefaultCarPaints,
            selectedPaint = paint,
            onPaintSelected = { paint = it },
            wheelStyles = DefaultWheelStyles,
            selectedWheelStyle = wheelStyle,
            onWheelStyleSelected = { wheelStyle = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}
