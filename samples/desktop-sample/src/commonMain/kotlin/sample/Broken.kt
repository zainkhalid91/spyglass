package sample

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// Kept on purpose: shows how a crashing preview is reported instead of stopping the run.
@Preview
@Composable
fun BrokenPreview() {
    error("Intentional failure to test error reporting")
}
