package com.example.arcarcustomizer.customization

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.example.arcarcustomizer.ui.theme.GarageColors
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import io.github.sceneview.SceneScope
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.material.setColor
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.math.colorOf
import io.github.sceneview.texture.ImageTexture
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The body geometry generated parts are fitted to, in the car's own native units. Every value
 * was measured by ray-casting the car's glTF triangles (surface heights along the centreline,
 * side-skin widths, bumper faces), the same way the wheel positions were measured. Neither
 * car's .glb has separate bumper/wing meshes, so these are the only way to know where parts go.
 *
 * Both catalog cars face +z (nose at +z, tail at -z).
 */
data class BodyAnchors(
    val centerX: Float,
    /** Nose / tail faces at splitter / diffuser height. */
    val frontZ: Float,
    val rearZ: Float,
    /** Underside height at the nose and tail. */
    val noseBottomY: Float,
    val tailBottomY: Float,
    /** Bottom of the side sills and the body's half-width there. */
    val sillY: Float,
    val sillHalfWidth: Float,
    /** Sill span between the rear and front wheel arches. */
    val skirtZ: ClosedFloatingPointRange<Float>,
    /** Door skin: half-width from [centerX], and the centre of the flattest decal area. */
    val doorSideHalfWidth: Float,
    val doorY: Float,
    val doorZ: Float,
    /** Trunk / engine-deck top height and its rear edge. */
    val deckY: Float,
    val deckRearZ: Float,
    /** Where an aftermarket wing mounts (forward of any stock wing baked into the body). */
    val wingZ: Float,
    /** Top surface along the centreline as (z, y), rear to front, stock wing excluded. */
    val topProfile: List<Pair<Float, Float>>,
    /** z ranges of glass that stripes skip. */
    val glassZ: List<ClosedFloatingPointRange<Float>>,
)

/** Parts built from primitives and code-drawn decals rather than loaded from a .glb. */
enum class GeneratedPart {
    None,
    LipSpoiler,
    Ducktail,
    GtWing,
    StreetKit,
    TrackKit,
    RacingStripes,
    RaceNumber,
    SideLivery,
    WheelFiveSpoke,
    WheelMesh,
    WheelDeepDish,
    SwanNeck,
    TwinDeck,
    WidebodyKit,
    RallyKit,
    TimeAttackKit,
    Flames,
    CheckerFade,
    HeadXenon,
    HeadIce,
    HeadGold,
    HeadViolet,
    TailLed,
    TailSmoked,
    TailBar,
    TailEmber,
    GlowCyan,
    GlowMagenta,
    GlowLime,
    GlowViolet,
}

/**
 * One light lens on the car, in native units: [center] sits on the lens surface (ray-cast from
 * the .glb), facing +z for headlights and -z for tail lights.
 */
data class LightPanel(val center: Position, val width: Float, val height: Float, val round: Boolean = false)

data class CarLights(val head: List<LightPanel>, val tail: List<LightPanel>)

/** Shared materials for generated parts; [bodyColor] tracks the current paint. */
class PartMaterials(private val materialLoader: MaterialLoader) {
    val carbon: MaterialInstance =
        materialLoader.createColorInstance(Color(0xFF141416), metallic = 0.2f, roughness = 0.35f)
    val bodyColor: MaterialInstance =
        materialLoader.createColorInstance(Color.Red, metallic = 0f, roughness = 0.3f)
    val white: MaterialInstance =
        materialLoader.createColorInstance(Color(0xFFF2F2F2), metallic = 0f, roughness = 0.4f)
    val black: MaterialInstance =
        materialLoader.createColorInstance(Color(0xFF101010), metallic = 0f, roughness = 0.4f)
    val rubber: MaterialInstance =
        materialLoader.createColorInstance(Color(0xFF1B1B1D), metallic = 0f, roughness = 0.9f)
    val wheelWell: MaterialInstance =
        materialLoader.createColorInstance(Color(0xFF0B0B0C), metallic = 0f, roughness = 0.8f)
    val silver: MaterialInstance =
        materialLoader.createColorInstance(Color(0xFFD9DCE0), metallic = 1f, roughness = 0.22f)
    val gold: MaterialInstance =
        materialLoader.createColorInstance(Color(0xFFD4A640), metallic = 1f, roughness = 0.3f)
    val gloss: MaterialInstance =
        materialLoader.createColorInstance(Color(0xFF1C1C20), metallic = 0.6f, roughness = 0.25f)

