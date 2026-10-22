package no.jene.weightcalendar.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import no.jene.weightcalendar.R
import no.jene.weightcalendar.ui.theme.GainBig

// Pop-up to set, change or remove the weight goal. Same layout as the daily log dialog.
@Composable
fun GoalDialog(
    current: Double?,
    onDismiss: () -> Unit,
    onSave: (Double?) -> Unit,   // null = remove the goal
) {
    val unit = LocalWeightUnit.current
    var text by remember { mutableStateOf(current?.let { "%.1f".format(unit.fromKg(it)) } ?: "") }
    val weight = parseWeight(text, unit)   // in kg

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.goal_label, unit.label)) },
                singleLine = true,
                isError = text.isNotEmpty() && weight == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(weight) }, enabled = weight != null) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            if (current != null) {
                TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.remove), color = GainBig) }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
