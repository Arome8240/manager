package com.example.arcarcustomizer.customization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import io.github.sceneview.math.Scale
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberModelInstance

/**
 * One selectable part model for a [PartSlot] (a specific tire, rim, steering wheel, headlight,
 * etc.).
 */
data class PartOption(val label: String, val assetPath: String)

/**
 * A customizable slot on a car — e.g. "wheels" (4 positions) or "steering wheel" (1 position).
 * [positions] and [targetSizeNative] are in the owning [CarModel]'s own native asset units (see
 * [CarModel.nativeToMeters]) — measured directly from that car's glTF geometry, not real meters.
 */
data class PartSlot(
    val id: String,
    val label: String,
    val positions: List<Position>,
    val targetSizeNative: Float,
    val options: List<PartOption>,
)

/**
 * One selectable car body, with everything about it that's specific to its own .glb: which
 * material is the paintable body, which of its own meshes are placeholder parts to always hide
 * (replaced by the matching [PartSlot] instead), the scale needed to bring its native units to
 * metres, and its customizable slots.
 *
 * Adding a new car means measuring these same facts from its own glTF JSON (see how
 * `car_nissan_gtr.glb` and `car_lamborghini.glb` were measured) — there's no auto-detection.
 */
data class CarModel(
    val id: String,
    val label: String,
    val assetPath: String,
    val bodyMaterialName: String,
    val hiddenNativeNodeNames: Set<String>,
    val nativeToMeters: Float,
    val slots: List<PartSlot>,
)

/**
 * The wheel-ish downloads sourced so far. None of them cleanly separate a tire from its rim, so
 * they're offered together as one "Wheels" slot rather than split into separate Tires/Rims
 * slots that would only have one real option each. Add a new [PartSlot] (e.g. "headlights") the
 * same way once a matching asset exists — nothing else about this system is wheel-specific.
 */
private val WheelOptions = listOf(
    PartOption("Hubcap", "models/wheels_hubcap.glb"),
    PartOption("Tire", "models/wheels_tire.glb"),
    PartOption("Vehicle Tire", "models/wheels_vehicle_tire.glb"),
)

private val SteeringWheelOptions = listOf(
    PartOption("Steering Wheel", "models/wheels_steering_wheel.glb"),
)

/**
 * Measured directly from car_nissan_gtr.glb's glTF JSON: `chasis_NONE` is the untextured
 * material spanning the whole shell (see the original measurement notes this replaced), the
 * four wheels are separate nodes named "Circle"/"Circle.001-3", and the model's own units are
 * already ~metres (its ~6.17 m chassis length is life-size once the big rear wing is included).
 */
private val NissanGtr = CarModel(
    id = "nissan_gtr",
    label = "Nissan GT-R",
    assetPath = "models/car_nissan_gtr.glb",
    bodyMaterialName = "chasis_NONE",
    hiddenNativeNodeNames = setOf("Circle", "Circle.001", "Circle.002", "Circle.003"),
    nativeToMeters = 1f,
    slots = listOf(
        PartSlot(
            id = "wheels",
            label = "Wheels",
            positions = listOf(
                Position(x = 1.02f, y = -0.37f, z = -1.64f), // front-right
                Position(x = -1.04f, y = -0.37f, z = -1.64f), // front-left
                Position(x = -1.04f, y = -0.37f, z = 1.94f), // rear-left
                Position(x = 1.02f, y = -0.37f, z = 1.94f), // rear-right
            ),
            targetSizeNative = 0.92f,
            options = WheelOptions,
        ),
        PartSlot(
            id = "steeringWheel",
            label = "Steering Wheel",
            // Estimated cabin position — car_nissan_gtr.glb has no dashboard reference point to
            // measure, so this needs visual tuning on-device once you can see it.
            positions = listOf(Position(x = -0.35f, y = 0.15f, z = -0.9f)),
            targetSizeNative = 0.4f,
            options = SteeringWheelOptions,
        ),
    ),
)

/**
 * Measured directly from car_lamborghini.glb's glTF JSON: body + wheels share one material
 * (`_Lamborghini_AventadorLamborghini_Aventador_BodySG`), the four wheels are separate nodes
 * named "Lamborghini_Aventador_Wheel_FL/FR/RL/RR", and the model's own units are centimetres —
 * confirmed by its ~489 cm (4.89 m) chassis length and ~70 cm wheel diameter both matching a
 * real Aventador.
 */
private val LamborghiniAventador = CarModel(
    id = "lamborghini_aventador",
    label = "Lamborghini Aventador",
    assetPath = "models/car_lamborghini.glb",
    bodyMaterialName = "_Lamborghini_AventadorLamborghini_Aventador_BodySG",
    hiddenNativeNodeNames = setOf(
        "Lamborghini_Aventador_Wheel_FL",
        "Lamborghini_Aventador_Wheel_FR",
        "Lamborghini_Aventador_Wheel_RL",
        "Lamborghini_Aventador_Wheel_RR",
    ),
    nativeToMeters = 0.01f,
    slots = listOf(
        PartSlot(
            id = "wheels",
            label = "Wheels",
            positions = listOf(
                Position(x = 65.89f, y = 34.6f, z = 99.44f), // front-right
                Position(x = -104.97f, y = 34.6f, z = 99.44f), // front-left
                Position(x = 65.89f, y = 36.42f, z = -175.47f), // rear-right
                Position(x = -104.97f, y = 36.42f, z = -175.47f), // rear-left
            ),
            targetSizeNative = 70f,
            options = WheelOptions,
        ),
        PartSlot(
            id = "steeringWheel",
            label = "Steering Wheel",
            // Estimated cabin position, same caveat as the Nissan's — needs visual tuning.
            positions = listOf(Position(x = -20f, y = 75f, z = 45f)),
            targetSizeNative = 35f,
            options = SteeringWheelOptions,
        ),
    ),
)

