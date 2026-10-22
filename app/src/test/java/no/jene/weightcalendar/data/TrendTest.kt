package no.jene.weightcalendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

// The colour rule: Δ ≤ -0.5 blue, -0.5 < Δ ≤ 0 green, 0 < Δ < 0.5 yellow, Δ ≥ 0.5 red.
// Each @Test function is one check; a failing assert stops it and reports the values.
class TrendTest {

    @Test
    fun thresholds() {
        assertEquals(Trend.BIG_LOSS, trendOf(-1.0))
        assertEquals(Trend.BIG_LOSS, trendOf(-0.5))     // -0.5 included in blue
        assertEquals(Trend.SMALL_LOSS, trendOf(-0.4))
        assertEquals(Trend.SMALL_LOSS, trendOf(0.0))    // no change counts as green
        assertEquals(Trend.SMALL_GAIN, trendOf(0.1))
        assertEquals(Trend.SMALL_GAIN, trendOf(0.4))
        assertEquals(Trend.BIG_GAIN, trendOf(0.5))      // +0.5 included in red
        assertEquals(Trend.FIRST, trendOf(null))
    }

    @Test
    fun firstDayIsGreyAndDeltasUsePreviousDay() {
        val d = LocalDate.of(2026, 1, 1)
        val days = listOf(
            WeightEntry(d, 100.0),
            WeightEntry(d.plusDays(1), 99.5),
            WeightEntry(d.plusDays(2), 99.6),
        ).toDays()
        assertNull(days[d]!!.delta)
        assertEquals(Trend.FIRST, days[d]!!.trend)
        assertEquals(-0.5, days[d.plusDays(1)]!!.delta!!, 1e-9)
        assertEquals(Trend.BIG_LOSS, days[d.plusDays(1)]!!.trend)
        assertEquals(Trend.SMALL_GAIN, days[d.plusDays(2)]!!.trend)
    }

    // 64.1 - 63.6 is 0.4999999999999929 in floating point: without rounding, a +0.5 kg day
    // would be yellow instead of red, and the way back green instead of blue.
    @Test
    fun floatingPointNoiseIsRoundedAway() {
        val d = LocalDate.of(2026, 1, 1)
        val days = listOf(
            WeightEntry(d, 63.6), WeightEntry(d.plusDays(1), 64.1), WeightEntry(d.plusDays(2), 63.6),
        ).toDays()
        assertEquals(Trend.BIG_GAIN, days[d.plusDays(1)]!!.trend)
        assertEquals(Trend.BIG_LOSS, days[d.plusDays(2)]!!.trend)
    }
}
