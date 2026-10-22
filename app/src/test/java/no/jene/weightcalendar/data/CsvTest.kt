package no.jene.weightcalendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CsvTest {

    private fun read(text: String) = Csv.read(text.trimIndent().lineSequence())

    @Test
    fun writeThenReadGivesTheSameData() {
        val entries = listOf(
            WeightEntry(LocalDate.of(2026, 10, 11), 110.6),
            WeightEntry(LocalDate.of(2026, 10, 12), 110.35, estimated = true),
            WeightEntry(LocalDate.of(2026, 10, 13), 110.1, note = "Restaurant, pizza; \"heavy\""),
        )
        val text = StringBuilder()
        Csv.write(text, entries, listOf("goal_kg" to "100.0", "unit" to "lb"))
        val back = Csv.read(text.lineSequence())
        assertEquals(entries, back.entries)
        assertEquals(mapOf("goal_kg" to "100.0", "unit" to "lb"), back.settings)
    }

    // Version 1.0-1.3 files: no settings, no note column.
    @Test
    fun readsOldFormat() {
        val content = read("""
            date,weight_kg,estimated
            2021-03-15,104.40,0
            2021-03-16,104.20,1
        """)
        assertEquals(2, content.entries.size)
        assertEquals(true, content.entries[1].estimated)
        assertNull(content.entries[0].note)
        assertEquals(emptyMap<String, String>(), content.settings)
    }

    // What a French spreadsheet saves: ";" separator, decimal comma, day/month/year,
    // empty cells added at the end of the setting lines.
    @Test
    fun readsSpreadsheetFormat() {
        val content = read("""
            # goal_kg=98,5;;;
            # reminder_on=0;;;
            date;weight_kg;estimated;note
            11/10/2026;110,60;0;"Il a dit ""ok""; fini"
        """)
        assertEquals("98,5", content.settings["goal_kg"])
        assertEquals("0", content.settings["reminder_on"])
        val e = content.entries.single()
        assertEquals(LocalDate.of(2026, 10, 11), e.date)
        assertEquals(110.6, e.weightKg, 1e-9)
        assertEquals("Il a dit \"ok\"; fini", e.note)
    }

    @Test
    fun splitLineHandlesQuotes() {
        assertEquals(listOf("a", "b,c", "d"), Csv.splitLine("a,\"b,c\",d", ','))
        assertEquals(listOf("x", "say \"hi\""), Csv.splitLine("x;\"say \"\"hi\"\"\"", ';'))
        assertEquals(listOf("1", ""), Csv.splitLine("1,", ','))
    }

    @Test(expected = Exception::class)
    fun badWeightThrows() {
        read("""
            date,weight_kg,estimated
            2026-10-11,abc,0
        """)
    }
}
