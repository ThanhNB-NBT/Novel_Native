package com.thanhnb.novelnative

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTab() {
    val uid = Session.user?.id
    Column {
        PageHeader("Theo dõi · Đang đọc", "Tủ truyện", "藏") {
            IconButton({ Nav.push(Screen.Offline) }) { Icon(Icons.Rounded.DownloadDone, "Bản offline") }
        }
        if (uid == null) return NeedLogin("Đăng nhập để theo dõi truyện và lưu chỗ đang đọc", Icons.AutoMirrored.Rounded.Login)
        val l = load("library", uid) { Api.library() }
        val scope = rememberCoroutineScope()
        var confirm by remember { mutableStateOf<Novel?>(null) }
        Loaded(l) { novels ->
            PullToRefreshBox(false, l.reload) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = DockSpace)) {
                    if (novels.isEmpty()) item { Empty("Tủ truyện trống — bấm Theo dõi ở trang truyện, hoặc đọc một chương là truyện tự vào đây.") }
                    items(novels, key = { it.id }) { n ->
                        val cur = n.curChapter
                        val total = n.chaptersSource.coerceAtLeast(1)
                        // dòng phẳng, không khung: bìa + thông tin, tiến độ nằm ngay dưới
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { cur?.let { Nav.push(Screen.Read(n.id, n.title, it)) } ?: Nav.push(Screen.Detail(n.id)) }
                                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
                        ) {
                            Cover(n.cover, 76.dp, 103.dp, label = n.title)
                            Column(Modifier.weight(1f).heightIn(min = 103.dp).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(n.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOf(n.author, n.genres.firstOrNull().orEmpty()).filter(String::isNotEmpty).joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.weight(1f))
                                if (cur != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Chương $cur/${n.chaptersSource}", style = monoStyle(12, MaterialTheme.colorScheme.primary))
                                        Spacer(Modifier.weight(1f))
                                        Text("${(cur * 100 / total).coerceIn(0, 100)}%", style = monoStyle(12))
                                    }
                                    // tối thiểu 3% để truyện mới đọc vẫn thấy một vệt tiến độ, không phải một chấm
                                    LinearProgressIndicator(
                                        { (cur.toFloat() / total).coerceIn(.03f, 1f) },
                                        Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(.6f),
                                        gapSize = 0.dp, drawStopIndicator = {},
                                    )
                                } else Text("Đang theo dõi · ${n.chaptersSource} chương", style = monoStyle(12, MaterialTheme.colorScheme.primary))
                            }
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Tuỳ chọn", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem({ Text("Thông tin truyện") }, { menu = false; Nav.push(Screen.Detail(n.id)) })
                                    DropdownMenuItem({ Text("Xoá khỏi tủ") }, { menu = false; confirm = n })
                                }
                            }
                        }
                    }
                }
            }
        }
        confirm?.let { n ->
            AlertDialog(
                { confirm = null },
                confirmButton = {
                    TextButton({
                        confirm = null
                        scope.launch { runCatching { Api.removeReading(n.id) }; l.reload() }
                    }) { Text("Xoá") }
                },
                dismissButton = { TextButton({ confirm = null }) { Text("Huỷ") } },
                title = { Text("Xoá khỏi Tủ truyện?") },
                text = { Text("Bỏ theo dõi \"${n.title}\" và xoá tiến độ đọc.") },
            )
        }
    }
}

