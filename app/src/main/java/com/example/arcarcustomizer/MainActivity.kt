package com.example.arcarcustomizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.example.arcarcustomizer.ui.theme.ArCarCustomizerTheme
import io.github.sceneview.SceneView
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode as ModelNodeImpl
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArCarCustomizerTheme {
                RenderingSmokeTestScreen()
            }
        }
    }
}

/**
 * Phase 1 smoke test (tasks.md): proves the Filament/SceneView rendering
 * pipeline works end to end before any AR or camera complexity is added.
 *
 * Loads "models/placeholder_car.glb" — a temporary stand-in until the real
 * car model [asset] is dropped into app/src/main/assets/models/.
 */
@Composable
private fun RenderingSmokeTestScreen() {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val model = rememberModelInstance(modelLoader, "models/placeholder_car.glb")

    var modelNode by remember { mutableStateOf<ModelNodeImpl?>(null) }

    SceneView(
        modifier = Modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
    ) {
        model?.let {
            ModelNode(
                modelInstance = it,
                scaleToUnits = 1f,
                apply = { modelNode = this }
            )
        }
    }

    // Slow auto-rotate so the smoke test needs no user interaction to prove
    // the model rendered.
    LaunchedEffect(modelNode) {
        val node = modelNode ?: return@LaunchedEffect
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameNanos ->
                val elapsedSeconds = (frameNanos - startNanos) / 1_000_000_000f
                node.rotation = Rotation(y = (elapsedSeconds * 30f) % 360f)
            }
        }
    }
}
