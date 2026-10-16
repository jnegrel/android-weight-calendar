package no.jene.weightcalendar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.jene.weightcalendar.data.Day
import no.jene.weightcalendar.data.SmoothedPoint
import no.jene.weightcalendar.data.weeklyAverage
import no.jene.weightcalendar.ui.theme.DailyLine
import no.jene.weightcalendar.ui.theme.Surface
import no.jene.weightcalendar.ui.theme.TextPrimary
import no.jene.weightcalendar.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

private const val WINDOW_DAYS = 365L

// Rolling one-year weight graph: the daily weights as a thin light-grey line, and on top
// the 7-day average, coloured by its weekly change (see Smoothing.kt).
// Missing days leave a hole in both lines.
@Composable
fun WeightGraph(days: Map<LocalDate, Day>, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val start = today.minusDays(WINDOW_DAYS)
    // remember(days): only recompute when the data changes, not on every redraw.
    val points = remember(days) { days.values.filter { it.entry.date > start } }
    // Computed on all days, then cut, so the first points of the year are full 7-day averages.
    val trend = remember(days) { days.weeklyAverage().filter { it.date > start } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
    ) {
        GraphHeader(points, start, today)
        if (points.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No data in the last year", color = TextSecondary)
            }
        } else {
            val textMeasurer = rememberTextMeasurer()
            Canvas(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
                drawGraph(points, trend, start, textMeasurer)
            }
        }
    }
}

// "Average 108.4 kg"                     "16 Oct 2025 - 16 Oct 2026"
@Composable
private fun GraphHeader(points: List<Day>, start: LocalDate, today: LocalDate) {
    val format = DateTimeFormatter.ofPattern("d MMM yyyy")
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text("Average", color = TextSecondary, fontSize = 14.sp)
            val average = points.map { it.entry.weightKg }.average()
            Text(if (points.isEmpty()) "–" else "%.1f kg".format(average), color = TextPrimary, fontSize = 26.sp)
        }
        Text(
            "${start.plusDays(1).format(format)} – ${today.format(format)}",
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

// Everything below runs inside the Canvas. Coordinates are in pixels, (0,0) = top-left,
// and y grows downwards, so a heavier weight must get a *smaller* y.
private fun DrawScope.drawGraph(
    points: List<Day>, trend: List<SmoothedPoint>, start: LocalDate, textMeasurer: TextMeasurer,
) {
    val labelStyle = TextStyle(color = TextSecondary, fontSize = 11.sp)
    val left = 34.dp.toPx()                  // room for the kg labels
    val bottom = size.height - 20.dp.toPx()  // room for the month labels
    val width = size.width - left

    // Vertical range: lowest - 2 kg to highest + 2 kg, widened to at least 5 kg.
    val weights = points.map { it.entry.weightKg }
    var low = weights.min() - 2
    var high = weights.max() + 2
    if (high - low < 5) {
        val middle = (low + high) / 2
        low = middle - 2.5
        high = middle + 2.5
    }

    // Conversions from data (date, kg) to pixels.
    fun x(date: LocalDate) = left + width * ChronoUnit.DAYS.between(start, date) / WINDOW_DAYS
    fun y(kg: Double) = (bottom * (high - kg) / (high - low)).toFloat()

    // Horizontal grid lines with kg labels, every 1, 2 or 5 kg depending on the range.
    val step = when {
        high - low <= 8 -> 1
        high - low <= 20 -> 2
        else -> 5
    }
    var kg = ceil(low / step) * step
    while (kg <= high) {
        val yPos = y(kg)
        drawLine(TextSecondary.copy(alpha = 0.15f), Offset(left, yPos), Offset(size.width, yPos), 1.dp.toPx())
        val label = textMeasurer.measure("%.0f".format(kg), labelStyle)
        drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), yPos - label.size.height / 2))
        kg += step
    }

    // Month labels on the first day of Jan, Apr, Jul and Oct, with a small tick mark.
    var month = YearMonth.from(start).plusMonths(1)
    val monthFormat = DateTimeFormatter.ofPattern("MMM yyyy")
    while (month.atDay(1) <= start.plusDays(WINDOW_DAYS)) {
        if ((month.monthValue - 1) % 3 == 0) {
            val xPos = x(month.atDay(1))
            drawLine(TextSecondary, Offset(xPos, bottom), Offset(xPos, bottom + 3.dp.toPx()), 1.dp.toPx())
            val label = textMeasurer.measure(month.format(monthFormat), labelStyle)
            val labelX = (xPos - label.size.width / 2).coerceIn(left, size.width - label.size.width)
            drawText(label, topLeft = Offset(labelX, bottom + 4.dp.toPx()))
        }
        month = month.plusMonths(1)
    }

    // Daily weights: thin grey line, one short segment per pair of consecutive days.
    for ((previous, current) in points.zipWithNext()) {
        if (ChronoUnit.DAYS.between(previous.entry.date, current.entry.date) != 1L) continue  // hole
        drawLine(
            color = DailyLine,
            start = Offset(x(previous.entry.date), y(previous.entry.weightKg)),
            end = Offset(x(current.entry.date), y(current.entry.weightKg)),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    // 7-day average on top: thicker, each segment coloured by the weekly change.
    for ((previous, current) in trend.zipWithNext()) {
        if (ChronoUnit.DAYS.between(previous.date, current.date) != 1L) continue
        drawLine(
            color = current.trend.color(),
            start = Offset(x(previous.date), y(previous.weightKg)),
            end = Offset(x(current.date), y(current.weightKg)),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
