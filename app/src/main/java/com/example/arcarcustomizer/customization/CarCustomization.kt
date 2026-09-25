package com.example.arcarcustomizer.customization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
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
data class PartOption(
    val label: String,
    val assetPath: String,
    val statDelta: CarStats = CarStats.Zero,
)

/**
 * Garage performance read-out, each stat on a 0..1 scale. These are illustrative game-style
 * values, not measured figures — tune them freely.
 */
data class CarStats(val topSpeed: Float, val acceleration: Float, val handling: Float) {
    operator fun plus(other: CarStats) = CarStats(
        topSpeed + other.topSpeed,
        acceleration + other.acceleration,
        handling + other.handling,
    )

    companion object {
        val Zero = CarStats(0f, 0f, 0f)
    }
}

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
    val baseStats: CarStats,
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
    PartOption("Tire", "models/wheels_tire.glb", CarStats(0f, 0.03f, 0.06f)),
    PartOption("Vehicle Tire", "models/wheels_vehicle_tire.glb", CarStats(0.02f, 0f, 0.03f)),
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
    baseStats = CarStats(topSpeed = 0.78f, acceleration = 0.84f, handling = 0.80f),
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
    baseStats = CarStats(topSpeed = 0.90f, acceleration = 0.88f, handling = 0.72f),
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
    CarPaint("Midnight Purple", Color(0xFF4A148C)),
    CarPaint("Lime", Color(0xFF76FF03)),
    CarPaint("Sunset Orange", Color(0xFFFF6D00)),
    CarPaint("Gunmetal", Color(0xFF546E7A)),
)

/**
 * Sets the body material's `baseColorFactor` to [color]. [bodyMaterialName] must match the
 * material name authored on the car .glb's body mesh (see [CarModel.bodyMaterialName]).
 *
 * glTF's `baseColorFactor` is linear, while [Color] swatches are sRGB — converting first keeps a
 * swatch's red from rendering as a washed-out salmon on the car.
 */
fun ModelInstance.applyPaint(bodyMaterialName: String, color: Color) {
    val linear = color.convert(ColorSpaces.LinearSrgb)
    materialInstances
        .firstOrNull { it.name == bodyMaterialName }
        ?.setParameter(
            "baseColorFactor",
            linear.red, linear.green, linear.blue, linear.alpha
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
    // Keyed on the car so switching cars rebuilds every node: ModelNode only applies
    // scaleToUnits when created, so reused part nodes would keep the previous car's sizing
    // (e.g. GT-R wheels sized in metres ending up ~1 cm wide under the Aventador's 0.01 scale).
    key(car.id) {
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
}
