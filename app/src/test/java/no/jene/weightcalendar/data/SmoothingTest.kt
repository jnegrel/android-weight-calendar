package no.jene.weightcalendar.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SmoothingTest {

    private val start = LocalDate.of(2026, 1, 1)

    // `kg(i)` = the weight of day i, for `count` days in a row.
    private fun days(count: Int, kg: (Int) -> Double) =
        (0 until count).map { WeightEntry(start.plusDays(it.toLong()), kg(it)) }.toDays()

    @Test
    fun constantWeightGivesSameAverage() {
        val avg = days(20) { 80.0 }.weeklyAverage()
        avg.forEach { assertEquals(80.0, it.weightKg, 1e-9) }
    }

    // On a straight line, a centred average gives back the line itself (away from the ends).
    @Test
    fun centredAverageOfALine() {
        val avg = days(30) { 100.0 - 0.1 * it }.weeklyAverage().associateBy { it.date }
        assertEquals(100.0 - 0.1 * 10, avg[start.plusDays(10)]!!.weightKg, 1e-9)
    }

    // At the last day only 4 days exist (the day and the 3 before): the window shrinks.
    @Test
    fun windowShrinksAtTheEnd() {
        val avg = days(10) { it.toDouble() }.weeklyAverage().associateBy { it.date }
        assertEquals((6 + 7 + 8 + 9) / 4.0, avg[start.plusDays(9)]!!.weightKg, 1e-9)
    }

    // -0.1 kg/day = -0.7 kg/week: blue for both curves (monthly change converted per week).
    @Test
    fun trendColourUsesChangePerWeek() {
        val d = days(80) { 100.0 - 0.1 * it }
        val day = start.plusDays(40)
        assertEquals(Trend.BIG_LOSS, d.weeklyAverage().first { it.date == day }.trend)
        assertEquals(Trend.BIG_LOSS, d.monthlyAverage().first { it.date == day }.trend)
        // Too early for a week before: grey.
        assertEquals(Trend.FIRST, d.weeklyAverage().first { it.date == start.plusDays(3) }.trend)
    }
}
