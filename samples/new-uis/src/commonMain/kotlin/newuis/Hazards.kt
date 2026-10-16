package newuis

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** A preview stuck in a loop, e.g. a busy-wait in fake data. It must not hang the whole render. */
@Preview
@Composable
fun StuckPreview() {
    val until = System.currentTimeMillis() + 120_000
    while (System.currentTimeMillis() < until) Thread.onSpinWait()
    Text("never")
}

/** Rendered after the stuck one, to see whether the others still come out. */
@Preview
@Composable
fun ZzzAfterStuck() {
    Text("I rendered")
}
