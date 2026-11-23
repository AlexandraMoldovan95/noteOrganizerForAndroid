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
    // Words and marks Android draws in the primary role, and the bar's
    // action in its inverse: shades of the accent held to text contrast
    // (Themes.primaryRole, Themes.barAction), where the raw accent was not.
    val primary = ro.noteorganizer.app.Themes.primaryRole(palette.hex)
    val scheme = base.copy(
        primary = hexColor(primary),
        onPrimary = hexColor(ro.noteorganizer.app.Themes.lettersOn(primary)),
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
        inversePrimary = hexColor(ro.noteorganizer.app.Themes.barAction(palette.hex)),
        error = palette.danger,
        onError = palette.onDanger,
        // A control's edge at rest (Themes.controlEdge), where Material draws
        // a switch that is off and a field's border; dividers keep the
        // hairline.
        outline = hexColor(ro.noteorganizer.app.Themes.controlEdge(palette.hex)),
        outlineVariant = palette.hairline,
    )
    MaterialTheme(colorScheme = scheme, typography = Typography) {
        CompositionLocalProvider(LocalPalette provides palette) { content() }
    }
}
