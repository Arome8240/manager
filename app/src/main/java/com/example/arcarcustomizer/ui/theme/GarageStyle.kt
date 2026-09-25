package com.example.arcarcustomizer.ui.theme

import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Garage palette: near-black showroom, one neon accent for selection, one for calls to action. */
object GarageColors {
    val Background = Color(0xFF06070B)
    val Spotlight = Color(0xFF1B2433)
    val Panel = Color(0xE00D1017)
    val PanelBorder = Color(0x33FFFFFF)
    val Accent = Color(0xFF00E5FF)
    val Action = Color(0xFFFF2E88)
    val Text = Color.White
    val TextMuted = Color(0x99FFFFFF)
}

/** The system condensed face, used in bold italic caps — the racing-game look without shipping a font file. */
private val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))

fun garageText(
    size: TextUnit,
    weight: FontWeight = FontWeight.Black,
    color: Color = GarageColors.Text,
    letterSpacing: TextUnit = 1.sp,
) = TextStyle(
    fontFamily = Condensed,
    fontWeight = weight,
    fontStyle = FontStyle.Italic,
    fontSize = size,
    letterSpacing = letterSpacing,
    color = color,
)

/** Parallelogram leaning right by [slant] — the angular panel shape used throughout the garage UI. */
class SlantedShape(private val slant: Dp = 10.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { slant.toPx() }.coerceAtMost(size.width / 2f)
        return Outline.Generic(
            Path().apply {
                moveTo(s, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width - s, size.height)
                lineTo(0f, size.height)
                close()
            }
        )
    }
}

/**
 * Staggered slide-and-fade entrance: the element starts [fromX] away and settles into place
 * after [delayMillis], so panels arrive one after another.
 */
@Composable
fun Modifier.entrance(delayMillis: Int, fromX: Dp = 0.dp, fromY: Dp = 0.dp): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        progress.animateTo(1f, tween(durationMillis = 550, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        val remaining = 1f - progress.value
        alpha = progress.value
        translationX = fromX.toPx() * remaining
        translationY = fromY.toPx() * remaining
    }
}
