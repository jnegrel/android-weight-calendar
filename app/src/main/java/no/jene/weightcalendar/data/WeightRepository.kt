package no.jene.weightcalendar.data

import androidx.room.withTransaction
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalTime
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

    // Read a CSV backup (format in Csv.kt) and add or overwrite those days.
    // Returns the number of days imported. The settings it contains are applied only if
    // all the days could be read; older files have none and leave the settings unchanged.
    suspend fun importCsv(input: InputStream, settings: Settings): Int {
        val content = input.bufferedReader().useLines { Csv.read(it) }
        db.withTransaction { dao.upsert(content.entries) }  // all or nothing, and much faster
        content.settings.forEach { (key, value) -> applySetting(key, value, settings) }
        return content.entries.size
    }

    // Unknown keys and bad values are ignored. "100,0" (decimal comma) is accepted.
    private fun applySetting(key: String, value: String, settings: Settings) {
        when (key) {
            "goal_kg" -> settings.goalKg = value.replace(',', '.').toDoubleOrNull()   // empty = no goal
            "reminder_on" -> settings.reminderEnabled = value == "1"
            "reminder_time" -> runCatching { LocalTime.parse(value) }.onSuccess { settings.reminderTime = it }
            "unit" -> WeightUnit.entries.find { it.label == value }?.let { settings.unit = it }
        }
    }

    // Write the settings and every day as a CSV backup. Returns the number of days exported.
    suspend fun exportCsv(output: OutputStream, settings: Settings): Int {
        val rows = dao.getAll()
        val settingLines = listOf(
            "goal_kg" to (settings.goalKg?.let { "%.1f".format(Locale.ROOT, it) } ?: ""),
            "reminder_on" to (if (settings.reminderEnabled) "1" else "0"),
            "reminder_time" to settings.reminderTime.toString(),
            "unit" to settings.unit.label,
        )
        output.bufferedWriter().use { Csv.write(it, rows, settingLines) }
        return rows.size
    }
}
