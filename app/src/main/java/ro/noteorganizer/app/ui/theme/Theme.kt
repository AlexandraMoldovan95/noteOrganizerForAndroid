package ro.noteorganizer.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The app drawn in one of the desktop's themes.
 *
 * The palette is handed down as [LocalPalette] for this app's own drawing,
 * and mapped onto Material 3's colour roles so the parts Android draws --
 * bottom sheets, dialogs, the navigation bar, the snackbar, the date
 * picker -- wear the same theme rather than Android's own purple.
 */
@Composable
fun AppTheme(palette: Palette, content: @Composable () -> Unit) {
    val base = if (palette.isDark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        primaryContainer = palette.calendarHasNotes,
        onPrimaryContainer = palette.text,
        secondary = palette.accent,
        onSecondary = palette.onAccent,
        secondaryContainer = palette.calendarHasNotes,
        onSecondaryContainer = palette.text,
        tertiary = palette.accent,
        background = palette.bg,
        onBackground = palette.text,
        surface = palette.bg,
        onSurface = palette.text,
        surfaceVariant = palette.card,
        onSurfaceVariant = palette.subtext,
        surfaceContainerLowest = palette.bg,
        surfaceContainerLow = palette.card,
        surfaceContainer = palette.sidebar,
        surfaceContainerHigh = palette.card,
        surfaceContainerHighest = palette.inset,
        inverseSurface = palette.text,
        inverseOnSurface = palette.bg,
        inversePrimary = palette.accent,
        error = palette.danger,
        onError = palette.onDanger,
        outline = palette.hairline,
        outlineVariant = palette.hairline,
    )
    MaterialTheme(colorScheme = scheme, typography = Typography) {
        CompositionLocalProvider(LocalPalette provides palette) { content() }
    }
}
