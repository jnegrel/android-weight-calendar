package no.jene.weightcalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.jene.weightcalendar.ui.theme.AccentBlue
import no.jene.weightcalendar.ui.theme.HeaderBottom
import no.jene.weightcalendar.ui.theme.HeaderTop
import no.jene.weightcalendar.ui.theme.TextPrimary

// The blue bar at the top of every screen: a centred title, an optional ‹ back button on
// the left, and optional buttons on the right (`actions`, e.g. the ⋮ menu).
// BoxScope: inside `actions`, Modifier.align() can be used to place things in the bar.
@Composable
fun AppHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(Brush.verticalGradient(listOf(HeaderTop, HeaderBottom)))
            .statusBarsPadding()                 // push the title below the clock/battery icons
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Text("‹", color = AccentBlue, fontSize = 30.sp)
            }
        }
        actions()
    }
}
