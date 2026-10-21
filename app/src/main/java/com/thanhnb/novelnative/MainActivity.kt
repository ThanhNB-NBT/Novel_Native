package com.thanhnb.novelnative

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Session.load()
        Nav.tab = if (Session.user != null) 0 else 1 // chưa đăng nhập → mở Khám phá
        enableEdgeToEdge()
        setContent {
            AppTheme {
                // Giữ trạng thái cuộn (rememberSaveable) của từng màn trong ngăn xếp khi màn trên đè lên
                val holder = rememberSaveableStateHolder()
                // Khoá theo cả vị trí lẫn màn: đổi chương (replace) phải ra state mới, không mang vị trí cuộn chương cũ
                val stack = Nav.stack
                val key = "${stack.lastIndex}:${stack.last()}"
                BackHandler(stack.size > 1) { holder.removeState(key); Nav.pop() }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    holder.SaveableStateProvider(key) {
                        when (val s = stack.last()) {
                            Screen.Tabs -> Tabs()
                            is Screen.Detail -> DetailScreen(s.id)
                            is Screen.Read -> ReaderScreen(s)
                            is Screen.More -> MoreScreen(s.section)
                            Screen.Search -> SearchScreen()
                            Screen.Login -> LoginScreen()
                        }
                    }
                }
            }
        }
    }
}

private class TabItem(val label: String, val on: ImageVector, val off: ImageVector)

private val tabs = listOf(
    TabItem("Tủ truyện", Icons.Rounded.Bookmarks, Icons.Outlined.Bookmarks),
    TabItem("Khám phá", Icons.Rounded.Explore, Icons.Outlined.Explore),
    TabItem("Thông báo", Icons.Rounded.Notifications, Icons.Outlined.Notifications),
    TabItem("Tôi", Icons.Rounded.Person, Icons.Outlined.Person),
)

@Composable
private fun Tabs() {
    val holder = rememberSaveableStateHolder()
    val unread = unreadCount()
    Box(Modifier.fillMaxSize()) {
        // Nội dung tràn xuống dưới dock; mỗi tab tự chừa DockSpace ở cuối danh sách
        holder.SaveableStateProvider("tab${Nav.tab}") {
            when (Nav.tab) {
                0 -> LibraryTab()
                1 -> ExploreTab()
                2 -> NotifTab()
                else -> AccountTab()
            }
        }
        Dock(unread, Modifier.align(Alignment.BottomCenter))
    }
}

/** Dock nổi bo tròn như bản Flutter: nền giấy mờ, đổ bóng, mục chọn đổi icon đặc + chữ đậm màu mực. */
@Composable
private fun Dock(unread: Int, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier.navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 8.dp).fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(.25f), spotColor = Color.Black.copy(.25f))
            .background(cs.card.copy(alpha = .98f), shape).border(1.dp, cs.outlineVariant.copy(.6f), shape)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        tabs.forEachIndexed { i, t ->
            val sel = Nav.tab == i
            val color = if (sel) cs.onSurface else cs.onSurfaceVariant
            Column(
                Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, null) { Nav.tab = i }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BadgedBox({ if (i == 2 && unread > 0 && !sel) Badge { Text("$unread") } }) {
                    Icon(if (sel) t.on else t.off, t.label, Modifier.size(26.dp), color)
                }
                Spacer(Modifier.height(3.dp))
                Text(t.label, color = color, fontSize = 11.5.sp, fontFamily = Jakarta, fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold)
            }
        }
    }
}