    private val unlitCache = mutableMapOf<Color, MaterialInstance>()
    private val decalCache = mutableMapOf<String, MaterialInstance>()

    /**
     * Transparent textured material for a code-drawn decal, created once per [key] and kept for
     * the life of this screen's engine (which frees it). Deliberately not SceneView's ImageNode:
     * removing one destroys its texture before its material, and Filament aborts the next frame
     * ("Invalid texture still bound to MaterialInstance").
     */
    fun decal(key: String, draw: () -> Bitmap): MaterialInstance = decalCache.getOrPut(key) {
        val texture = ImageTexture.Builder().bitmap(draw()).build(materialLoader.engine)
        materialLoader.createTextureInstance(texture, isOpaque = false, metallic = 0f, roughness = 0.45f)
    }

    /** Flat, full-brightness colour that ignores scene lighting — reads as a glowing lamp. */
    fun glow(color: Color): MaterialInstance =
        unlitCache.getOrPut(color) { materialLoader.createUnlitColorInstance(color) }

    fun matchPaint(paint: CarPaint) = bodyColor.setColor(colorOf(paint.color))

    /** Stripe colour that stands out against [paint]. */
    fun contrastFor(paint: CarPaint) = if (paint.color.luminance() > 0.5f) black else white
}

@Composable
fun rememberPartMaterials(materialLoader: MaterialLoader, paint: CarPaint): PartMaterials {
    val materials = remember(materialLoader) { PartMaterials(materialLoader) }
    LaunchedEffect(materials, paint) { materials.matchPaint(paint) }
    return materials
}

/**
 * Renders [part] for [car]. Must be called inside the car's native-unit node (see
 * [CustomizableCar]); sizes below are designed in metres and converted with [u].
 */
@Composable
fun SceneScope.GeneratedPartNodes(
    part: GeneratedPart,
    car: CarModel,
    slot: PartSlot,
    materials: PartMaterials,
    paint: CarPaint,
) {
    val body = car.body
    val u = 1f / car.nativeToMeters
    when (part) {
        GeneratedPart.None -> Unit
        GeneratedPart.LipSpoiler -> {
            CubeNode(
                size = Size(body.sillHalfWidth * 1.5f, 0.045f * u, 0.08f * u),
                materialInstance = materials.carbon,
                position = Position(body.centerX, body.deckY + 0.025f * u, body.deckRearZ + 0.04f * u),
                rotation = Rotation(x = 20f),
            )
        }
        GeneratedPart.Ducktail -> {
            CubeNode(
                size = Size(body.sillHalfWidth * 1.6f, 0.05f * u, 0.26f * u),
                materialInstance = materials.bodyColor,
                position = Position(body.centerX, body.deckY + 0.04f * u, body.deckRearZ + 0.1f * u),
                rotation = Rotation(x = 14f),
            )
        }
        GeneratedPart.GtWing -> GtWing(body, u, materials)
        GeneratedPart.SwanNeck -> SwanNeckWing(body, u, materials)
        GeneratedPart.TwinDeck -> TwinDeckWing(body, u, materials)
        GeneratedPart.StreetKit -> BodyKit(body, u, materials, level = 1)
        GeneratedPart.TrackKit -> BodyKit(body, u, materials, level = 2)
        GeneratedPart.TimeAttackKit -> BodyKit(body, u, materials, level = 3)
        GeneratedPart.WidebodyKit -> {
            BodyKit(body, u, materials, level = 1)
            FenderFlares(car, u, materials)
        }
        GeneratedPart.RallyKit -> RallyKit(car, u, materials)
        GeneratedPart.RacingStripes -> RacingStripes(body, u, materials.contrastFor(paint))
        GeneratedPart.RaceNumber -> DoorDecals(body, u, widthM = 0.5f, heightM = 0.5f, materials.decal("race27") { raceNumberBitmap("27") })
        GeneratedPart.SideLivery -> DoorDecals(body, u, widthM = 1.5f, heightM = 0.375f, materials.decal("livery", ::liveryBitmap))
        GeneratedPart.Flames -> DoorDecals(body, u, widthM = 1.6f, heightM = 0.45f, materials.decal("flames", ::flamesBitmap))
        GeneratedPart.CheckerFade -> DoorDecals(body, u, widthM = 1.6f, heightM = 0.4f, materials.decal("checker", ::checkerBitmap))
        GeneratedPart.HeadXenon -> Headlights(car, u, materials, Color(0xFFF4F8FF))
        GeneratedPart.HeadIce -> Headlights(car, u, materials, Color(0xFF7FD8FF))
        GeneratedPart.HeadGold -> Headlights(car, u, materials, Color(0xFFFFC94A))
        GeneratedPart.HeadViolet -> Headlights(car, u, materials, Color(0xFFB57BFF))
        GeneratedPart.TailLed -> TailLights(car, u, materials.glow(Color(0xFFFF1A1A)), glowColor = Color(0xFFFF1A1A), bar = false)
        GeneratedPart.TailSmoked -> TailLights(car, u, materials.gloss, glowColor = null, bar = false)
        GeneratedPart.TailBar -> TailLights(car, u, materials.glow(Color(0xFFFF1A1A)), glowColor = Color(0xFFFF1A1A), bar = true)
        GeneratedPart.TailEmber -> TailLights(car, u, materials.glow(Color(0xFFFF6A00)), glowColor = Color(0xFFFF6A00), bar = false)
        GeneratedPart.GlowCyan -> Underglow(car, u, materials, Color(0xFF00E5FF))
        GeneratedPart.GlowMagenta -> Underglow(car, u, materials, Color(0xFFFF2E88))
        GeneratedPart.GlowLime -> Underglow(car, u, materials, Color(0xFF76FF03))
        GeneratedPart.GlowViolet -> Underglow(car, u, materials, Color(0xFF9D4DFF))
        GeneratedPart.WheelFiveSpoke,
        GeneratedPart.WheelMesh,
        GeneratedPart.WheelDeepDish -> slot.positions.forEach { position ->
            // Each wheel's rim face goes on the side facing away from the car.
            val outward = if (position.x >= body.centerX) 1f else -1f
            Wheel(part, position, diameter = slot.targetSizeNative, outward = outward, materials = materials)
        }
    }
}

