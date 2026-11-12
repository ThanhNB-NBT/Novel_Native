package com.thanhnb.novelnative

import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import android.content.Intent
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
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
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
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
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); openFromNotification(intent) }

    // chạm thông báo "chương dịch xong" → mở thẳng truyện đó; mở lại từ Gần đây (intent cũ) thì không
    private fun openFromNotification(i: Intent?) {
        val id = i?.getIntExtra(Notify.EXTRA_NOVEL, 0) ?: 0
        if (id > 0 && i!!.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) Nav.push(Screen.Detail(id))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Endpoint.start(kotlinx.coroutines.MainScope())
        AppErrorLog.install()
        Offline.init(this)
        Notify.init(this)
        Notify.schedule(this)
        Thread { HanViet.load(applicationContext) }.start()
        Session.load()
        if (Session.user != null) {
            Notify.askPermission(this)
            val app = applicationContext
            CoroutineScope(Dispatchers.IO).launch { Notify.checkMissed(app) }
        }
        Nav.tab = if (Session.user != null) 0 else 1 // chưa đăng nhập → mở Khám phá
        if (savedInstanceState == null) openFromNotification(intent) // xoay màn/khôi phục: Nav còn nguyên, đừng đẩy lần nữa
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
                Box(Modifier.fillMaxSize().mascotGaze()) {
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
                                        Screen.Cultivation -> CultivationScreen()
                                        Screen.Guide -> GuideScreen()
                                        Screen.Changelog -> ChangelogScreen()
                                        Screen.Lunar -> LunarScreen()
                                        is Screen.LaSo -> LaSoScreen(s.h, s.mine)
                                        Screen.Monitor -> MonitorScreen()
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
                    SplashOverlay() // phủ trên cùng, chỉ lần mở app đầu tiên của tiến trình
                }
            }
        }
    }

    // Phím âm lượng lật trang khi màn đọc bật tuỳ chọn này
    override fun dispatchKeyEvent(event: android.view.KeyEvent) =
        ReaderKeys.handler?.invoke(event) == true || super.dispatchKeyEvent(event)
}

private class TabItem(val label: String, val on: ImageVector, val off: ImageVector, val orb: Boolean = false)

private val tabs = listOf(
    TabItem("Tủ truyện", Icons.Rounded.Bookmarks, Icons.Outlined.Bookmarks),
    TabItem("Khám phá", Icons.Rounded.Explore, Icons.Outlined.Explore),
    TabItem("Tu Tiên", Icons.Rounded.SelfImprovement, Icons.Rounded.SelfImprovement, orb = true), // người ngồi thiền; vẽ thành viên nổi giữa dock
    TabItem("Lịch âm", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    TabItem("Tôi", Icons.Rounded.Person, Icons.Outlined.Person),
)

@Composable
private fun Tabs() {
    val holder = rememberSaveableStateHolder()
    val unread = unreadCount()
    val glass = rememberHazeState()
    Box(Modifier.fillMaxSize()) {
        // Nội dung tràn xuống dưới dock; mỗi tab tự chừa DockSpace ở cuối danh sách. hazeSource = nguồn để dock kính làm mờ.
        // Vuốt ngang đổi tab theo ngón tay; chạm dock thì lò xo trượt tới tab đó. Nav.tab là nguồn chung của dock.
        val pager = rememberPagerState(Nav.tab) { tabs.size }
        LaunchedEffect(Nav.tab) { if (pager.targetPage != Nav.tab) pager.animateScrollToPage(Nav.tab, animationSpec = spring(dampingRatio = .9f, stiffness = 300f)) }
        LaunchedEffect(pager) { snapshotFlow { pager.targetPage }.collect { Nav.tab = it } }
        HorizontalPager(
            pager, Modifier.fillMaxSize().hazeSource(glass), key = { it },
            flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = spring(dampingRatio = .85f, stiffness = 320f)),
        ) { tab ->
            Box(Modifier.fillMaxSize().graphicsLayer {
                // trang đang rời đi mờ + co nhẹ: cảm giác chiều sâu thay vì hai tấm phẳng trượt qua nhau
                val d = ((pager.currentPage - tab) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f).let { kotlin.math.abs(it) }
                alpha = 1f - .35f * d; val s = 1f - .04f * d; scaleX = s; scaleY = s
            }) { holder.SaveableStateProvider("tab$tab") {
                when (tab) {
                    0 -> LibraryTab()
                    1 -> ExploreTab()
                    2 -> CultivationScreen(tab = true)
                    3 -> LunarScreen(tab = true)
                    else -> AccountTab()
                }
            } }
        }
        val bg = MaterialTheme.colorScheme.background
        // mờ dần nội dung ở đầu: chữ không chồng lên đồng hồ
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp)
            .background(Brush.verticalGradient(0f to bg, .6f to bg.copy(.88f), 1f to Color.Transparent)))
        Dock(unread, glass, { pager.currentPage + pager.currentPageOffsetFraction }, Modifier.align(Alignment.BottomCenter))
    }
}

