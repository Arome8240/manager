package com.example.arcarcustomizer.customization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import io.github.sceneview.SceneScope
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberModelInstance

/** What an option puts on the car: a loaded .glb, or a part generated in code. */
sealed interface PartVisual {
    data class Model(val assetPath: String) : PartVisual
    data class Generated(val part: GeneratedPart) : PartVisual
}

/**
 * One selectable option for a [PartSlot] (a specific tire, spoiler, decal set, etc.).
 */
data class PartOption(
    val label: String,
    val visual: PartVisual,
    val statDelta: CarStats = CarStats.Zero,
) {
    constructor(label: String, assetPath: String, statDelta: CarStats = CarStats.Zero) :
        this(label, PartVisual.Model(assetPath), statDelta)

    constructor(label: String, part: GeneratedPart, statDelta: CarStats = CarStats.Zero) :
        this(label, PartVisual.Generated(part), statDelta)
}

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
 *
 * For [PartVisual.Model] options they place and size each model instance. Generated parts fit
 * themselves to [CarModel.body], so for those slots [positions] and [targetSizeNative] only
 * tell the garage camera what to frame.
 */
data class PartSlot(
    val id: String,
    val label: String,
    val positions: List<Position>,
    val targetSizeNative: Float,
    val options: List<PartOption>,
    /** Garage camera height for this slot's close-up; null uses the default low angle. */
    val cameraElevationDeg: Float? = null,
) {
    /** Whether this slot's parts are loaded models placed at [positions]. */
    val isModelSlot get() = options.all { it.visual is PartVisual.Model }
}

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
    val body: BodyAnchors,
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

// Stat deltas are illustrative, like CarStats itself.
private val SpoilerOptions = listOf(
    PartOption("Stock", GeneratedPart.None),
    PartOption("Lip", GeneratedPart.LipSpoiler, CarStats(0f, 0f, 0.02f)),
    PartOption("Ducktail", GeneratedPart.Ducktail, CarStats(0f, 0f, 0.03f)),
    PartOption("GT Wing", GeneratedPart.GtWing, CarStats(-0.03f, 0f, 0.08f)),
)

private val BodyKitOptions = listOf(
    PartOption("Stock", GeneratedPart.None),
    PartOption("Street", GeneratedPart.StreetKit, CarStats(0.01f, 0f, 0.02f)),
    PartOption("Track", GeneratedPart.TrackKit, CarStats(0.01f, 0.02f, 0.05f)),
)

private val DecalOptions = listOf(
    PartOption("None", GeneratedPart.None),
    PartOption("Racing Stripes", GeneratedPart.RacingStripes),
    PartOption("Race Number", GeneratedPart.RaceNumber),
    PartOption("Side Livery", GeneratedPart.SideLivery),
)

/**
 * Spoiler / body kit / decal slots, anchored on [body]. The positions and sizes here only aim
 * the garage camera: the rear deck for spoilers, the front corner for body kits, the door (from
 * higher up, to take in the stripes too) for decals.
 */
private fun generatedSlots(body: BodyAnchors, nativeToMeters: Float): Array<PartSlot> {
    val u = 1f / nativeToMeters
    return arrayOf(
        PartSlot(
            id = "spoiler",
            label = "Spoilers",
            positions = listOf(Position(body.centerX, body.deckY + 0.2f * u, body.deckRearZ)),
            targetSizeNative = 1.3f * u,
            options = SpoilerOptions,
            cameraElevationDeg = 16f,
        ),
        PartSlot(
            id = "bodyKit",
            label = "Body Kits",
            positions = listOf(Position(body.centerX + body.sillHalfWidth, body.sillY, body.frontZ - 0.9f * u)),
            targetSizeNative = 1.8f * u,
            options = BodyKitOptions,
            cameraElevationDeg = 6f,
        ),
        PartSlot(
            id = "decals",
            label = "Decals",
            positions = listOf(Position(body.centerX + body.doorSideHalfWidth, body.doorY, body.doorZ)),
            targetSizeNative = 2.2f * u,
            options = DecalOptions,
            cameraElevationDeg = 30f,
        ),
    )
}

