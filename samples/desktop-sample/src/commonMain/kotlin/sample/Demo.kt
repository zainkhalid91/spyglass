package sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The README demo: a design target, a first attempt with the usual mistakes, and the fixed version.
// All three share one fixed canvas so `spyglass diff` compares like with like.

private val Ink = Color(0xFF14213D)
private val Muted = Color(0xFF6B7A90)
private val Violet = Color(0xFF6C47FF)
private val Pink = Color(0xFFFF4D9D)

/** The values a designer chose, and the ones a first attempt usually gets slightly wrong. */
data class PlanCardStyle(
    val corner: Dp = 28.dp,
    val padding: Dp = 24.dp,
    val priceSize: TextUnit = 40.sp,
    val priceColor: Color = Ink,
    val featureGap: Dp = 12.dp,
    val gradientButton: Boolean = true,
    val features: List<String> = listOf("Unlimited projects", "Design comparisons", "Priority support"),
)

@Composable
fun PlanCard(style: PlanCardStyle = PlanCardStyle()) {
    Column(
        Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(style.corner))
            .background(Color.White)
            .padding(style.padding)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText("Pro", style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold))
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFEDE7FF)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                BasicText("Popular", style = TextStyle(color = Violet, fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText("$9", style = TextStyle(color = style.priceColor, fontSize = style.priceSize, fontWeight = FontWeight.Bold))
            BasicText(" / month", style = TextStyle(color = Muted, fontSize = 14.sp), modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(style.featureGap)) {
            for (feature in style.features) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xFFE6F8EE)), contentAlignment = Alignment.Center) {
                        BasicText("✓", style = TextStyle(color = Color(0xFF1E9E5A), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                    }
                    BasicText(feature, style = TextStyle(color = Ink, fontSize = 14.sp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        val button = if (style.gradientButton) Brush.horizontalGradient(listOf(Violet, Pink)) else Brush.linearGradient(listOf(Violet, Violet))
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(button),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Upgrade", style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun DemoCanvas(style: PlanCardStyle) {
    Box(Modifier.padding(30.dp)) { PlanCard(style) }
}

@Preview(name = "Design target", widthDp = 360, heightDp = 430, showBackground = true, backgroundColor = 0xFFD5DBE8)
@Composable
fun DemoDesignTarget() = DemoCanvas(PlanCardStyle())

@Preview(name = "First attempt", widthDp = 360, heightDp = 430, showBackground = true, backgroundColor = 0xFFD5DBE8)
@Composable
fun DemoFirstAttempt() = DemoCanvas(
    PlanCardStyle(corner = 12.dp, priceColor = Violet, featureGap = 8.dp, gradientButton = false)
)

@Preview(name = "After fix", widthDp = 360, heightDp = 430, showBackground = true, backgroundColor = 0xFFD5DBE8)
@Composable
fun DemoAfterFix() = DemoCanvas(
    PlanCardStyle(features = listOf("Unlimited projects", "Design comparisons", "Priority Support"))
)
