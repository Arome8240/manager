package com.example.arcarcustomizer.ar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.arcarcustomizer.customization.CarModelConfig
import com.example.arcarcustomizer.customization.CustomizableCar
import com.example.arcarcustomizer.customization.CustomizationControls
import com.example.arcarcustomizer.customization.DefaultCarPaints
import com.example.arcarcustomizer.customization.DefaultWheelStyles
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener

/**
 * AR mode (tasks.md Phase 3): plane detection ([ARSceneView]'s `planeRenderer`), tap-to-place
 * (a screen-centre [PlacementReticle] plus a confirmed-tap gesture that turns its last hit into
 * an [Anchor]), and — via [CustomizableCar]'s `isEditable` rig node — pinch-scale, two-finger
 * rotate and drag-reposition, all provided by SceneView's own gesture system.
 */
@Composable
fun ArPlacementScreen() {
    var anchor by remember { mutableStateOf<Anchor?>(null) }
    var reticleHit by remember { mutableStateOf<HitResult?>(null) }
    var paint by remember { mutableStateOf(DefaultCarPaints.first()) }
    var wheelStyle by remember { mutableStateOf(DefaultWheelStyles.first()) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val carModel = rememberModelInstance(modelLoader, CarModelConfig.CAR_ASSET_PATH)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewWidthPx = constraints.maxWidth.toFloat()
        val viewHeightPx = constraints.maxHeight.toFloat()

        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            planeRenderer = true,
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { _, _ ->
                    if (anchor == null) {
                        reticleHit?.createAnchor()?.let { anchor = it }
                    }
                }
            ),
        ) {
            if (anchor == null) {
                PlacementReticle(
                    xPx = viewWidthPx / 2f,
                    yPx = viewHeightPx / 2f,
                    onHitResultChanged = { reticleHit = it },
                )
            }

            val model = carModel
            val currentAnchor = anchor
            if (model != null && currentAnchor != null) {
                AnchorNode(anchor = currentAnchor) {
                    // A single editable rig so the body and wheel-set move, scale and rotate
                    // together as one rigid object instead of independently.
                    Node(isEditable = true) {
                        CustomizableCar(
                            carModel = model,
                            wheelModelLoader = modelLoader,
                            paint = paint,
                            wheelStyle = wheelStyle,
                        )
                    }
                }
            }
        }

        if (anchor == null) {
            Text(
                text = "Move your phone to find a surface, then tap to place the car",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            )
        } else {
            Button(
                onClick = { anchor = null },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            ) {
                Text("Reset placement")
            }
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
