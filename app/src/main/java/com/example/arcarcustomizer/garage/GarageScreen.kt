package com.example.arcarcustomizer.garage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arcarcustomizer.customization.CarModel
import com.example.arcarcustomizer.customization.CustomizableCar
import com.example.arcarcustomizer.customization.CustomizationState
import com.example.arcarcustomizer.customization.PartPicker
import com.example.arcarcustomizer.customization.footprint
import com.example.arcarcustomizer.ui.theme.GarageColors
import com.example.arcarcustomizer.ui.theme.SlantedShape
import com.example.arcarcustomizer.ui.theme.entrance
import com.example.arcarcustomizer.ui.theme.garageText
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.createEnvironment
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.delay

/**
 * NFS-style garage: the car on a lit turntable in a dark showroom, a camera that flies to
 * whichever part category is open, and the part picker, car switcher and performance read-out
 * layered over it. Entry point of the app; [onOpenLiveView] hands the same build over to AR or
 * Fallback mode.
 *
 * @param liveViewLabel label for the hand-off button, or null while AR support is still being
 * checked (the button shows as disabled).
 */
@Composable
fun GarageScreen(
    state: CustomizationState,
    liveViewLabel: String?,
    onOpenLiveView: () -> Unit,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val cameraNode = rememberCameraNode(engine)
    val camera = remember { GarageCameraController() }

    val car = state.car
    val category = state.category
    LaunchedEffect(car, category) { camera.moveTo(cameraShotFor(car, category)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GarageColors.Background),
    ) {
        ShowroomBackdrop()

        // Transparent 3D layer over the Compose backdrop — the same compositing approach
        // Fallback mode uses over its camera feed.
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            surfaceType = SurfaceType.TextureSurface,
            isOpaque = false,
            autoCenterContent = false,
            autoFitContent = false,
            cameraNode = cameraNode,
            // The garage flies its own camera; SceneView's orbit manipulator would fight it.
            cameraManipulator = null,
            environment = rememberEnvironment(environmentLoader, isOpaque = false) {
                runCatching {
                    environmentLoader.createHDREnvironment("environments/studio.hdr", createSkybox = false)
                }.getOrNull() ?: createEnvironment(environmentLoader, false)
            },
            onFrame = { frameTimeNanos -> camera.update(cameraNode, frameTimeNanos) },
        ) {
            val footprint = car.footprint
            val turntableRadius = footprint.halfLength * 0.95f
            val deck = remember(materialLoader) {
                materialLoader.createColorInstance(Color(0xFF15181F), metallic = 0f, roughness = 0.35f)
            }
            val ring = remember(materialLoader) {
                materialLoader.createColorInstance(GarageColors.Accent, metallic = 0f, roughness = 0.4f)
            }
            val floorPosition = Position(footprint.center.x, footprint.groundY, footprint.center.z)

            // Keyed on the car: CylinderNode doesn't rebuild its geometry when radius changes,
            // so each car gets a fresh turntable sized to it.
            key(car.id) {
                CylinderNode(
                    radius = turntableRadius,
                    height = 0.06f,
                    materialInstance = deck,
                    position = floorPosition - Position(y = 0.031f),
                )
                // Thin neon rim just proud of the deck edge.
                CylinderNode(
                    radius = turntableRadius + 0.05f,
                    height = 0.025f,
                    materialInstance = ring,
                    position = floorPosition - Position(y = 0.05f),
                )
            }
            CustomizableCar(
                car = car,
                partModelLoader = modelLoader,
                paint = state.paint,
                selectedOptions = state.selectedOptions,
            )
        }

        // Orbit/zoom surface. Sits under the UI panels, so their taps never reach it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(camera) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        camera.onDragStart()
                        do {
                            val event = awaitPointerEvent()
                            val pan = event.calculatePan()
                            camera.onDrag(pan.x, pan.y, event.calculateZoom())
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } while (event.changes.any { it.pressed })
                        camera.onDragEnd()
                    }
                },
        )

        GarageHud(state, liveViewLabel, onOpenLiveView)
    }
}

/** Soft overhead "spotlight" pool on a near-black showroom, drawn in Compose behind the 3D layer. */
@Composable
private fun ShowroomBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(GarageColors.Spotlight, GarageColors.Background),
                center = Offset(size.width / 2f, size.height * 0.52f),
                radius = maxOf(size.width, size.height) * 0.6f,
            )
        )
        // Faint horizon line where the floor meets the back wall.
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.58f to Color.Transparent,
                0.6f to GarageColors.Accent.copy(alpha = 0.06f),
                0.66f to Color.Transparent,
            )
        )
    }
}

/**
 * HUD layout. Wide screens (landscape, tablets) put the hand-off button and stats top-right;
 * narrow ones stack the stats under the car name and move the button down above the picker, so
 * the car name never gets squeezed.
 */
