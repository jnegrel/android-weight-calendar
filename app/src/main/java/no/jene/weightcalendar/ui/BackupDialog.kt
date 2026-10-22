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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import no.jene.weightcalendar.R
import no.jene.weightcalendar.backup.Backup
import no.jene.weightcalendar.ui.theme.AccentBlue
import no.jene.weightcalendar.ui.theme.TextSecondary

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
        title = { Text(stringResource(R.string.backup_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.backup_explanation, Backup.KEEP),
                    color = TextSecondary, fontSize = 14.sp,
                )
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        backup.folderName?.let { stringResource(R.string.backup_folder, it) }
                            ?: stringResource(R.string.backup_no_folder),
                        Modifier.weight(1f),
                    )
                    TextButton(onClick = onChooseFolder) { Text(stringResource(R.string.choose), color = AccentBlue) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.backup_switch), Modifier.weight(1f))
                    Switch(checked = backup.enabled && hasFolder, onCheckedChange = onEnabledChange, enabled = hasFolder)
                }
                val last = backup.lastBackup?.format(DateTimeFormatter.ofPattern(stringResource(R.string.pattern_date_time)))
                    ?: stringResource(R.string.never)
                Text(stringResource(R.string.backup_last, last), color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            TextButton(onClick = onBackupNow, enabled = hasFolder) { Text(stringResource(R.string.back_up_now)) }
        },
    )
}
