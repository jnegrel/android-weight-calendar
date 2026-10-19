package no.jene.weightcalendar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.jene.weightcalendar.backup.Backup
import no.jene.weightcalendar.ui.theme.AccentBlue
import no.jene.weightcalendar.ui.theme.TextSecondary
import java.time.format.DateTimeFormatter

// Pop-up for the daily backup. Unlike the other dialogs, changes apply at once
// (choosing a folder opens Android's folder picker, which must happen right away).
@Composable
fun BackupDialog(
    backup: BackupSetting,
    onChooseFolder: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hasFolder = backup.folderName != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily backup") },
        text = {
            Column {
                Text(
                    "Once a day, a CSV backup is written to the folder below, and the last " +
                        "${Backup.KEEP} are kept. Your NAS app can sync that folder.",
                    color = TextSecondary, fontSize = 14.sp,
                )
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(backup.folderName?.let { "Folder: $it" } ?: "No folder chosen", Modifier.weight(1f))
                    TextButton(onClick = onChooseFolder) { Text("CHOOSE", color = AccentBlue) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Back up every day", Modifier.weight(1f))
                    Switch(checked = backup.enabled && hasFolder, onCheckedChange = onEnabledChange, enabled = hasFolder)
                }
                val last = backup.lastBackup?.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")) ?: "never"
                Text("Last backup: $last", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } },
        dismissButton = {
            TextButton(onClick = onBackupNow, enabled = hasFolder) { Text("BACK UP NOW") }
        },
    )
}
