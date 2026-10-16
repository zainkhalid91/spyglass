@file:OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)

package newuis

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Badge
import androidx.compose.material.BadgedBox
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.Chip
import androidx.compose.material.Divider
import androidx.compose.material.DrawerValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.ModalDrawer
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Slider
import androidx.compose.material.Switch
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TopAppBar
import androidx.compose.material.darkColors
import androidx.compose.material.rememberDrawerState
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

private val Indigo = Color(0xFF3F51B5)
private val palette = listOf(Color(0xFFEF5350), Color(0xFFAB47BC), Color(0xFF42A5F5), Color(0xFF26A69A), Color(0xFFFFCA28), Color(0xFF8D6E63))

// ---------------------------------------------------------------- Admin dashboard (desktop size)

@Preview(widthDp = 1280, heightDp = 800)
@Composable
fun AdminDashboard() {
    MaterialTheme {
        Row(Modifier.fillMaxSize().background(Color(0xFFF4F6FA))) {
            Column(Modifier.width(220.dp).fillMaxHeight().background(Color(0xFF1E2235)).padding(20.dp)) {
                Text("Spyglass Admin", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(24.dp))
                listOf("Overview", "Orders", "Customers", "Products", "Reports", "Settings").forEachIndexed { i, item ->
                    Text(
                        item,
                        color = if (i == 0) Color.White else Color(0xFF9AA0B8),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (i == 0) Indigo else Color.Transparent).padding(12.dp),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Overview", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf("Revenue" to "$48,210", "Orders" to "1,204", "Refunds" to "18", "Conversion" to "3.9%").forEachIndexed { i, (label, value) ->
                        Card(Modifier.weight(1f), elevation = 2.dp) {
                            Column(Modifier.padding(16.dp)) {
                                Text(label, color = Color.Gray, fontSize = 13.sp)
                                Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Text(if (i == 2) "▼ 4%" else "▲ 12%", color = if (i == 2) Color(0xFFD32F2F) else Color(0xFF2E7D32), fontSize = 12.sp)
                            }
                        }
                    }
                }
                Row(Modifier.height(260.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Card(Modifier.weight(2f).fillMaxHeight(), elevation = 2.dp) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Revenue, last 12 weeks", fontWeight = FontWeight.Medium)
                            LineChart(listOf(12f, 18f, 15f, 22f, 28f, 25f, 31f, 35f, 30f, 38f, 42f, 48f), Modifier.fillMaxSize().padding(top = 12.dp))
                        }
                    }
                    Card(Modifier.weight(1f).fillMaxHeight(), elevation = 2.dp) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Orders by channel", fontWeight = FontWeight.Medium)
                            DonutChart(listOf(45f, 25f, 20f, 10f), Modifier.fillMaxSize().padding(16.dp))
                        }
                    }
                }
                Card(elevation = 2.dp) {
                    Column {
                        Row(Modifier.background(Color(0xFFF0F2F7)).padding(12.dp)) {
                            listOf("Order", "Customer", "Status", "Total").forEach { Text(it, Modifier.weight(1f), fontWeight = FontWeight.Bold) }
                        }
                        listOf(
                            listOf("#1042", "Ayesha Khan", "Shipped", "$120.00"),
                            listOf("#1041", "Bilal Ahmed", "Pending", "$89.50"),
                            listOf("#1040", "Sara Malik", "Refunded", "$45.00"),
                        ).forEach { row ->
                            Row(Modifier.padding(12.dp)) { row.forEach { Text(it, Modifier.weight(1f)) } }
                            Divider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LineChart(values: List<Float>, modifier: Modifier) {
    Canvas(modifier) {
        val max = values.max()
        val step = size.width / (values.size - 1)
        val points = values.mapIndexed { i, v -> Offset(i * step, size.height * (1 - v / max)) }
        val line = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        val fill = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(Indigo.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, Indigo, style = Stroke(width = 3.dp.toPx()))
        points.forEach { drawCircle(Color.White, 4.dp.toPx(), it); drawCircle(Indigo, 4.dp.toPx(), it, style = Stroke(2.dp.toPx())) }
    }
}

@Composable
private fun DonutChart(values: List<Float>, modifier: Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        var start = -90f
        values.forEachIndexed { i, v ->
            val sweep = 360f * v / values.sum()
            drawArc(palette[i], start, sweep - 2f, useCenter = false, style = Stroke(width = 28.dp.toPx()))
            start += sweep
        }
    }
}

// ---------------------------------------------------------------- Shop home (phone)

@Preview(widthDp = 360, heightDp = 800)
@Composable
fun ShopHome() {
    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Bazaar") }, backgroundColor = Color.White) },
            bottomBar = {
                BottomNavigation(backgroundColor = Color.White) {
                    listOf("Home", "Search", "Cart", "Profile").forEachIndexed { i, label ->
                        BottomNavigationItem(
                            selected = i == 0, onClick = {}, label = { Text(label) },
                            icon = {
                                if (label == "Cart") BadgedBox(badge = { Badge { Text("3") } }) { Box(Modifier.size(20.dp).background(Color.Gray, CircleShape)) }
                                else Box(Modifier.size(20.dp).background(if (i == 0) Indigo else Color.Gray, CircleShape))
                            },
                        )
                    }
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding)) {
                OutlinedTextField("", {}, placeholder = { Text("Search products") }, modifier = Modifier.fillMaxWidth().padding(12.dp))
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All", "Shoes", "Watches", "Bags", "Phones", "Beauty")) { Chip(onClick = {}) { Text(it) } }
                }
                val pager = rememberPagerState(initialPage = 1) { 3 }
                HorizontalPager(pager, Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp), pageSpacing = 12.dp) { page ->
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(palette[page], palette[page + 2]))).padding(16.dp)) {
                        Text("Sale ${page + 1}: up to ${(page + 3) * 10}% off", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(3) { Box(Modifier.padding(3.dp).size(8.dp).clip(CircleShape).background(if (it == pager.currentPage) Indigo else Color.LightGray)) }
                }
                LazyVerticalGrid(GridCells.Fixed(2), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(8) { i ->
                        Card(elevation = 3.dp) {
                            Column {
                                Box(Modifier.fillMaxWidth().height(90.dp).background(palette[i % palette.size].copy(alpha = 0.3f)))
                                Text("Product ${i + 1}", Modifier.padding(start = 8.dp, top = 6.dp), fontWeight = FontWeight.Medium)
                                Text("Rs ${(i + 1) * 1499}", Modifier.padding(start = 8.dp, bottom = 8.dp), color = Indigo)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Chat with a typing indicator

@Preview(widthDp = 360, heightDp = 640)
@Composable
fun ChatScreen() {
    val messages = listOf(
        true to "Hey! Did the build pass?",
        false to "Yes, all 46 tests are green 🎉",
        true to "Nice. Can you send me the screenshot of the dashboard?",
        false to "Rendering it now with Spyglass…",
        true to "No IDE needed?",
        false to "Nope. Headless.",
    )
    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color(0xFFECE5DD))) {
            TopAppBar(title = { Text("Ayesha") })
            LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp), reverseLayout = true) {
                item { TypingDots() }
                items(messages.reversed()) { (mine, text) ->
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                        Text(
                            text,
                            Modifier.widthIn280().clip(RoundedCornerShape(12.dp)).background(if (mine) Color(0xFFDCF8C6) else Color.White).padding(10.dp),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField("", {}, placeholder = { Text("Message") }, modifier = Modifier.weight(1f))
                Box(Modifier.padding(start = 8.dp).size(44.dp).clip(CircleShape).background(Color(0xFF128C7E)), contentAlignment = Alignment.Center) {
                    Text("➤", color = Color.White)
                }
            }
        }
    }
}

private fun Modifier.widthIn280() = this.then(Modifier.width(260.dp))

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition()
    val a by t.animateFloat(0.2f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse))
    Row(Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp)) {
        repeat(3) { Box(Modifier.padding(2.dp).size(8.dp).graphicsLayer { alpha = if (it == 1) 1.2f - a else a }.background(Color.Gray, CircleShape)) }
    }
}

