package no.jene.weightcalendar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import no.jene.weightcalendar.data.WeightEntry
import no.jene.weightcalendar.ui.theme.GainBig
import no.jene.weightcalendar.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Pop-up to type, change or delete the weight of one day.
// `existing` is the day's current entry, if any: its weight is pre-filled, and a measured
// entry can be deleted (an estimated one can't: it would just be recalculated).
@Composable
fun DailyLogDialog(
    date: LocalDate,
    existing: WeightEntry?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onDelete: () -> Unit,
) {
    // "remember { mutableStateOf(...) }" = a variable that survives redraws;
    // changing it makes Compose redraw the parts that use it.
    var text by remember { mutableStateOf(existing?.let { "%.1f".format(it.weightKg) } ?: "") }
    val weight = parseWeight(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"))) },
        text = {
            Column {
                if (existing?.estimated == true) {
                    Text("Estimated day. Saving makes it a measured day.",
                        color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    isError = text.isNotEmpty() && weight == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { weight?.let(onSave) }, enabled = weight != null) { Text("SAVE") }
        },
        dismissButton = {
            if (existing != null && !existing.estimated) {
                TextButton(onClick = onDelete) { Text("DELETE", color = GainBig) }
            }
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        },
    )
}

// Accepts "82.4" or "82,4" (the phone keyboard may use a comma), rounded to one decimal.
// Returns null for anything that is not a plausible body weight.
// "internal" = visible everywhere in this app (the goal dialog uses it too).
internal fun parseWeight(text: String): Double? =
    text.replace(',', '.').toDoubleOrNull()
        ?.takeIf { it in 20.0..400.0 }
        ?.let { Math.round(it * 10) / 10.0 }
