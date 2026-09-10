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
import com.example.arcarcustomizer.customization.CarCatalog
import com.example.arcarcustomizer.customization.CustomizableCar
import com.example.arcarcustomizer.customization.CustomizationControls
import com.example.arcarcustomizer.customization.DefaultCarPaints
import com.example.arcarcustomizer.customization.PartOption
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.createEnvironment
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.ContactShadowContext
import io.github.sceneview.node.Node as NodeImpl
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelLoader

/** Car appears at real-world scale via [com.example.arcarcustomizer.customization.CarModel]'s own
 * metre normalization; this multiplies that down further so it reads as a small display model
 * floating in front of the camera, not something the lens is essentially inside of. */
private const val FALLBACK_TOY_SCALE = 0.1f

/**
 * Fallback mode (tasks.md Phase 4): the same car rendered in a non-AR SceneView over a live
 * CameraX preview, rotated by the device's gyroscope, with a soft contact shadow and HDR-based
 * indirect lighting (there's no ARCore light estimation to fall back on here).
 *
 * Reuses [CustomizableCar] — the identical shared module AR mode uses — so paint and part swaps
 * behave the same way in both modes.
 */
@Composable
fun FallbackScreen() {
    var selectedCar by remember { mutableStateOf(CarCatalog.first()) }
    var paint by remember { mutableStateOf(DefaultCarPaints.first()) }
    var selectedOptions by remember { mutableStateOf(mapOf<String, PartOption>()) }
    var rigNode by remember { mutableStateOf<NodeImpl?>(null) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    RegisterDeviceRotationListener { rotation -> rigNode?.rotation = rotation }

    CameraPermissionGate {
        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(modifier = Modifier.fillMaxSize())

            // TextureSurface + isOpaque = false so this layer composites over the camera
            // preview beneath it instead of covering it.
            SceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                surfaceType = SurfaceType.TextureSurface,
                isOpaque = false,
                environment = rememberEnvironment(environmentLoader, isOpaque = false) {
                    // createSkybox = false: only the HDR's indirect lighting is wanted here —
                    // its skybox would otherwise paint over the live camera feed behind it.
                    // No HDR asset exists yet, so this must tolerate createHDREnvironment
                    // throwing (missing-asset I/O error), not just returning null.
                    runCatching {
                        environmentLoader.createHDREnvironment(
                            "environments/studio.hdr",
                            createSkybox = false,
                        )
                    }.getOrNull() ?: createEnvironment(environmentLoader, false)
                },
            ) {
                Node(
                    position = Position(y = -0.3f, z = -1.5f),
                    scale = Scale(FALLBACK_TOY_SCALE),
                    apply = { rigNode = this },
                ) {
                    CustomizableCar(
                        car = selectedCar,
                        partModelLoader = modelLoader,
                        paint = paint,
                        selectedOptions = selectedOptions,
                    )
                }

                // Resized to match the small (FALLBACK_TOY_SCALE'd) car footprint — this is a
                // separate, unscaled node, so it doesn't inherit the rig's scale automatically.
                ContactShadow(
                    size = Size(0.4f, 0.4f, 0f),
                    context = ContactShadowContext.Floor,
                    position = Position(y = -0.34f, z = -1.5f),
                )
            }

            CustomizationControls(
                cars = CarCatalog,
                selectedCar = selectedCar,
                onCarSelected = { selectedCar = it },
                paints = DefaultCarPaints,
                selectedPaint = paint,
                onPaintSelected = { paint = it },
                selectedOptions = selectedOptions,
                onOptionSelected = { slotId, option ->
                    selectedOptions = selectedOptions + (slotId to option)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}