// ---------------------------------------------------------------- Form controls, with a focused field

@Preview(showBackground = true, widthDp = 360)
@Composable
fun SettingsForm() {
    MaterialTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            OutlinedTextField("zain@example.com", {}, label = { Text("Email") }, modifier = Modifier.fillMaxWidth().focusRequester(focus))
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Dark mode", Modifier.weight(1f)); Switch(true, {}) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Notifications", Modifier.weight(1f)); Switch(false, {}) }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(true, {}); Text("Accept terms") }
            Text("Quality")
            listOf("Low", "Medium", "High").forEachIndexed { i, q -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(i == 2, {}); Text(q) } }
            Text("Volume: 60%")
            Slider(0.6f, {})
            TabRow(selectedTabIndex = 1) { listOf("Day", "Week", "Month").forEachIndexed { i, s -> Tab(i == 1, {}, text = { Text(s) }) } }
        }
    }
}

// ---------------------------------------------------------------- Open bottom sheet and drawer

@Preview(widthDp = 360, heightDp = 640)
@Composable
fun ShareSheetOpen() {
    MaterialTheme {
        ModalBottomSheetLayout(
            sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Expanded),
            sheetContent = {
                Column(Modifier.padding(16.dp)) {
                    Text("Share to", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    listOf("WhatsApp", "Instagram", "Copy link", "Email").forEach { Text(it, Modifier.fillMaxWidth().padding(vertical = 14.dp)) }
                }
            },
        ) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(16.dp)) { Text("Photo details screen") }
        }
    }
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
fun NavDrawerOpen() {
    MaterialTheme {
        ModalDrawer(
            drawerState = rememberDrawerState(DrawerValue.Open),
            drawerContent = {
                Column(Modifier.padding(16.dp)) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Indigo))
                    Text("Zain Khalid", Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
                    Divider(Modifier.padding(vertical = 12.dp))
                    listOf("Inbox", "Starred", "Sent", "Trash").forEach { Text(it, Modifier.padding(vertical = 10.dp)) }
                }
            },
        ) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(16.dp)) { Text("Main content") }
        }
    }
}

