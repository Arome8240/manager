package com.example.arcarcustomizer.ar

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.google.ar.core.ArCoreApk
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import io.github.sceneview.ar.ARCoreAvailability
import io.github.sceneview.ar.toARCoreAvailability

/** Top-level destination this app routes to, decided once per launch (tasks.md Phase 2). */
sealed interface AppMode {
    data object Checking : AppMode
    data object Ar : AppMode
    data object Fallback : AppMode
}

/**
 * Drives ARCore's checkAvailability/requestInstall dance from the activity lifecycle (per
 * ArCoreApk's own docs, this must be re-driven from onResume so a Play Store install/update
 * flow that pauses this activity is picked back up when it resumes) and resolves to [AppMode].
 *
 * Reuses SceneView's own [ARCoreAvailability] classification
 * ([io.github.sceneview.ar.ArCoreApk.Availability.toARCoreAvailability]) instead of
 * re-deriving it, since [io.github.sceneview.ar.ARSceneView] is built against the same enum.
 */
class ArAvailabilityController {
    var mode: MutableState<AppMode> = mutableStateOf(AppMode.Checking)
        private set

    // ArCoreApk.requestInstall's own contract: pass true the first time you ask for a given
    // device, false on every subsequent call in this process so a user who backs out of the
    // Play Store flow isn't re-prompted in a loop.
    private var userRequestedArCoreInstall = true

    fun refresh(activity: Activity) {
        if (mode.value !== AppMode.Checking) return

        val availability = ArCoreApk.getInstance().checkAvailability(activity)
        if (availability.isTransient) {
            // UNKNOWN_CHECKING (or a timed-out/errored check): ARCore is still asking the Play
            // Store. Poll again shortly rather than picking a mode from an unresolved answer.
            Handler(Looper.getMainLooper()).postDelayed({ refresh(activity) }, 200L)
            return
        }

        when (availability.toARCoreAvailability()) {
            null -> mode.value = AppMode.Ar // SUPPORTED_INSTALLED
            ARCoreAvailability.NotInstalled, ARCoreAvailability.NeedsUpdate ->
                requestInstall(activity)
            ARCoreAvailability.Unsupported,
            ARCoreAvailability.CheckFailed,
            ARCoreAvailability.SessionFailed -> mode.value = AppMode.Fallback
        }
    }

    private fun requestInstall(activity: Activity) {
        try {
            when (ArCoreApk.getInstance().requestInstall(activity, userRequestedArCoreInstall)) {
                ArCoreApk.InstallStatus.INSTALLED -> mode.value = AppMode.Ar
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    // Play Store install/update UI is launching; this activity will pause and
                    // refresh() runs again from onResume once it returns.
                    userRequestedArCoreInstall = false
                }
            }
        } catch (e: UnavailableUserDeclinedInstallationException) {
            mode.value = AppMode.Fallback
        } catch (e: UnavailableDeviceNotCompatibleException) {
            mode.value = AppMode.Fallback
        }
    }
}
