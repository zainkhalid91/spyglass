package sample

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// Stands in for a designer's mock, so `spyglass diff` has something to compare WeatherCardPreview with.
@Preview(name = "Design mock")
@Composable
fun WeatherCardDesignMock() = WeatherCard("Islamabad", 31, "Sunny")