private val DockH = 64.dp
private val OrbSize = 46.dp // + vầng linh khí và lúc nở khi chọn vẫn nằm gọn trong dock
private const val Mid = 2 // vị trí tab Tu Tiên

/**
 * Dock kính lỏng (haze-glass: khúc xạ + làm nhoè nội dung phía sau; máy dưới Android 13 tự lùi về kính mờ).
 * Thấu kính bám THEO VỊ TRÍ LIÊN TỤC của pager ([pos] = trang hiện tại + phần lẻ khi đang vuốt): ngón tay kéo tới đâu nó trượt tới đó,
 * giãn ra giữa hai tab như giọt nước rồi co lại; tới ô giữa thì tan đi, nhường cho viên Tu Tiên sáng lên.
 */
@Composable
private fun Dock(unread: Int, glass: HazeState, pos: () -> Float, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < .5f
    val shape = RoundedCornerShape(DockH / 2)
    // đủ đục để nhãn tab đạt tương phản cả khi bìa truyện tối/sặc sỡ trượt qua phía sau
    val tint = cs.background.copy(alpha = if (dark) .58f else .6f)
    // nhoè mạnh hơn mặc định: bìa truyện và chữ phía sau không tranh với nhãn tab
    val style = remember(tint) { GlassStyle { shape(shape); tint(tint); optics { copy(blurRadius = OpticalSizeValue.Fixed(16.dp)) } } }
    val lens = cs.primary.copy(alpha = if (dark) .26f else .14f)
    // viền sáng trên, mờ dưới: ánh đèn hắt vào mép kính
    val rim = remember(dark) { Brush.verticalGradient(listOf(Color.White.copy(if (dark) .28f else .9f), Color.White.copy(if (dark) .04f else .2f))) }
    // Dock KHÔNG đọc pos() lúc dựng: vuốt tab chỉ chạy lại pha vẽ (thấu kính, viên Tu Tiên), không dựng lại cả hàng mỗi khung hình.
    Row(
        modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 10.dp).fillMaxWidth().height(DockH)
            .shadow(20.dp, shape, ambientColor = cs.primary.copy(.16f), spotColor = Color.Black.copy(.22f))
            .hazeGlass(HazeInput.Sources(glass), style)
            .background(cs.background.copy(alpha = .34f), shape) // lớp nền mỏng dưới chữ: nhãn vẫn đọc được khi bìa sặc sỡ nằm sau kính
            .border(.8.dp, rim, shape)
            .padding(6.dp)
            .drawBehind { // đọc pos() ở pha vẽ: vuốt chỉ vẽ lại thấu kính
                val x = pos(); val f = x - kotlin.math.floor(x)
                val w = size.width / tabs.size
                val lw = w * (1f + 1.2f * f * (1f - f)) // rộng nhất khi ở giữa hai tab
                val a = kotlin.math.abs(x - Mid).coerceAtMost(1f)
                val o = Offset((x + .5f) * w - lw / 2, 0f); val sz = Size(lw, size.height); val r = CornerRadius(size.height / 2)
                drawRoundRect(lens, o, sz, r, alpha = a)
                drawRoundRect(rim, o, sz, r, alpha = a, style = Stroke(1.dp.toPx()))
            },
    ) {
        tabs.forEachIndexed { i, t ->
            if (t.orb) Box(Modifier.weight(1f).fillMaxHeight(), Alignment.Center) { CultivationOrb({ (1f - kotlin.math.abs(pos() - i)).coerceIn(0f, 1f) }) { Nav.tab = i } }
            else DockItem(t, i, pos, if (i == 0) unread else 0) { Nav.tab = i }
        }
    }
}