val CarCatalog = listOf(NissanGtr, LamborghiniAventador)

/** A selectable paint color, applied to the body material's baseColorFactor. Car-independent. */
data class CarPaint(val label: String, val color: Color)

val DefaultCarPaints = listOf(
    CarPaint("Red", Color(0xFFC62828)),
    CarPaint("Blue", Color(0xFF1565C0)),
    CarPaint("Black", Color(0xFF212121)),
    CarPaint("White", Color(0xFFFAFAFA)),
)

/**
 * Sets the body material's `baseColorFactor` to [color]. [bodyMaterialName] must match the
 * material name authored on the car .glb's body mesh (see [CarModel.bodyMaterialName]).
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
 * Renders [car]'s body plus whatever [PartOption] is selected for each of its [PartSlot]s. This
 * is the shared module: it is a plain [SceneScope] extension with no AR-specific or
 * CameraX-specific dependency, so the exact same call works inside both
 * [io.github.sceneview.SceneView] (Fallback mode) and [io.github.sceneview.ar.ARSceneView] (AR
 * mode, whose `ARSceneScope` extends `SceneScope`).
 *
 * Everything inside is expressed in [car]'s own native units and wrapped in one
 * `Node(scale = car.nativeToMeters)`, so from the outside (an AR anchor, a Fallback rig) every
 * car — regardless of what units it was authored in — presents as a consistent, real-world-metre
 * object. Swapping [car] or any [selectedOptions] entry is a plain state change: each part is
 * loaded via [rememberModelInstance], keyed on its own asset path, so Compose disposes the old
 * model and loads the new one — no manual node surgery.
 */
@Composable
fun SceneScope.CustomizableCar(
    car: CarModel,
    partModelLoader: ModelLoader,
    paint: CarPaint,
    selectedOptions: Map<String, PartOption>,
) {
    Node(scale = Scale(car.nativeToMeters)) {
        val carModel = rememberModelInstance(partModelLoader, car.assetPath)
        carModel?.let { model ->
            LaunchedEffect(model, paint, car) {
                model.applyPaint(car.bodyMaterialName, paint.color)
            }
            ModelNode(
                modelInstance = model,
                apply = {
                    // The car's own part meshes only mark where each slot's positions came
                    // from — always hidden in favor of the selected PartOption instances below.
                    renderableNodes
                        .filter { it.name in car.hiddenNativeNodeNames }
                        .forEach { it.isVisible = false }
                },
            )
        }

        car.slots.forEach { slot ->
            val option = selectedOptions[slot.id] ?: slot.options.first()
            slot.positions.forEach { position ->
                val partInstance = rememberModelInstance(partModelLoader, option.assetPath)
                partInstance?.let {
                    ModelNode(
                        modelInstance = it,
                        position = position,
                        centerOrigin = Position(x = 0f, y = 0f, z = 0f),
                        scaleToUnits = slot.targetSizeNative,
                    )
                }
            }
        }
    }
}

/**
 * Car picker, paint swatches, and one row per the selected car's [PartSlot]s — shared by both
 * modes. A plain Compose overlay: it draws over whatever 3D content is behind it, AR or
 * Fallback alike.
 */
@Composable
fun CustomizationControls(
    cars: List<CarModel>,
    selectedCar: CarModel,
    onCarSelected: (CarModel) -> Unit,
    paints: List<CarPaint>,
    selectedPaint: CarPaint,
    onPaintSelected: (CarPaint) -> Unit,
    selectedOptions: Map<String, PartOption>,
    onOptionSelected: (slotId: String, option: PartOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LabeledChipRow(
            options = cars,
            optionLabel = { it.label },
            isSelected = { it.id == selectedCar.id },
            onSelected = onCarSelected,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            paints.forEach { paint ->
                val selected = paint == selectedPaint
                Box(
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

        selectedCar.slots.forEach { slot ->
            Column {
                Text(slot.label, color = Color.White.copy(alpha = 0.7f))
                LabeledChipRow(
                    options = slot.options,
                    optionLabel = { it.label },
                    isSelected = { (selectedOptions[slot.id] ?: slot.options.first()) == it },
                    onSelected = { onOptionSelected(slot.id, it) },
                )
            }
        }
    }
}

@Composable
private fun <T> LabeledChipRow(
    options: List<T>,
    optionLabel: (T) -> String,
    isSelected: (T) -> Boolean,
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { option ->
            val selected = isSelected(option)
            Text(
                text = optionLabel(option),
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { onSelected(option) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
