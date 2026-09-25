package com.example.arcarcustomizer.customization

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arcarcustomizer.ui.theme.GarageColors
import com.example.arcarcustomizer.ui.theme.SlantedShape
import com.example.arcarcustomizer.ui.theme.garageText

/**
 * NFS-style picker shared by the garage, AR and Fallback modes: slanted category tabs over a
 * snapping carousel of that category's options. Selecting an option applies it to the car
 * immediately — there is no separate "apply" step.
 */
@Composable
fun PartPicker(state: CustomizationState, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.car.categories.forEach { category ->
                CategoryTab(
                    label = category.label,
                    selected = category.id == state.category.id,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        state.categoryId = category.id
                    },
                )
            }
        }

        // Keyed on car + category so switching either slides a fresh carousel in.
        AnimatedContent(
            targetState = state.car.id to state.category,
            transitionSpec = {
                (slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(320)))
                    .togetherWith(slideOutHorizontally(tween(200)) { -it / 4 } + fadeOut(tween(200)))
            },
            label = "carousel",
        ) { (_, category) ->
            when (category) {
                CustomizationCategory.Paint -> OptionCarousel(
                    options = state.paints,
                    isSelected = { it == state.paint },
                    onSelected = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        state.paint = it
                    },
                ) { paint, selected -> PaintCard(paint, selected) }

                is CustomizationCategory.Part -> OptionCarousel(
                    options = category.slot.options,
                    isSelected = { it == state.optionFor(category.slot) },
                    onSelected = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        state.selectOption(category.slot.id, it)
                    },
                ) { option, selected ->
                    PartCard(index = category.slot.options.indexOf(option), option = option, selected = selected)
                }
            }
        }
    }
}

@Composable
private fun CategoryTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) GarageColors.Accent else GarageColors.Panel,
        label = "tabBackground",
    )
    Text(
        text = label.uppercase(),
        style = garageText(
            size = 14.sp,
            color = if (selected) Color.Black else GarageColors.TextMuted,
        ),
        modifier = Modifier
            .clip(SlantedShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp),
    )
}

@Composable
private fun <T> OptionCarousel(
    options: List<T>,
    isSelected: (T) -> Boolean,
    onSelected: (T) -> Unit,
    card: @Composable (T, Boolean) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = options.indexOfFirst(isSelected).coerceAtLeast(0),
    )
    LazyRow(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(options) { _, option ->
            val selected = isSelected(option)
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.06f else 1f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
                label = "cardScale",
            )
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(SlantedShape(14.dp))
                    .background(GarageColors.Panel)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) GarageColors.Accent else GarageColors.PanelBorder,
                        shape = SlantedShape(14.dp),
                    )
                    .then(if (selected) Modifier.lightSweep() else Modifier)
                    .clickable { onSelected(option) },
            ) {
                card(option, selected)
            }
        }
    }
}

@Composable
private fun PaintCard(paint: CarPaint, selected: Boolean) {
    Column(
        modifier = Modifier
            .width(118.dp)
            .padding(start = 22.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(SlantedShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(paint.color.copy(alpha = 1f), paint.color.darken(0.45f)),
                    )
                ),
        )
        Text(
            text = paint.label.uppercase(),
            style = garageText(
                size = 12.sp,
                color = if (selected) GarageColors.Text else GarageColors.TextMuted,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun PartCard(index: Int, option: PartOption, selected: Boolean) {
    Box(modifier = Modifier.size(width = 140.dp, height = 78.dp)) {
        // Oversized faded index behind the label, like a race number.
        Text(
            text = "%02d".format(index + 1),
            style = garageText(size = 52.sp, color = Color.White.copy(alpha = 0.07f)),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 24.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = option.label.uppercase(),
                style = garageText(
                    size = 15.sp,
                    color = if (selected) GarageColors.Text else GarageColors.TextMuted,
                ),
                maxLines = 2,
            )
            if (selected) {
                Text(
                    text = "EQUIPPED",
                    style = garageText(size = 10.sp, color = GarageColors.Accent, letterSpacing = 2.sp),
                )
            }
        }
    }
}

/** A diagonal band of light that sweeps across the selected card on a loop. */
@Composable
private fun Modifier.lightSweep(): Modifier {
    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "sweepProgress",
    )
    return drawWithContent {
        drawContent()
        val x = size.width * sweep
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                start = Offset(x - size.height, 0f),
                end = Offset(x, size.height),
            )
        )
    }
}

private fun Color.darken(fraction: Float) =
    Color(red * (1 - fraction), green * (1 - fraction), blue * (1 - fraction), alpha)
