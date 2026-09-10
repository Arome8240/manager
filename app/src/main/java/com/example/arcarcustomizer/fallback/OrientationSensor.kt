package com.example.arcarcustomizer.fallback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import io.github.sceneview.math.Rotation

/**
 * Registers a `TYPE_ROTATION_VECTOR` listener for the composition's lifetime and calls
 * [onRotationChanged] directly from the sensor callback (main thread) with Euler degrees
 * suitable for a SceneView node's `rotation` — bypassing Compose recomposition for these
 * ~50 Hz updates, the same imperative-mutation pattern the Phase 1 smoke test uses for its
 * per-frame spin.
 *
 * [device]-only: the emulator has no real rotation-vector sensor, so [onRotationChanged] never
 * fires there.
 */
@Composable
fun RegisterDeviceRotationListener(onRotationChanged: (Rotation) -> Unit) {
    val context = LocalContext.current
    val latestOnRotationChanged by rememberUpdatedState(onRotationChanged)

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
                val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
                latestOnRotationChanged(Rotation(x = pitchDeg, y = -azimuthDeg, z = rollDeg))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(
                listener,
                rotationVectorSensor,
                SensorManager.SENSOR_DELAY_GAME,
            )
        }

        onDispose { sensorManager.unregisterListener(listener) }
    }
}
