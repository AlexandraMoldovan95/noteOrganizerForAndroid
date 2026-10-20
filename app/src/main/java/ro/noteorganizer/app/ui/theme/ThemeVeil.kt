package ro.noteorganizer.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A theme changed under a picture of the screen it replaces, faded out:
 * the change between a light and a dark theme dissolves instead of cutting.
 *
 * Cut at once, the whole screen turned from white to dark or back in one
 * frame, and the phone's bars a moment after it: the owner saw it flicker,
 * and more so once the cut was made sharper (2026-10-20). Now the screen
 * as it is is kept as a picture, the theme changes beneath it, and the
 * picture fades over [FADE_MS]; the bars follow halfway through, when the
 * new theme is the one mostly seen.
 */
class ThemeVeil(private val scope: CoroutineScope) {

    /** The app's whole content, recorded on every drawing: what the picture is taken from. */
    internal var layer: GraphicsLayer? = null

    /** The screen as it was, over the new theme, while it fades. */
    var picture by mutableStateOf<ImageBitmap?>(null)
        private set

    /** The bars still in the old theme's contrast: until halfway through the fade. */
    var barsHeld by mutableStateOf(false)
        private set

    private val alpha = Animatable(0f)

    /**
     * [apply] the change -- a theme or the mode -- under a picture of the
     * screen as it is, then fade the picture. A change asked for while one
     * fades is made at once: the picture on the screen is already the old
     * one, and a second would show a theme that is gone.
     */
    fun change(apply: () -> Unit) {
        val recorded = layer
        if (picture != null || recorded == null) {
            apply()
            return
        }
        scope.launch {
            val shot = try {
                recorded.toImageBitmap()
            } catch (e: Exception) {
                null
            }
            if (shot == null) {
                apply()
                return@launch
            }
            alpha.snapTo(1f)
            picture = shot
            barsHeld = true
            apply()
            try {
                alpha.animateTo(0f, tween(FADE_MS, easing = FastOutSlowInEasing)) {
                    if (value <= 0.5f) barsHeld = false
                }
            } finally {
                barsHeld = false
                picture = null
            }
        }
    }

    /** The app's [content], recorded for the picture, with the picture drawn over it while it fades. */
    @Composable
    fun Over(content: @Composable () -> Unit) {
        val recording = rememberGraphicsLayer()
        SideEffect { layer = recording }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    recording.record { this@drawWithContent.drawContent() }
                    drawLayer(recording)
                },
            ) { content() }
            picture?.let { shot ->
                Image(
                    bitmap = shot,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    // Read while drawing only: the fade costs no composition.
                    modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = this@ThemeVeil.alpha.value },
                )
            }
        }
    }

    companion object {
        const val FADE_MS = 320
    }
}

/**
 * How a screen changes the theme: through the veil, so the change dissolves.
 * Outside the app's root -- a preview, a test -- it is made at once.
 */
val LocalThemeChange = staticCompositionLocalOf<(() -> Unit) -> Unit> { { apply -> apply() } }
