package com.thanhnb.novelnative

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Cơ duyên NGẪU NHIÊN khi đọc, theo (user, truyện, chương) — mirror y hệt SQL cult_gift_at() (migration 049)
 * và giftHash/giftAt bên Flutter: 6 hex đầu của md5("uid:novel:index") → số nguyên;
 * % 100 < 50 ⇒ chương có quà (~50%); % số đoạn ⇒ quà nằm ngay dưới đoạn nào.
 * Đổi ở đây phải đổi cả SQL. Rơi đồ do server chọn (cult_claim_gift), client chỉ biết có nút hay không và vị trí nút.
 */
object Gift {
    fun hash(uid: String, novelId: Int, index: Int): Int =
        java.security.MessageDigest.getInstance("MD5").digest("$uid:$novelId:$index".toByteArray())
            .take(3).fold(0) { a, b -> (a shl 8) or (b.toInt() and 0xFF) } // 3 byte = 6 hex đầu

    fun at(uid: String, novelId: Int, index: Int) = hash(uid, novelId, index) % 100 < 50

    /** Chỉ số đoạn mà nút quà nằm dưới; -1 nếu chương này không có quà. */
    fun after(uid: String?, novelId: Int, index: Int, paras: Int): Int =
        if (uid == null || paras <= 0 || !at(uid, novelId, index)) -1 else hash(uid, novelId, index) % paras

    private val Flavors = listOf(
        "Khe đá ven đường lóe hào quang, bên trong giấu một vật…",
        "Một tiền bối ẩn cư để lại cơ duyên cho kẻ hữu duyên…",
        "Đọc tới đây tâm cảnh chợt thông suốt, trời giáng linh vật…",
        "Con cá chép trong hồ nhả ra một vật lấp lánh…",
        "Sương tan, dưới gốc tùng cổ lộ ra bảo vật…",
        "Tiếng chuông xa vọng, trước mặt hiện ra chiếc rương nhỏ…",
        "Lật tảng đá phủ rêu, hiện ra di vật của tán tu vô danh…",
        "Linh khí quanh thân ngưng tụ, hóa thành vật thực…",
    )

    fun flavor(uid: String, novelId: Int, index: Int) = Flavors[hash(uid, novelId, index) % Flavors.size]

    /** Các chương đã nhận quà của truyện (cult_claims, RLS chỉ trả dòng của mình). */
    suspend fun claimed(novelId: Int): Set<Int> {
        val uid = Session.user?.id ?: return emptySet()
        val a = Api.get("cult_claims?select=chapter_index&user_id=eq.$uid&novel_id=eq.$novelId")
        return List(a.length()) { a.getJSONObject(it).optInt("chapter_index") }.toSet()
    }
}

@Composable
fun GiftButton(novelId: Int, index: Int, fg: Color, onClaimed: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var got by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), Alignment.Center) {
        OutlinedButton(
            {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            got = JSONObject(Api.rpc("cult_claim_gift", "p_novel_id" to novelId, "p_index" to index))
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { err = friendly(e) }
                        busy = false
                    }
                }
            },
            border = BorderStroke(1.dp, fg.copy(alpha = 0.3f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = fg.copy(alpha = 0.85f)),
            contentPadding = PaddingValues(16.dp, 10.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = fg.copy(alpha = 0.6f))
            else Icon(Icons.Rounded.CardGiftcard, null, Modifier.size(20.dp), tint = Color(0xFFFFA000))
            Text("Cơ duyên hé mở — nhận bảo vật", Modifier.padding(start = 8.dp))
        }
    }
    got?.let { it ->
        val grade = it.optInt("grade", 1)
        val type = it.optString("type")
        AlertDialog(
            { got = null; onClaimed() }, containerColor = cs.card,
            title = { Text("Cơ duyên!") },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Gift.flavor(Session.user?.id.orEmpty(), novelId, index), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    ItemGlory(it)
                    Text(it.optString("name"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${typeLabel(type)} · phẩm ${GradeNames.getOrElse(grade - 1) { "?" }}", color = gradeColor(grade), style = MaterialTheme.typography.labelMedium)
                    Text(it.optString("descr"), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    if (it.strOrNull("halo") != null) Text("Thiên cơ hiển lộ — đắc Tiên trận!", color = Color(0xFFFFA000), style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = { TextButton({ got = null; onClaimed() }) { Text("Thu vào kho") } },
        )
    }
    err?.let { m -> AlertDialog({ err = null }, containerColor = cs.card, text = { Text(m) }, confirmButton = { TextButton({ err = null }) { Text("OK") } }) }
}
