package no.jene.weightcalendar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// The app is always dark, like the Headache Calendar, whatever the phone setting.
private val DarkColors = darkColorScheme(
    primary = AccentBlue,
    // Selected fields (time picker) and switch thumb: dark blue instead of Material's purple.
    primaryContainer = Today,
    onPrimaryContainer = TextPrimary,
    onPrimary = Surface,
    background = Background,
    surface = Surface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun WeightCalendarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
