package no.jene.weightcalendar.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import no.jene.weightcalendar.R
import no.jene.weightcalendar.data.WeightUnit
import no.jene.weightcalendar.ui.theme.Background
import no.jene.weightcalendar.ui.theme.Surface
import no.jene.weightcalendar.ui.theme.TextPrimary
import no.jene.weightcalendar.ui.theme.TextSecondary

// Settings screen: one row per setting (title on the left, current value on the right).
// Tapping a row opens the same pop-ups as before; the screen itself only lists them.
@Composable
fun SettingsScreen(
    goal: Double?,
    onGoalChange: (Double?) -> Unit,
    reminder: ReminderSetting,
    onReminderChange: (Boolean, LocalTime) -> Unit,
    backup: BackupSetting,
    onChooseBackupFolder: () -> Unit,
    onBackupEnabledChange: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
    unit: WeightUnit,
    onUnitChange: (WeightUnit) -> Unit,
    onBack: () -> Unit,
) {
    // Which pop-up is open (rememberSaveable: still open after rotating the phone).
    var editingGoal by rememberSaveable { mutableStateOf(false) }
    var editingReminder by rememberSaveable { mutableStateOf(false) }
    var editingBackup by rememberSaveable { mutableStateOf(false) }
    var editingUnit by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName

    Column(Modifier.fillMaxSize().background(Background)) {
        AppHeader(stringResource(R.string.settings), onBack = onBack)
        Column(
            Modifier
                .padding(16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),   // scrolls if the screen is too small
        ) {
            SettingsCard {
                SettingRow(stringResource(R.string.goal_title), goal?.let { unit.format(it) } ?: stringResource(R.string.not_set)) { editingGoal = true }
                HorizontalDivider(color = Background)
                SettingRow(
                    stringResource(R.string.reminder_title),
                    if (reminder.enabled) stringResource(R.string.reminder_summary, reminder.time.toString())
                    else stringResource(R.string.off),
                ) { editingReminder = true }
                HorizontalDivider(color = Background)
                SettingRow(stringResource(R.string.backup_title), backupSummary(backup)) { editingBackup = true }
            }
            SettingsCard(Modifier.padding(top = 16.dp)) {
                SettingRow(stringResource(R.string.unit_title), unit.label) { editingUnit = true }
                // The app's own language setting exists since Android 13 (API 33, "TIRAMISU").
                // Android shows it (with the languages of locales_config.xml); we just open it.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    HorizontalDivider(color = Background)
                    SettingRow(stringResource(R.string.language_title), currentLanguage()) {
                        val intent = Intent(
                            android.provider.Settings.ACTION_APP_LOCALE_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        context.startActivity(intent)
                    }
                }
            }
            SettingsCard(Modifier.padding(top = 16.dp)) {
                SettingRow(stringResource(R.string.about_title), "${stringResource(R.string.app_name)} $version", onClick = null)
            }
        }
    }

    if (editingGoal) {
        GoalDialog(
            current = goal,
            onDismiss = { editingGoal = false },
            onSave = { onGoalChange(it); editingGoal = false },
        )
    }
    if (editingReminder) {
        ReminderDialog(
            enabled = reminder.enabled,
            time = reminder.time,
            onDismiss = { editingReminder = false },
            onSave = { on, time -> onReminderChange(on, time); editingReminder = false },
        )
    }
    if (editingUnit) {
        UnitDialog(
            current = unit,
            onDismiss = { editingUnit = false },
            onSelect = { onUnitChange(it); editingUnit = false },
        )
    }
    if (editingBackup) {
        BackupDialog(
            backup = backup,
            onChooseFolder = onChooseBackupFolder,
            onEnabledChange = onBackupEnabledChange,
            onBackupNow = onBackupNow,
            onDismiss = { editingBackup = false },
        )
    }
}

// Choice between kg and lb: one line per unit, with a round radio button.
@Composable
private fun UnitDialog(current: WeightUnit, onDismiss: () -> Unit, onSelect: (WeightUnit) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unit_title)) },
        text = {
            Column {
                for (unit in WeightUnit.entries) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(unit) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = unit == current, onClick = { onSelect(unit) })
                        Text(stringResource(if (unit == WeightUnit.KG) R.string.unit_kg else R.string.unit_lb))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// "Français", "Norsk bokmål"...: the language the app is shown in, in that language.
private fun currentLanguage(): String {
    val locale = Locale.getDefault()
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}

// "On · last 19 Oct, 16:20", or "Off".
// @Composable only to read the texts with stringResource.
@Composable
private fun backupSummary(backup: BackupSetting): String {
    if (!backup.enabled || backup.folderName == null) return stringResource(R.string.off)
    val last = backup.lastBackup?.format(DateTimeFormatter.ofPattern(stringResource(R.string.pattern_date_time)))
        ?: stringResource(R.string.never)
    return stringResource(R.string.backup_summary, last)
}

// A rounded dark card, like the calendar and the graph, holding a group of rows.
// `content: @Composable () -> Unit` = the rows, written between { } by the caller.
@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)) {
        content()
    }
}

// One row: title on the left, value on the right. onClick = null: not clickable (About).
@Composable
private fun SettingRow(title: String, value: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = TextPrimary, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextSecondary, fontSize = 15.sp)
    }
}
