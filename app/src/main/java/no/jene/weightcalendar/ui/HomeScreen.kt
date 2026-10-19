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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import java.time.YearMonth

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
    val goal by viewModel.goal.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Runs a file operation in the background and shows its outcome in a Toast (short pop-up).
    fun runFileTask(task: suspend () -> String) {
        scope.launch {
            val message = try { task() } catch (e: Exception) { "Failed: ${e.message}" }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Android's file pickers; the chosen file comes back as a Uri (a file reference),
    // or null if the user cancelled.
    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runFileTask { "Imported ${viewModel.importCsv(uri)} days" }
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runFileTask { "Exported ${viewModel.exportCsv(uri)} days" }
    }

    HomeContent(
        days = days,
        goal = goal,
        onGoalChange = viewModel::setGoal,
        onSave = viewModel::setWeight,
        onDelete = viewModel::delete,
        onImport = { pickCsv.launch(arrayOf("text/*", "application/*")) },
        onExport = { createCsv.launch("weights-${LocalDate.now()}.csv") },  // suggested file name
    )
}

@Composable
private fun HomeContent(
    days: Map<LocalDate, Day>,
    goal: Double?,
    onGoalChange: (Double?) -> Unit,
    onSave: (LocalDate, Double) -> Unit,
    onDelete: (LocalDate) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    // The day being edited in the dialog, or null when the dialog is closed.
    // (rememberSaveable keeps the dialog open when the phone is rotated.)
    var editing by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var editingGoal by rememberSaveable { mutableStateOf(false) }
    // The month shown in the calendar. It lives here because the graph follows it too.
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    // The graph ends on the last day visible in the calendar grid, but never after the
    // latest logged day (so for the current month it ends on the last entry).
    val graphEnd = minOf(month.gridDays().last(), days.keys.lastOrNull() ?: LocalDate.now())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Header(onImport, onExport, onGoal = { editingGoal = true })

        Column(
            modifier = Modifier
                .weight(1f)                      // fill the space left under the header
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),        // stay above the Android back/home bar
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonthCalendar(days, month, onMonthChange = { month = it }, onEdit = { editing = it },
                modifier = Modifier.padding(top = 12.dp))
            WeightGraph(days, end = graphEnd, goal = goal, modifier = Modifier.weight(1f))
            DailyLogButton(onClick = { editing = LocalDate.now() })
        }
    }

    if (editingGoal) {
        GoalDialog(
            current = goal,
            onDismiss = { editingGoal = false },
            onSave = { onGoalChange(it); editingGoal = false },
        )
    }

    // "?.let": only show the dialog when a day is being edited.
    editing?.let { date ->
        // key(date): start a fresh dialog (with its own text field) for each day.
        key(date) {
            DailyLogDialog(
                date = date,
                existing = days[date]?.entry,
                onDismiss = { editing = null },
                onSave = { weight -> onSave(date, weight); editing = null },
                onDelete = { onDelete(date); editing = null },
            )
        }
    }
}

@Composable
private fun Header(onImport: () -> Unit, onExport: () -> Unit, onGoal: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(Brush.verticalGradient(listOf(HeaderTop, HeaderBottom)))
            .statusBarsPadding()                 // push the title below the clock/battery icons
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Weight Calendar", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)

        // ⋮ menu on the right; more entries (settings) will go here later.
        Box(Modifier.align(Alignment.CenterEnd)) {
            TextButton(onClick = { menuOpen = true }) { Text("⋮", color = TextPrimary, fontSize = 26.sp) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Weight goal") }, onClick = { menuOpen = false; onGoal() })
                DropdownMenuItem(text = { Text("Import CSV") }, onClick = { menuOpen = false; onImport() })
                DropdownMenuItem(text = { Text("Export CSV") }, onClick = { menuOpen = false; onExport() })
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
            .height(45.dp)
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
    WeightCalendarTheme { HomeContent(fake.toDays(), goal = 100.0, onGoalChange = {}, onSave = { _, _ -> }, onDelete = {}, onImport = {}, onExport = {}) }
}