@Composable
private fun SceneScope.GtWing(body: BodyAnchors, u: Float, materials: PartMaterials) {
    val span = minOf(body.sillHalfWidth * 1.9f, 1.9f * u)
    val wingY = body.deckY + 0.43f * u
    // Main plane, nose-down angle of attack.
    CubeNode(
        size = Size(span, 0.035f * u, 0.32f * u),
        materialInstance = materials.carbon,
        position = Position(body.centerX, wingY, body.wingZ),
        rotation = Rotation(x = -8f),
    )
    // Gurney-style second element.
    CubeNode(
        size = Size(span, 0.02f * u, 0.12f * u),
        materialInstance = materials.carbon,
        position = Position(body.centerX, wingY + 0.06f * u, body.wingZ - 0.17f * u),
        rotation = Rotation(x = -30f),
    )
    for (side in listOf(-1f, 1f)) {
        // Endplates.
        CubeNode(
            size = Size(0.012f * u, 0.2f * u, 0.42f * u),
            materialInstance = materials.carbon,
            position = Position(body.centerX + side * span / 2f, wingY + 0.02f * u, body.wingZ - 0.03f * u),
        )
        // Swan-neck uprights down to the deck.
        CubeNode(
            size = Size(0.02f * u, 0.43f * u, 0.12f * u),
            materialInstance = materials.carbon,
            position = Position(body.centerX + side * span * 0.3f, body.deckY + 0.215f * u, body.wingZ + 0.04f * u),
        )
    }
}

/**
 * Aero kit in three levels: 1 = street (splitter + body-colour skirts), 2 = track (deeper
 * splitter, canards, diffuser), 3 = time attack (everything bigger, carbon skirts, more strakes).
 */
