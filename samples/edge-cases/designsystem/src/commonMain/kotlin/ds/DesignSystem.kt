package ds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** A team-wide multi-preview annotation, declared in a different module from the previews using it. */
@Preview(name = "Normal", showBackground = true)
@Preview(name = "Big text", showBackground = true, fontScale = 1.6f)
annotation class ThemePreviews

@Composable
fun Badge(text: String) {
    BasicText(text, Modifier.background(Color(0xFF6C47FF)).padding(8.dp))
}
