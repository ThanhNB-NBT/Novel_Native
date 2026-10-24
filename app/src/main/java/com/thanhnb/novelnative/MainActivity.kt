package com.thanhnb.novelnative

import android.os.Bundle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Offline.init(this)
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
                AutoUpdatePrompt()
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Đẩy màn mới vào từ phải (màn cũ lùi nhẹ + mờ), lùi lại thì ngược chiều; đổi chương (cùng độ sâu) không hiệu ứng
                    AnimatedContent(
                        stack,
                        transitionSpec = {
                            val push = targetState.size - initialState.size
                            val ease = tween<IntOffset>(320, easing = FastOutSlowInEasing)
                            when {
                                push > 0 -> (slideInHorizontally(ease) { it } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally(ease) { -it / 4 } + fadeOut(tween(220, delayMillis = 60)))
                                push < 0 -> (slideInHorizontally(ease) { -it / 4 } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally(ease) { it } + fadeOut(tween(200)))
                                else -> EnterTransition.None togetherWith ExitTransition.None
                            }.apply { targetContentZIndex = if (push >= 0) 1f else 0f }
                        },
                        label = "page",
                    ) { st ->
                        val k = "${st.lastIndex}:${st.last()}"
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            holder.SaveableStateProvider(k) {
                                when (val s = st.last()) {
                                    Screen.Tabs -> Tabs()
                                    is Screen.Detail -> DetailScreen(s.id)
                                    is Screen.Read -> ReaderScreen(s)
                                    is Screen.Glossary -> GlossaryScreen(s.novelId)
                                    is Screen.More -> MoreScreen(s.section)
                                    Screen.Search -> SearchScreen()
                                    Screen.Login -> LoginScreen()
                                    Screen.Offline -> OfflineScreen()
                                    Screen.EditProfile -> EditProfileScreen()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Phím âm lượng lật trang khi màn đọc bật tuỳ chọn này
    override fun dispatchKeyEvent(event: android.view.KeyEvent) =
        ReaderKeys.handler?.invoke(event) == true || super.dispatchKeyEvent(event)
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
    val glass = rememberHazeState()
    Box(Modifier.fillMaxSize()) {
        // Nội dung tràn xuống dưới dock; mỗi tab tự chừa DockSpace ở cuối danh sách. hazeSource = nguồn để dock kính làm mờ.
        AnimatedContent(
            Nav.tab,
            Modifier.fillMaxSize().hazeSource(glass),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { dir * it / 10 }) togetherWith
                    (fadeOut(tween(120)) + slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -dir * it / 10 })
            },
            label = "tab",
        ) { tab ->
            holder.SaveableStateProvider("tab$tab") {
                when (tab) {
                    0 -> LibraryTab()
                    1 -> ExploreTab()
                    2 -> NotifTab()
                    else -> AccountTab()
                }
            }
        }
        val bg = MaterialTheme.colorScheme.background
        // mờ dần nội dung ở đầu: chữ không chồng lên đồng hồ
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp)
            .background(Brush.verticalGradient(0f to bg, .6f to bg.copy(.88f), 1f to Color.Transparent)))
        Dock(unread, glass, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Dock kính lỏng: không khung viền, nền sau bị làm mờ (Haze) + nhuộm màu nhấn, một vệt sáng chéo,
 * viên "thấu kính" trượt theo lò xo tới tab đang chọn.
 */
@Composable
private fun Dock(unread: Int, glass: HazeState, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < .5f
    val shape = RoundedCornerShape(34.dp)
    val sheen = Color.White
    BoxWithConstraints(
        modifier.navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 10.dp).fillMaxWidth().height(70.dp)
            .shadow(22.dp, shape, ambientColor = cs.primary.copy(.25f), spotColor = Color.Black.copy(.3f))
            .clip(shape)
            .hazeEffect(glass) {
                blurRadius = 28.dp
                noiseFactor = 0f
                backgroundColor = cs.background
                tints = listOf(HazeTint(cs.primary.copy(alpha = if (dark) .16f else .10f)))
            }
            .background(Brush.linearGradient(listOf(sheen.copy(if (dark) .16f else .50f), sheen.copy(.04f), cs.primary.copy(if (dark) .10f else .07f)))),
    ) {
        val pad = 6.dp
        val cell = (maxWidth - pad * 2) / tabs.size
        val x by animateDpAsState(pad + cell * Nav.tab, spring(dampingRatio = .68f, stiffness = 340f), label = "lens")
        // thấu kính: sáng hơn nền dock, ngả màu nhấn, cạnh trên có vệt phản sáng
        Box(
            Modifier.offset(x = x).padding(vertical = pad).width(cell).fillMaxHeight()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(sheen.copy(if (dark) .26f else .70f), cs.primary.copy(if (dark) .22f else .16f))))
                .drawBehind {
                    drawLine(
                        Brush.horizontalGradient(listOf(Color.Transparent, sheen.copy(.9f), Color.Transparent)),
                        Offset(size.width * .18f, 1.5.dp.toPx()), Offset(size.width * .82f, 1.5.dp.toPx()), 1.5.dp.toPx(),
                    )
                },
        )
        Row(Modifier.fillMaxSize().padding(horizontal = pad)) {
            tabs.forEachIndexed { i, t ->
                val sel = Nav.tab == i
                val color by animateColorAsState(if (sel) cs.primary else cs.onSurfaceVariant, label = "tabColor")
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) { Nav.tab = i },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    BadgedBox({ if (i == 2 && unread > 0 && !sel) Badge { Text("$unread") } }) {
                        Icon(if (sel) t.on else t.off, t.label, Modifier.size(25.dp), color)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(t.label, color = color, fontSize = 11.5.sp, fontFamily = Jakarta, fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold)
                }
            }
        }
    }
}
