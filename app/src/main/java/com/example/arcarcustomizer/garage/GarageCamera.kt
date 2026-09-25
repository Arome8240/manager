package com.example.arcarcustomizer.garage

import com.example.arcarcustomizer.customization.CarModel
import com.example.arcarcustomizer.customization.CustomizationCategory
import com.example.arcarcustomizer.customization.footprint
import io.github.sceneview.math.Position
import io.github.sceneview.node.CameraNode
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Where the garage camera sits for one picker category. The camera orbits [target] at
 * [azimuth]/[elevation] (radians) and pulls back until a sphere of [frameRadius] metres fits the
 * view — so framing holds in both portrait and landscape without knowing the lens.
 */
data class CameraShot(
    val target: Position,
    val azimuth: Float,
    val elevation: Float,
    val frameRadius: Float,
    /** Whether the camera drifts round the car on its own after the user stops touching it. */
    val autoOrbit: Boolean,
)

private fun Float.deg() = this * PI.toFloat() / 180f

/**
 * Picks the shot for [category] on [car]. Paint gets a wide, slowly orbiting hero shot; each
 * part slot gets a low three-quarter close-up of its first position, viewed from outside the
 * car. All of it is derived from the car's measured slot positions, so a newly added car or
 * slot gets sensible shots with no per-car camera tuning.
 */
fun cameraShotFor(car: CarModel, category: CustomizationCategory): CameraShot {
    val footprint = car.footprint
    return when (category) {
        CustomizationCategory.Paint -> CameraShot(
            target = Position(footprint.center.x, footprint.groundY + 0.45f, footprint.center.z),
            azimuth = 35f.deg(),
            elevation = 14f.deg(),
            frameRadius = footprint.halfLength * 1.05f,
            autoOrbit = true,
        )

        is CustomizationCategory.Part -> {
            val slot = category.slot
            val target = slot.positions.first() * car.nativeToMeters
            // Look along the line from the car's centre out to the part, so we view it from
            // outside the bodywork, then swing 25° off-axis for a three-quarter angle.
            val outward = atan2(target.x - footprint.center.x, target.z - footprint.center.z)
            CameraShot(
                target = target,
                azimuth = outward + 25f.deg(),
                elevation = 10f.deg(),
                frameRadius = max(slot.targetSizeNative * car.nativeToMeters * 1.4f, 0.45f),
                autoOrbit = false,
            )
        }
    }
}

/**
 * Drives a SceneView [CameraNode] like a racing-game garage camera: it eases toward the current
 * [CameraShot] (exponential ease-out, so moves start fast and settle softly), lets the user
 * orbit and zoom by hand, and resumes a slow idle orbit on shots that allow it.
 *
 * Plain mutable fields rather than Compose state: [update] runs every frame from SceneView's
 * `onFrame`, and nothing in the UI needs to recompose when the camera moves.
 */
class GarageCameraController {
    private var shot: CameraShot? = null

    private var azimuth = 0f
    private var elevation = 0f
    private var radius = 1f
    private var target = Position()

    private var goalAzimuth = 0f
    private var goalElevation = 0f
    private var goalRadius = 1f
    private var goalTarget = Position()
    private var zoom = 1f

    private var lastFrameNanos = 0L
    private var lastTouchNanos = 0L
    private var touching = false

    fun moveTo(newShot: CameraShot) {
        val first = shot == null
        shot = newShot
        // Take the short way round rather than unwinding any accumulated orbit.
        goalAzimuth = azimuth + wrapAngle(newShot.azimuth - azimuth)
        goalElevation = newShot.elevation
        goalRadius = newShot.frameRadius
        goalTarget = newShot.target
        zoom = 1f
        if (first) {
            // First shot: start further out and swung round, so the car is "revealed".
            azimuth = goalAzimuth - 70f.deg()
            elevation = goalElevation + 12f.deg()
            radius = goalRadius * 2.2f
            target = goalTarget
        }
    }

    fun onDragStart() {
        touching = true
    }

    fun onDragEnd() {
        touching = false
        lastTouchNanos = lastFrameNanos
    }

    /** [dx]/[dy] in pixels; [zoomFactor] is the pinch scale change for this event (>1 = pinch out). */
    fun onDrag(dx: Float, dy: Float, zoomFactor: Float) {
        goalAzimuth -= dx * DRAG_RADIANS_PER_PX
        goalElevation = (goalElevation + dy * DRAG_RADIANS_PER_PX).coerceIn(MIN_ELEVATION, MAX_ELEVATION)
        zoom = (zoom / zoomFactor).coerceIn(0.5f, 1.8f)
    }

    fun update(camera: CameraNode, frameTimeNanos: Long) {
        val currentShot = shot ?: return
        val dt = if (lastFrameNanos == 0L) 0f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceAtMost(0.1f)
        lastFrameNanos = frameTimeNanos
        if (lastTouchNanos == 0L) lastTouchNanos = frameTimeNanos

        val idleSeconds = (frameTimeNanos - lastTouchNanos) / 1e9f
        if (currentShot.autoOrbit && !touching && idleSeconds > IDLE_ORBIT_DELAY_SECONDS) {
            goalAzimuth += IDLE_ORBIT_RADIANS_PER_SECOND * dt
        }

        val t = 1f - exp(-EASE_RATE * dt)
        azimuth += (goalAzimuth - azimuth) * t
        elevation += (goalElevation - elevation) * t
        radius += (goalRadius * zoom - radius) * t
        target += (goalTarget - target) * t

        // Distance at which a sphere of `radius` fills the narrower half-FOV. The projection's
        // [0][0] and [1][1] terms are 1/tan(halfFov) for each axis, so this adapts to
        // orientation and to whatever lens SceneView configured.
        val projection = camera.projectionTransform
        val inverseTanHalfFov = max(projection.x.x, projection.y.y).takeIf { it > 0f } ?: 2.4f
        val distance = radius * inverseTanHalfFov

        val eye = Position(
            x = target.x + distance * cos(elevation) * sin(azimuth),
            y = target.y + distance * sin(elevation),
            z = target.z + distance * cos(elevation) * cos(azimuth),
        )
        camera.lookAt(eye, target, Position(y = 1f))
    }

    private fun wrapAngle(angle: Float): Float {
        val twoPi = (2 * PI).toFloat()
        var a = angle % twoPi
        if (a > PI) a -= twoPi
        if (a < -PI) a += twoPi
        return a
    }

    private companion object {
        const val EASE_RATE = 3.2f
        const val DRAG_RADIANS_PER_PX = 0.006f
        const val IDLE_ORBIT_DELAY_SECONDS = 3.5f
        const val IDLE_ORBIT_RADIANS_PER_SECOND = 0.18f
        val MIN_ELEVATION = 2f.deg()
        val MAX_ELEVATION = 60f.deg()
    }
}
