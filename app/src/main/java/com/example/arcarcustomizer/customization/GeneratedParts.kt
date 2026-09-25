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
import com.google.android.filament.MaterialInstance
import io.github.sceneview.SceneScope
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.material.setColor
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.math.colorOf
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
}

/** Shared materials for generated parts; [bodyColor] tracks the current paint. */
class PartMaterials(materialLoader: MaterialLoader) {
    val carbon: MaterialInstance =
        materialLoader.createColorInstance(Color(0xFF141416), metallic = 0.2f, roughness = 0.35f)
    val bodyColor: MaterialInstance =
        materialLoader.createColorInstance(Color.Red, metallic = 0f, roughness = 0.3f)
    val white: MaterialInstance =
        materialLoader.createColorInstance(Color(0xFFF2F2F2), metallic = 0f, roughness = 0.4f)
    val black: MaterialInstance =
        materialLoader.createColorInstance(Color(0xFF101010), metallic = 0f, roughness = 0.4f)

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
        GeneratedPart.StreetKit -> BodyKit(body, u, materials, track = false)
        GeneratedPart.TrackKit -> BodyKit(body, u, materials, track = true)
        GeneratedPart.RacingStripes -> RacingStripes(body, u, materials.contrastFor(paint))
        GeneratedPart.RaceNumber -> DoorDecals(body, u, widthM = 0.5f, heightM = 0.5f, bitmap = remember { raceNumberBitmap("27") })
        GeneratedPart.SideLivery -> DoorDecals(body, u, widthM = 1.5f, heightM = 0.375f, bitmap = remember { liveryBitmap() })
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

@Composable
private fun SceneScope.BodyKit(body: BodyAnchors, u: Float, materials: PartMaterials, track: Boolean) {
    val splitterDepth = (if (track) 0.26f else 0.16f) * u
    val protrude = (if (track) 0.12f else 0.06f) * u
    // Front splitter under the nose.
    CubeNode(
        size = Size(body.sillHalfWidth * 1.64f, 0.025f * u, splitterDepth),
        materialInstance = materials.carbon,
        position = Position(body.centerX, body.noseBottomY + 0.012f * u, body.frontZ + protrude - splitterDepth / 2f),
    )
    // Body-coloured side skirts along the sills.
    val skirtLength = body.skirtZ.endInclusive - body.skirtZ.start
    val skirtZ = (body.skirtZ.start + body.skirtZ.endInclusive) / 2f
    for (side in listOf(-1f, 1f)) {
        CubeNode(
            size = Size(0.05f * u, 0.09f * u, skirtLength),
            materialInstance = materials.bodyColor,
            position = Position(body.centerX + side * (body.sillHalfWidth + 0.01f * u), body.sillY + 0.03f * u, skirtZ),
        )
    }
    if (!track) return

    // Canards on the nose corners, two per side.
    for (side in listOf(-1f, 1f)) {
        for ((i, height) in listOf(0.14f, 0.26f).withIndex()) {
            CubeNode(
                size = Size(0.22f * u, 0.012f * u, 0.14f * u),
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
    val diffuserWidth = body.sillHalfWidth * 1.3f
    CubeNode(
        size = Size(diffuserWidth, 0.02f * u, 0.36f * u),
        materialInstance = materials.carbon,
        position = Position(body.centerX, body.tailBottomY - 0.01f * u, body.rearZ + 0.14f * u),
        rotation = Rotation(x = 10f),
    )
    for (i in -2..2) {
        CubeNode(
            size = Size(0.015f * u, 0.13f * u, 0.32f * u),
            materialInstance = materials.carbon,
            position = Position(body.centerX + i * diffuserWidth / 5f, body.tailBottomY - 0.06f * u, body.rearZ + 0.14f * u),
            rotation = Rotation(x = 10f),
        )
    }
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

/** The same decal image on both doors, rotated (not mirrored) so it reads correctly on each side. */
@Composable
private fun SceneScope.DoorDecals(body: BodyAnchors, u: Float, widthM: Float, heightM: Float, bitmap: Bitmap) {
    for (side in listOf(-1f, 1f)) {
        ImageNode(
            bitmap = bitmap,
            size = Size(widthM * u, heightM * u, 0f),
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
