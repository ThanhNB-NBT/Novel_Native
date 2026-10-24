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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
                        // danh sách phẳng, không khung: bìa + thông tin, tiến độ nằm ngay trong dòng
                        Column(Modifier.bounceClick { Nav.push(Screen.Detail(n.id)) }.padding(start = 20.dp, top = 10.dp, bottom = 10.dp)) {
                        Row {
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
                            // cột phải: menu ⋮ phía trên, nút Đọc tiếp phía dưới (một chạm vào thẳng chương đang dở)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box {
                                    IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Tuỳ chọn", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    DropdownMenu(menu, { menu = false }) {
                                        DropdownMenuItem({ Text("Thông tin truyện") }, { menu = false; Nav.push(Screen.Detail(n.id)) })
                                        DropdownMenuItem({ Text("Xoá khỏi tủ") }, { menu = false; confirm = n })
                                    }
                                }
                                FilledTonalIconButton({ Nav.push(Screen.Read(n.id, n.title, cur ?: 1)) }) {
                                    Icon(Icons.AutoMirrored.Rounded.MenuBook, if (cur == null) "Đọc từ chương 1" else "Đọc tiếp chương $cur")
                                }
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
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = DockSpace)) {
                    if (notifs.isEmpty()) item { Empty("Chưa có chương mới trong 7 ngày qua.") }
                    items(notifs, key = { it.novelId }) { n ->
                        val fresh = n.latestAt > seenBefore
                        Column(Modifier.bounceClick { Nav.push(Screen.Read(n.novelId, n.title, n.chapters.first())) }) {
                            Row(Modifier.padding(start = 20.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                // một thẻ duy nhất: hồ sơ · số liệu · lịch âm
                PaperCard {
                    Row(Modifier.bounceClick { Nav.push(Screen.EditProfile) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val nm = prof?.name ?: u.email.substringBefore('@')
                        Avatar(prof?.avatar, nm, 52.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(nm, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(u.email, Modifier.padding(top = 2.dp), style = monoStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(Session::signOut) { Icon(Icons.AutoMirrored.Rounded.Logout, "Đăng xuất", tint = cs.onSurfaceVariant) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant.copy(.5f))
                    StatsCard(stats?.first ?: 0, stats?.second ?: 0, Api.liveStreak(prof))
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant.copy(.5f))
                    Box(Modifier.padding(16.dp, 12.dp)) { LunarCard() }
                }
            }

            SectionLabel("Giao diện")
            SettingGroup(
                { PrefRow("Chế độ") {
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(cs.onSurface.copy(.07f)).padding(3.dp)) {
                        listOf("Tự động", "Sáng", "Tối").forEachIndexed { i, label ->
                            val on = Prefs.themeMode == i
                            Box(Modifier.clip(RoundedCornerShape(9.dp)).background(if (on) cs.primary else Color.Transparent).bounceClick { Prefs.setTheme(i) }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                                Text(label, color = if (on) cs.onPrimary else cs.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } },
                { PrefRow("Màu nhấn") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        accents.forEachIndexed { i, a -> Dot(a.accent, Prefs.accent == i, cs.primary) { Prefs.pickAccent(i) } }
                    }
                } },
                { PrefRow("Màu nền") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        papers.forEachIndexed { i, p -> Dot(p.bg, Prefs.paper == i, cs.primary, ring = p.ink.copy(.25f)) { Prefs.pickPaper(i) } }
                    }
                } },
                inset = 16.dp,
            )

            if (u != null) {
                SectionLabel("Thư viện")
                SettingGroup(
                    { SettingTile(Icons.Rounded.DownloadDone, "Bản offline", "Truyện đã tải để đọc không cần mạng", { Nav.push(Screen.Offline) }) },
                    { SettingTile(Icons.Rounded.HourglassBottom, "Hàng đợi dịch", "Chương đang chờ và đang dịch", { Nav.push(Screen.Queue) }) },
                )
                SectionLabel("Tài khoản")
                val isAdmin = load("admin", u.id) { Api.isAdmin() }.res?.getOrNull() == true
                SettingGroup(
                    { SettingTile(Icons.Rounded.PersonOutline, "Sửa thông tin", "Tên hiển thị và ảnh đại diện", { Nav.push(Screen.EditProfile) }) },
                    *(if (isAdmin) arrayOf<@Composable () -> Unit>({ SettingTile(Icons.Rounded.AdminPanelSettings, "Quản trị", "Worker, crawl, truyện, báo cáo", { Nav.push(Screen.Admin) }) }) else emptyArray()),
                )
            }

            SectionLabel("Ứng dụng")
            var ahead by remember { mutableStateOf(Prefs.autoAhead) }
            SettingGroup(
                {
                    SettingTile(
                        Icons.AutoMirrored.Rounded.MenuBook, "Tự dịch trước 15 chương", "Mở chương nào thì xếp dịch sẵn 15 chương kế tiếp",
                        onClick = { ahead = !ahead; Prefs.autoAhead = ahead },
                        trailing = { Switch(ahead, { ahead = it; Prefs.autoAhead = it }) },
                    )
                },
                { SettingTile(Icons.Rounded.Lightbulb, "Hướng dẫn sử dụng", "Mẹo đọc, lưu và yêu cầu dịch", { Nav.push(Screen.Guide) }) },
                { UpdateTile() },
            )
            Text(
                "Gác Truyện native · ${BuildConfig.VERSION_NAME}", Modifier.fillMaxWidth().padding(top = 20.dp, bottom = DockSpace),
                textAlign = TextAlign.Center, style = monoStyle(),
            )
        }
    }
}

@Composable
fun SectionLabel(text: String) =
    Text(text, Modifier.padding(start = 8.dp, top = 26.dp, bottom = 10.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Nhóm dòng cài đặt trong một thẻ, ngăn bằng vạch mảnh thụt vào. */
@Composable
fun SettingGroup(vararg tiles: @Composable () -> Unit, inset: Dp = 66.dp) = PaperCard {
    tiles.forEachIndexed { i, t ->
        if (i > 0) HorizontalDivider(Modifier.padding(start = inset, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(.5f))
        t()
    }
}

@Composable
fun SettingTile(icon: ImageVector, label: String, subtitle: String?, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().bounceClick(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(cs.primary.copy(.12f)), Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), cs.primary)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (trailing != null) trailing() else Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant)
    }
}

/** Một dòng cài đặt: nhãn bên trái, bộ chọn bên phải. */
@Composable
private fun PrefRow(label: String, control: @Composable () -> Unit) =
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        control()
    }

/** Chấm chọn màu nhỏ; đang chọn thì có vòng viền ngoài. */
@Composable
private fun Dot(fill: Color, on: Boolean, sel: Color, ring: Color = Color.Transparent, onClick: () -> Unit) {
    Box(Modifier.size(30.dp).border(2.dp, if (on) sel else Color.Transparent, CircleShape).padding(3.dp).background(fill, CircleShape).border(1.dp, ring, CircleShape).bounceClick(onClick = onClick))
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
