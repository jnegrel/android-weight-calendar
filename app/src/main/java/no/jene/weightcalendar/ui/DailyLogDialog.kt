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
import androidx.compose.ui.text.input.KeyboardType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Pop-up to type today's weight.
@Composable
fun DailyLogDialog(onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    // "remember { mutableStateOf(...) }" = a variable that survives redraws;
    // changing it makes Compose redraw the parts that use it.
    var text by remember { mutableStateOf("") }
    val weight = parseWeight(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM"))) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Weight (kg)") },
                singleLine = true,
                isError = text.isNotEmpty() && weight == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = { weight?.let(onSave) }, enabled = weight != null) { Text("SAVE") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

// Accepts "82.4" or "82,4" (the phone keyboard may use a comma), rounded to one decimal.
// Returns null for anything that is not a plausible body weight.
private fun parseWeight(text: String): Double? =
    text.replace(',', '.').toDoubleOrNull()
        ?.takeIf { it in 20.0..400.0 }
        ?.let { Math.round(it * 10) / 10.0 }
