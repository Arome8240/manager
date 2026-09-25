package com.example.arcarcustomizer.ar

import android.view.MotionEvent
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
import com.example.arcarcustomizer.customization.CustomizableCar
import com.example.arcarcustomizer.customization.CustomizationState
import com.example.arcarcustomizer.customization.PartPicker
import com.example.arcarcustomizer.ui.theme.GarageBackButton
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener

/**
 * AR mode (tasks.md Phase 3): plane detection ([ARSceneView]'s `planeRenderer`), tap-to-place
 * (a screen-centre [PlacementReticle] plus a confirmed-tap gesture that turns its last hit into
 * an [Anchor]), and — via [CustomizableCar]'s `isEditable` rig node — pinch-scale, two-finger
 * rotate and drag-reposition, all provided by SceneView's own gesture system.
 */
@Composable
fun ArPlacementScreen(state: CustomizationState, onBack: () -> Unit) {
    var anchor by remember { mutableStateOf<Anchor?>(null) }
    var reticleHit by remember { mutableStateOf<HitResult?>(null) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

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

            val currentAnchor = anchor
            if (currentAnchor != null) {
                AnchorNode(anchor = currentAnchor) {
                    // A single editable rig so the whole car (body, wheels, steering wheel)
                    // moves, scales and rotates together as one rigid object.
                    Node(isEditable = true) {
                        CustomizableCar(
                            car = state.car,
                            partModelLoader = modelLoader,
                            paint = state.paint,
                            selectedOptions = state.selectedOptions,
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
                    .padding(start = 24.dp, top = 72.dp, end = 24.dp)
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

        GarageBackButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(16.dp))

        PartPicker(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }
}
