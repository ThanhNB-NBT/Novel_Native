package com.thanhnb.novelnative

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Session.load()
        Nav.tab = if (Session.user != null) 0 else 1 // chưa đăng nhập → mở Khám phá
        enableEdgeToEdge()
        setContent {
            val dark = when (Prefs.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            MaterialTheme(if (dark) darkColorScheme() else lightColorScheme()) {
                // Giữ trạng thái cuộn (rememberSaveable) của từng màn trong ngăn xếp khi màn trên đè lên
                val holder = rememberSaveableStateHolder()
                // Khoá theo cả vị trí lẫn màn: đổi chương (replace) phải ra state mới, không mang vị trí cuộn chương cũ
                val stack = Nav.stack
                val key = "${stack.lastIndex}:${stack.last()}"
                BackHandler(stack.size > 1) { holder.removeState(key); Nav.pop() }
                Surface(Modifier.fillMaxSize()) {
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

private val tabs = listOf(
    "Tủ truyện" to Icons.Default.Favorite,
    "Khám phá" to Icons.Default.Home,
    "Thông báo" to Icons.Default.Notifications,
    "Tôi" to Icons.Default.Person,
)

@Composable
private fun Tabs() {
    val holder = rememberSaveableStateHolder()
    val unread = unreadCount()
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        Nav.tab == i, { Nav.tab = i },
                        icon = {
                            BadgedBox({ if (i == 2 && unread > 0 && Nav.tab != 2) Badge { Text("$unread") } }) { Icon(icon, label) }
                        },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).consumeWindowInsets(pad)) {
            holder.SaveableStateProvider("tab${Nav.tab}") {
                when (Nav.tab) {
                    0 -> LibraryTab()
                    1 -> ExploreTab()
                    2 -> NotifTab()
                    else -> AccountTab()
                }
            }
        }
    }
}