@Composable
private fun SceneScope.BodyKit(body: BodyAnchors, u: Float, materials: PartMaterials, level: Int) {
    val splitterDepth = when (level) { 1 -> 0.16f; 2 -> 0.26f; else -> 0.42f } * u
    val protrude = when (level) { 1 -> 0.06f; 2 -> 0.12f; else -> 0.22f } * u
    val splitterWidth = body.sillHalfWidth * if (level == 3) 1.95f else 1.64f
    // Front splitter under the nose.
    CubeNode(
        size = Size(splitterWidth, 0.025f * u, splitterDepth),
        materialInstance = materials.carbon,
        position = Position(body.centerX, body.noseBottomY + 0.012f * u, body.frontZ + protrude - splitterDepth / 2f),
    )
    if (level == 3) {
        // Splitter support rods.
        for (side in listOf(-1f, 1f)) {
            CubeNode(
                size = Size(0.015f * u, 0.22f * u, 0.015f * u),
                materialInstance = materials.silver,
                position = Position(body.centerX + side * splitterWidth * 0.3f, body.noseBottomY + 0.12f * u, body.frontZ + protrude * 0.6f),
                rotation = Rotation(x = -25f),
            )
        }
    }
    // Side skirts along the sills: body colour for street/track, taller carbon for time attack.
    val skirtLength = body.skirtZ.endInclusive - body.skirtZ.start
    val skirtZ = (body.skirtZ.start + body.skirtZ.endInclusive) / 2f
    val skirtHeight = (if (level == 3) 0.14f else 0.09f) * u
    for (side in listOf(-1f, 1f)) {
        CubeNode(
            size = Size(0.05f * u, skirtHeight, skirtLength),
            materialInstance = if (level == 3) materials.carbon else materials.bodyColor,
            position = Position(body.centerX + side * (body.sillHalfWidth + 0.01f * u), body.sillY + 0.03f * u, skirtZ),
        )
    }
    if (level == 1) return

    // Canards on the nose corners.
    val canardRows = if (level == 3) listOf(0.12f, 0.22f, 0.32f) else listOf(0.14f, 0.26f)
    for (side in listOf(-1f, 1f)) {
        for ((i, height) in canardRows.withIndex()) {
            CubeNode(
                size = Size((if (level == 3) 0.3f else 0.22f) * u, 0.012f * u, 0.14f * u),
                materialInstance = materials.carbon,
                position = Position(
                    body.centerX + side * body.sillHalfWidth * 0.86f,
                    body.noseBottomY + height * u,
                    body.frontZ - (0.32f + i * 0.05f) * u,
                ),
                rotation = Rotation(z = side * -12f, y = side * 18f),
            )
        }
    }
    // Rear diffuser: an upswept floor with vertical strakes.
    val diffuserWidth = body.sillHalfWidth * if (level == 3) 1.6f else 1.3f
    val diffuserDepth = (if (level == 3) 0.5f else 0.36f) * u
    val strakes = if (level == 3) 4 else 2
    CubeNode(
        size = Size(diffuserWidth, 0.02f * u, diffuserDepth),
        materialInstance = materials.carbon,
        position = Position(body.centerX, body.tailBottomY - 0.01f * u, body.rearZ + 0.14f * u),
        rotation = Rotation(x = 10f),
    )
    for (i in -strakes..strakes) {
        CubeNode(
            size = Size(0.015f * u, 0.13f * u, diffuserDepth * 0.9f),
            materialInstance = materials.carbon,
            position = Position(body.centerX + i * diffuserWidth / (2 * strakes + 1), body.tailBottomY - 0.06f * u, body.rearZ + 0.14f * u),
            rotation = Rotation(x = 10f),
        )
    }
}

/** The car's wheel slot, which wheel-relative parts (flares, mud flaps, underglow) fit to. */
private val CarModel.wheelSlot get() = slots.first { it.id == "wheels" }

/** Body-colour arches bolted over each wheel, three segments per arch. */
@Composable
private fun SceneScope.FenderFlares(car: CarModel, u: Float, materials: PartMaterials) {
    val body = car.body
    val wheels = car.wheelSlot
    val r = wheels.targetSizeNative / 2f
    for (p in wheels.positions) {
        val side = if (p.x >= body.centerX) 1f else -1f
        val x = body.centerX + side * (body.sillHalfWidth + 0.03f * u)
        CubeNode(
            size = Size(0.1f * u, 0.06f * u, r * 1.1f),
            materialInstance = materials.bodyColor,
            position = Position(x, p.y + r * 1.08f, p.z),
        )
        for (end in listOf(-1f, 1f)) {
            CubeNode(
                size = Size(0.1f * u, 0.06f * u, r * 0.8f),
                materialInstance = materials.bodyColor,
                position = Position(x, p.y + r * 0.7f, p.z + end * r * 0.88f),
                // Tip each end down and away from the arch top.
                rotation = Rotation(x = end * 50f),
            )
        }
    }
}

