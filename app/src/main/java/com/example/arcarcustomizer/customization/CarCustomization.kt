package com.example.arcarcustomizer.customization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import io.github.sceneview.SceneScope
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode
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
 * A customizable slot on a car — e.g. "wheels" (4 positions) or "spoiler" (1 camera aim).
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
    /**
     * True when [positions] are where parts physically sit (the wheels), so they
     * describe the car's footprint; false when they're only camera aims (spoilers, decals…).
     */
    val positionsAreGeometry: Boolean = true,
)

/**
 * One selectable car body, with everything about it that's specific to its own .glb: which
 * material is the paintable body, which of its own meshes are placeholder parts to always hide
 * (replaced by the matching [PartSlot] instead), the scale needed to bring its native units to
 * metres, and its customizable slots.
 *
 * Adding a new car means measuring these same facts from its own geometry: the GT-R and
 * Aventador were ray-cast by hand, the Kenney cars by the conversion script that also split out
 * their paintable body material — there's no runtime auto-detection.
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
    val lights: CarLights,
    val slots: List<PartSlot>,
    /** Stock parts baked into the .glb as their own nodes, hidden once an aftermarket spoiler is fitted. */
    val stockSpoilerNodeNames: Set<String> = emptySet(),
)

/**
 * Wheel options: the downloaded tire models plus three wheels generated in code (see
 * [GeneratedPart]). None of the downloads cleanly separate a tire from its rim, so wheels are
 * offered whole as one slot rather than split into separate Tires/Rims slots.
 */
private val WheelOptions = listOf(
    PartOption("Tire", "models/wheels_tire.glb", CarStats(0f, 0.03f, 0.06f)),
    PartOption("Vehicle Tire", "models/wheels_vehicle_tire.glb", CarStats(0.02f, 0f, 0.03f)),
    PartOption("Blade 5-Spoke", GeneratedPart.WheelFiveSpoke, CarStats(0.01f, 0.02f, 0.03f)),
    PartOption("Gold Mesh", GeneratedPart.WheelMesh, CarStats(0f, 0.01f, 0.05f)),
    PartOption("Deep Dish", GeneratedPart.WheelDeepDish, CarStats(-0.01f, 0.03f, 0.04f)),
)

// Stat deltas are illustrative, like CarStats itself.
private val SpoilerOptions = listOf(
    PartOption("Stock", GeneratedPart.None),
    PartOption("Lip", GeneratedPart.LipSpoiler, CarStats(0f, 0f, 0.02f)),
    PartOption("Ducktail", GeneratedPart.Ducktail, CarStats(0f, 0f, 0.03f)),
    PartOption("GT Wing", GeneratedPart.GtWing, CarStats(-0.03f, 0f, 0.08f)),
    PartOption("Swan Neck", GeneratedPart.SwanNeck, CarStats(-0.02f, 0f, 0.09f)),
    PartOption("Twin Deck", GeneratedPart.TwinDeck, CarStats(-0.04f, 0f, 0.1f)),
)

private val BodyKitOptions = listOf(
    PartOption("Stock", GeneratedPart.None),
    PartOption("Street", GeneratedPart.StreetKit, CarStats(0.01f, 0f, 0.02f)),
    PartOption("Track", GeneratedPart.TrackKit, CarStats(0.01f, 0.02f, 0.05f)),
    PartOption("Widebody", GeneratedPart.WidebodyKit, CarStats(-0.01f, 0.01f, 0.06f)),
    PartOption("Rally", GeneratedPart.RallyKit, CarStats(-0.03f, 0.03f, 0.02f)),
    PartOption("Time Attack", GeneratedPart.TimeAttackKit, CarStats(0f, 0.03f, 0.08f)),
)

private val DecalOptions = listOf(
    PartOption("None", GeneratedPart.None),
    PartOption("Racing Stripes", GeneratedPart.RacingStripes),
    PartOption("Race Number", GeneratedPart.RaceNumber),
    PartOption("Side Livery", GeneratedPart.SideLivery),
    PartOption("Flames", GeneratedPart.Flames),
    PartOption("Checker Fade", GeneratedPart.CheckerFade),
)

private val HeadlightOptions = listOf(
    PartOption("Stock", GeneratedPart.None),
    PartOption("Xenon", GeneratedPart.HeadXenon),
    PartOption("Ice Blue", GeneratedPart.HeadIce),
    PartOption("Golden", GeneratedPart.HeadGold),
    PartOption("Violet", GeneratedPart.HeadViolet),
)

