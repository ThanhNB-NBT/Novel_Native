package com.thanhnb.novelnative

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTab() {
    val uid = Session.user?.id
    Column {
        TopAppBar(title = { Text("Tủ truyện") })
        if (uid == null) return NeedLogin("Đăng nhập để lưu truyện theo dõi và tiến độ đọc.")
        val l = load("library", uid) { Api.library() }
        val scope = rememberCoroutineScope()
        var confirm by remember { mutableStateOf<Novel?>(null) }
        Loaded(l) { novels ->
            PullToRefreshBox(false, l.reload) {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (novels.isEmpty()) item { Empty("Tủ truyện trống — bấm Theo dõi ở trang truyện, hoặc đọc một chương là truyện tự vào đây.") }
                    items(novels, key = { it.id }) { n ->
                        NovelRow(
                            n,
                            sub = n.curChapter?.let { "Đang đọc chương $it / ${n.chaptersSource}" } ?: "Đang theo dõi · ${n.chaptersSource} chương",
                            onClick = { n.curChapter?.let { Nav.push(Screen.Read(n.id, n.title, it)) } ?: Nav.push(Screen.Detail(n.id)) },
                        ) {
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Tuỳ chọn") }
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
            TopAppBar(title = { Text("Thông báo") })
            return NeedLogin("Đăng nhập để nhận báo chương mới của truyện bạn theo dõi.")
        }
        val l = load("notif", uid) { Api.notifications() }
        // Mở tab = đã xem; giữ mốc cũ trong phiên để vẫn tô đậm những dòng vừa mới
        val seenBefore = remember { Prefs.notifSeenAt }
        LaunchedEffect(Unit) { Prefs.setSeen(Instant.now().toString()) }
        val list = l.res?.getOrNull().orEmpty()
        TopAppBar(
            title = { Text("Thông báo") },
            actions = { if (list.isNotEmpty()) TextButton({ list.forEach(Api::dismiss); l.reload() }) { Text("Xoá hết") } },
        )
        Loaded(l) { notifs ->
            PullToRefreshBox(false, l.reload) {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (notifs.isEmpty()) item { Empty("Chưa có chương mới trong 7 ngày qua.") }
                    items(notifs, key = { it.novelId }) { n ->
                        ListItem(
                            leadingContent = { Cover(n.cover, 40.dp, 56.dp) },
                            headlineContent = { Text(n.title, maxLines = 1) },
                            supportingContent = {
                                Text(
                                    if (n.chapters.size == 1) "Chương ${n.chapters[0]} đã dịch xong"
                                    else "${n.chapters.size} chương mới (${n.chapters.first()}–${n.chapters.last()})",
                                )
                            },
                            trailingContent = { IconButton({ Api.dismiss(n); l.reload() }) { Icon(Icons.Default.Close, "Xoá") } },
                            colors = if (n.latestAt > seenBefore) ListItemDefaults.colors(MaterialTheme.colorScheme.secondaryContainer) else ListItemDefaults.colors(),
                            modifier = Modifier.clickable { Nav.push(Screen.Read(n.novelId, n.title, n.chapters.first())) },
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountTab() {
    val u = Session.user
    Column {
        TopAppBar(title = { Text("Tôi") })
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (u == null) {
                Text("Chưa đăng nhập")
                Button({ Nav.push(Screen.Login) }) { Text("Đăng nhập") }
            } else {
                Text(u.email, style = MaterialTheme.typography.titleMedium)
                OutlinedButton(Session::signOut) { Text("Đăng xuất") }
            }
            HorizontalDivider()
            Text("Giao diện", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow {
                listOf("Hệ thống", "Sáng", "Tối").forEachIndexed { i, label ->
                    SegmentedButton(Prefs.themeMode == i, { Prefs.setTheme(i) }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
                }
            }
            var ahead by remember { mutableStateOf(Prefs.autoAhead) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tự dịch trước 15 chương")
                    Text("Mở chương nào thì xếp dịch sẵn 15 chương kế tiếp", style = MaterialTheme.typography.bodySmall)
                }
                Switch(ahead, { ahead = it; Prefs.autoAhead = it })
            }
            HorizontalDivider()
            Text("Phiên bản ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun LoginScreen() {
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { Bar("Đăng nhập") }) { pad ->
        Column(Modifier.padding(pad).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Dùng tài khoản được cấp để đọc và yêu cầu dịch.")
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), label = { Text("Mật khẩu") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
                Modifier.fillMaxWidth(), enabled = !busy,
            ) { Text(if (busy) "Đang đăng nhập…" else "Đăng nhập") }
        }
    }
}