/** Rally pack: mud flaps behind every wheel, a silver skid plate, and a roof light pod. */
@Composable
private fun SceneScope.RallyKit(car: CarModel, u: Float, materials: PartMaterials) {
    val body = car.body
    val wheels = car.wheelSlot
    val r = wheels.targetSizeNative / 2f
    for (p in wheels.positions) {
        CubeNode(
            size = Size(0.28f * u, 0.3f * u, 0.015f * u),
            materialInstance = materials.black,
            position = Position(p.x, p.y - r + 0.03f * u + 0.15f * u, p.z - r - 0.06f * u),
        )
    }
    CubeNode(
        size = Size(body.sillHalfWidth * 1.2f, 0.02f * u, 0.45f * u),
        materialInstance = materials.silver,
        position = Position(body.centerX, body.noseBottomY - 0.01f * u, body.frontZ - 0.2f * u),
        rotation = Rotation(x = -12f),
    )
    // Light pod on the front edge of the roof.
    val roofY = body.topProfile.maxOf { it.second }
    val roofFrontZ = body.topProfile.filter { it.second >= roofY - 0.02f * u }.maxOf { it.first }
    val podWidth = body.sillHalfWidth * 1.1f
    CubeNode(
        size = Size(podWidth, 0.06f * u, 0.08f * u),
        materialInstance = materials.carbon,
        position = Position(body.centerX, roofY + 0.09f * u, roofFrontZ - 0.1f * u),
    )
    for (i in 0 until 4) {
        CylinderNode(
            radius = 0.06f * u,
            height = 0.03f * u,
            materialInstance = materials.glow(Color(0xFFFFF4D6)),
            position = Position(body.centerX + (i - 1.5f) * podWidth / 4f, roofY + 0.09f * u, roofFrontZ - 0.05f * u),
            rotation = Rotation(x = 90f),
        )
    }
}

@Composable
private fun SceneScope.SwanNeckWing(body: BodyAnchors, u: Float, materials: PartMaterials) {
    val span = minOf(body.sillHalfWidth * 1.9f, 1.9f * u)
    val wingY = body.deckY + 0.5f * u
    CubeNode(
        size = Size(span, 0.035f * u, 0.3f * u),
        materialInstance = materials.carbon,
        position = Position(body.centerX, wingY, body.wingZ),
        rotation = Rotation(x = -6f),
    )
    for (side in listOf(-1f, 1f)) {
        CubeNode(
            size = Size(0.012f * u, 0.18f * u, 0.36f * u),
            materialInstance = materials.carbon,
            position = Position(body.centerX + side * span / 2f, wingY, body.wingZ),
        )
        // Uprights rise behind the wing and hook over its top, so the underside stays clean.
        val x = body.centerX + side * span * 0.28f
        CubeNode(
            size = Size(0.02f * u, 0.56f * u, 0.05f * u),
            materialInstance = materials.silver,
            position = Position(x, body.deckY + 0.28f * u, body.wingZ - 0.17f * u),
        )
        CubeNode(
            size = Size(0.02f * u, 0.04f * u, 0.18f * u),
            materialInstance = materials.silver,
            position = Position(x, wingY + 0.05f * u, body.wingZ - 0.09f * u),
        )
    }
}

@Composable
private fun SceneScope.TwinDeckWing(body: BodyAnchors, u: Float, materials: PartMaterials) {
    val span = minOf(body.sillHalfWidth * 1.8f, 1.8f * u)
    for ((i, height) in listOf(0.3f, 0.5f).withIndex()) {
        CubeNode(
            size = Size(span, 0.03f * u, 0.26f * u),
            materialInstance = if (i == 0) materials.bodyColor else materials.carbon,
            position = Position(body.centerX, body.deckY + height * u, body.wingZ - i * 0.05f * u),
            rotation = Rotation(x = -10f),
        )
    }
    for (side in listOf(-1f, 1f)) {
        CubeNode(
            size = Size(0.014f * u, 0.36f * u, 0.4f * u),
            materialInstance = materials.carbon,
            position = Position(body.centerX + side * span / 2f, body.deckY + 0.4f * u, body.wingZ - 0.02f * u),
        )
        CubeNode(
            size = Size(0.02f * u, 0.3f * u, 0.1f * u),
            materialInstance = materials.carbon,
            position = Position(body.centerX + side * span * 0.3f, body.deckY + 0.15f * u, body.wingZ + 0.03f * u),
        )
    }
}

