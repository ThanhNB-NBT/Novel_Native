package com.thanhnb.novelnative

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Ảnh đại diện chọn sẵn (emoji) — lưu thẳng ký tự vào profiles.avatar_url, giống bản Flutter. */
val avatarPresets = listOf(
    "🐉", "⚔️", "🦊", "🔥", "🏯", "🐼", "🎭", "👑", "☯️", "🐯", "🦅", "🐍",
    "🪷", "🏮", "🧧", "⚡", "💎", "🦉",
    "🗿", "🐸", "💀", "🤡", "👽", "🤖", "🦆", "🐧", "😼", "🍄", "🫠", "😴",
)

/** Ô avatar bo vuông mềm: emoji nếu đã chọn, không thì chữ cái đầu của tên. */
@Composable
fun Avatar(emoji: String?, name: String, size: Dp) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(size * 0.28f)
    Box(Modifier.size(size).clip(shape).background(cs.primaryContainer.copy(.6f)).border(1.dp, cs.primary.copy(.35f), shape), Alignment.Center) {
        if (!emoji.isNullOrEmpty()) Text(emoji, fontSize = (size.value * .5f).sp)
        else Text(name.take(1).uppercase().ifEmpty { "?" }, color = cs.primary, fontSize = (size.value * .45f).sp, fontWeight = FontWeight.Bold)
    }
}

/** Số liệu đọc: truyện đang đọc · tổng chương đã tiến · chuỗi ngày đọc liên tiếp (vàng khi còn chuỗi). */
@Composable
fun StatsCard(novels: Int, chapters: Int, streak: Int) {
    val cs = MaterialTheme.colorScheme
    val hot = streak > 0
    PaperCard {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            Stat("$novels", "truyện đang đọc", cs.onSurface, Modifier.weight(1f))
            Stat("$chapters", "chương đã đọc", cs.onSurface, Modifier.weight(1f))
            Stat("$streak", if (hot) "ngày liên tiếp" else "chưa có chuỗi", if (hot) cs.secondary else cs.onSurfaceVariant, Modifier.weight(1f), fire = hot)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier, fire: Boolean = false) =
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (fire) Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(20.dp), color)
            Text(value, style = monoStyle(22, color), fontWeight = FontWeight.Bold)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }

/** Sửa hồ sơ: tên hiển thị + ảnh đại diện (bấm lại ảnh đang chọn để bỏ). */
@Composable
fun EditProfileScreen() {
    val l = load("profile", Session.user?.id) { Api.profile() }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Hồ sơ cá nhân", "Hồ sơ của bạn", "我", back = true)
        Loaded(l) { EditForm(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditForm(p: Profile?) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val email = Session.user?.email.orEmpty()
    var name by remember { mutableStateOf(p?.name ?: email.substringBefore('@')) }
    var avatar by remember { mutableStateOf(p?.avatar?.takeIf { it in avatarPresets }) }
    var saving by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp, 4.dp, 20.dp, 24.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.primaryContainer.copy(.45f)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(avatar, name, 64.dp)
            Column(Modifier.padding(start = 14.dp)) {
                Text(name.trim().ifEmpty { "Bạn đọc" }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(email, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
        Text("Tên hiển thị", Modifier.padding(top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Ví dụ: Bạn đọc") })

        Text("Ảnh đại diện", Modifier.padding(top = 20.dp, bottom = 10.dp), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            avatarPresets.forEach { e ->
                val on = avatar == e
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier.size(50.dp).clip(shape).background(if (on) cs.primaryContainer else cs.card)
                        .border(if (on) 2.dp else 1.dp, if (on) cs.primary else cs.outlineVariant, shape)
                        .clickable { avatar = if (on) null else e },
                    Alignment.Center,
                ) { Text(e, fontSize = 24.sp) }
            }
        }

        Button(
            {
                val n = name.trim()
                if (n.isEmpty()) return@Button Toast.makeText(ctx, "Tên hiển thị không được để trống", Toast.LENGTH_SHORT).show()
                saving = true
                scope.launch {
                    runCatching { Api.updateProfile(n, avatar.orEmpty()) }
                        .onSuccess { Toast.makeText(ctx, "Đã lưu hồ sơ", Toast.LENGTH_SHORT).show(); Nav.pop() }
                        .onFailure { Toast.makeText(ctx, "Lỗi lưu hồ sơ: ${friendly(it)}", Toast.LENGTH_SHORT).show() }
                    saving = false
                }
            },
            Modifier.fillMaxWidth().padding(top = 24.dp).height(50.dp), enabled = !saving,
        ) {
            Icon(Icons.Rounded.Check, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (saving) "Đang lưu" else "Lưu thay đổi", fontWeight = FontWeight.Bold)
        }
    }
}
