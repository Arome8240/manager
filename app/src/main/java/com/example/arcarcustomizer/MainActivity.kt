package com.example.arcarcustomizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.arcarcustomizer.ar.AppMode
import com.example.arcarcustomizer.ar.ArAvailabilityController
import com.example.arcarcustomizer.ar.ArPlacementScreen
import com.example.arcarcustomizer.fallback.FallbackScreen
import com.example.arcarcustomizer.garage.GarageScreen
import com.example.arcarcustomizer.ui.theme.ArCarCustomizerTheme

class MainActivity : ComponentActivity() {
    private val arAvailability = ArAvailabilityController()
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArCarCustomizerTheme {
                val mode by arAvailability.mode
                // Restored from SQLite by AppViewModel; one build shared by every screen.
                val customization = appViewModel.customization

                if (!appViewModel.restored) {
                    LoadingScreen()
                } else if (!appViewModel.showingLiveView) {
                    GarageScreen(
                        state = customization,
                        liveViewLabel = when (mode) {
                            AppMode.Checking -> null
                            AppMode.Ar -> "VIEW IN AR"
                            AppMode.Fallback -> "VIEW IN CAMERA"
                        },
                        onOpenLiveView = { appViewModel.showingLiveView = true },
                    )
                } else {
                    val backToGarage = { appViewModel.showingLiveView = false }
                    BackHandler(onBack = backToGarage)
                    when (mode) {
                        AppMode.Checking -> LoadingScreen()
                        AppMode.Ar -> ArPlacementScreen(customization, onBack = backToGarage)
                        AppMode.Fallback -> FallbackScreen(customization, onBack = backToGarage)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // ArCoreApk's own contract: (re-)drive checkAvailability/requestInstall from onResume,
        // since a Play Store install/update flow pauses and then resumes this activity.
        arAvailability.refresh(this)
    }
}

/** Shown while the saved session loads and while [ArAvailabilityController] resolves [AppMode.Checking]. */
@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}
