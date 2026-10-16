package no.jene.weightcalendar.data

import java.time.LocalDate

// One point of the smoothed curve, with the colour rule applied to its weekly change.
data class SmoothedPoint(val date: LocalDate, val weightKg: Double, val trend: Trend)

// Centered 7-day moving average, like the spreadsheet's "Week Avg": the mean of the day
// and of the 3 days on each side. Near the latest day fewer days exist, so the window
// shrinks (the last 3 points still move a little as new days are logged).
//
// Colour: change of the average over one week (today's average minus the one 7 days
// earlier), with the same thresholds as the daily rule but in kg/week:
// blue ≤ -0.5, green ≤ 0, yellow < +0.5, red ≥ +0.5, grey when there is no week before.
fun Map<LocalDate, Day>.weeklyAverage(): List<SmoothedPoint> {
    val average = keys.associateWith { date ->
        (-3L..3L).mapNotNull { this[date.plusDays(it)]?.entry?.weightKg }.average()
    }
    return average.map { (date, kg) ->
        val weekBefore = average[date.minusDays(7)]
        val change = weekBefore?.let { Math.round((kg - it) * 100) / 100.0 }  // see toDays()
        SmoothedPoint(date, kg, trendOf(change))
    }
}
