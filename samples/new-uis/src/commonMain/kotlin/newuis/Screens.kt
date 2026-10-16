package newuis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Snackbar
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val contacts = List(40) { "Contact #${it + 1}" }

/** A full screen: app bar, list, FAB and snackbar. */
@Preview(widthDp = 360, heightDp = 640)
@Composable
fun ContactsScreen() {
    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Contacts") }) },
            floatingActionButton = { FloatingActionButton(onClick = {}) { Text("+") } },
            snackbarHost = { Snackbar(Modifier.padding(8.dp)) { Text("Contact deleted") } },
        ) { padding ->
            LazyColumn(Modifier.padding(padding)) {
                items(contacts) { name ->
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF7986CB)))
                        Text(name, Modifier.padding(start = 12.dp))
                    }
                    Divider()
                }
            }
        }
    }
}

/** A screen with no size: fillMaxSize inside an unsized preview. */
@Preview(showBackground = true)
@Composable
fun UnsizedFullScreen() {
    Box(Modifier.fillMaxSize().background(Color(0xFFE8F5E9)), contentAlignment = Alignment.Center) {
        Text("Centered on whatever screen this is")
    }
}

/** Content taller than Spyglass's default 1024dp limit. */
@Preview(showBackground = true, widthDp = 300)
@Composable
fun LongSettingsPage() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(30) { Text("Setting ${it + 1}", Modifier.fillMaxWidth().height(40.dp).background(Color(0xFFF1F1F1)).padding(8.dp)) }
        Text("LAST ROW: Sign out", color = Color.Red)
    }
}

/** A confirmation dialog shown over a screen. */
@Preview(widthDp = 360, heightDp = 400)
@Composable
fun DeleteDialog() {
    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Color(0xFFEEEEEE))) {
            Text("Background screen", Modifier.padding(16.dp))
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Delete photo?") },
                text = { Text("This can't be undone.") },
                confirmButton = { TextButton(onClick = {}) { Text("Delete") } },
                dismissButton = { TextButton(onClick = {}) { Text("Cancel") } },
            )
        }
    }
}

/** An open menu: popups are separate layers. */
@Preview(widthDp = 300, heightDp = 300)
@Composable
fun OpenMenu() {
    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Color.White).padding(16.dp)) {
            Button(onClick = {}) { Text("Sort by") }
            DropdownMenu(expanded = true, onDismissRequest = {}) {
                DropdownMenuItem(onClick = {}) { Text("Name") }
                DropdownMenuItem(onClick = {}) { Text("Date added") }
                DropdownMenuItem(onClick = {}) { Text("Size") }
            }
        }
    }
}

/** Urdu, right to left. */
@Preview(showBackground = true, widthDp = 320)
@Composable
fun UrduProfile() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("خوش آمدید، عائشہ", style = MaterialTheme.typography.h6)
            Text("آپ کے پاس ۳ نئے پیغامات ہیں")
            OutlinedTextField(value = "عائشہ خان", onValueChange = {}, label = { Text("نام") })
        }
    }
}

/** Text that must ellipsize, plus emoji. */
@Preview(showBackground = true, widthDp = 220)
@Composable
fun TightText() {
    Column(Modifier.padding(8.dp)) {
        Text("A very long product title that cannot possibly fit on one line here", maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Rated 4.8 ⭐⭐⭐⭐⭐ 🎉")
    }
}

/** Responsive layout picks columns from the width it gets. */
@Preview(showBackground = true, widthDp = 320, name = "Phone")
@Preview(showBackground = true, widthDp = 840, name = "Tablet")
@Composable
fun ResponsiveGrid() {
    BoxWithConstraints(Modifier.padding(8.dp)) {
        val width = maxWidth
        val columns = if (width < 600.dp) 1 else 3
        Column {
            Text("$columns column(s) at ${width.value.toInt()}dp")
            repeat(2) {
                Row {
                    repeat(columns) { Box(Modifier.weight(1f).height(60.dp).padding(4.dp).background(Color(0xFFBBDEFB))) }
                }
            }
        }
    }
}
