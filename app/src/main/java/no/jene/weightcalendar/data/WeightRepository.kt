package no.jene.weightcalendar.data

import androidx.room.withTransaction
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// The repository holds the app's rules for reading and writing data.
// The screens talk to the repository, never directly to the database.
//
// Rule for estimated days: they are always the straight line between the nearest
// measured days. Every change below redraws that line around the changed date.
class WeightRepository(private val db: WeightDatabase) {

    private val dao = db.weightDao()

    val entries = dao.observeAll()

    // Save a measured weight (and optional note) for any day (today or in the past).
    // withTransaction = all the steps happen together, or none if something fails.
    suspend fun setWeight(date: LocalDate, weightKg: Double, note: String?) = db.withTransaction {
        dao.upsert(listOf(WeightEntry(date, weightKg, estimated = false, note = note)))
        refillAround(date)
    }

    // Remove a day. Between two measured days it comes back as an estimated day.
    suspend fun delete(date: LocalDate) = db.withTransaction {
        dao.delete(date)
        refillAround(date)
    }

    // Redraw the estimated days between the measured day before `date`, `date` itself
    // (if measured) and the measured day after. Also fills days that were missing.
    private suspend fun refillAround(date: LocalDate) {
        val before = dao.lastMeasuredBefore(date)
        val after = dao.firstMeasuredAfter(date)
        val current = dao.get(date)?.takeIf { !it.estimated }

        dao.deleteEstimatedBetween(before?.date ?: LocalDate.MIN, after?.date ?: LocalDate.MAX)
        val anchors = listOfNotNull(before, current, after)
        dao.upsert(anchors.zipWithNext { a, b -> interpolate(a, b) }.flatten())
    }

    // The days strictly between a and b, on a straight line, marked as estimated.
    private fun interpolate(a: WeightEntry, b: WeightEntry): List<WeightEntry> {
        val gap = ChronoUnit.DAYS.between(a.date, b.date)
        return (1 until gap).map { i ->
            val w = a.weightKg + (b.weightKg - a.weightKg) * i / gap
            WeightEntry(a.date.plusDays(i), w, estimated = true)
        }
    }

    // Read a CSV file in the format "date,weight_kg,estimated,note" (header line first)
    // and add or overwrite those days. Returns the number of days imported.
    // The note column is optional (files from version 1.3 and older don't have it).
    // Lines starting with # before the header are settings (see exportCsv); they are
    // applied only if all the days could be read. Older files have none: settings unchanged.
    // Also accepts what a French/Norwegian spreadsheet may save after editing:
    // "16/10/2026;109,6;0;..." (semicolon separator, decimal comma, day/month/year).
    suspend fun importCsv(input: InputStream, settings: Settings): Int {
        val rows = mutableListOf<WeightEntry>()
        val settingLines = mutableListOf<String>()
        var separator: Char? = null   // decided by the header line
        input.bufferedReader().useLines { lines ->
            for (line in lines) {
                val sep = separator
                when {
                    line.isBlank() -> {}
                    line.startsWith("#") -> settingLines.add(line)
                    // The header decides the separator (a note may contain the other one).
                    sep == null -> separator = if (';' in line) ';' else ','
                    else -> rows.add(parseRow(line, sep))
                }
            }
        }
        db.withTransaction { dao.upsert(rows) }  // all or nothing, and much faster
        settingLines.forEach { applySetting(it, settings) }
        return rows.size
    }

    private fun parseRow(line: String, separator: Char): WeightEntry {
        val fields = splitCsvLine(line, separator).map { it.trim() }
        return WeightEntry(
            date = parseDate(fields[0]),
            weightKg = fields[1].replace(',', '.').toDouble(),
            estimated = fields[2] == "1",
            note = fields.getOrNull(3)?.takeIf { it.isNotEmpty() },
        )
    }

    // "# goal_kg=100.0" -> settings.goalKg = 100.0. Unknown keys and bad values are ignored.
    // A spreadsheet may add empty cells (";;;") at the end, or write "100,0": both are fine.
    private fun applySetting(line: String, settings: Settings) {
        val parts = line.removePrefix("#").split('=', limit = 2)
        if (parts.size != 2) return
        val key = parts[0].trim()
        val value = parts[1].trim().trimEnd(',', ';').trim()
        when (key) {
            "goal_kg" -> settings.goalKg = value.replace(',', '.').toDoubleOrNull()   // empty = no goal
            "reminder_on" -> settings.reminderEnabled = value == "1"
            "reminder_time" -> runCatching { LocalTime.parse(value) }.onSuccess { settings.reminderTime = it }
        }
    }

    // "2026-10-16" (standard) or "16/10/2026" (European spreadsheet format).
    private fun parseDate(text: String): LocalDate =
        if ('/' in text) LocalDate.parse(text, DateTimeFormatter.ofPattern("d/M/yyyy"))
        else LocalDate.parse(text)

    // Split one CSV line into fields. A field in double quotes may contain the separator,
    // and "" inside quotes stands for one " (standard CSV, as spreadsheets write it).
    private fun splitCsvLine(line: String, separator: Char): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && line.getOrNull(i + 1) == '"' -> { current.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == separator && !inQuotes -> { fields.add(current.toString()); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }

    // Write the settings, then every day, to a CSV file, in the same format as the import:
    //   # Weight Calendar backup. Lines starting with # are settings.
    //   # goal_kg=100.0
    //   # reminder_on=1
    //   # reminder_time=09:00
    //   date,weight_kg,estimated,note
    //   2026-10-12,110.00,0,"Restaurant, pizza"
    // Returns the number of days exported.
    suspend fun exportCsv(output: OutputStream, settings: Settings): Int {
        val rows = dao.getAll()
        output.bufferedWriter().use { out ->
            out.write("# Weight Calendar backup. Lines starting with # are settings.\n")
            out.write("# goal_kg=${settings.goalKg?.let { "%.1f".format(Locale.ROOT, it) } ?: ""}\n")
            out.write("# reminder_on=${if (settings.reminderEnabled) 1 else 0}\n")
            out.write("# reminder_time=${settings.reminderTime}\n")
            out.write("date,weight_kg,estimated,note\n")
            for (e in rows) {
                // Locale.ROOT forces a dot as decimal separator: on a French phone,
                // "%.2f" alone would write "110,20" and break the comma-separated format.
                val weight = "%.2f".format(Locale.ROOT, e.weightKg)
                out.write("${e.date},$weight,${if (e.estimated) 1 else 0},${csvField(e.note ?: "")}\n")
            }
        }
        return rows.size
    }

    // A note with a comma, semicolon or quote goes in quotes, with " doubled (standard CSV).
    private fun csvField(text: String): String =
        if (text.any { it in ",;\"" }) "\"" + text.replace("\"", "\"\"") + "\"" else text
}