private val TailLightOptions = listOf(
    PartOption("Stock", GeneratedPart.None),
    PartOption("LED Red", GeneratedPart.TailLed),
    PartOption("Smoked", GeneratedPart.TailSmoked),
    PartOption("Light Bar", GeneratedPart.TailBar),
    PartOption("Ember", GeneratedPart.TailEmber),
)

private val UnderglowOptions = listOf(
    PartOption("None", GeneratedPart.None),
    PartOption("Cyan", GeneratedPart.GlowCyan),
    PartOption("Magenta", GeneratedPart.GlowMagenta),
    PartOption("Lime", GeneratedPart.GlowLime),
    PartOption("Violet", GeneratedPart.GlowViolet),
)

/**
 * Every slot but the wheels, anchored on the car's measured [body] and [lights]. The positions
 * and sizes here only aim the garage camera: the rear deck for spoilers, the front corner for
 * body kits, the door (from higher up, to take in the stripes too) for decals, the lamps for
 * lights, and low along the sill for underglow.
 */
private fun generatedSlots(body: BodyAnchors, lights: CarLights, nativeToMeters: Float): Array<PartSlot> {
    val u = 1f / nativeToMeters
    fun slot(id: String, label: String, aim: Position, sizeM: Float, options: List<PartOption>, elevation: Float) =
        PartSlot(
            id = id,
            label = label,
            positions = listOf(aim),
            targetSizeNative = sizeM * u,
            options = options,
            cameraElevationDeg = elevation,
            positionsAreGeometry = false,
        )
    return arrayOf(
        slot("spoiler", "Spoilers", Position(body.centerX, body.deckY + 0.2f * u, body.deckRearZ), 1.3f, SpoilerOptions, 16f),
        slot("bodyKit", "Body Kits", Position(body.centerX + body.sillHalfWidth, body.sillY, body.frontZ - 0.9f * u), 1.8f, BodyKitOptions, 6f),
        slot("decals", "Decals", Position(body.centerX + body.doorSideHalfWidth, body.doorY, body.doorZ), 2.2f, DecalOptions, 30f),
        slot("headlights", "Headlights", lights.head.maxBy { it.center.x }.center, 1.1f, HeadlightOptions, 8f),
        slot("tailLights", "Tail Lights", lights.tail.maxBy { it.center.x }.center, 1.1f, TailLightOptions, 10f),
        slot("underglow", "Underglow", Position(body.centerX + body.sillHalfWidth, body.sillY, body.doorZ), 2.4f, UnderglowOptions, 3f),
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

/** Headlight lenses and the four round tail lamps, located from front/rear renders of the .glb. */
private val NissanGtrLights = CarLights(
    head = listOf(-1f, 1f).map { LightPanel(Position(it * 0.82f, 0.14f, 2.849f), 0.36f, 0.12f) },
    tail = listOf(-1f, 1f).flatMap {
        listOf(
            LightPanel(Position(it * 0.6f, 0.37f, -2.783f), 0.14f, 0.14f, round = true),
            LightPanel(Position(it * 0.94f, 0.37f, -2.721f), 0.2f, 0.2f, round = true),
        )
    },
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

private val LamborghiniAventadorLights = CarLights(
    head = listOf(48.46f, -87.54f).map { LightPanel(Position(it, 62f, 171.5f), 20f, 7f) },
    tail = listOf(54.46f, -93.54f).map { LightPanel(Position(it, 81f, -245.35f), 26f, 6f) },
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
    lights = NissanGtrLights,
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
        *generatedSlots(NissanGtrBody, NissanGtrLights, nativeToMeters = 1f),
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
    lights = LamborghiniAventadorLights,
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
        *generatedSlots(LamborghiniAventadorBody, LamborghiniAventadorLights, nativeToMeters = 0.01f),
    ),
)

/*
 * Kenney Car Kit cars (CC0, https://kenney.nl/assets/car-kit), converted for this app: texture
 * embedded, and the body-colour triangles moved into their own "paint" material whose texture
 * is a grayscale copy of Kenney's palette, so baseColorFactor paints the body while keeping its
 * shading (windows, trim and lamps keep the original palette). All three face +z with the
 * ground at y = 0; wheels are 0.6 units across with centres at x = ±0.425.
 */

private val KenneyWheelNodes = setOf("wheel-front-left", "wheel-front-right", "wheel-back-left", "wheel-back-right")

private fun kenneyWheels(frontZ: Float, rearZ: Float) = PartSlot(
    id = "wheels",
    label = "Wheels & Rims",
    positions = listOf(
        Position(0.425f, 0.3f, frontZ), Position(-0.425f, 0.3f, frontZ),
        Position(0.425f, 0.3f, rearZ), Position(-0.425f, 0.3f, rearZ),
    ),
    targetSizeNative = 0.6f,
    options = WheelOptions,
)

private fun kenneyLights(head: Position, headW: Float, headH: Float, tail: Position, tailW: Float, tailH: Float) = CarLights(
    head = listOf(-1f, 1f).map { LightPanel(Position(it * head.x, head.y, head.z), headW, headH) },
    tail = listOf(-1f, 1f).map { LightPanel(Position(it * tail.x, tail.y, tail.z), tailW, tailH) },
)

private val SportsSedanBody = BodyAnchors(
    centerX = 0f, frontZ = 1.25f, rearZ = -1.3f, noseBottomY = 0.15f, tailBottomY = 0.15f,
    sillY = 0.2f, sillHalfWidth = 0.65f, skirtZ = -0.3f..0.3f,
    doorSideHalfWidth = 0.55f, doorY = 0.66f, doorZ = 0f,
    deckY = 0.677f, deckRearZ = -1.17f, wingZ = -0.94f,
    topProfile = listOf(
        -1.198f to 0.626f, -1.108f to 0.671f, -1.018f to 0.716f, -0.927f to 0.761f,
        -0.837f to 0.813f, -0.747f to 0.903f, -0.657f to 0.993f, -0.566f to 1.084f,
        -0.476f to 1.1f, -0.296f to 1.1f, -0.115f to 1.1f, 0.065f to 1.1f,
        0.155f to 1.096f, 0.246f to 1.028f, 0.336f to 0.961f, 0.426f to 0.893f,
        0.516f to 0.825f, 0.607f to 0.792f, 0.697f to 0.779f, 0.787f to 0.766f,
        0.877f to 0.753f, 0.968f to 0.709f, 1.058f to 0.655f, 1.148f to 0.601f,
    ),
    glassZ = listOf(-0.84f..-0.48f, 0.17f..0.6f),
)
private val SportsSedanLights = kenneyLights(
    head = Position(0.42f, 0.665f, 1.042f), headW = 0.25f, headH = 0.07f,
    tail = Position(0.53f, 0.72f, -1.01f), tailW = 0.14f, tailH = 0.08f,
)
private val SportsSedan = CarModel(
    id = "kenney_sports_sedan",
    label = "Kenney Sports Sedan",
    assetPath = "models/car_sports_sedan.glb",
    bodyMaterialName = "paint",
    hiddenNativeNodeNames = KenneyWheelNodes,
    nativeToMeters = 1.843f,
    baseStats = CarStats(topSpeed = 0.74f, acceleration = 0.72f, handling = 0.76f),
    body = SportsSedanBody,
    lights = SportsSedanLights,
    slots = listOf(kenneyWheels(0.66f, -0.66f), *generatedSlots(SportsSedanBody, SportsSedanLights, 1.843f)),
    stockSpoilerNodeNames = setOf("spoiler"),
)

private val HotHatchBody = BodyAnchors(
    centerX = 0f, frontZ = 1.4f, rearZ = -1.45f, noseBottomY = 0.15f, tailBottomY = 0.25f,
    sillY = 0.2f, sillHalfWidth = 0.65f, skirtZ = -0.45f..0.45f,
    doorSideHalfWidth = 0.55f, doorY = 0.6f, doorZ = 0f,
    // Spoilers mount on the roof's trailing edge, above the sloped hatch glass.
    deckY = 1.1f, deckRearZ = -0.95f, wingZ = -0.82f,
    topProfile = listOf(
        -1.235f to 0.834f, -1.134f to 0.888f, -1.033f to 0.941f, -0.933f to 1.1f,
        -0.731f to 1.1f, -0.529f to 1.1f, -0.328f to 1.1f, -0.126f to 1.1f,
        0.076f to 1.1f, 0.177f to 1.097f, 0.278f to 1.022f, 0.378f to 0.946f,
        0.479f to 0.87f, 0.58f to 0.798f, 0.681f to 0.77f, 0.782f to 0.743f,
        0.883f to 0.715f, 0.983f to 0.687f, 1.084f to 0.659f, 1.185f to 0.632f,
        1.286f to 0.604f,
    ),
    glassZ = listOf(-1.25f..-0.94f, 0.18f..0.58f),
)
private val HotHatchLights = kenneyLights(
    head = Position(0.45f, 0.605f, 1.282f), headW = 0.2f, headH = 0.07f,
    tail = Position(0.52f, 0.875f, -1.158f), tailW = 0.12f, tailH = 0.09f,
)
private val HotHatch = CarModel(
    id = "kenney_hot_hatch",
    label = "Kenney Hot Hatch",
    assetPath = "models/car_hot_hatch.glb",
    bodyMaterialName = "paint",
    hiddenNativeNodeNames = KenneyWheelNodes,
    nativeToMeters = 1.474f,
    baseStats = CarStats(topSpeed = 0.66f, acceleration = 0.74f, handling = 0.82f),
    body = HotHatchBody,
    lights = HotHatchLights,
    slots = listOf(kenneyWheels(0.81f, -0.81f), *generatedSlots(HotHatchBody, HotHatchLights, 1.474f)),
)

private val LuxurySuvBody = BodyAnchors(
    centerX = 0f, frontZ = 1.45f, rearZ = -1.4f, noseBottomY = 0.15f, tailBottomY = 0.125f,
    sillY = 0.3f, sillHalfWidth = 0.5f, skirtZ = -0.35f..0.45f,
    doorSideHalfWidth = 0.55f, doorY = 0.72f, doorZ = 0.05f,
    // Roof spoiler on the trailing edge of the flat roof.
    deckY = 1.3f, deckRearZ = -1.05f, wingZ = -0.9f,
    topProfile = listOf(
        -1.023f to 1.3f, -0.842f to 1.3f, -0.66f to 1.3f, -0.479f to 1.3f,
        -0.297f to 1.3f, -0.116f to 1.3f, 0.066f to 1.3f, 0.247f to 1.3f,
        0.338f to 1.3f, 0.429f to 1.252f, 0.519f to 1.101f, 0.61f to 0.95f,
        0.701f to 0.8f, 0.792f to 0.8f, 0.882f to 0.8f, 0.973f to 0.8f,
        1.064f to 0.8f, 1.155f to 0.8f, 1.245f to 0.664f,
    ),
    glassZ = listOf(0.43f..0.7f),
)
private val LuxurySuvLights = kenneyLights(
    head = Position(0.40f, 0.74f, 1.22f), headW = 0.22f, headH = 0.05f,
    tail = Position(0.46f, 0.785f, -1.4f), tailW = 0.18f, tailH = 0.07f,
)
private val LuxurySuv = CarModel(
    id = "kenney_luxury_suv",
    label = "Kenney Luxury SUV",
    assetPath = "models/car_luxury_suv.glb",
    bodyMaterialName = "paint",
    hiddenNativeNodeNames = KenneyWheelNodes,
    nativeToMeters = 1.719f,
    baseStats = CarStats(topSpeed = 0.64f, acceleration = 0.62f, handling = 0.58f),
    body = LuxurySuvBody,
    lights = LuxurySuvLights,
    slots = listOf(kenneyWheels(0.81f, -0.71f), *generatedSlots(LuxurySuvBody, LuxurySuvLights, 1.719f)),
)

val CarCatalog = listOf(NissanGtr, LamborghiniAventador, SportsSedan, HotHatch, LuxurySuv)

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
            var carNode by remember { mutableStateOf<ModelNode?>(null) }
            val stockSpoiler = (selectedOptions["spoiler"]?.visual as? PartVisual.Generated)?.part
                .let { it == null || it == GeneratedPart.None }
            LaunchedEffect(carNode, stockSpoiler) {
                carNode?.renderableNodes
                    ?.filter { it.name in car.stockSpoilerNodeNames }
                    ?.forEach { it.isVisible = stockSpoiler }
            }
            carModel?.let { model ->
                LaunchedEffect(model, paint, car) {
                    model.applyPaint(car.bodyMaterialName, paint.color)
                }
                ModelNode(
                    modelInstance = model,
                    apply = {
                        carNode = this
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
                            GeneratedPartNodes(visual.part, car, slot, partMaterials, paint)
                    }
                }
            }
        }
    }
}
