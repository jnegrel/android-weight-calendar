package no.jene.weightcalendar.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sign
import no.jene.weightcalendar.R
import no.jene.weightcalendar.data.Day
import no.jene.weightcalendar.data.SmoothedPoint
import no.jene.weightcalendar.data.WeightUnit
import no.jene.weightcalendar.data.monthlyAverage
import no.jene.weightcalendar.data.trendOf
import no.jene.weightcalendar.data.weeklyAverage
import no.jene.weightcalendar.ui.theme.AccentBlue
import no.jene.weightcalendar.ui.theme.DailyLine
import no.jene.weightcalendar.ui.theme.GoalLine
import no.jene.weightcalendar.ui.theme.Surface
import no.jene.weightcalendar.ui.theme.TextPrimary
import no.jene.weightcalendar.ui.theme.TextSecondary

// The periods the graph can show, in the order of the selector under the graph.
// Like tabs: swiping left goes to the next one (longer period), swiping right to the previous.
// `years = null` means everything since the first logged day.
// `label` is a text resource (R.string...): the short name differs between languages.
enum class Period(@StringRes val label: Int, val years: Long?) {
    ONE_YEAR(R.string.period_1y, 1), TWO_YEARS(R.string.period_2y, 2),
    FIVE_YEARS(R.string.period_5y, 5), ALL(R.string.period_all, null)
}

// Weight graph over the chosen period: a thin light-grey line for the details, and on top
// a smoothed curve coloured by its trend (see Smoothing.kt).
//   1Y: daily weights in grey, 7-day average in colour.
//   2Y: daily weights in grey, 30-day average in colour.
//   5Y and All: 7-day average in grey (daily weights are an unreadable band), 30-day in colour.
// Missing days leave a hole in both lines.
@Composable
// `end` is the last day shown (it follows the month displayed in the calendar).
// `goal` (kg, optional) is drawn as a dashed line, labelled with the estimated date it is reached.
fun WeightGraph(days: Map<LocalDate, Day>, end: LocalDate, goal: Double?, modifier: Modifier = Modifier) {
    var period by rememberSaveable { mutableStateOf(Period.ONE_YEAR) }
    // The day before the first one shown (the graph starts just after it).
    // With less data than the period (a new user), the graph starts at the first weight
    // instead of leaving the year empty, but always shows at least a week.
    val dataStart = days.keys.minOrNull()?.let { minOf(it.minusDays(1), end.minusDays(7)) }
    val years = period.years
    val start = when {
        dataStart == null -> end.minusYears(years ?: 1)          // no data at all
        years == null -> dataStart                               // All: from the first weight
        else -> maxOf(end.minusYears(years), dataStart)          // 1Y/2Y/5Y, or less if less data
    }
    fun inWindow(date: LocalDate) = date > start && date <= end
    // remember(...): only recompute when the data (or the window) changes, not on every redraw.
    val points = remember(days, start, end) { days.values.filter { inWindow(it.entry.date) } }
    // Computed on all days, then cut, so the first points are full averages.
    val weekly = remember(days) { days.weeklyAverage() }
    val monthly = remember(days) { days.monthlyAverage() }
    val smoothed = remember(weekly, monthly, period, start, end) {
        (if (period == Period.ONE_YEAR) weekly else monthly).filter { inWindow(it.date) }
    }
    val grey = remember(points, weekly, period, start, end) {
        if (period == Period.ONE_YEAR || period == Period.TWO_YEARS) {
            points.map { SmoothedPoint(it.entry.date, it.entry.weightKg, it.trend) }
        } else {
            weekly.filter { inWindow(it.date) }
        }
    }

    fun next() { period = Period.entries.getOrElse(period.ordinal + 1) { period } }
    fun previous() { period = Period.entries.getOrElse(period.ordinal - 1) { period } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            // Same swipe detection as the calendar.
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > 100) previous() else if (total < -100) next() },
                ) { _, dragAmount -> total += dragAmount }
            },
    ) {
        val weeklyToEnd = weekly.filter { it.date <= end }
        val unit = LocalWeightUnit.current
        GraphHeader(points, weeklyToEnd, start, end, unit)
        if (points.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                // No data at all (first use): say how to start. Otherwise only this period is empty.
                val message = if (days.isEmpty()) stringResource(R.string.no_data_yet, stringResource(R.string.daily_log))
                    else stringResource(R.string.no_data_period)
                Text(message, color = TextSecondary, textAlign = TextAlign.Center)
            }
        } else {
            val textMeasurer = rememberTextMeasurer()
            // The estimate only makes sense from the latest data, not from a past month.
            // (Computed here and not in the Canvas below: drawing code can't read texts.)
            val isLatest = end == days.keys.lastOrNull()
            val goalLabel = goal?.let { goalLabel(it, if (isLatest) weeklyToEnd else emptyList(), unit) }
            Canvas(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
                drawGraph(grey, smoothed, start, end, goal, goalLabel, unit, textMeasurer)
            }
        }
        PeriodSelector(period, onSelect = { period = it })
    }
}

