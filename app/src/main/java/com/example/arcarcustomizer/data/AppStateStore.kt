package com.example.arcarcustomizer.data

/**
 * Everything the app restores on its next launch: the build in progress and where the user was.
 * Holds ids/labels only, so it can be compared cheaply and written without touching Compose state.
 */
data class SavedAppState(
    val carId: String? = null,
    val paintLabel: String? = null,
    val categoryId: String? = null,
    /** Slot id → selected option label. */
    val partSelections: Map<String, String> = emptyMap(),
    val showingLiveView: Boolean = false,
)

/** Reads and writes [SavedAppState] to [AppDatabase]. Call off the main thread when possible. */
class AppStateStore(private val db: AppDatabase) {

    fun load(): SavedAppState {
        val state = db.readState()
        return SavedAppState(
            carId = state[KEY_CAR],
            paintLabel = state[KEY_PAINT],
            categoryId = state[KEY_CATEGORY],
            partSelections = db.readPartSelections(),
            showingLiveView = state[KEY_LIVE_VIEW].toBoolean(),
        )
    }

    fun save(saved: SavedAppState) {
        db.writeAll(
            state = buildMap {
                saved.carId?.let { put(KEY_CAR, it) }
                saved.paintLabel?.let { put(KEY_PAINT, it) }
                saved.categoryId?.let { put(KEY_CATEGORY, it) }
                put(KEY_LIVE_VIEW, saved.showingLiveView.toString())
            },
            partSelections = saved.partSelections,
        )
    }

    private companion object {
        const val KEY_CAR = "car_id"
        const val KEY_PAINT = "paint_label"
        const val KEY_CATEGORY = "category_id"
        const val KEY_LIVE_VIEW = "showing_live_view"
    }
}
