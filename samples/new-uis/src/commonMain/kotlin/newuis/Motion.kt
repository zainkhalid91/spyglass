package newuis

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay

/** A progress bar that animates from 0 to 70% when shown, as most dashboards do. */
@Preview(showBackground = true, widthDp = 300)
@Composable
fun AnimatedProgress() {
    var target by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { target = 0.7f }
    val progress by animateFloatAsState(target, tween(600))
    Column(Modifier.padding(16.dp)) {
        Text("Storage used: ${(progress * 100).toInt()}%")
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE0E0E0))) {
            Box(Modifier.fillMaxWidth(progress).height(12.dp).background(Color(0xFF3F51B5)))
        }
    }
}

/** A banner that slides in after appearing. */
@Preview(showBackground = true, widthDp = 300)
@Composable
fun EnteringBanner() {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Column(Modifier.padding(16.dp)) {
        Text("Inbox")
        AnimatedVisibility(shown) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).background(Color(0xFFFFF3CD)).padding(12.dp)) {
                Text("You have 3 new messages")
            }
        }
    }
}

/** Data that "loads" after a short delay, like a fake repository in a preview. */
@Preview(showBackground = true, widthDp = 300)
@Composable
fun DelayedLoad() {
    var name by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        delay(300)
        name = "Ayesha Khan"
    }
    Crossfade(name, Modifier.padding(16.dp)) { loaded ->
        if (loaded == null) CircularProgressIndicator(Modifier.size(32.dp)) else Text("Signed in as $loaded")
    }
}

/** Indeterminate indicators animate forever. */
@Preview(showBackground = true, widthDp = 300)
@Composable
fun Spinners() {
    Column(Modifier.padding(16.dp)) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(Modifier.width(200.dp))
    }
}

/** A ticking clock: state that changes every frame, forever. */
@Preview(showBackground = true, widthDp = 200)
@Composable
fun Ticker() {
    var ticks by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            ticks++
        }
    }
    Text("Frame $ticks", Modifier.padding(16.dp))
}