// "1Y  2Y  5Y  All" under the graph: shows where you are, and can also be tapped.
@Composable
private fun PeriodSelector(selected: Period, onSelect: (Period) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        for (p in Period.entries) {
            val isSelected = p == selected
            Text(
                stringResource(p.label),
                color = if (isSelected) TextPrimary else TextSecondary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) AccentBlue.copy(alpha = 0.3f) else Color.Transparent)
                    .clickable { onSelect(p) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

// "Average                             17 Oct 2025 – 16 Oct 2026
//  110.3 kg                                   Last 30 days: -0.8 kg"
// The 30-day change always comes from the weekly curve, whatever the period shown.
@Composable
private fun GraphHeader(
    points: List<Day>, trend: List<SmoothedPoint>, start: LocalDate, end: LocalDate, unit: WeightUnit,
) {
    val format = DateTimeFormatter.ofPattern(stringResource(R.string.pattern_date))
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.average), color = TextSecondary, fontSize = 14.sp)
            val average = points.map { it.entry.weightKg }.average()
            Text(if (points.isEmpty()) "–" else unit.format(average), color = TextPrimary, fontSize = 26.sp)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 4.dp)) {
            Text("${start.plusDays(1).format(format)} – ${end.format(format)}", color = TextSecondary, fontSize = 13.sp)
            monthlyChange(trend)?.let { change ->
                // Coloured with the weekly rule, using the equivalent change per week.
                val color = trendOf(change * 7 / MONTH_DAYS).color()
                Text(stringResource(R.string.last_30_days, unit.formatChange(change)), color = color, fontSize = 15.sp)
            }
        }
    }
}

private const val MONTH_DAYS = 30L

// Change of the 7-day average between its last point (the graph's end) and 30 days earlier,
// or null if there is not enough data.
private fun monthlyChange(trend: List<SmoothedPoint>): Double? {
    val last = trend.lastOrNull() ?: return null
    val monthBefore = trend.find { it.date == last.date.minusDays(MONTH_DAYS) } ?: return null
    return last.weightKg - monthBefore.weightKg
}

// "Goal 100.0 kg · ~Mar 2027": the date when the 7-day average reaches the goal if the
// change of the last 30 days goes on at the same pace. Without a date when there is no
// recent trend, or when the weight is moving away from the goal.
// @Composable only to read the texts with stringResource.
@Composable
private fun goalLabel(goal: Double, trend: List<SmoothedPoint>, unit: WeightUnit): String {
    val label = stringResource(R.string.goal_graph, unit.format(goal))
    val reached = stringResource(R.string.goal_reached)
    val far = stringResource(R.string.goal_far)
    val last = trend.lastOrNull() ?: return label
    val remaining = goal - last.weightKg
    if (abs(remaining) < 0.2) return "$label · $reached"
    val perDay = (monthlyChange(trend) ?: return label) / MONTH_DAYS
    if (perDay == 0.0 || sign(perDay) != sign(remaining)) return label   // wrong way
    val daysLeft = (remaining / perDay).toLong()
    if (daysLeft > 5 * 365) return "$label · $far"
    return "$label · ~" + last.date.plusDays(daysLeft).format(DateTimeFormatter.ofPattern("MMM yyyy"))
}

