package com.thanhnb.novelnative

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
    var reqOpen by rememberSaveable { mutableStateOf(false) }
    Column {
        if (reqOpen) RequestSheet { reqOpen = false }
        PageHeader("Theo dõi · Đang đọc", "Tủ truyện", "藏") {
            IconButton({ if (uid == null) Nav.push(Screen.Login) else reqOpen = true }) { Icon(Icons.Rounded.TravelExplore, "Yêu cầu truyện mới") }
            IconButton({ Nav.push(Screen.Offline) }) { Icon(Icons.Rounded.DownloadDone, "Bản offline") }
        }
        if (uid == null) return NeedLogin("Đăng nhập để theo dõi truyện và lưu chỗ đang đọc", Icons.AutoMirrored.Rounded.Login)
        val l = load("library", uid) { Api.library() }
        val scope = rememberCoroutineScope()
        var confirm by remember { mutableStateOf<Novel?>(null) }
        var grid by rememberSaveable { mutableStateOf(true) }
        Loaded(l) { novels ->
            val hero = novels.firstOrNull { it.curChapter != null }
            val shown = novels.filter { it.id != hero?.id }
            PullToRefreshBox(false, l.reload) {
                if (novels.isEmpty()) LazyColumn(Modifier.fillMaxSize()) { item { Empty("Tủ truyện trống — bấm Theo dõi ở trang truyện, hoặc đọc một chương là truyện tự vào đây.") } }
                else LazyVerticalGrid(
                    GridCells.Fixed(if (grid) 3 else 1), Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = DockSpace),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(if (grid) 20.dp else 4.dp),
                ) {
                    if (hero != null) item(span = { GridItemSpan(maxLineSpan) }) { ContinueCard(hero) }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${novels.size} truyện", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            IconButton({ grid = !grid }, Modifier.size(36.dp)) {
                                Icon(if (grid) Icons.Rounded.ViewList else Icons.Rounded.GridView, if (grid) "Dạng danh sách" else "Dạng lưới", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    items(shown, key = { it.id }) { n -> if (grid) ShelfTile(n) { confirm = n } else ShelfRow(n) { confirm = n } }
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
            var colorSheet by rememberSaveable { mutableStateOf(false) }
            if (colorSheet) ColorSheet { colorSheet = false }
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
                {
                    Row(Modifier.fillMaxWidth().clickable { colorSheet = true }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Bảng màu", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.size(18.dp).background(accents.getOrElse(Prefs.accent) { accents[0] }.accent, CircleShape))
                        Box(Modifier.padding(start = 6.dp).size(18.dp).background(papers.getOrElse(Prefs.paper) { papers[0] }.bg, CircleShape).border(1.dp, cs.outline, CircleShape))
                        Text(
                            presets.firstOrNull { it.second == Prefs.accent && it.third == Prefs.paper }?.first ?: "Tuỳ chỉnh",
                            Modifier.padding(start = 10.dp), color = cs.onSurfaceVariant, fontSize = 13.sp,
                        )
                        Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant)
                    }
                },
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
            SettingGroup(
                { SettingTile(Icons.Rounded.Lightbulb, "Hướng dẫn sử dụng", "Mẹo đọc, lưu và yêu cầu dịch", { Nav.push(Screen.Guide) }) },
                { SettingTile(Icons.Rounded.NewReleases, "Có gì mới", "Ghi chú các bản phát hành gần nhất", { Nav.push(Screen.Changelog) }) },
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

/** Bảng chọn màu đầy đủ: bộ màu hệ điều hành ở trên, màu nhấn và màu nền lẻ ở dưới. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ColorSheet(dismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(dismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            accentGroups.forEach { (group, names) ->
                Text(group, style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    names.forEach { name ->
                        val i = accents.indexOfFirst { it.name == name }
                        if (i < 0) return@forEach
                        val a = accents[i]
                        val on = Prefs.accent == i
                        Column(
                            Modifier.width(104.dp).clip(RoundedCornerShape(16.dp)).background(if (on) cs.surface else cs.onSurface.copy(.04f))
                                .border(if (on) 2.dp else 1.dp, if (on) cs.primary else cs.outlineVariant, RoundedCornerShape(16.dp))
                                .bounceClick { Prefs.pickAccent(i) }.padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                (listOf(a.accent) + a.extra).forEach { Box(Modifier.size(20.dp).background(it, CircleShape).border(1.5.dp, cs.surface, CircleShape)) }
                            }
                            Text(a.name.removePrefix("$group ").removePrefix("iOS ").ifEmpty { a.name }, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
            }
            Text("Bộ màu hệ điều hành", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (name, ai, pi) ->
                    val on = Prefs.accent == ai && Prefs.paper == pi
                    Row(
                        Modifier.clip(CircleShape).background(if (on) cs.primary.copy(.14f) else cs.onSurface.copy(.06f))
                            .border(1.5.dp, if (on) cs.primary else Color.Transparent, CircleShape)
                            .bounceClick { Prefs.pickAccent(ai); Prefs.pickPaper(pi) }.padding(start = 8.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(18.dp).background(accents[ai].accent, CircleShape).border(2.dp, papers[pi].bg, CircleShape))
                        Text(name, Modifier.padding(start = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text("Màu nhấn", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                accents.forEachIndexed { i, a -> Dot(a.accent, Prefs.accent == i, cs.primary) { Prefs.pickAccent(i) } }
            }
            Text("Màu nền", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                papers.forEachIndexed { i, p -> Dot(p.bg, Prefs.paper == i, cs.primary, ring = p.ink.copy(.25f)) { Prefs.pickPaper(i) } }
            }
        }
    }
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


private fun pct(n: Novel) = ((n.curChapter ?: 0) * 100 / n.chaptersSource.coerceAtLeast(1)).coerceIn(0, 100)

@Composable
private fun ShelfMenu(show: Boolean, dismiss: () -> Unit, n: Novel, remove: () -> Unit) {
    val cur = n.curChapter
    DropdownMenu(show, dismiss) {
        DropdownMenuItem({ Text(if (cur == null) "Đọc từ chương 1" else "Đọc tiếp chương $cur") }, { dismiss(); Nav.push(Screen.Read(n.id, n.title, cur ?: 1)) })
        DropdownMenuItem({ Text("Thông tin truyện") }, { dismiss(); Nav.push(Screen.Detail(n.id)) })
        DropdownMenuItem({ Text("Xoá khỏi tủ") }, { dismiss(); remove() })
    }
}

/** Thẻ lớn "Đọc tiếp" cho truyện vừa đọc dở gần nhất. */
@Composable
private fun ContinueCard(n: Novel) {
    val cs = MaterialTheme.colorScheme
    val cur = n.curChapter ?: 1
    val dark = cs.background.luminance() < .5f
    // màn sáng: khối màu nhấn đậm + chữ trắng để nổi như màn tối; màn tối: thẻ giấy tối
    val fg = if (dark) cs.onSurface else Color.White
    val sub = if (dark) cs.onSurfaceVariant else Color.White.copy(.82f)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).shadow(if (dark) 0.dp else 10.dp, RoundedCornerShape(22.dp), ambientColor = cs.primary.copy(.5f), spotColor = cs.secondary.copy(.55f))
            .clip(RoundedCornerShape(22.dp))
            .then(if (dark) Modifier.border(1.dp, cs.outlineVariant, RoundedCornerShape(22.dp)) else Modifier)
            .background(if (dark) Brush.linearGradient(listOf(cs.surface, cs.surface)) else Brush.linearGradient(listOf(cs.primary, androidx.compose.ui.graphics.lerp(cs.primary, Color.Black, .42f))))
            .bounceClick { Nav.push(Screen.Detail(n.id)) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(n.cover, 84.dp, 118.dp, label = n.title)
        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("TIẾP TỤC ĐỌC", style = MaterialTheme.typography.labelSmall, color = cs.tertiary, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
            Text(n.title, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Chương $cur/${n.chaptersSource}" + if (pct(n) > 0) " · ${pct(n)}%" else "", style = monoStyle(12, sub))
            LinearProgressIndicator(
                { (cur.toFloat() / n.chaptersSource.coerceAtLeast(1)).coerceIn(.03f, 1f) },
                Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                color = cs.tertiary, trackColor = if (dark) cs.onSurface.copy(.1f) else Color.White.copy(.28f), gapSize = 0.dp, drawStopIndicator = {},
            )
        }
        Box(
            Modifier.padding(start = 8.dp).size(52.dp).clip(CircleShape).background(if (dark) cs.primary else Color.White)
                .bounceClick { Nav.push(Screen.Read(n.id, n.title, cur)) },
            Alignment.Center,
        ) { Icon(Icons.Rounded.PlayArrow, "Đọc tiếp", Modifier.size(30.dp), if (dark) cs.onPrimary else cs.primary) }
    }
}

/** Ô bìa trong lưới 3 cột: huy hiệu %, vạch tiến độ đè chân bìa; chạm → trang truyện, giữ → menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfTile(n: Novel, remove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.combinedClickable(onClick = { Nav.push(Screen.Detail(n.id)) }, onLongClick = { menu = true })) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = maxWidth
            Cover(n.cover, w, w * 1.45f, label = n.title)
            ShelfMenu(menu, { menu = false }, n, remove)
        }
        if (n.curChapter != null) LinearProgressIndicator(
            { (n.curChapter.toFloat() / n.chaptersSource.coerceAtLeast(1)).coerceIn(.03f, 1f) },
            Modifier.padding(top = 8.dp).fillMaxWidth().height(3.dp).clip(CircleShape),
            color = cs.secondary, trackColor = cs.onSurface.copy(.1f), gapSize = 0.dp, drawStopIndicator = {},
        ) else Spacer(Modifier.height(11.dp))
        Text(n.title, Modifier.padding(top = 6.dp), fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
        Text(if (n.curChapter != null) "Ch. ${n.curChapter}" + if (pct(n) > 0) " · ${pct(n)}%" else "" else "${n.chaptersSource} chương", Modifier.padding(top = 2.dp), style = monoStyle(11))
    }
}

/** Một dòng trên kệ: chạm → trang truyện; ⋮ → đọc tiếp / thông tin / xoá. */
@Composable
private fun ShelfRow(n: Novel, remove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val cur = n.curChapter
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().bounceClick { Nav.push(Screen.Detail(n.id)) }.padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(n.cover, 48.dp, 67.dp, label = n.title, flat = true)
        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(n.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (cur != null) {
                Text("Chương $cur/${n.chaptersSource}", style = monoStyle(12))
                LinearProgressIndicator(
                    { (cur.toFloat() / n.chaptersSource.coerceAtLeast(1)).coerceIn(.03f, 1f) },
                    Modifier.padding(top = 3.dp).fillMaxWidth(.7f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = cs.primary, trackColor = cs.outlineVariant.copy(.6f), gapSize = 0.dp, drawStopIndicator = {},
                )
            } else Text("${n.chaptersSource} chương", style = monoStyle(12))
        }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Tuỳ chọn", tint = cs.onSurfaceVariant) }
            ShelfMenu(menu, { menu = false }, n, remove)
        }
    }
}
