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
fun Map<LocalDate, Day>.weeklyAverage(): List<SmoothedPoint> = movingAverage(halfWidth = 3, changeDays = 7)

// Same idea over about a month (the day and 15 days on each side), for the long graphs.
// Its colour uses the change over 30 days, converted to kg/week so that the thresholds
// mean the same thing as for the weekly curve (0.5 kg/week ≈ 2.1 kg/month).
fun Map<LocalDate, Day>.monthlyAverage(): List<SmoothedPoint> = movingAverage(halfWidth = 15, changeDays = 30)

// The average of each day and the `halfWidth` days on each side. Its colour comes from
// the change since the average `changeDays` earlier, expressed in kg per week.
private fun Map<LocalDate, Day>.movingAverage(halfWidth: Long, changeDays: Long): List<SmoothedPoint> {
    val average = keys.associateWith { date ->
        (-halfWidth..halfWidth).mapNotNull { this[date.plusDays(it)]?.entry?.weightKg }.average()
    }
    return average.map { (date, kg) ->
        val before = average[date.minusDays(changeDays)]
        val perWeek = before?.let { Math.round((kg - it) * 7 / changeDays * 100) / 100.0 }  // see toDays()
        SmoothedPoint(date, kg, trendOf(perWeek))
    }
}
