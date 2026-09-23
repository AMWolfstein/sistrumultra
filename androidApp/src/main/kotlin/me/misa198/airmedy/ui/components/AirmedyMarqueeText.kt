package me.misa198.airmedy.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt

/**
 * A single-line marquee that travels to the end of overflowing text and reverses direction.
 * When [animate] is false (playback paused) the text eases back to its start and stays still.
 */
@Composable
fun AirmedyMarqueeText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().clipToBounds()) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val availableWidthPx = with(density) { maxWidth.roundToPx() }
        val textWidthPx = remember(text, style, density) {
            textMeasurer.measure(
                text = AnnotatedString(text),
                style = style,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            ).size.width
        }
        val travelDistancePx = (textWidthPx - availableWidthPx).coerceAtLeast(0)
        if (travelDistancePx == 0) {
            // Text fits: stay static. An infinite transition here would still tick every
            // frame (animating 0 -> 0) and keep the app redrawing at 60 fps while idle.
            MarqueeTextLine(text, color, style, translationX = { 0f })
        } else {
            ScrollingMarqueeTextLine(text, color, style, travelDistancePx, animate)
        }
    }
}

/** Only composed when the text overflows ([travelDistancePx] > 0). */
@Composable
private fun ScrollingMarqueeTextLine(text: String, color: Color, style: TextStyle, travelDistancePx: Int, animate: Boolean) {
    val targetOffset = -travelDistancePx.toFloat()
    val totalDurationMs = ((travelDistancePx / 20f + 4f) * 1000f).roundToInt().coerceAtLeast(4_000)
    val pauseStartMs = (totalDurationMs * 0.15f).roundToInt()
    val moveEndMs = (totalDurationMs * 0.45f).roundToInt()
    val pauseEndMs = (totalDurationMs * 0.55f).roundToInt()
    val moveBackMs = (totalDurationMs * 0.85f).roundToInt()

    // Last drawn offset, so pausing mid-scroll can ease back instead of jumping.
    val lastOffset = remember { floatArrayOf(0f) }
    if (animate) {
        // An infinite transition (not an Animatable loop) so the infinite-animation policy
        // and Compose tests treat it as such. It only exists while playing.
        val transition = rememberInfiniteTransition(label = "airmedy-marquee")
        val translationX by transition.animateFloat(
            initialValue = 0f,
            targetValue = targetOffset,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = totalDurationMs
                    0f at 0 using FastOutSlowInEasing
                    0f at pauseStartMs using FastOutSlowInEasing
                    targetOffset at moveEndMs using FastOutSlowInEasing
                    targetOffset at pauseEndMs using FastOutSlowInEasing
                    0f at moveBackMs using FastOutSlowInEasing
                    0f at totalDurationMs
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "airmedy-marquee-translation",
        )
        MarqueeTextLine(text, color, style, translationX = { translationX.also { lastOffset[0] = it } })
    } else {
        // Paused: settle back to the start, then stay still (no frames drawn).
        val settle = remember { Animatable(lastOffset[0]) }
        LaunchedEffect(Unit) {
            settle.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
            lastOffset[0] = 0f
        }
        MarqueeTextLine(text, color, style, translationX = { settle.value })
    }
}

@Composable
private fun MarqueeTextLine(text: String, color: Color, style: TextStyle, translationX: () -> Float) {
    Text(
        text = text,
        modifier = Modifier
            .wrapContentWidth(align = Alignment.Start, unbounded = true)
            .graphicsLayer { this.translationX = translationX() },
        color = color,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}
