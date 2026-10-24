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

/** Số liệu đọc: chuỗi ngày (vàng khi còn chuỗi) | đang đọc | chương đã đọc, ngăn bằng vạch mảnh. */
@Composable
fun StatsCard(novels: Int, chapters: Int, streak: Int) {
    val cs = MaterialTheme.colorScheme
    val hot = streak > 0
    val gold = cs.secondary
    PaperCard {
        Row(
            Modifier.fillMaxWidth().background(if (hot) gold.copy(.08f) else androidx.compose.ui.graphics.Color.Transparent).padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stat("$streak", "NGÀY LIÊN TIẾP", if (hot) gold else cs.onSurface, Modifier.weight(1f), fire = true, fireColor = if (hot) gold else cs.onSurfaceVariant)
            Box(Modifier.width(1.dp).height(30.dp).background(cs.outlineVariant.copy(.7f)))
            Stat("$novels", "ĐANG ĐỌC", cs.onSurface, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(30.dp).background(cs.outlineVariant.copy(.7f)))
            Stat("$chapters", "CHƯƠNG ĐÃ ĐỌC", cs.onSurface, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(
    value: String, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier,
    fire: Boolean = false, fireColor: androidx.compose.ui.graphics.Color = color,
) = Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (fire) Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(20.dp), fireColor)
        Text(value, style = monoStyle(22, color), fontWeight = FontWeight.Bold)
    }
    Text(label, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp, fontSize = 10.5.sp, maxLines = 1)
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
                        .bounceClick { avatar = if (on) null else e },
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
