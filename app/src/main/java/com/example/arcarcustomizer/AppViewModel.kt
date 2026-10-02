package com.example.arcarcustomizer

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.arcarcustomizer.customization.CarCatalog
import com.example.arcarcustomizer.customization.CustomizationState
import com.example.arcarcustomizer.customization.DefaultCarPaints
import com.example.arcarcustomizer.data.AppDatabase
import com.example.arcarcustomizer.data.AppStateStore
import com.example.arcarcustomizer.data.SavedAppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the app-wide state and keeps it in SQLite: restores the last session once on start, then
 * writes every change back as it happens, so the build survives the process being killed.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppStateStore(AppDatabase.get(application))

    /** One build shared by every screen, so garage changes carry into AR / Fallback and back. */
    val customization = CustomizationState(CarCatalog, DefaultCarPaints)

    var showingLiveView by mutableStateOf(false)

    /** False until the saved session has been read; screens wait so they don't flash defaults. */
    var restored by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { store.load() }
                    .onFailure { Log.e(TAG, "Couldn't load saved state", it) }
                    .getOrDefault(SavedAppState())
            }
            customization.restore(saved)
            showingLiveView = saved.showingLiveView
            restored = true

            snapshotFlow { customization.toSaved(showingLiveView) }
                .conflate()
                .collect { state ->
                    withContext(Dispatchers.IO) {
                        runCatching { store.save(state) }
                            .onFailure { Log.e(TAG, "Couldn't save state", it) }
                    }
                }
        }
    }

    private companion object {
        const val TAG = "AppViewModel"
    }
}