/** A lens-shaped glowing panel (or disc, for round lamps) sitting just proud of [panel]. */
@Composable
private fun SceneScope.LampLens(panel: LightPanel, facing: Float, u: Float, material: MaterialInstance) {
    val depth = 0.012f * u
    val center = Position(panel.center.x, panel.center.y, panel.center.z + facing * depth / 2f)
    if (panel.round) {
        CylinderNode(
            radius = panel.width / 2f,
            height = depth,
            materialInstance = material,
            position = center,
            rotation = Rotation(x = 90f),
        )
    } else {
        CubeNode(size = Size(panel.width, panel.height, depth), materialInstance = material, position = center)
    }
}

/** Glowing headlight lenses plus a soft light thrown onto the floor ahead of the car. */
@Composable
private fun SceneScope.Headlights(car: CarModel, u: Float, materials: PartMaterials, color: Color) {
    val lens = materials.glow(color)
    car.lights.head.forEach { LampLens(it, facing = 1f, u = u, material = lens) }
    GlowLight(
        color = color,
        position = Position(car.body.centerX, car.lights.head.map { it.center.y }.average().toFloat(), car.body.frontZ + 0.7f * u),
        falloffM = 3f,
    )
}

@Composable
private fun SceneScope.TailLights(car: CarModel, u: Float, lens: MaterialInstance, glowColor: Color?, bar: Boolean) {
    val tails = car.lights.tail
    tails.forEach { LampLens(it, facing = -1f, u = u, material = lens) }
    if (bar) {
        // Full-width LED strip joining the two lamps.
        val minX = tails.minOf { it.center.x - it.width / 2f }
        val maxX = tails.maxOf { it.center.x + it.width / 2f }
        val y = tails.map { it.center.y }.average().toFloat()
        val z = tails.maxOf { it.center.z } - 0.008f * u
        CubeNode(
            size = Size(maxX - minX, 0.025f * u, 0.012f * u),
            materialInstance = lens,
            position = Position((minX + maxX) / 2f, y, z),
        )
    }
    if (glowColor != null) {
        GlowLight(
            color = glowColor,
            position = Position(car.body.centerX, tails.map { it.center.y }.average().toFloat(), car.body.rearZ - 0.5f * u),
            falloffM = 2f,
        )
    }
}

/** Neon tubes along the underside plus coloured light pooling on the floor beneath the car. */
@Composable
private fun SceneScope.Underglow(car: CarModel, u: Float, materials: PartMaterials, color: Color) {
    val body = car.body
    val tube = materials.glow(color)
    val wheels = car.wheelSlot
    val floorY = wheels.positions.minOf { it.y } - wheels.targetSizeNative / 2f
    val length = (body.frontZ - body.rearZ) * 0.7f
    val midZ = (body.frontZ + body.rearZ) / 2f
    for (side in listOf(-1f, 1f)) {
        CubeNode(
            size = Size(0.03f * u, 0.02f * u, length),
            materialInstance = tube,
            position = Position(body.centerX + side * (body.sillHalfWidth - 0.08f * u), body.sillY - 0.015f * u, midZ),
        )
    }
    for (z in listOf(body.frontZ - 0.35f * u, body.rearZ + 0.35f * u)) {
        CubeNode(
            size = Size(body.sillHalfWidth * 1.5f, 0.02f * u, 0.03f * u),
            materialInstance = tube,
            position = Position(body.centerX, minOf(body.noseBottomY, body.tailBottomY) - 0.01f * u, z),
        )
    }
    for (z in listOf(body.rearZ + 0.2f * (body.frontZ - body.rearZ), midZ, body.frontZ - 0.2f * (body.frontZ - body.rearZ))) {
        GlowLight(color = color, position = Position(body.centerX, (floorY + body.sillY) / 2f, z), falloffM = 1.8f)
    }
}

/** A coloured point light; intensity is in lumens, and [falloffM] is in real metres. */
@Composable
private fun SceneScope.GlowLight(color: Color, position: Position, falloffM: Float) {
    LightNode(
        type = LightManager.Type.POINT,
        intensity = 400_000f,
        position = position,
        color = colorOf(color),
        apply = { falloff(falloffM) },
    )
}

/**
 * Twin stripes laid nose to tail as short segments that follow [BodyAnchors.topProfile], each
 * pitched to the surface slope, skipping glass.
 */
