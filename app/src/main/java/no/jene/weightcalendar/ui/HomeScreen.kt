package no.jene.weightcalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.jene.weightcalendar.ui.theme.Background
import no.jene.weightcalendar.ui.theme.ButtonEnd
import no.jene.weightcalendar.ui.theme.ButtonStart
import no.jene.weightcalendar.ui.theme.HeaderBottom
import no.jene.weightcalendar.ui.theme.HeaderTop
import no.jene.weightcalendar.ui.theme.Surface
import no.jene.weightcalendar.ui.theme.TextPrimary
import no.jene.weightcalendar.ui.theme.TextSecondary
import no.jene.weightcalendar.ui.theme.WeightCalendarTheme

// A @Composable function describes a piece of UI. Compose calls it to draw the screen,
// and calls it again whenever the data it uses changes.
//
// Layout of the home screen, top to bottom:
//   header  |  calendar  |  graph (takes the remaining space)  |  DAILY LOG button
@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Header()

        Column(
            modifier = Modifier
                .weight(1f)                      // fill the space left under the header
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),        // stay above the Android back/home bar
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Placeholder("Calendar", Modifier.padding(top = 12.dp).height(340.dp))
            Placeholder("Graph", Modifier.weight(1f))
            DailyLogButton(onClick = { /* step 2: open the weight entry dialog */ })
        }
    }
}

@Composable
private fun Header() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.verticalGradient(listOf(HeaderTop, HeaderBottom)))
            .statusBarsPadding()                 // push the title below the clock/battery icons
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Weight Calendar", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}

// Grey box standing in for parts we haven't built yet.
@Composable
private fun Placeholder(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TextSecondary, fontSize = 18.sp)
    }
}

@Composable
private fun DailyLogButton(onClick: () -> Unit) {
    // A plain Material Button can't have a gradient, so we draw our own: a clickable rounded box.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.horizontalGradient(listOf(ButtonStart, ButtonEnd)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("DAILY LOG", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

// Lets Android Studio show this screen in its design panel without running the app.
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun HomeScreenPreview() {
    WeightCalendarTheme { HomeScreen() }
}
