package no.jene.weightcalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import no.jene.weightcalendar.ui.HomeScreen
import no.jene.weightcalendar.ui.theme.WeightCalendarTheme

// An Activity is one screen of an Android app. This app has a single one,
// and everything inside it is drawn with Jetpack Compose (the setContent block).
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status bar, like the Headache Calendar header,
        // with light icons (clock, battery) since our background is always dark.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            WeightCalendarTheme {
                HomeScreen()
            }
        }
    }
}