/** Số truyện có chương mới chưa xem — cho huy hiệu trên tab. */
@Composable
fun unreadCount(): Int {
    val uid = Session.user?.id ?: return 0
    val list = load("notif", uid) { Api.notifications() }.res?.getOrNull().orEmpty()
    return list.count { it.latestAt > Prefs.notifSeenAt }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotifTab() {
    val uid = Session.user?.id
    Column {
        if (uid == null) {
            PageHeader("Chương mới dịch xong", "Thông báo", "訊")
            return NeedLogin("Đăng nhập để nhận báo chương mới của truyện bạn theo dõi.", Icons.Rounded.NotificationsNone)
        }
        val l = load("notif", uid) { Api.notifications() }
        // Mở tab = đã xem; giữ mốc cũ trong phiên để vẫn tô đậm những dòng vừa mới
        val seenBefore = remember { Prefs.notifSeenAt }
        LaunchedEffect(Unit) { Prefs.setSeen(Instant.now().toString()) }
        val list = l.res?.getOrNull().orEmpty()
        PageHeader("Chương mới dịch xong", "Thông báo", "訊") {
            if (list.isNotEmpty()) IconButton({ list.forEach(Api::dismiss); l.reload() }) { Icon(Icons.Rounded.DeleteSweep, "Xoá tất cả") }
        }
        Loaded(l) { notifs ->
            PullToRefreshBox(false, l.reload) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = DockSpace), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (notifs.isEmpty()) item { Empty("Chưa có chương mới trong 7 ngày qua.") }
                    items(notifs, key = { it.novelId }) { n ->
                        val fresh = n.latestAt > seenBefore
                        PaperCard(onClick = { Nav.push(Screen.Read(n.novelId, n.title, n.chapters.first())) }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Cover(n.cover, 44.dp, 60.dp, label = n.title, flat = true)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(n.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (n.chapters.size == 1) "Chương ${n.chapters[0]} đã dịch xong"
                                        else "${n.chapters.size} chương mới (${n.chapters.first()}–${n.chapters.last()})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (fresh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (fresh) FontWeight.SemiBold else null,
                                    )
                                }
                                if (fresh) Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                IconButton({ Api.dismiss(n); l.reload() }) { Icon(Icons.Rounded.Close, "Xoá", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountTab() {
    val u = Session.user
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("", "Tôi", "我")
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (u == null) {
                Text(
                    "Đăng nhập để lưu truyện đang đọc và tiến độ.", Modifier.fillMaxWidth().padding(top = 24.dp),
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
                )
                Button({ Nav.push(Screen.Login) }, Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp), contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp)) {
                    Text("Đăng nhập", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
            } else {
                val prof = load("profile", u.id) { Api.profile() }.res?.getOrNull()
                val stats = load("stats", u.id) { Api.readStats() }.res?.getOrNull()
                val name = prof?.name ?: u.email.substringBefore('@')
                PaperCard(onClick = { Nav.push(Screen.EditProfile) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(prof?.avatar, name, 56.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(u.email, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Sửa hồ sơ", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelMedium, color = cs.primary, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(Session::signOut, border = androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant)) { Text("Đăng xuất") }
                    }
                }
                Spacer(Modifier.height(10.dp))
                StatsCard(stats?.first ?: 0, stats?.second ?: 0, Api.liveStreak(prof))
            }

            Eyebrow("Giao diện", Modifier.padding(start = 4.dp, top = 28.dp, bottom = 10.dp))
            // segmented: viên nhấn đặc trên thẻ giấy
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.card).border(1.dp, cs.outlineVariant, RoundedCornerShape(20.dp)).padding(6.dp)) {
                listOf("Hệ thống", "Sáng", "Tối").forEachIndexed { i, label ->
                    val on = Prefs.themeMode == i
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) cs.primary else Color.Transparent)
                            .clickable { Prefs.setTheme(i) }.padding(vertical = 12.dp),
                        Alignment.Center,
                    ) { Text(label, color = if (on) cs.onPrimary else cs.onSurfaceVariant, fontSize = 16.sp) }
                }
            }

            Text("Màu nhấn", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accents.forEachIndexed { i, a ->
                    Swatch(a.name, listOf(a.accent, a.seal, a.soft), cs.card, Prefs.accent == i, Modifier.weight(1f)) { Prefs.pickAccent(i) }
                }
            }
            Text("Nền", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                papers.forEachIndexed { i, p ->
                    Swatch(p.name, listOf(p.surfaceAlt, p.inkSoft, p.ink), p.bg, Prefs.paper == i, Modifier.weight(1f), ink = p.ink) { Prefs.pickPaper(i) }
                }
            }

            Eyebrow("Ứng dụng", Modifier.padding(start = 4.dp, top = 28.dp, bottom = 10.dp))
            PaperCard {
                var ahead by remember { mutableStateOf(Prefs.autoAhead) }
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(cs.primaryContainer, RoundedCornerShape(12.dp)), Alignment.Center) {
                        Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = cs.primary)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text("Tự dịch trước 15 chương", style = MaterialTheme.typography.titleMedium)
                        Text("Mở chương nào thì xếp dịch sẵn 15 chương kế tiếp", style = MaterialTheme.typography.labelMedium)
                    }
                    Switch(ahead, { ahead = it; Prefs.autoAhead = it })
                }
            }
            Spacer(Modifier.height(10.dp))
            UpdateCard()
            Text(
                "Gác Truyện native · ${BuildConfig.VERSION_NAME}", Modifier.fillMaxWidth().padding(top = 20.dp, bottom = DockSpace),
                textAlign = TextAlign.Center, style = monoStyle(),
            )
        }
    }
}