@Composable
private fun RowScope.DockItem(t: TabItem, i: Int, pos: () -> Float, badge: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    // chỉ dựng lại khi pager đi qua nửa đường tới/khỏi tab này; phần chuyển động liên tục đã có thấu kính lo
    val on by remember(i, pos) { derivedStateOf { kotlin.math.abs(pos() - i) < .5f } }
    val color by animateColorAsState(if (on) cs.primary else cs.onSurfaceVariant, tween(160), label = "màu tab")
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .9f else 1f, spring(.55f, 520f), label = "s")
    Column(
        Modifier.weight(1f).fillMaxHeight().graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(26.dp)).clickable(src, null, onClick = onClick),
        Arrangement.Center, Alignment.CenterHorizontally,
    ) {
        BadgedBox({ if (badge > 0 && !on) Badge { Text("$badge") } }) { Icon(if (on) t.on else t.off, null, Modifier.size(24.dp), color) }
        Text(
            t.label, color = color, fontSize = 10.5.sp, lineHeight = 14.sp, fontFamily = Jakarta,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false,
        )
    }
}

/**
 * Nút Tu Tiên: viên ngọc nạm giữa dock, không nhãn. [sel] chạy theo pager: càng tới gần tab Tu Tiên viên càng nở,
 * quầng sáng càng đậm và vầng linh khí (hai vệt sáng đuổi nhau quanh vành) hiện dần.
 * Màu lấy thẳng từ bảng màu nhấn (accent/deep/dAccent) chứ không qua colorScheme: primary ở giao diện tối bị đẩy sáng cho đủ tương phản
 * với nền, tô lên viên sẽ thành nhạt; ba tông gốc thì đậm như nhau ở cả sáng lẫn tối, đổi màu nhấn là viên đổi theo.
 */
@Composable
private fun CultivationOrb(sel: () -> Float, onClick: () -> Unit) {
    val a = accents.getOrElse(Prefs.accent) { accents[0] }
    val light = if (MaterialTheme.colorScheme.background.luminance() < .5f) a.dAccent else a.accent // tông nổi trên nền hiện tại
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) .9f else 1f, spring(.5f, 500f), label = "orb")
    // vầng linh khí chỉ quay khi đang ở (hoặc đang vuốt tới) tab Tu Tiên: ở tab khác không tốn khung hình nào
    val active by remember(sel) { derivedStateOf { sel() > 0f } }
    val spin = if (active) rememberInfiniteTransition(label = "khí").animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "quay") else null
    val aura = remember(light) { Brush.sweepGradient(0f to Color.Transparent, .22f to light, .25f to Color.Transparent, .5f to Color.Transparent, .72f to light, .75f to Color.Transparent) }
    Box(
        Modifier.size(OrbSize).graphicsLayer { val s = press * (1f + .08f * sel()); scaleX = s; scaleY = s }
            .drawBehind {
                val r = size.minDimension / 2
                drawCircle(Brush.radialGradient(listOf(light.copy(alpha = .26f + .26f * sel()), Color.Transparent), radius = r * 1.45f), r * 1.45f)
                if (spin != null) rotate(spin.value) { drawCircle(aura, r + 3.5.dp.toPx(), alpha = sel(), style = Stroke(2.dp.toPx())) }
            }
            .shadow(8.dp, CircleShape, ambientColor = a.deep, spotColor = a.deep)
            .clip(CircleShape)
            .drawWithCache {
                val w = size.width; val bw = 2.5.dp.toPx()
                // khối cầu: sáng ở góc trên trái, sẫm dần về mép dưới phải
                val body = Brush.radialGradient(listOf(lerp(a.accent, Color.White, .45f), a.accent, a.deep), Offset(w * .36f, w * .3f), w * .8f)
                val gloss = Brush.verticalGradient(listOf(Color.White.copy(.5f), Color.Transparent), endY = w * .46f)
                val bezel = Brush.verticalGradient(listOf(lerp(a.dAccent, Color.White, .55f), a.deep)) // vành nạm: sáng trên, sẫm dưới
                onDrawBehind {
                    drawCircle(body)
                    drawOval(gloss, Offset(w * .2f, bw + w * .03f), Size(w * .6f, w * .4f))
                    drawCircle(bezel, w / 2 - bw / 2, style = Stroke(bw))
                }
            }
            .clickable(src, null, onClick = onClick),
        Alignment.Center,
    ) { Icon(Icons.Rounded.SelfImprovement, "Tu Tiên", Modifier.size(27.dp), Color(0xFFFFF4E6)) }
}
