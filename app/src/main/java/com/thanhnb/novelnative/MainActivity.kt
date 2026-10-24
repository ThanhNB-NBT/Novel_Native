package com.thanhnb.novelnative

import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import android.os.Bundle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
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
        Notify.init(this)
        Thread { HanViet.load(applicationContext) }.start()
        Session.load()
        if (Session.user != null) {
            Notify.askPermission(this)
            val app = applicationContext
            CoroutineScope(Dispatchers.IO).launch { Notify.checkMissed(app) }
        }
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
                            val ease = spring<IntOffset>(dampingRatio = .86f, stiffness = 380f)
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
                                    Screen.Queue -> QueueScreen()
                                    Screen.Guide -> GuideScreen()
                                    Screen.Lunar -> LunarScreen()
                                    Screen.Admin -> AdminScreen()
                                    Screen.AdminLlm -> LlmScreen()
                                    Screen.AdminVps -> VpsScreen()
                                    Screen.AdminErrors -> ErrorsScreen()
                                    is Screen.AdminNovel -> AdminNovelScreen(s.id)
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
 * Dock kính mờ: viên thuốc nổi, nền làm mờ (Haze) + vệt sáng mảnh ở viền. Tab đang chọn nở ra thành viên nhấn
 * có kèm tên (độ rộng chạy theo lò xo, các tab khác co lại chỉ còn icon); chạm thì ô nhún nhẹ.
 */
@Composable
private fun Dock(unread: Int, glass: HazeState, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < .5f
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 10.dp).fillMaxWidth().height(64.dp)
            .shadow(20.dp, shape, ambientColor = cs.primary.copy(.16f), spotColor = Color.Black.copy(.22f))
            .clip(shape)
            .hazeEffect(glass) {
                blurRadius = 30.dp
                noiseFactor = 0f
                backgroundColor = cs.background
                tints = listOf(HazeTint(cs.background.copy(alpha = if (dark) .5f else .6f)))
            }
            .background(Brush.verticalGradient(listOf(Color.White.copy(if (dark) .12f else .42f), Color.White.copy(if (dark) .03f else .10f))))
            .border(.6.dp, Color.White.copy(if (dark) .14f else .6f), shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { i, t -> DockItem(t, Nav.tab == i, if (i == 2) unread else 0) { Nav.tab = i } }
    }
}

@Composable
private fun RowScope.DockItem(t: TabItem, sel: Boolean, badge: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < .5f
    val w by animateFloatAsState(if (sel) 2.15f else 1f, spring(dampingRatio = .78f, stiffness = 260f), label = "w")
    val fill by animateFloatAsState(if (sel) 1f else 0f, tween(280), label = "fill")
    val color by animateColorAsState(if (sel) cs.primary else cs.onSurfaceVariant, tween(220), label = "c")
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .9f else 1f, spring(.55f, 520f), label = "s")
    Box(
        Modifier.weight(w).fillMaxHeight().graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(26.dp))
            .background(cs.primary.copy(alpha = (if (dark) .26f else .15f) * fill))
            .clickable(src, null, onClick = onClick),
        Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BadgedBox({ if (badge > 0 && !sel) Badge { Text("$badge") } }) { Icon(if (sel) t.on else t.off, t.label, Modifier.size(25.dp), color) }
            AnimatedVisibility(
                sel,
                enter = fadeIn(tween(220, 80)) + expandHorizontally(spring(.8f, 300f)),
                exit = fadeOut(tween(120)) + shrinkHorizontally(spring(.9f, 400f)),
            ) {
                Text(t.label, Modifier.padding(start = 7.dp), color = color, fontSize = 13.5.sp, fontFamily = Jakarta, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    }
}
