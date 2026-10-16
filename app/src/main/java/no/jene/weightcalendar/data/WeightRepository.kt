package no.jene.weightcalendar.data

import androidx.room.withTransaction
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
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

    // Save a measured weight for any day (today or in the past).
    // withTransaction = all the steps happen together, or none if something fails.
    suspend fun setWeight(date: LocalDate, weightKg: Double) = db.withTransaction {
        dao.upsert(listOf(WeightEntry(date, weightKg, estimated = false)))
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

    // Read a CSV file in the format "date,weight_kg,estimated" (header line first)
    // and add or overwrite those days. Returns the number of days imported.
    // Also accepts what a French/Norwegian spreadsheet may save after editing:
    // "16/10/2026;109,6;0" (semicolon separator, decimal comma, day/month/year).
    suspend fun importCsv(input: InputStream): Int {
        val rows = input.bufferedReader().useLines { lines ->
            lines.drop(1)                        // skip the header
                .filter { it.isNotBlank() }
                .map { line ->
                    val separator = if (';' in line) ';' else ','
                    val (date, weight, estimated) = line.split(separator).map { it.trim() }
                    WeightEntry(parseDate(date), weight.replace(',', '.').toDouble(), estimated == "1")
                }
                .toList()
        }
        db.withTransaction { dao.upsert(rows) }  // all or nothing, and much faster
        return rows.size
    }

    // "2026-10-16" (standard) or "16/10/2026" (European spreadsheet format).
    private fun parseDate(text: String): LocalDate =
        if ('/' in text) LocalDate.parse(text, DateTimeFormatter.ofPattern("d/M/yyyy"))
        else LocalDate.parse(text)

    // Write every day to a CSV file, in the same format as the import.
    // Returns the number of days exported.
    suspend fun exportCsv(output: OutputStream): Int {
        val rows = dao.getAll()
        output.bufferedWriter().use { out ->
            out.write("date,weight_kg,estimated\n")
            for (e in rows) {
                // Locale.ROOT forces a dot as decimal separator: on a French phone,
                // "%.2f" alone would write "110,20" and break the comma-separated format.
                out.write("${e.date},${"%.2f".format(Locale.ROOT, e.weightKg)},${if (e.estimated) 1 else 0}\n")
            }
        }
        return rows.size
    }
}