@Composable
private fun GarageHud(state: CustomizationState, liveViewLabel: String?, onOpenLiveView: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        val compact = maxWidth < 600.dp
        val switcher = @Composable { modifier: Modifier ->
            CarSwitcher(
                car = state.car,
                onCycle = state::cycleCar,
                modifier = modifier.entrance(delayMillis = 100, fromX = (-40).dp),
            )
        }

        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                switcher(Modifier.fillMaxWidth())
                StatsPanel(state, Modifier.entrance(delayMillis = 250, fromX = 40.dp))
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                switcher(Modifier.weight(1f))
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.entrance(delayMillis = 250, fromX = 40.dp),
                ) {
                    LiveViewButton(liveViewLabel, onOpenLiveView)
                    StatsPanel(state)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (compact) {
                LiveViewButton(
                    liveViewLabel,
                    onOpenLiveView,
                    Modifier.padding(end = 16.dp).entrance(delayMillis = 300, fromX = 40.dp),
                )
            }
            PartPicker(
                state = state,
                modifier = Modifier.fillMaxWidth().entrance(delayMillis = 400, fromY = 60.dp),
            )
        }
    }
}

@Composable
private fun CarSwitcher(car: CarModel, onCycle: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    var direction by remember { mutableStateOf(1) }
    val cycle = { step: Int ->
        direction = step
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onCycle(step)
    }
    Column(modifier = modifier) {
        Text("GARAGE", style = garageText(size = 11.sp, color = GarageColors.Accent, letterSpacing = 4.sp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArrowButton("‹") { cycle(-1) }
            AnimatedContent(
                targetState = car,
                transitionSpec = {
                    (slideInHorizontally(tween(350)) { it / 3 * direction } + fadeIn(tween(350)))
                        .togetherWith(slideOutHorizontally(tween(200)) { -it / 3 * direction } + fadeOut(tween(200)))
                },
                label = "carName",
                modifier = Modifier.weight(1f, fill = false).padding(horizontal = 10.dp),
            ) { shownCar ->
                val make = shownCar.label.substringBefore(' ')
                val model = shownCar.label.substringAfter(' ', missingDelimiterValue = "")
                Column {
                    Text(make.uppercase(), style = garageText(size = 14.sp, color = GarageColors.TextMuted, letterSpacing = 3.sp), maxLines = 1, softWrap = false)
                    ScrambleText(model.ifEmpty { make }.uppercase(), style = garageText(size = 34.sp))
                }
            }
            ArrowButton("›") { cycle(1) }
        }
    }
}

@Composable
private fun ArrowButton(glyph: String, onClick: () -> Unit) {
    Text(
        text = glyph,
        style = garageText(size = 26.sp, color = GarageColors.Text),
        modifier = Modifier
            .clip(SlantedShape(6.dp))
            .background(GarageColors.Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 0.dp),
    )
}

/**
 * Text that resolves out of random glyphs, left to right — the "decoding" effect racing-game
 * menus use on titles.
 */
@Composable
private fun ScrambleText(text: String, style: TextStyle) {
    var shown by remember { mutableStateOf(text) }
    LaunchedEffect(text) {
        val glyphs = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789#/"
        val frames = 12
        for (frame in 1..frames) {
            val revealed = text.length * frame / frames
            shown = text.mapIndexed { i, c ->
                if (i < revealed || c == ' ' || c == '-') c else glyphs.random()
            }.joinToString("")
            delay(28)
        }
        shown = text
    }
    Text(shown, style = style, maxLines = 1, softWrap = false)
}

@Composable
private fun LiveViewButton(label: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val enabled = label != null
    Text(
        text = (label ?: "CHECKING AR…") + if (enabled) "  ›" else "",
        style = garageText(size = 15.sp, color = if (enabled) Color.White else GarageColors.TextMuted),
        modifier = modifier
            .clip(SlantedShape(10.dp))
            .background(if (enabled) GarageColors.Action else GarageColors.Panel)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    )
}

@Composable
private fun StatsPanel(state: CustomizationState, modifier: Modifier = Modifier) {
    val stats = state.stats
    Column(
        modifier = modifier
            .width(168.dp)
            .clip(SlantedShape(12.dp))
            .background(GarageColors.Panel)
            .padding(start = 20.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("PERFORMANCE", style = garageText(size = 10.sp, color = GarageColors.Accent, letterSpacing = 3.sp))
        StatBar("TOP SPEED", stats.topSpeed)
        StatBar("ACCELERATION", stats.acceleration)
        StatBar("HANDLING", stats.handling)
    }
}

/** A segmented, skewed bar that springs to [value] (0..1) whenever the build changes. */
@Composable
private fun StatBar(label: String, value: Float) {
    val animated by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 120f),
        label = "stat",
    )
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = garageText(size = 10.sp, color = GarageColors.TextMuted), modifier = Modifier.weight(1f))
            Text("%.1f".format(animated * 10f), style = garageText(size = 10.sp))
        }
        Spacer(Modifier.height(3.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(7.dp)) {
            val segments = 20
            val gap = 2.dp.toPx()
            val skew = size.height * 0.6f
            val segmentWidth = (size.width - gap * (segments - 1)) / segments
            val lit = animated * segments
            for (i in 0 until segments) {
                val left = i * (segmentWidth + gap)
                val fill = (lit - i).coerceIn(0f, 1f)
                val path = Path().apply {
                    moveTo(left + skew, 0f)
                    lineTo(left + segmentWidth, 0f)
                    lineTo(left + segmentWidth - skew, size.height)
                    lineTo(left, size.height)
                    close()
                }
                drawPath(path, Color.White.copy(alpha = 0.12f))
                if (fill > 0f) drawPath(path, GarageColors.Accent.copy(alpha = fill))
            }
        }
    }
}