@Composable
private fun SceneScope.RacingStripes(body: BodyAnchors, u: Float, material: MaterialInstance) {
    val lift = 0.006f * u
    val profile = body.topProfile
    for (i in 0 until profile.size - 1) {
        val (z1, y1) = profile[i]
        val (z2, y2) = profile[i + 1]
        val midZ = (z1 + z2) / 2f
        if (body.glassZ.any { midZ in it }) continue
        val dz = z2 - z1
        val dy = y2 - y1
        val pitch = atan2(-dy, dz) * 180f / PI.toFloat()
        for (side in listOf(-1f, 1f)) {
            CubeNode(
                // Slightly overlong so neighbouring segments meet without gaps at slope changes.
                size = Size(0.16f * u, 0.004f * u, hypot(dz, dy) * 1.04f),
                materialInstance = material,
                position = Position(body.centerX + side * 0.13f * u, (y1 + y2) / 2f + lift, midZ),
                rotation = Rotation(x = pitch),
            )
        }
    }
}

/**
 * A wheel built from primitives: a rubber tyre, a dark rim well on the outer face, and the
 * design's spokes and lip on top of it. [diameter] is in native units; the axle runs along x.
 */
@Composable
private fun SceneScope.Wheel(
    part: GeneratedPart,
    center: Position,
    diameter: Float,
    outward: Float,
    materials: PartMaterials,
) {
    val r = diameter / 2f
    val width = diameter * 0.3f
    val face = center.x + outward * (width / 2f)
    val axleAlongX = Rotation(z = 90f)
    val (rimRadius, spokeCount, spokeWidth, metal) = when (part) {
        GeneratedPart.WheelFiveSpoke -> WheelStyle(0.70f * r, 5, 0.16f * r, materials.silver)
        GeneratedPart.WheelMesh -> WheelStyle(0.70f * r, 10, 0.045f * r, materials.gold)
        else -> WheelStyle(0.74f * r, 6, 0.13f * r, materials.gloss)
    }

    CylinderNode(radius = r, height = width, materialInstance = materials.rubber, position = center, rotation = axleAlongX)
    CylinderNode(
        radius = rimRadius,
        height = diameter * 0.01f,
        materialInstance = materials.wheelWell,
        position = Position(face + outward * diameter * 0.005f, center.y, center.z),
        rotation = axleAlongX,
    )

    val spokeX = face + outward * diameter * 0.018f
    val hubRadius = 0.16f * r
    val spokeLength = rimRadius - hubRadius * 0.6f
    val midRadius = hubRadius * 0.6f + spokeLength / 2f
    // Mesh: a second, cross-laced set of spokes skewed the other way.
    val skews = if (part == GeneratedPart.WheelMesh) listOf(-14f, 14f) else listOf(0f)
    for (skew in skews) {
        for (i in 0 until spokeCount) {
            val angle = 360f / spokeCount * i
            val rad = angle * PI.toFloat() / 180f
            CubeNode(
                size = Size(diameter * 0.03f, spokeWidth, spokeLength),
                materialInstance = metal,
                position = Position(spokeX, center.y + midRadius * kotlin.math.sin(rad), center.z + midRadius * kotlin.math.cos(rad)),
                // Pitching by -angle about the axle lays the spoke's length along the radius.
                rotation = Rotation(x = -angle + skew),
            )
        }
    }
    // Hub and centre cap.
    CylinderNode(
        radius = hubRadius,
        height = diameter * 0.05f,
        materialInstance = metal,
        position = Position(spokeX, center.y, center.z),
        rotation = axleAlongX,
    )
    // Rim lip; the deep dish gets a much wider polished one.
    val lip = if (part == GeneratedPart.WheelDeepDish) 0.07f * r else 0.035f * r
    TorusNode(
        majorRadius = rimRadius - lip,
        minorRadius = lip,
        materialInstance = if (part == GeneratedPart.WheelDeepDish) materials.silver else metal,
        position = Position(face + outward * diameter * 0.01f, center.y, center.z),
        rotation = axleAlongX,
    )
}

private data class WheelStyle(
    val rimRadius: Float,
    val spokeCount: Int,
    val spokeWidth: Float,
    val metal: MaterialInstance,
)

