package no.jene.weightcalendar.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import no.jene.weightcalendar.data.Day
import no.jene.weightcalendar.data.WeightEntry
import no.jene.weightcalendar.data.toDays
import no.jene.weightcalendar.ui.theme.Background
import no.jene.weightcalendar.ui.theme.ButtonEnd
import no.jene.weightcalendar.ui.theme.ButtonStart
import no.jene.weightcalendar.ui.theme.TextPrimary
import no.jene.weightcalendar.ui.theme.WeightCalendarTheme
import java.time.LocalDate
import java.time.LocalTime
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
    val reminder by viewModel.reminder.collectAsStateWithLifecycle()
    val backup by viewModel.backup.collectAsStateWithLifecycle()
    val unit by viewModel.unit.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Runs a file operation in the background and shows its outcome in a Toast (short pop-up).
    fun runFileTask(task: suspend () -> String) {
        scope.launch {
            val message = try { task() } catch (e: Exception) { "Failed: ${e.message}" }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Android's folder picker, for the daily backup.
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setBackupFolder(uri)
    }

    // Since Android 13, the user must allow notifications. Asked when the reminder is turned on
    // (in its dialog, or by importing a backup where it is on).
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Notifications blocked: the reminder won't show", Toast.LENGTH_LONG).show()
    }
    fun askNotificationsIfNeeded() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (viewModel.reminder.value.enabled && needsPermission) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun setReminder(enabled: Boolean, time: LocalTime) {
        viewModel.setReminder(enabled, time)
        askNotificationsIfNeeded()
    }

    // Android's file pickers; the chosen file comes back as a Uri (a file reference),
    // or null if the user cancelled.
    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runFileTask {
            val count = viewModel.importCsv(uri)
            askNotificationsIfNeeded()
            "Imported $count days"
        }
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runFileTask { "Exported ${viewModel.exportCsv(uri)} days" }
    }

    // Which screen is shown: the home screen, or the settings on top of it.
    // A single extra screen doesn't need a navigation library: a true/false is enough.
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // Everything inside can read the unit with LocalWeightUnit.current (see LocalWeightUnit.kt).
    CompositionLocalProvider(LocalWeightUnit provides unit) {
        if (showSettings) {
            // Android's back gesture/button closes the settings instead of leaving the app.
            BackHandler { showSettings = false }
            SettingsScreen(
                goal = goal,
                onGoalChange = viewModel::setGoal,
                reminder = reminder,
                onReminderChange = ::setReminder,
                backup = backup,
                onChooseBackupFolder = { pickFolder.launch(null) },
                onBackupEnabledChange = viewModel::setBackupEnabled,
                onBackupNow = { runFileTask { "Backup written: ${viewModel.backupNow()}" } },
                unit = unit,
                onUnitChange = viewModel::setUnit,
                onBack = { showSettings = false },
            )
        } else {
            HomeContent(
                days = days,
                goal = goal,
                onSave = viewModel::setWeight,
                onDelete = viewModel::delete,
                onImport = { pickCsv.launch(arrayOf("text/*", "application/*")) },
                onExport = { createCsv.launch("weights-${LocalDate.now()}.csv") },  // suggested file name
                onSettings = { viewModel.refreshBackup(); showSettings = true },
            )
        }
    }
}

@Composable
private fun HomeContent(
    days: Map<LocalDate, Day>,
    goal: Double?,
    onSave: (LocalDate, Double, String?) -> Unit,
    onDelete: (LocalDate) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onSettings: () -> Unit,
) {
    // The day being edited in the dialog, or null when the dialog is closed.
    // (rememberSaveable keeps the dialog open when the phone is rotated.)
    var editing by rememberSaveable { mutableStateOf<LocalDate?>(null) }
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
        AppHeader("Weight Calendar") { MainMenu(onImport, onExport, onSettings) }

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

    // "?.let": only show the dialog when a day is being edited.
    editing?.let { date ->
        // key(date): start a fresh dialog (with its own text field) for each day.
        key(date) {
            DailyLogDialog(
                date = date,
                existing = days[date]?.entry,
                onDismiss = { editing = null },
                onSave = { weight, note -> onSave(date, weight, note); editing = null },
                onDelete = { onDelete(date); editing = null },
            )
        }
    }
}

// ⋮ menu on the right of the header.
@Composable
private fun BoxScope.MainMenu(onImport: () -> Unit, onExport: () -> Unit, onSettings: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.align(Alignment.CenterEnd)) {
        TextButton(onClick = { menuOpen = true }) { Text("⋮", color = TextPrimary, fontSize = 26.sp) }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Import CSV") }, onClick = { menuOpen = false; onImport() })
            DropdownMenuItem(text = { Text("Export CSV") }, onClick = { menuOpen = false; onExport() })
            DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onSettings() })
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
    WeightCalendarTheme {
        HomeContent(fake.toDays(), goal = 100.0, onSave = { _, _, _ -> }, onDelete = {}, onImport = {}, onExport = {}, onSettings = {})
    }
}
