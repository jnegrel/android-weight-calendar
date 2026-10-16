package no.jene.weightcalendar.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import no.jene.weightcalendar.data.Day
import no.jene.weightcalendar.data.WeightEntry
import no.jene.weightcalendar.data.toDays
import no.jene.weightcalendar.ui.theme.Background
import no.jene.weightcalendar.ui.theme.ButtonEnd
import no.jene.weightcalendar.ui.theme.ButtonStart
import no.jene.weightcalendar.ui.theme.HeaderBottom
import no.jene.weightcalendar.ui.theme.HeaderTop
import no.jene.weightcalendar.ui.theme.TextPrimary
import no.jene.weightcalendar.ui.theme.WeightCalendarTheme
import java.time.LocalDate

// A @Composable function describes a piece of UI. Compose calls it to draw the screen,
// and calls it again whenever the data it uses changes.
//
// Layout of the home screen, top to bottom:
//   header  |  calendar  |  graph (takes the remaining space)  |  DAILY LOG button
//
// HomeScreen connects the screen to its ViewModel (the data); HomeContent only draws.
// Keeping them apart lets the preview at the bottom draw HomeContent with fake data.
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Opens Android's file picker; the chosen file comes back as a Uri (a file reference).
    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val message = try {
                "Imported ${viewModel.importCsv(uri)} days"
            } catch (e: Exception) {
                "Import failed: ${e.message}"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    HomeContent(
        days = days,
        onLog = viewModel::logToday,
        onImport = { pickCsv.launch(arrayOf("text/*", "application/*")) },
    )
}

@Composable
private fun HomeContent(days: Map<LocalDate, Day>, onLog: (Double) -> Unit, onImport: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Header(onImport)

        Column(
            modifier = Modifier
                .weight(1f)                      // fill the space left under the header
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),        // stay above the Android back/home bar
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonthCalendar(days, Modifier.padding(top = 12.dp))
            WeightGraph(days, Modifier.weight(1f))
            DailyLogButton(onClick = { showDialog = true })
        }
    }

    if (showDialog) {
        DailyLogDialog(
            onDismiss = { showDialog = false },
            onSave = { weight -> onLog(weight); showDialog = false },
        )
    }
}

@Composable
private fun Header(onImport: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
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

        // ⋮ menu on the right; more entries (export, settings) will go here later.
        Box(Modifier.align(Alignment.CenterEnd)) {
            TextButton(onClick = { menuOpen = true }) { Text("⋮", color = TextPrimary, fontSize = 26.sp) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Import CSV") }, onClick = { menuOpen = false; onImport() })
            }
        }
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
    // 60 days of made-up weights, with every 9th day marked as estimated
    val today = LocalDate.now()
    val fake = (0L..60L).map {
        WeightEntry(today.minusDays(60 - it), 100.0 + 0.8 * kotlin.math.sin(it * 1.3), estimated = it % 9 == 4L)
    }
    WeightCalendarTheme { HomeContent(fake.toDays(), onLog = {}, onImport = {}) }
}