// Everything below runs inside the Canvas. Coordinates are in pixels, (0,0) = top-left,
// and y grows downwards, so a heavier weight must get a *smaller* y.
private fun DrawScope.drawGraph(
    grey: List<SmoothedPoint>, trend: List<SmoothedPoint>, start: LocalDate, end: LocalDate,
    goal: Double?, goalLabel: String?, unit: WeightUnit, textMeasurer: TextMeasurer,
) {
    val labelStyle = TextStyle(color = TextSecondary, fontSize = 11.sp)
    val left = 34.dp.toPx()                  // room for the kg labels
    val bottom = size.height - 20.dp.toPx()  // room for the month labels
    val width = size.width - left

    // Vertical range: lowest - 2 kg to highest + 2 kg of the grey line, widened to at least 5 kg.
    val weights = grey.map { it.weightKg }
    var low = weights.min() - 2
    var high = weights.max() + 2
    if (high - low < 5) {
        val middle = (low + high) / 2
        low = middle - 2.5
        high = middle + 2.5
    }

    // Conversions from data (date, kg) to pixels.
    val windowDays = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1)
    fun x(date: LocalDate) = left + width * ChronoUnit.DAYS.between(start, date) / windowDays
    // The axis is drawn in the display unit: yUnit() takes a value in kg or lb,
    // y() takes kg (the data) and converts it first.
    val lowUnit = unit.fromKg(low)
    val highUnit = unit.fromKg(high)
    fun yUnit(value: Double) = (bottom * (highUnit - value) / (highUnit - lowUnit)).toFloat()
    fun y(kg: Double) = yUnit(unit.fromKg(kg))

    // Horizontal grid lines with labels: the smallest step (1, 2, 5, 10 or 20 kg or lb) that
    // keeps the labels at least 20 dp apart, so they stay readable however small the graph is.
    val step = listOf(1, 2, 5, 10).firstOrNull { yUnit(lowUnit) - yUnit(lowUnit + it) >= 20.dp.toPx() } ?: 20
    var value = ceil(lowUnit / step) * step
    while (value <= highUnit) {
        val yPos = yUnit(value)
        drawLine(TextSecondary.copy(alpha = 0.15f), Offset(left, yPos), Offset(size.width, yPos), 1.dp.toPx())
        val label = textMeasurer.measure("%.0f".format(value), labelStyle)
        drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), yPos - label.size.height / 2))
        value += step
    }

    // Date labels with a small tick mark, spaced so they don't overlap: every month for a
    // few months, every 3 months for one year (Jan, Apr, Jul, Oct), 6 for two, else every January.
    val (monthStep, monthFormat) = when {
        windowDays <= 120 -> 1 to DateTimeFormatter.ofPattern("MMM yyyy")
        windowDays <= 400 -> 3 to DateTimeFormatter.ofPattern("MMM yyyy")
        windowDays <= 800 -> 6 to DateTimeFormatter.ofPattern("MMM yyyy")
        else -> 12 to DateTimeFormatter.ofPattern("yyyy")
    }
    var month = YearMonth.from(start).plusMonths(1)
    while (month.atDay(1) <= end) {
        if ((month.monthValue - 1) % monthStep == 0) {
            val xPos = x(month.atDay(1))
            drawLine(TextSecondary, Offset(xPos, bottom), Offset(xPos, bottom + 3.dp.toPx()), 1.dp.toPx())
            val label = textMeasurer.measure(month.format(monthFormat), labelStyle)
            val labelX = (xPos - label.size.width / 2).coerceIn(left, size.width - label.size.width)
            drawText(label, topLeft = Offset(labelX, bottom + 4.dp.toPx()))
        }
        month = month.plusMonths(1)
    }

    // Thin grey line, one short segment per pair of consecutive days.
    for ((previous, current) in grey.zipWithNext()) {
        if (ChronoUnit.DAYS.between(previous.date, current.date) != 1L) continue  // hole
        drawLine(
            color = DailyLine,
            start = Offset(x(previous.date), y(previous.weightKg)),
            end = Offset(x(current.date), y(current.weightKg)),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    // A day with no day just before or after it (like the very first weight) has no
    // segment to draw: show it as a small dot instead, so it doesn't stay invisible.
    val greyDates = grey.map { it.date }.toSet()
    for (point in grey) {
        if (point.date.minusDays(1) !in greyDates && point.date.plusDays(1) !in greyDates) {
            drawCircle(DailyLine, radius = 2.5.dp.toPx(), center = Offset(x(point.date), y(point.weightKg)))
        }
    }

    // Smoothed curve on top: thicker, each segment coloured by its trend.
    for ((previous, current) in trend.zipWithNext()) {
        if (ChronoUnit.DAYS.between(previous.date, current.date) != 1L) continue
        drawLine(
            color = current.trend.color(),
            start = Offset(x(previous.date), y(previous.weightKg)),
            end = Offset(x(current.date), y(current.weightKg)),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    // Weight goal: dashed line with its label above the right end. The range is not widened
    // for it (a far goal would squash the curves): when it is outside, only the label is
    // shown at the top or bottom edge, with an arrow.
    if (goal != null && goalLabel != null) {
        val inside = goal in low..high
        val text = when {
            inside -> goalLabel
            goal > high -> "↑ $goalLabel"
            else -> "↓ $goalLabel"
        }
        val label = textMeasurer.measure(text, labelStyle.copy(color = GoalLine))
        val yGoal = y(goal.coerceIn(low, high))
        if (inside) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            drawLine(GoalLine, Offset(left, yGoal), Offset(size.width, yGoal), 1.5.dp.toPx(), pathEffect = dash)
        }
        // Above the line, unless that would go off the top of the graph.
        val labelY = (yGoal - label.size.height - 2.dp.toPx()).let { if (it < 0) yGoal + 2.dp.toPx() else it }
        val topLeft = Offset(size.width - label.size.width - 4.dp.toPx(), labelY)
        // A small background so the text stays readable over the curves.
        drawRoundRect(
            Surface.copy(alpha = 0.85f), topLeft - Offset(4.dp.toPx(), 0f),
            Size(label.size.width + 8.dp.toPx(), label.size.height.toFloat()),
            CornerRadius(4.dp.toPx()),
        )
        drawText(label, topLeft = topLeft)
    }
}
