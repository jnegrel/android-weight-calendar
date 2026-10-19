package no.jene.weightcalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.jene.weightcalendar.data.Day
import no.jene.weightcalendar.data.Trend
import no.jene.weightcalendar.ui.theme.AccentBlue
import no.jene.weightcalendar.ui.theme.EstimatedOutline
import no.jene.weightcalendar.ui.theme.GainBig
import no.jene.weightcalendar.ui.theme.GainSmall
import no.jene.weightcalendar.ui.theme.LossBig
import no.jene.weightcalendar.ui.theme.LossSmall
import no.jene.weightcalendar.ui.theme.Neutral
import no.jene.weightcalendar.ui.theme.Surface
import no.jene.weightcalendar.ui.theme.TextPrimary
import no.jene.weightcalendar.ui.theme.TextSecondary
import no.jene.weightcalendar.ui.theme.Today
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// The colour of each trend, shared with the graph.
fun Trend.color(): Color = when (this) {
    Trend.BIG_LOSS -> LossBig
    Trend.SMALL_LOSS -> LossSmall
    Trend.SMALL_GAIN -> GainSmall
    Trend.BIG_GAIN -> GainBig
    Trend.FIRST -> Neutral
}

// Month view: ‹ OCTOBER 2026 ›, weekday letters, a 6-week grid, and a line with the
// details of the selected day and an EDIT button. Change month with the arrows or by
// swiping sideways.
@Composable
fun MonthCalendar(days: Map<LocalDate, Day>, onEdit: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val lastMonth = YearMonth.from(today)
    val firstMonth = days.keys.firstOrNull()?.let(YearMonth::from) ?: lastMonth

    // rememberSaveable: like remember, but also survives screen rotation.
    var month by rememberSaveable { mutableStateOf(lastMonth) }
    var selected by rememberSaveable { mutableStateOf(today) }

    fun previous() { if (month > firstMonth) month = month.minusMonths(1) }
    fun next() { if (month < lastMonth) month = month.plusMonths(1) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            // Swipe detection: add up the horizontal finger movement, decide when it is lifted.
            .pointerInput(firstMonth) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > 100) previous() else if (total < -100) next() },
                ) { _, dragAmount -> total += dragAmount }
            },
    ) {
        MonthHeader(month, canGoBack = month > firstMonth, canGoForward = month < lastMonth,
            onPrevious = ::previous, onNext = ::next)
        WeekdayLetters()

        // Monday-first grid. "offset" = number of empty cells before the 1st of the month.
        val offset = month.atDay(1).dayOfWeek.value - 1
        for (week in 0 until 6) {
            Row {
                for (weekday in 0 until 7) {
                    val dayNumber = week * 7 + weekday - offset + 1
                    val date = if (dayNumber in 1..month.lengthOfMonth()) month.atDay(dayNumber) else null
                    DayCell(
                        date = date,
                        day = date?.let { days[it] },
                        isToday = date == today,
                        isSelected = date == selected,
                        onClick = { if (date != null && date <= today) selected = date },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        SelectedDayLine(selected, days[selected], onEdit = { onEdit(selected) })
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth, canGoBack: Boolean, canGoForward: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrevious, enabled = canGoBack) { Text("‹", fontSize = 30.sp, color = arrowColor(canGoBack)) }
        Text(
            month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase() + "  ${month.year}",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = TextPrimary,
            fontSize = 22.sp,
        )
        TextButton(onClick = onNext, enabled = canGoForward) { Text("›", fontSize = 30.sp, color = arrowColor(canGoForward)) }
    }
}

private fun arrowColor(enabled: Boolean) = if (enabled) AccentBlue else Color.Transparent

@Composable
private fun WeekdayLetters() {
    Row(Modifier.padding(bottom = 4.dp)) {
        for (day in DayOfWeek.entries) {   // MONDAY..SUNDAY
            Text(
                day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = TextSecondary,
            )
        }
    }
}

// One square of the grid. Layers, from outside in:
//   red ring (estimated days only)  ->  small gap  ->  coloured rounded square  ->  day number
@Composable
private fun DayCell(
    date: LocalDate?, day: Day?, isToday: Boolean, isSelected: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    Box(modifier.height(40.dp).padding(2.dp), contentAlignment = Alignment.Center) {
        if (date == null) return@Box   // empty cell before the 1st / after the last day

        val ring = RoundedCornerShape(12.dp)
        val square = RoundedCornerShape(9.dp)
        val fill = when {
            day != null -> day.trend.color()
            isToday -> Today
            else -> Color.Transparent
        }
        Box(
            Modifier
                .fillMaxSize()
                .clip(ring)
                .clickable(onClick = onClick)
                .then(if (day?.entry?.estimated == true) Modifier.border(2.dp, EstimatedOutline, ring) else Modifier)
                .padding(4.dp)
                .clip(square)
                .background(fill)
                .then(if (isSelected) Modifier.border(2.dp, Color.White, square) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                color = TextPrimary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

// e.g. "Tuesday 13 October  ·  109.9 kg  ·  -0.1"                 EDIT
@Composable
private fun SelectedDayLine(date: LocalDate, day: Day?, onEdit: () -> Unit) {
    val text = buildString {
        append(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")))
        if (day == null) {
            append("  ·  no entry")
        } else {
            append("  ·  %.1f kg".format(day.entry.weightKg))
            day.delta?.let { append("  ·  %+.1f".format(it)) }
            if (day.entry.estimated) append("  (estimated)")
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f), color = TextSecondary, fontSize = 14.sp)
        TextButton(onClick = onEdit) { Text(if (day == null) "ADD" else "EDIT", color = AccentBlue) }
    }
}