/** The same decal image on both doors, rotated (not mirrored) so it reads correctly on each side. */
@Composable
private fun SceneScope.DoorDecals(body: BodyAnchors, u: Float, widthM: Float, heightM: Float, material: MaterialInstance) {
    // Shrink (keeping the aspect ratio) to fit between the wheel arches on shorter cars.
    val fit = minOf(1f, (body.skirtZ.endInclusive - body.skirtZ.start) * 1.25f / (widthM * u))
    for (side in listOf(-1f, 1f)) {
        PlaneNode(
            size = Size(widthM * u * fit, heightM * u * fit, 0f),
            materialInstance = material,
            position = Position(body.centerX + side * (body.doorSideHalfWidth + 0.012f * u), body.doorY, body.doorZ),
            rotation = Rotation(y = side * 90f),
        )
    }
}

private fun raceNumberBitmap(number: String): Bitmap {
    val size = 512
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val c = size / 2f
    paint.color = android.graphics.Color.BLACK
    canvas.drawCircle(c, c, c, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(c, c, c * 0.88f, paint)
    paint.color = android.graphics.Color.BLACK
    paint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
    paint.textSize = size * 0.52f
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(number, c, c - (paint.descent() + paint.ascent()) / 2f, paint)
    return bitmap
}

/** Three speed slashes fading back along the door, in the garage's accent colours. */
private fun liveryBitmap(): Bitmap {
    val w = 1024
    val h = 256
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val slant = h * 0.55f
    val slashes = listOf(
        Triple(0.02f, 0.30f, GarageColors.Action.toArgb()),
        Triple(0.34f, 0.18f, GarageColors.Accent.toArgb()),
        Triple(0.55f, 0.10f, android.graphics.Color.WHITE),
    )
    for ((start, width, color) in slashes) {
        paint.color = color
        val left = start * w
        val right = left + width * w
        canvas.drawPath(
            Path().apply {
                moveTo(left + slant, 0f)
                lineTo(right + slant, 0f)
                lineTo(right, h.toFloat())
                lineTo(left, h.toFloat())
                close()
            },
            paint,
        )
    }
    // Thin pinstripe trailing off the last slash.
    paint.color = android.graphics.Color.WHITE
    canvas.drawRect(0.68f * w, h * 0.78f, w.toFloat(), h * 0.84f, paint)
    return bitmap
}

/** Flame licks running from the front of the door back, yellow core to red tips. */
private fun flamesBitmap(): Bitmap {
    val w = 1024
    val h = 288
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val tongues = listOf(0.18f to 0.95f, 0.36f to 0.7f, 0.55f to 0.85f, 0.72f to 0.6f, 0.86f to 0.75f)
    val layers = listOf(
        android.graphics.Color.rgb(214, 32, 20) to 1f,
        android.graphics.Color.rgb(255, 120, 0) to 0.78f,
        android.graphics.Color.rgb(255, 214, 64) to 0.52f,
    )
    for ((color, scale) in layers) {
        paint.color = color
        val path = Path()
        path.moveTo(0f, h * (0.5f - 0.42f * scale))
        for ((y, reach) in tongues) {
            val tipX = w * reach * scale
            val tipY = h * y
            path.quadTo(tipX * 0.55f, tipY - h * 0.2f * scale, tipX, tipY - h * 0.06f)
            path.quadTo(tipX * 0.5f, tipY + h * 0.02f, w * 0.08f, tipY + h * 0.06f)
        }
        path.lineTo(0f, h * (0.5f + 0.42f * scale))
        path.close()
        canvas.drawPath(path, paint)
    }
    return bitmap
}

/** A checkerboard band that breaks up and fades out towards the rear. */
private fun checkerBitmap(): Bitmap {
    val w = 1024
    val h = 256
    val cell = 32
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    for (cx in 0 until w / cell) {
        val fade = 1f - cx.toFloat() / (w / cell)
        for (cy in 0 until h / cell) {
            // Drop more squares the further back we go, for a dissolving edge.
            if (((cx * 7 + cy * 13) % 10) / 10f > fade * 1.3f) continue
            paint.color = if ((cx + cy) % 2 == 0) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            paint.alpha = (255 * fade.coerceIn(0.25f, 1f)).toInt()
            canvas.drawRect(
                (cx * cell).toFloat(), (cy * cell).toFloat(),
                ((cx + 1) * cell).toFloat(), ((cy + 1) * cell).toFloat(), paint,
            )
        }
    }
    return bitmap
}