// ---------------------------------------------------------------- Scroll positions

@Preview(widthDp = 300, heightDp = 300, showBackground = true)
@Composable
fun ListScrolledToMiddle() {
    LazyColumn(state = rememberLazyListState(initialFirstVisibleItemIndex = 20)) {
        items(50) { Text("Row ${it + 1}", Modifier.fillMaxWidth().padding(12.dp)) }
    }
}

@Preview(widthDp = 300, heightDp = 200, showBackground = true)
@Composable
fun ScrolledColumn() {
    val scroll = rememberScrollState(initial = 400)
    Column(Modifier.verticalScroll(scroll)) {
        repeat(30) { Text("Line ${it + 1}", Modifier.padding(8.dp)) }
    }
}

@Preview(widthDp = 300, heightDp = 80, showBackground = true)
@Composable
fun HorizontalChipsOverflow() {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(12) { Text("Filter $it", Modifier.border(1.dp, Color.Gray, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) }
    }
}

// ---------------------------------------------------------------- Effects

@Preview(showBackground = true, widthDp = 360)
@Composable
fun VisualEffects() {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(Modifier.size(120.dp, 60.dp).blur(8.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded).background(palette[2]))
        Box(Modifier.size(120.dp, 60.dp).shadow(12.dp, RoundedCornerShape(12.dp)).background(Color.White))
        Box(Modifier.size(80.dp).rotate(30f).background(palette[0]))
        Box(Modifier.size(120.dp, 60.dp).graphicsLayer { rotationX = 40f; cameraDistance = 8f * density }.background(palette[3]))
        Text("Gradient text", style = TextStyle(brush = Brush.linearGradient(listOf(palette[0], palette[2])), fontSize = 32.sp, fontWeight = FontWeight.Black))
    }
}

// ---------------------------------------------------------------- State from flows and produceState

@Preview(showBackground = true, widthDp = 300)
@Composable
fun FlowDrivenProfile() {
    val status by remember {
        flow {
            emit("Connecting…")
            delay(400)
            emit("Online · 12 followers")
        }
    }.collectAsState("Idle")
    val count by produceState(0) { repeat(5) { delay(100); value++ } }
    Column(Modifier.padding(16.dp)) {
        Text("Ayesha Khan", fontWeight = FontWeight.Bold)
        Text(status)
        Text("$count new posts")
    }
}

// ---------------------------------------------------------------- Popup tooltip

@Preview(widthDp = 260, heightDp = 160, showBackground = true)
@Composable
fun TooltipShown() {
    Box(Modifier.padding(24.dp)) {
        Text("Hover me", Modifier.border(1.dp, Indigo).padding(8.dp))
        Popup(offset = androidx.compose.ui.unit.IntOffset(0, 90)) {
            Text("Copied to clipboard", color = Color.White, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xDD000000)).padding(8.dp))
        }
    }
}

// ---------------------------------------------------------------- Calendar and staggered grid

@Preview(showBackground = true, widthDp = 320)
@Composable
fun MonthCalendar() {
    Column(Modifier.padding(12.dp)) {
        Text("October 2026", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.Gray) } }
        val firstOffset = 3 // 1 Oct 2026 is a Thursday
        (0 until 5).forEach { week ->
            Row {
                (0 until 7).forEach { d ->
                    val day = week * 7 + d - firstOffset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape).background(if (day == 16) Indigo else Color.Transparent), contentAlignment = Alignment.Center) {
                        if (day in 1..31) Text("$day", color = if (day == 16) Color.White else Color.Black)
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 500, showBackground = true)
@Composable
fun PinterestGrid() {
    LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(2), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalItemSpacing = 8.dp) {
        items(10) { i ->
            Box(Modifier.fillMaxWidth().height((80 + (i * 37) % 120).dp).clip(RoundedCornerShape(12.dp)).background(palette[i % palette.size]), contentAlignment = Alignment.BottomStart) {
                Text("Pin $i", color = Color.White, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Dark theme screen

@Preview(widthDp = 360, heightDp = 300)
@Composable
fun DarkMusicPlayer() {
    MaterialTheme(colors = darkColors()) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2B1055), Color(0xFF0B0B12)))).padding(20.dp)) {
            Box(Modifier.size(120.dp).align(Alignment.CenterHorizontally).shadow(16.dp, RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(palette[1], palette[2]))))
            Spacer(Modifier.height(16.dp))
            Text("Midnight Drive", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("The Spyglass Band", color = Color(0xFFB0A8C8), modifier = Modifier.align(Alignment.CenterHorizontally))
            Slider(0.35f, {}, Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().offset(y = (-8).dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1:12", color = Color.Gray, fontSize = 12.sp); Text("3:28", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}
