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
import io.github.sceneview.math.Position
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberModelInstance

/**
 * Everything about the car model itself that depends on the [asset] the user supplies.
 * Update these if `car.glb` is ever replaced with a different model.
 */
object CarModelConfig {
    const val CAR_ASSET_PATH = "models/car.glb"

    /**
     * The body-paint material, as authored in car.glb. Measured directly from the asset's glTF
     * JSON: the model has TWO chassis-ish materials — "chasis" turned out to be a small
     * textured trim primitive, while "chasis_NONE" (untextured, so baseColorFactor fully
     * controls its color) is the one covering the whole car shell. Re-measure if the car .glb
     * is ever replaced.
     */
    const val BODY_MATERIAL_NAME = "chasis_NONE"

    /**
     * car.glb's own wheel meshes (node names, one per wheel) — kept only as wheel-well
     * placeholders. [CustomizableCar] always hides them: a [WheelStyle] model is rendered at
     * each of [WheelWellPositions] instead, so a wheel swap actually replaces what's visible.
     */
    val NativeWheelNodeNames = setOf("Circle", "Circle.001", "Circle.002", "Circle.003")

    /**
     * The four wheel-well centers, measured from car.glb's own native wheel meshes' bounding
     * boxes (front-right, front-left, rear-left, rear-right). Re-measure if car.glb changes.
     */
    val WheelWellPositions = listOf(
        Position(x = 1.02f, y = -0.37f, z = -1.64f), // front-right
        Position(x = -1.04f, y = -0.37f, z = -1.64f), // front-left
        Position(x = -1.04f, y = -0.37f, z = 1.94f), // rear-left
        Position(x = 1.02f, y = -0.37f, z = 1.94f), // rear-right
    )

    /** Target wheel diameter (metres) each [WheelStyle] model is normalized to via `scaleToUnits`. */
    const val WHEEL_DIAMETER_METERS = 0.92f

    /**
     * car.glb's own chassis (the "chasis_NONE" primitive) is ~6.17 units long as authored —
     * true life-size for AR mode (walk around a real-size car in your driveway), but far too
     * large for Fallback mode, which shows the car floating close in front of the camera like a
     * small display model. This uniformly scales the WHOLE composite (body + wheel-well
     * positions together, via the shared rig node) down to roughly a 0.6 m long toy replica.
     * Re-measure/tune if car.glb changes or this doesn't look right on-device.
     */
    const val FALLBACK_SCALE = 0.1f

    /**
     * HDR environment map for Fallback mode's indirect lighting (Phase 4). Until this asset
     * exists, [io.github.sceneview.loaders.EnvironmentLoader.createHDREnvironment] simply
     * returns `null` and callers fall back to a neutral default environment.
     */
    const val HDR_ASSET_PATH = "environments/studio.hdr"
}

/** A selectable paint color, applied to the body material's baseColorFactor. */
data class CarPaint(val label: String, val color: Color)

/** A selectable wheel style, rendered once per [CarModelConfig.WheelWellPositions] entry. */
data class WheelStyle(val label: String, val assetPath: String)

val DefaultCarPaints = listOf(
    CarPaint("Red", Color(0xFFC62828)),
    CarPaint("Blue", Color(0xFF1565C0)),
    CarPaint("Black", Color(0xFF212121)),
    CarPaint("White", Color(0xFFFAFAFA)),
)

// The four sourced wheel-adjacent models are wildly different native scales (millimeters,
// arbitrary CAD units, near-correct meters) — `scaleToUnits` in CustomizableCar normalizes
// each to CarModelConfig.WHEEL_DIAMETER_METERS regardless.
val DefaultWheelStyles = listOf(
    WheelStyle("Hubcap", "models/wheels_hubcap.glb"),
    WheelStyle("Tire", "models/wheels_tire.glb"),
    WheelStyle("Vehicle Tire", "models/wheels_vehicle_tire.glb"),
    WheelStyle("Steering Wheel", "models/wheels_steering_wheel.glb"),
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

    ModelNode(
        modelInstance = carModel,
        apply = {
            // car.glb's own wheel meshes only mark the wheel-well positions — always hidden in
            // favor of the WheelStyle instances below, which are what a wheel swap changes.
            renderableNodes
                .filter { it.name in CarModelConfig.NativeWheelNodeNames }
                .forEach { it.isVisible = false }
        },
    )

    CarModelConfig.WheelWellPositions.forEach { wellPosition ->
        val wheelInstance = rememberModelInstance(wheelModelLoader, wheelStyle.assetPath)
        wheelInstance?.let {
            ModelNode(
                modelInstance = it,
                position = wellPosition,
                centerOrigin = Position(x = 0f, y = 0f, z = 0f),
                scaleToUnits = CarModelConfig.WHEEL_DIAMETER_METERS,
            )
        }
    }
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
