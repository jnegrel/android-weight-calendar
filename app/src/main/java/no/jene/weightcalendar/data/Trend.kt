package no.jene.weightcalendar.data

import java.time.LocalDate

// How the weight changed compared with the previous day. Drives the colours of the
// calendar and of the graph.
enum class Trend { BIG_LOSS, SMALL_LOSS, SMALL_GAIN, BIG_GAIN, FIRST }

// One calendar day: its entry plus the change since the previous entry.
data class Day(val entry: WeightEntry, val delta: Double?, val trend: Trend)

//  Δ ≤ -0.5        BIG_LOSS   (blue)
//  -0.5 < Δ ≤ 0    SMALL_LOSS (green)
//  0 < Δ < +0.5    SMALL_GAIN (yellow)
//  Δ ≥ +0.5        BIG_GAIN   (red)
//  no previous day FIRST      (grey)
fun trendOf(delta: Double?): Trend = when {
    delta == null -> Trend.FIRST
    delta <= -0.5 -> Trend.BIG_LOSS
    delta <= 0.0 -> Trend.SMALL_LOSS
    delta < 0.5 -> Trend.SMALL_GAIN
    else -> Trend.BIG_GAIN
}

// Turns the list of entries (oldest first) into a lookup table date -> Day.
fun List<WeightEntry>.toDays(): Map<LocalDate, Day> {
    var previous: WeightEntry? = null
    return associate { entry ->
        // Round to 0.01 kg: computers store decimals approximately, so 109.7 - 110.2 gives
        // -0.49999999999, which would wrongly count as a small loss instead of -0.5.
        val delta = previous?.let { Math.round((entry.weightKg - it.weightKg) * 100) / 100.0 }
        previous = entry
        entry.date to Day(entry, delta, trendOf(delta))
    }
}
