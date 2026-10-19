package no.jene.weightcalendar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalTime

// Pop-up to turn the daily reminder on/off and choose its time.
// @OptIn: Material3's time picker is still marked "experimental" (its API may change).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(
    enabled: Boolean,
    time: LocalTime,
    onDismiss: () -> Unit,
    onSave: (enabled: Boolean, time: LocalTime) -> Unit,
) {
    var on by remember { mutableStateOf(enabled) }
    val timeState = rememberTimePickerState(time.hour, time.minute, is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily reminder") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Notify me if today's weight is not logged", Modifier.weight(1f))
                    Switch(checked = on, onCheckedChange = { on = it })
                }
                if (on) {
                    // TimeInput: two small hour/minute fields (the clock-face picker is too big here).
                    TimeInput(timeState, Modifier.padding(top = 16.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(on, LocalTime.of(timeState.hour, timeState.minute)) }) { Text("SAVE") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        },
    )
}
