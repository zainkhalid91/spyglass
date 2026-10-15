package sample

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Previews that exercise the less common ways of declaring them.

@Composable
fun ThemedNote(text: String) {
    val dark = isSystemInDarkTheme()
    Column(
        Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) Color(0xFF1E2433) else Color(0xFFFFFFFF))
            .padding(16.dp)
    ) {
        BasicText(if (dark) "Dark theme" else "Light theme", style = TextStyle(color = if (dark) Color(0xFF9FB3C8) else Color(0xFF6B7A90), fontSize = 12.sp))
        BasicText(text, style = TextStyle(color = if (dark) Color.White else Color(0xFF14213D), fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** A multi-preview annotation from the Compose library itself: renders a Light and a Dark variant. */
@PreviewLightDark
@Composable
fun ThemedNotePreview() = ThemedNote("Follows isSystemInDarkTheme()")

/** Previews may live in an object, as in Android Studio. */
object ObjectPreviews {
    @Preview(showBackground = true)
    @Composable
    fun InsideObject() = ThemedNote("Declared inside an object")
}

/** A device spec sets the canvas; with showSystemUi it is the exact size. */
@Preview(device = "spec:width=240dp,height=140dp", showSystemUi = true, showBackground = true, backgroundColor = 0xFFEEF1F7)
@Composable
fun DeviceSpecPreview() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText("240 x 140 dp device", style = TextStyle(color = Color(0xFF14213D), fontSize = 14.sp))
    }
}

@Composable
fun StatusChip(status: String) {
    val color = when (status) {
        "Online" -> Color(0xFF1E9E5A)
        "Away" -> Color(0xFFE8A317)
        else -> Color(0xFF9AA5B8)
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        BasicText(status, style = TextStyle(color = Color(0xFF14213D), fontSize = 14.sp, fontWeight = FontWeight.Medium))
    }
}

class StatusProvider : PreviewParameterProvider<String> {
    override val values = sequenceOf("Online", "Away", "Offline")
}

/** One image per provider value. */
@Preview(showBackground = true)
@Composable
fun StatusChipPreview(@PreviewParameter(StatusProvider::class) status: String) = StatusChip(status)
