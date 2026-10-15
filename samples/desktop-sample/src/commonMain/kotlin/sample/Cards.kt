package sample

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WeatherCard(city: String, tempC: Int, condition: String) {
    Column(
        Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF6FB1FC), Color(0xFF4364F7))))
            .padding(20.dp)
    ) {
        BasicText(city, style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp))
        BasicText("$tempC°", style = TextStyle(color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light))
        BasicText(condition, style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
fun ProgressRing(progress: Float, label: String) {
    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(140.dp)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color(0x22FFFFFF), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                brush = Brush.sweepGradient(listOf(Color(0xFF00E5FF), Color(0xFFB388FF), Color(0xFF00E5FF))),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText("${(progress * 100).toInt()}%", style = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold))
            BasicText(label, style = TextStyle(color = Color(0xFF9FB3C8), fontSize = 12.sp))
        }
    }
}

@Composable
fun ProfileChip(name: String, role: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFF1F4F9))
            .padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF4364F7)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(name.take(1), style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
        }
        Column {
            BasicText(name, style = TextStyle(color = Color(0xFF14213D), fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            BasicText(role, style = TextStyle(color = Color(0xFF6B7A90), fontSize = 12.sp))
        }
        Spacer(Modifier.width(2.dp))
    }
}

@Preview
@Composable
fun WeatherCardPreview() = WeatherCard("Islamabad", 27, "Clear skies")

@Preview(name = "Ring", showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
fun ProgressRingPreview() = ProgressRing(0.72f, "Weekly goal")

@Preview(name = "Default", showBackground = true)
@Preview(name = "Large text", showBackground = true, fontScale = 1.5f)
@Composable
fun ProfileChipPreview() = ProfileChip("Zain", "Android engineer")

@Preview(widthDp = 320, heightDp = 120, showBackground = true, backgroundColor = 0xFFFFF4E5)
@Composable
private fun FixedSizePreview() {
    Box(Modifier.padding(16.dp)) {
        ProfileChip("Ayesha", "Fixed 320x120 dp canvas")
    }
}
