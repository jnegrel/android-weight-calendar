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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

// The 42 days (6 weeks, Monday first) shown for a month: the end of the previous month,
// the month itself, and the start of the next one.
fun YearMonth.gridDays(): List<LocalDate> {
    val first = atDay(1)
    val gridStart = first.minusDays(first.dayOfWeek.value - 1L)   // the Monday on or before the 1st
    return (0L until 42L).map { gridStart.plusDays(it) }
}

// Month view: ‹ OCTOBER 2026 ›, weekday letters, a 6-week grid, and a line with the
// details of the selected day and an EDIT button. Change month with the arrows or by
// swiping sideways.
// The month shown is "hoisted": it lives in the caller (HomeScreen), which also gives it
// to the graph. The calendar only displays it and asks for changes with onMonthChange.
@Composable
fun MonthCalendar(
    days: Map<LocalDate, Day>,
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onEdit: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val lastMonth = YearMonth.from(today)
    val firstMonth = days.keys.firstOrNull()?.let(YearMonth::from) ?: lastMonth

    // rememberSaveable: like remember, but also survives screen rotation.
    var selected by rememberSaveable { mutableStateOf(today) }

    // rememberUpdatedState: the swipe detector below is created once, so it must read
    // the *current* month through this, not the month it saw when it was created.
    val currentMonth by rememberUpdatedState(month)
    fun previous() { if (currentMonth > firstMonth) onMonthChange(currentMonth.minusMonths(1)) }
    fun next() { if (currentMonth < lastMonth) onMonthChange(currentMonth.plusMonths(1)) }

    // Tapping a dimmed day of the previous/next month also shows that month
    // (unless it is before the first month with data).
    fun select(date: LocalDate) {
        selected = date
        val dateMonth = YearMonth.from(date)
        if (dateMonth != month && dateMonth >= firstMonth) onMonthChange(dateMonth)
    }

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

        // chunked(7): split the 42 days into 6 rows of 7.
        for (week in month.gridDays().chunked(7)) {
            Row {
                for (date in week) {
                    DayCell(
                        date = date,
                        day = days[date],
                        isToday = date == today,
                        isSelected = date == selected,
                        inMonth = YearMonth.from(date) == month,
                        onClick = { if (date <= today) select(date) },
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
// Days of the previous/next month are drawn semi-transparent.
@Composable
private fun DayCell(
    date: LocalDate, day: Day?, isToday: Boolean, isSelected: Boolean, inMonth: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    Box(
        modifier.height(40.dp).padding(2.dp).alpha(if (inMonth) 1f else 0.35f),
        contentAlignment = Alignment.Center,
    ) {
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
            // Small white dot in the corner: this day has a note.
            if (day?.entry?.note != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(3.dp).size(5.dp)
                        .clip(CircleShape).background(TextPrimary)
                )
            }
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
        Column(Modifier.weight(1f)) {
            Text(text, color = TextSecondary, fontSize = 14.sp)
            // The note, if any, on a second line (cut with "…" if too long; EDIT shows it all).
            day?.entry?.note?.let {
                Text(it, color = TextPrimary, fontSize = 14.sp, fontStyle = FontStyle.Italic,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TextButton(onClick = onEdit) { Text(if (day == null) "ADD" else "EDIT", color = AccentBlue) }
    }
}
