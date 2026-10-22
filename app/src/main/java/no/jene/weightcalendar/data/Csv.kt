package no.jene.weightcalendar.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// The CSV backup format, read and written without any database or Android code, so that
// it can be checked by fast tests on the computer (app/src/test/.../CsvTest.kt).
//
//   # Weight Calendar backup. Lines starting with # are settings.
//   # goal_kg=100.0
//   # reminder_on=1
//   # reminder_time=09:00
//   # unit=kg
//   date,weight_kg,estimated,note
//   2026-10-12,110.00,0,"Restaurant, pizza"
//
// Reading also accepts what a French/Norwegian spreadsheet may save after editing:
// "16/10/2026;109,6;0;..." (semicolon separator, decimal comma, day/month/year),
// files without settings or without the note column (versions 1.3 and older).
object Csv {

    // What a file contains: the days, and the settings as key -> value text.
    data class Content(val entries: List<WeightEntry>, val settings: Map<String, String>)

    // Throws if a day line can't be read (bad date or weight).
    fun read(lines: Sequence<String>): Content {
        val entries = mutableListOf<WeightEntry>()
        val settings = mutableMapOf<String, String>()
        var separator: Char? = null   // decided by the header line
        for (line in lines) {
            val sep = separator
            when {
                line.isBlank() -> {}
                line.startsWith("#") -> readSetting(line)?.let { (key, value) -> settings[key] = value }
                // The header decides the separator (a note may contain the other one).
                sep == null -> separator = if (';' in line) ';' else ','
                else -> entries.add(readRow(line, sep))
            }
        }
        return Content(entries, settings)
    }

    // `settings`: key -> value, written as "# key=value" lines before the header.
    fun write(out: Appendable, entries: List<WeightEntry>, settings: List<Pair<String, String>>) {
        out.append("# Weight Calendar backup. Lines starting with # are settings.\n")
        for ((key, value) in settings) out.append("# $key=$value\n")
        out.append("date,weight_kg,estimated,note\n")
        for (e in entries) {
            // Locale.ROOT forces a dot as decimal separator: on a French phone,
            // "%.2f" alone would write "110,20" and break the comma-separated format.
            val weight = "%.2f".format(Locale.ROOT, e.weightKg)
            out.append("${e.date},$weight,${if (e.estimated) 1 else 0},${field(e.note ?: "")}\n")
        }
    }

    private fun readRow(line: String, separator: Char): WeightEntry {
        val fields = splitLine(line, separator).map { it.trim() }
        return WeightEntry(
            date = parseDate(fields[0]),
            weightKg = fields[1].replace(',', '.').toDouble(),
            estimated = fields[2] == "1",
            note = fields.getOrNull(3)?.takeIf { it.isNotEmpty() },
        )
    }

    // "# goal_kg=100.0" -> ("goal_kg", "100.0"). A spreadsheet may add empty cells (";;;")
    // at the end of the line: they are removed. Lines without "=" are comments: null.
    private fun readSetting(line: String): Pair<String, String>? {
        val parts = line.removePrefix("#").split('=', limit = 2)
        if (parts.size != 2) return null
        return parts[0].trim() to parts[1].trim().trimEnd(',', ';').trim()
    }

    // "2026-10-16" (standard) or "16/10/2026" (European spreadsheet format).
    private fun parseDate(text: String): LocalDate =
        if ('/' in text) LocalDate.parse(text, DateTimeFormatter.ofPattern("d/M/yyyy"))
        else LocalDate.parse(text)

    // Split one CSV line into fields. A field in double quotes may contain the separator,
    // and "" inside quotes stands for one " (standard CSV, as spreadsheets write it).
    // "internal": visible to the tests.
    internal fun splitLine(line: String, separator: Char): List<String> {
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

    // A text with a comma, semicolon or quote goes in quotes, with " doubled.
    internal fun field(text: String): String =
        if (text.any { it in ",;\"" }) "\"" + text.replace("\"", "\"\"") + "\"" else text
}
