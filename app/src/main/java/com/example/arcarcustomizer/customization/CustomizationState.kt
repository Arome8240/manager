package com.example.arcarcustomizer.customization

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.sceneview.math.Position
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One tab in the part picker: the car's paint, or one of its [PartSlot]s. The garage also uses
 * the selected category to decide where its camera flies to.
 */
sealed interface CustomizationCategory {
    val id: String
    val label: String

    data object Paint : CustomizationCategory {
        override val id = "paint"
        override val label = "Paint"
    }

    data class Part(val slot: PartSlot) : CustomizationCategory {
        override val id get() = slot.id
        override val label get() = slot.label
    }
}

val CarModel.categories: List<CustomizationCategory>
    get() = listOf(CustomizationCategory.Paint) + slots.map { CustomizationCategory.Part(it) }

/**
 * The build the user is working on — car, paint, parts, and which picker tab is open. Hoisted
 * above every screen so a build made in the garage carries straight into AR / Fallback mode and
 * back.
 */
@Stable
class CustomizationState(val cars: List<CarModel>, val paints: List<CarPaint>) {
    var car by mutableStateOf(cars.first())
        private set
    var paint by mutableStateOf(paints.first())
    var selectedOptions by mutableStateOf(mapOf<String, PartOption>())
        private set
    var categoryId by mutableStateOf(CustomizationCategory.Paint.id)

    val category: CustomizationCategory
        get() = car.categories.firstOrNull { it.id == categoryId } ?: CustomizationCategory.Paint

    val stats: CarStats
        get() = car.slots.fold(car.baseStats) { total, slot -> total + optionFor(slot).statDelta }

    fun selectCar(newCar: CarModel) {
        car = newCar
        // Keep the open tab if the new car has the same slot, otherwise fall back to paint.
        if (newCar.categories.none { it.id == categoryId }) {
            categoryId = CustomizationCategory.Paint.id
        }
    }

    /** Cycles through [cars]; [step] is +1 / -1. */
    fun cycleCar(step: Int) {
        val index = cars.indexOfFirst { it.id == car.id }
        selectCar(cars[(index + step).mod(cars.size)])
    }

    fun optionFor(slot: PartSlot): PartOption = selectedOptions[slot.id] ?: slot.options.first()

    fun selectOption(slotId: String, option: PartOption) {
        selectedOptions = selectedOptions + (slotId to option)
    }
}

/**
 * Rough real-world framing of a car, derived from its measured [PartSlot] positions (the only
 * geometry this app knows about each .glb), in metres, relative to the car's own origin.
 */
data class CarFootprint(
    val center: Position,
    val groundY: Float,
    /** Half the bodywork's extent along x and z; the larger of the two is [halfLength]. */
    val halfExtentX: Float,
    val halfExtentZ: Float,
) {
    val halfLength get() = max(halfExtentX, halfExtentZ)

    /** Horizontal distance from [center] to the edge of the bodywork in direction [angle] (radians, atan2(x, z)). */
    fun edgeDistance(angle: Float): Float {
        val sx = sin(angle) / halfExtentX
        val cz = cos(angle) / halfExtentZ
        return 1f / sqrt(sx * sx + cz * cz)
    }
}

val CarModel.footprint: CarFootprint
    get() {
        val points = slots.flatMap { it.positions }.map { it * nativeToMeters }
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minZ = points.minOf { it.z }
        val maxZ = points.maxOf { it.z }
        // Lowest point any part reaches, i.e. the bottom of the tyres.
        val groundY = slots.minOf { slot ->
            slot.positions.minOf { it.y } - slot.targetSizeNative / 2f
        } * nativeToMeters
        // Slot positions only span the wheelbase and track. Bodywork overhangs the wheelbase by
        // roughly 70% on real cars (checked against both catalog cars' full chassis lengths), but
        // barely overhangs the track.
        val spanX = maxX - minX
        val spanZ = maxZ - minZ
        val lengthwiseIsZ = spanZ >= spanX
        return CarFootprint(
            center = Position((minX + maxX) / 2f, groundY, (minZ + maxZ) / 2f),
            groundY = groundY,
            halfExtentX = spanX / 2f * if (lengthwiseIsZ) 1.05f else 1.7f,
            halfExtentZ = spanZ / 2f * if (lengthwiseIsZ) 1.7f else 1.05f,
        )
    }