/** Ô chọn màu: 3 chấm chồng nhau + tên; đang chọn thì viền son dày. */
@Composable
private fun Swatch(name: String, dots: List<Color>, bg: Color, on: Boolean, modifier: Modifier, ink: Color? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(bg).border(if (on) 2.dp else 1.dp, if (on) cs.primary else cs.outlineVariant, shape)
            .clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            dots.forEachIndexed { i, c -> Box(Modifier.padding(start = (i * 12).dp).size(18.dp).background(c, CircleShape)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(name, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = ink?.copy(alpha = if (on) 1f else .7f) ?: if (on) cs.onSurface else cs.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun LoginScreen() {
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val a = accents.getOrElse(Prefs.accent) { accents[0] }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        // đầu trang dải son đậm như bản Flutter
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                .background(Brush.linearGradient(listOf(a.accent, a.deep, Color(0xFF4A1A10))))
                .statusBarsPadding().padding(start = 8.dp, end = 20.dp, bottom = 56.dp),
        ) {
            IconButton(Nav::pop) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Quay lại", tint = Color.White) }
            Column(Modifier.padding(start = 12.dp, top = 12.dp)) {
                Box(Modifier.size(64.dp).background(Color.White.copy(.18f), RoundedCornerShape(18.dp)).border(1.dp, Color.White.copy(.3f), RoundedCornerShape(18.dp)), Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(30.dp), Color.White)
                }
                Spacer(Modifier.height(20.dp))
                Text("TÀNG THƯ CÁC · TRUY CẬP", color = Color.White.copy(.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontFamily = Jakarta)
                Text("Kho truyện dịch của bạn", style = MaterialTheme.typography.displaySmall, color = Color.White)
            }
        }
        PaperCard(Modifier.padding(horizontal = 20.dp).offset(y = (-32).dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Đăng nhập", style = MaterialTheme.typography.headlineSmall)
                Text("Dùng tài khoản được cấp để đọc và yêu cầu dịch.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), label = { Text("Mật khẩu") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                err?.let { Text(it, color = cs.error, style = MaterialTheme.typography.labelMedium) }
                Button(
                    {
                        err = when {
                            !email.contains('@') -> "Nhập email hợp lệ"
                            pw.length < 6 -> "Mật khẩu tối thiểu 6 ký tự"
                            else -> null
                        }
                        if (err != null) return@Button
                        busy = true
                        scope.launch {
                            runCatching { Session.signIn(email.trim(), pw) }
                                .onSuccess { Nav.tab = 0; Nav.pop() }
                                .onFailure { err = friendly(it) }
                            busy = false
                        }
                    },
                    Modifier.fillMaxWidth().padding(top = 4.dp), enabled = !busy, contentPadding = PaddingValues(vertical = 14.dp),
                ) { Text(if (busy) "Đang đăng nhập…" else "Đăng nhập", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
