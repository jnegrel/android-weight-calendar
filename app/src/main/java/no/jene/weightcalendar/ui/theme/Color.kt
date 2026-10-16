package no.jene.weightcalendar.ui.theme

import androidx.compose.ui.graphics.Color

// Colours picked from the Headache Calendar screenshots.

// Backgrounds
val Background = Color(0xFF26272B)      // main screen background
val Surface = Color(0xFF1C1D21)         // cards (calendar, graph)
val HeaderTop = Color(0xFF2C4A6E)       // top bar gradient
val HeaderBottom = Color(0xFF23344D)

// Text
val TextPrimary = Color(0xFFE6E6E9)
val TextSecondary = Color(0xFF9A9AA2)

// Accent (buttons, arrows)
val AccentBlue = Color(0xFF6FA8E8)
val ButtonStart = Color(0xFF5592DA)
val ButtonEnd = Color(0xFF3F6290)

// Day-to-day weight change colours
val LossBig = Color(0xFF6FA8E8)         // Δ ≤ -0.5 kg      (blue)
val LossSmall = Color(0xFF4CBB7F)       // -0.5 < Δ ≤ 0     (green)
val GainSmall = Color(0xFFF5C04A)       // 0 < Δ < +0.5     (yellow)
val GainBig = Color(0xFFF06464)         // Δ ≥ +0.5 kg      (red)
val Neutral = Color(0xFF6B6B73)         // no previous day to compare with (grey)

// Graph: daily weights, drawn under the coloured 7-day average
val DailyLine = Color(0xCCB4B4BC)       // light grey, 80 % opaque (first byte = opacity: CC = 204/255)

// Calendar extras
val Today = Color(0xFF3A6496)           // today, before it is logged (dark blue)
val EstimatedOutline = Color(0xFFF06464) // ring around interpolated days
