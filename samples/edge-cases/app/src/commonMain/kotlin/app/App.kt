package app

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import ds.Badge
import ds.ThemePreviews

@ThemePreviews
@Composable
fun BadgePreview() = Badge("From another module")

class Names : PreviewParameterProvider<String> {
    override val values = sequenceOf("Ann", "Bo")
}

@Preview
@Composable
fun WithParameter(@PreviewParameter(Names::class) name: String) = Badge(name)

class ClassPreviews {
    @Preview
    @Composable
    fun InsideClass() = Badge("Inside a class")
}
