# AR Car Customization App — Build Checklist

Target stack: Kotlin + Jetpack Compose, **SceneView `io.github.sceneview:arsceneview` 2.3.x** (Compose-native `ARScene`/`Scene` composables, built on Filament + ARCore), CameraX, minSdk 24. Verify the exact latest 2.3.x patch on Maven Central when you start Phase 1 — this library ships frequently.

Work top to bottom. Nothing in an earlier task depends on anything from a later one.

---

## Prerequisites

Have these ready before starting Task 1:

- [ ] Android Studio (current stable release) installed, with bundled JDK 17
- [ ] A physical Android phone on Google's ARCore-supported device list, with "Google Play Services for AR" installed from Play Store, USB debugging enabled — required for every `[device]` task
- [ ] A working Android Emulator AVD (any image) — used deliberately in Phase 2 because emulators reliably report ARCore as **unsupported**, which is the one case where the emulator is the *right* tool
- [ ] A Google Play Console developer account (one-time $25 fee) — not needed until Build & Ship, but registration can take time to verify, so start it early
- [ ] `[asset]` A car `.glb` model, license permitting your intended use, with a body material you can identify (for `baseColorFactor` paint swaps) and separate wheel nodes in its hierarchy
- [ ] `[asset]` One or more alternate wheel `.glb` models, scaled/oriented to match the car model's wheel wells
- [ ] `[asset]` An `.hdr` environment map, for lighting the car in Fallback mode (AR mode instead uses ARCore's automatic light estimation)
- [ ] adb access confirmed (USB or wireless debugging) so you can watch logcat during device testing

---

## Phase 1 — Project Setup

- [ ] Create a new Android Studio project: Empty Activity template, Compose, Kotlin, `minSdk = 24`. Done = project builds and runs a blank Compose screen on an emulator.
- [ ] Add dependencies: `io.github.sceneview:arsceneview:2.3.x`, `com.google.ar:core` (explicit, for direct `ArCoreApk` calls), CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`), Compose BOM, `lifecycle-runtime-compose`, `activity-compose`. Done = Gradle sync succeeds with no dependency conflicts.
- [ ] Re-run the blank app after adding the above. Done = it still launches with no crash (confirms the new deps don't break startup).
- [ ] Configure the manifest for AR-optional install: `<uses-permission android:name="android.permission.CAMERA"/>`, `<uses-feature android:name="android.hardware.camera.ar" android:required="false"/>`, `<meta-data android:name="com.google.ar.core" android:value="optional"/>`. Done = Android Studio's "Merged Manifest" view shows all three exactly as above, with no `required="true"` on the AR feature.
- [ ] `[asset]` Place the car `.glb` (and wheel `.glb`s) under `app/src/main/assets/`. Done = files are present and referenced by a relative path you've written down for later steps.
- [ ] Build a throwaway screen with a bare non-AR `Scene` composable that loads the car `.glb` and auto-rotates it slowly. Done = the model renders and spins with no crash — this is your baseline proof that Filament/SceneView rendering works before any AR or camera complexity is added.

## Phase 2 — AR Capability Detection

- [ ] Create a state holder that calls `ArCoreApk.getInstance().checkAvailability(context)` and exposes a sealed state: `Checking`, `Supported`, `Unsupported`. Done = compiles and is unit-testable independent of any UI.
- [ ] Handle the `UNKNOWN_CHECKING` transient state by re-polling after a short delay (per ARCore's own guidance, since it may be querying the Play Store) and showing a loading screen meanwhile. Done = the loading state never gets stuck and always resolves to Supported or Unsupported.
- [ ] Handle `SUPPORTED_NOT_INSTALLED` / `SUPPORTED_APK_TOO_OLD` by calling `ArCoreApk.requestInstall()`, handling the activity result, and re-checking availability afterward. Done = a device with missing/outdated Play Services for AR is prompted to install/update, and re-enters your state machine correctly whether the user accepts or declines.
- [ ] Wire the three states into your app's entry point as a Compose router: `Checking` → loading UI, `Supported` → AR-mode route (placeholder screen for now), `Unsupported` (or declined install) → Fallback-mode route (placeholder screen for now). Done = replaces the Phase 1 blank screen as the real app entry point.
- [ ] `[device]` Launch on your ARCore-supported phone. Done = it routes to the AR-mode placeholder.
- [ ] Launch on the emulator. Done = it routes to the Fallback-mode placeholder (emulators report `UNSUPPORTED_DEVICE_NOT_CAPABLE`).
- [ ] `[device]` Optional but recommended: on your supported phone, uninstall "Google Play Services for AR" from Settings, relaunch the app, and confirm the install-prompt path from the `requestInstall` task actually fires and recovers correctly.

## Phase 3 — AR Placement

- [ ] `[device]` Replace the AR-mode placeholder with an `ARScene` composable showing the live camera feed and default plane-detection visualization only (no model yet). Done = runs on your supported phone with no crash.
- [ ] `[device]` Point the camera at a real horizontal surface (table/floor) in decent lighting. Done = a plane grid/mesh visibly appears on that surface.
- [ ] `[device]` Implement tap-to-place: hit-test a tap against detected planes, anchor the car `.glb` at the hit pose (one instance; later taps do nothing until reset). Done = tapping a detected plane places the car, and it stays fixed to that real-world spot as you move the phone around it.
- [ ] `[device]` Implement pinch-to-scale on the placed node, clamped to a sane range (e.g. 0.5x–2x). Done = pinching resizes the car smoothly with no flip/inversion and no drift off its anchor.
- [ ] `[device]` Implement two-finger twist-to-rotate around the model's vertical axis. Done = rotation tracks the gesture smoothly and persists across repeated gestures (doesn't snap back).
- [ ] `[device]` Implement one-finger drag-to-reposition, re-hit-testing against detected planes under the drag point. Done = dragging slides the car across detected surfaces, following plane geometry rather than floating in space.
- [ ] `[device]` Add a "reset placement" control that removes the current anchor/node so the user can tap to place again. Done = repeated place → transform → reset cycles work cleanly, with no accumulating nodes/anchors visible in logcat.

## Phase 4 — Fallback Mode

- [ ] `[device]` Build a CameraX preview (`PreviewView` via `AndroidView`, bound through `camera-lifecycle`) as a full-screen background, with runtime camera-permission handling. Done = the live back-camera feed displays with no crash.
- [ ] `[device]` Overlay a non-AR `Scene` composable (transparent background) on top of the CameraX preview, statically rendering the car `.glb` centered on screen. Done = the car appears composited over the live camera feed.
- [ ] `[device]` Register a `SensorManager` listener on `TYPE_ROTATION_VECTOR`, convert the rotation vector to a rotation you apply to the model node each update (with light smoothing to avoid jitter). Done = physically rotating the phone visibly spins the displayed car to match, with no jarring jumps.
- [ ] `[device]` Add a soft contact shadow under the model (shadow-only ground plane or a soft blob-shadow decal). Done = a soft shadow renders beneath the car and shifts believably as it rotates.
- [ ] Load the `[asset]` HDR as an `IndirectLight` for this scene only. Done = the car's shading looks noticeably more realistic than with no environment lighting, since there's no ARCore light estimation to fall back on here.
- [ ] `[device]` Regression check: relaunch on both your supported phone and the emulator and confirm the Phase 2 router still sends you to AR vs. Fallback correctly with neither path crashing on entry.

## Phase 5 — Customization (shared by both modes)

- [ ] Design a plain-Kotlin `CarCustomizer` (or equivalent) module that operates on a loaded model's nodes/material instances, with `applyPaint(colorRgba)` and `applyWheels(wheelGlbPath)` — no dependency on AR-specific or CameraX-specific types. Done = it compiles standalone and has no imports from your AR-mode or Fallback-mode UI code.
- [ ] Implement `applyPaint`: locate the body `MaterialInstance` and set `baseColorFactor` to the given color. Done = calling it against your Phase 1 or Phase 4 scene changes body color live, with no model reload.
- [ ] Build a Compose paint-swatch picker that calls `applyPaint`. Done = tapping swatches changes the car's color instantly in Fallback mode.
- [ ] Implement `applyWheels`: load the alternate wheel `[asset]` `.glb`, detach the current wheel child nodes, attach the new ones at the same transforms. Done = wheels visually swap in Fallback mode with no misalignment relative to the body.
- [ ] Build a Compose wheel-style picker that calls `applyWheels`. Done = cycling wheel options works repeatedly in Fallback mode without leftover/duplicate wheel nodes.
- [ ] `[device]` Wire the same `CarCustomizer` and picker composables into the AR-mode placed node from Phase 3. Done = paint and wheel swap work identically once a car is placed in AR, using the same module — no reimplementation.
- [ ] `[device]` Regression check: confirm pinch-scale/rotate/drag from Phase 3 still work correctly after applying a paint or wheel change (customization must not reset transform or anchor).

## Phase 6 — Build & Ship

- [ ] Add a real app icon and name (placeholder is fine functionally, but confirm it's not the default Android icon). Done = visually confirmed on a home screen.
- [ ] In Google Play Console, complete the "Google Play Services for AR" app content declaration (AR usage, required = false) — distinct from the manifest flag. Done = declaration saved and visible in App content status as complete.
- [ ] Build a signed release AAB and check the Play Console pre-launch report / device catalog. Done = it confirms the app is installable on both ARCore-supported and ARCore-unsupported device profiles.
- [ ] `[device]` Full manual pass on your supported phone: capability check → AR placement → all four gestures → paint → wheel swap, start to finish with no crash.
- [ ] `[device]` Full manual pass on a non-AR path (emulator or a phone with Play Services for AR uninstalled): capability check → Fallback camera preview → gyro rotation → paint → wheel swap, start to finish with no crash.
- [ ] `[device]` Fresh-install check (uninstall then reinstall) on both a supported and unsupported device. Done = no first-run crash, permission prompts behave correctly, and cold start lands on the correct mode both times.
