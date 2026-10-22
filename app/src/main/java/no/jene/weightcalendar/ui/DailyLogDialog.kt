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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import no.jene.weightcalendar.R
import no.jene.weightcalendar.data.WeightEntry
import no.jene.weightcalendar.data.WeightUnit
import no.jene.weightcalendar.ui.theme.GainBig
import no.jene.weightcalendar.ui.theme.TextSecondary

// Pop-up to type, change or delete the weight of one day, with an optional note.
// `existing` is the day's current entry, if any: its weight is pre-filled, and a measured
// entry can be deleted (an estimated one can't: it would just be recalculated).
@Composable
fun DailyLogDialog(
    date: LocalDate,
    existing: WeightEntry?,
    onDismiss: () -> Unit,
    onSave: (weight: Double, note: String?) -> Unit,
    onDelete: () -> Unit,
) {
    // "remember { mutableStateOf(...) }" = a variable that survives redraws;
    // changing it makes Compose redraw the parts that use it.
    val unit = LocalWeightUnit.current
    var text by remember { mutableStateOf(existing?.let { "%.1f".format(unit.fromKg(it.weightKg)) } ?: "") }
    val weight = parseWeight(text, unit)   // in kg
    var note by remember { mutableStateOf(existing?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(DateTimeFormatter.ofPattern(stringResource(R.string.pattern_day_year)))) },
        text = {
            Column {
                if (existing?.estimated == true) {
                    Text(stringResource(R.string.estimated_day_hint),
                        color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.weight_label, unit.label)) },
                    singleLine = true,
                    isError = text.isNotEmpty() && weight == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = note,
                    // One line (a line break would also break the CSV export), at most 200 characters.
                    onValueChange = { note = it.replace('\n', ' ').take(200) },
                    label = { Text(stringResource(R.string.note_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { weight?.let { onSave(it, note.trim().ifEmpty { null }) } }, enabled = weight != null) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            if (existing != null && !existing.estimated) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = GainBig) }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

// Accepts "82.4" or "82,4" (the phone keyboard may use a comma) in the display unit,
// rounded to one decimal, and returns it in kg.
// Returns null for anything that is not a plausible body weight (20 to 400 kg).
// "internal" = visible everywhere in this app (the goal dialog uses it too).
//
// The kg value is rounded to 0.01: 242.5 lb is 109.9955... kg, stored as 110.00, which
// shows again as 242.5 lb. Without it, saving a day unchanged in lb would shift it a little.
internal fun parseWeight(text: String, unit: WeightUnit): Double? =
    text.replace(',', '.').toDoubleOrNull()
        ?.let { unit.toKg(Math.round(it * 10) / 10.0) }
        ?.let { Math.round(it * 100) / 100.0 }
        ?.takeIf { it in 20.0..400.0 }
