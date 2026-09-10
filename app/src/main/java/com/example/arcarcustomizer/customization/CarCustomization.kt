package com.example.arcarcustomizer.customization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneScope
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberModelInstance

/**
 * Everything about the car model itself that depends on the [asset] the user supplies.
 * Update these once the real car/wheel .glb files replace the Phase 1 placeholder.
 */
object CarModelConfig {
    const val CAR_ASSET_PATH = "models/placeholder_car.glb"

    /** The body mesh's material name, as authored in the .glb (check it in Blender/your DCC tool). */
    const val BODY_MATERIAL_NAME = "body"
}

/** A selectable paint color, applied to the body material's baseColorFactor. */
data class CarPaint(val label: String, val color: Color)

/** A selectable wheel set, pre-positioned in its own .glb to align with the car's wheel wells. */
data class WheelStyle(val label: String, val assetPath: String)

val DefaultCarPaints = listOf(
    CarPaint("Red", Color(0xFFC62828)),
    CarPaint("Blue", Color(0xFF1565C0)),
    CarPaint("Black", Color(0xFF212121)),
    CarPaint("White", Color(0xFFFAFAFA)),
)

val DefaultWheelStyles = listOf(
    WheelStyle("Stock", "models/wheels/stock_wheels.glb"),
    WheelStyle("Sport", "models/wheels/sport_wheels.glb"),
)

/**
 * Sets the body material's `baseColorFactor` to [color]. [bodyMaterialName] must match the
 * material name authored on the car .glb's body mesh — this is the one place a real car asset
 * requires a matching config value (see [CarModelConfig.BODY_MATERIAL_NAME]).
 */
fun ModelInstance.applyPaint(bodyMaterialName: String, color: Color) {
    materialInstances
        .firstOrNull { it.name == bodyMaterialName }
        ?.setParameter(
            "baseColorFactor",
            color.red, color.green, color.blue, color.alpha
        )
}

/**
 * Renders the car body plus its currently-selected wheel set. This is the shared module: it is a
 * plain [SceneScope] extension with no AR-specific or CameraX-specific dependency, so the exact
 * same call works inside both [io.github.sceneview.SceneView] (Fallback mode) and
 * [io.github.sceneview.ar.ARSceneView] (AR mode, whose `ARSceneScope` extends `SceneScope`).
 *
 * Swapping [wheelStyle] is a plain state change: [rememberModelInstance] is keyed on its asset
 * path, so Compose disposes the old wheel model and loads the new one — no manual node surgery.
 */
@Composable
fun SceneScope.CustomizableCar(
    carModel: ModelInstance,
    wheelModelLoader: ModelLoader,
    paint: CarPaint,
    wheelStyle: WheelStyle,
    bodyMaterialName: String = CarModelConfig.BODY_MATERIAL_NAME,
) {
    LaunchedEffect(carModel, paint, bodyMaterialName) {
        carModel.applyPaint(bodyMaterialName, paint.color)
    }

    ModelNode(modelInstance = carModel)

    val wheelInstance = rememberModelInstance(wheelModelLoader, wheelStyle.assetPath)
    wheelInstance?.let { ModelNode(modelInstance = it) }
}

/**
 * Paint-swatch + wheel-style pickers, shared by both modes. A plain Compose overlay: it draws
 * over whatever 3D content is behind it, AR or Fallback alike.
 */
@Composable
fun CustomizationControls(
    paints: List<CarPaint>,
    selectedPaint: CarPaint,
    onPaintSelected: (CarPaint) -> Unit,
    wheelStyles: List<WheelStyle>,
    selectedWheelStyle: WheelStyle,
    onWheelStyleSelected: (WheelStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            paints.forEach { paint ->
                val selected = paint == selectedPaint
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(paint.color)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = Color.White,
                            shape = CircleShape,
                        )
                        .clickable { onPaintSelected(paint) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            wheelStyles.forEach { style ->
                val selected = style == selectedWheelStyle
                Text(
                    text = style.label,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                        .clickable { onWheelStyleSelected(style) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