/** Ray-cast from car_nissan_gtr.glb's triangles (metres). The stock wing sits at z≈-2.53, y≈0.74. */
private val NissanGtrBody = BodyAnchors(
    centerX = 0f,
    frontZ = 3.17f,
    rearZ = -2.94f,
    noseBottomY = -0.60f,
    tailBottomY = -0.40f,
    sillY = -0.55f,
    sillHalfWidth = 1.17f,
    skirtZ = -1.05f..1.35f,
    doorSideHalfWidth = 1.16f,
    doorY = 0.08f,
    doorZ = -0.2f,
    deckY = 0.55f,
    deckRearZ = -2.78f,
    wingZ = -2.62f,
    topProfile = listOf(
        -2.74f to 0.520f, -2.53f to 0.545f, -2.33f to 0.570f, -2.12f to 0.585f,
        -1.92f to 0.657f, -1.71f to 0.729f, -1.50f to 0.801f, -1.30f to 0.873f,
        -1.09f to 0.945f, -0.89f to 0.980f, -0.68f to 0.997f, -0.48f to 0.997f,
        -0.27f to 0.994f, -0.07f to 0.986f, 0.14f to 0.977f, 0.35f to 0.947f,
        0.55f to 0.877f, 0.76f to 0.779f, 0.96f to 0.682f, 1.17f to 0.585f,
        1.37f to 0.488f, 1.58f to 0.450f, 1.79f to 0.426f, 1.99f to 0.401f,
        2.20f to 0.376f, 2.40f to 0.342f, 2.61f to 0.296f, 2.81f to 0.249f,
        3.02f to 0.175f,
    ),
    glassZ = listOf(-1.95f..-1.1f, 0.42f..1.42f),
)

/**
 * Ray-cast from car_lamborghini.glb's triangles (centimetres; the body is centred on
 * x≈-19.5). The stock wing sits right at the tail (z≈-255, y≈106-110).
 */
private val LamborghiniAventadorBody = BodyAnchors(
    centerX = -19.54f,
    frontZ = 194f,
    rearZ = -234f,
    noseBottomY = 13f,
    tailBottomY = 19f,
    sillY = 14f,
    sillHalfWidth = 100f,
    skirtZ = -132f..58f,
    doorSideHalfWidth = 96f,
    doorY = 48f,
    doorZ = -25f,
    deckY = 91f,
    deckRearZ = -242f,
    wingZ = -222f,
    topProfile = listOf(
        -239.1f to 90.9f, -222.8f to 93.9f, -206.5f to 90.9f, -190.2f to 96.5f,
        -173.9f to 97.1f, -157.6f to 101.3f, -141.3f to 105.1f, -124.9f to 105.1f,
        -108.6f to 112.4f, -92.3f to 114.2f, -76.0f to 115.6f, -59.7f to 116.5f,
        -43.4f to 116.5f, -27.1f to 116.0f, -10.7f to 114.8f, 5.6f to 113.0f,
        21.9f to 109.8f, 38.2f to 105.2f, 54.5f to 100.4f, 70.8f to 94.9f,
        87.2f to 88.9f, 103.5f to 82.9f, 119.8f to 79.7f, 136.1f to 76.1f,
        152.4f to 71.9f, 168.7f to 67.5f, 185.0f to 60.8f, 201.4f to 52.7f,
    ),
    glassZ = listOf(0f..62f),
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
    body = NissanGtrBody,
    slots = listOf(
        PartSlot(
            id = "wheels",
            label = "Wheels & Rims",
            positions = listOf(
                Position(x = 1.02f, y = -0.37f, z = -1.64f), // front-right
                Position(x = -1.04f, y = -0.37f, z = -1.64f), // front-left
                Position(x = -1.04f, y = -0.37f, z = 1.94f), // rear-left
                Position(x = 1.02f, y = -0.37f, z = 1.94f), // rear-right
            ),
            targetSizeNative = 0.92f,
            options = WheelOptions,
        ),
        *generatedSlots(NissanGtrBody, nativeToMeters = 1f),
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
    body = LamborghiniAventadorBody,
    slots = listOf(
        PartSlot(
            id = "wheels",
            label = "Wheels & Rims",
            positions = listOf(
                Position(x = 65.89f, y = 34.6f, z = 99.44f), // front-right
                Position(x = -104.97f, y = 34.6f, z = 99.44f), // front-left
                Position(x = 65.89f, y = 36.42f, z = -175.47f), // rear-right
                Position(x = -104.97f, y = 36.42f, z = -175.47f), // rear-left
            ),
            targetSizeNative = 70f,
            options = WheelOptions,
        ),
        *generatedSlots(LamborghiniAventadorBody, nativeToMeters = 0.01f),
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
    materialLoader: MaterialLoader,
    paint: CarPaint,
    selectedOptions: Map<String, PartOption>,
) {
    val partMaterials = rememberPartMaterials(materialLoader, paint)
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
                // Keyed on the option too, so switching e.g. a wing for a ducktail replaces
                // its nodes instead of reusing mismatched ones.
                key(slot.id, option.label) {
                    when (val visual = option.visual) {
                        is PartVisual.Model -> slot.positions.forEach { position ->
                            val partInstance = rememberModelInstance(partModelLoader, visual.assetPath)
                            partInstance?.let {
                                ModelNode(
                                    modelInstance = it,
                                    position = position,
                                    centerOrigin = Position(x = 0f, y = 0f, z = 0f),
                                    scaleToUnits = slot.targetSizeNative,
                                )
                            }
                        }
                        is PartVisual.Generated ->
                            GeneratedPartNodes(visual.part, car, partMaterials, paint)
                    }
                }
            }
        }
    }
}
