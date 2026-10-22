package no.jene.weightcalendar.ui

import androidx.compose.runtime.staticCompositionLocalOf
import no.jene.weightcalendar.data.WeightUnit

// The display unit, available to every composable without passing it as a parameter
// through all the levels in between: HomeScreen "provides" it once, and any function
// below reads it with LocalWeightUnit.current. Compose uses the same mechanism for the
// theme colours. Good for a display preference that almost everything needs.
val LocalWeightUnit = staticCompositionLocalOf { WeightUnit.KG }
