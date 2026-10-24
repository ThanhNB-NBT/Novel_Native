package com.thanhnb.novelnative

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Yêu cầu truyện mới (bảng novel_requests): worker tìm tên gốc, crawl về rồi tự thêm vào tủ. */
object NovelRequests {
    suspend fun mine(): List<JSONObject> {
        val uid = Session.user?.id ?: return emptyList()
        val a = Api.get("novel_requests?select=id,query,status,note,novel_id,created_at,novels(title_vi,title_zh)&user_id=eq.$uid&order=created_at.desc&limit=20")
        return List(a.length()) { a.getJSONObject(it) }
    }

    suspend fun add(query: String) {
        val uid = Session.user?.id ?: return
        Api.raw("POST", "rest/v1/novel_requests", JSONObject().put("user_id", uid).put("query", query.trim()), prefer = "return=minimal")
    }

    suspend fun remove(id: Int) { Api.delete("novel_requests?id=eq.$id") }
}

/**
 * Sheet "Yêu cầu truyện mới": nhập tên Việt (Hán-Việt) hoặc tên gốc tiếng Trung, tự làm mới 5 giây khi còn yêu cầu đang tìm.
 * [initialQuery] điền sẵn tên đang tìm (mở từ màn tìm kiếm).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestSheet(initialQuery: String = "", close: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val list = load("novel_requests", Session.user?.id) { NovelRequests.mine() }
    var input by remember { mutableStateOf(initialQuery) }
    var sending by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val reqs = list.res?.getOrNull().orEmpty()
    val waiting = reqs.any { it.optString("status") == "pending" }
    LaunchedEffect(waiting) { while (waiting) { delay(5000); list.reload() } }

    fun submit() {
        val q = input.trim()
        if (q.isEmpty() || sending) return
        sending = true; err = null
        scope.launch {
            try { NovelRequests.add(q); input = ""; list.reload() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { err = friendly(e) }
            finally { sending = false }
        }
    }

    ModalBottomSheet(close, containerColor = cs.card) {
        Column(Modifier.imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Yêu cầu truyện mới", style = MaterialTheme.typography.titleLarge)
            Text(
                "Nhập tên truyện tiếng Việt (Hán-Việt) hoặc tên gốc tiếng Trung. Hệ thống tự tìm tên gốc, crawl về và thêm vào tủ sách của bạn — thường xong trong dưới 1 phút.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    input, { if (it.length <= 100) input = it }, Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("vd: Kiếm Lai / 剑来") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit() }),
                )
                Button({ submit() }, enabled = !sending && input.isNotBlank()) {
                    if (sending) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Tìm")
                }
            }
            err?.let { Text(it, color = cs.error, style = MaterialTheme.typography.labelMedium) }
            if (reqs.isNotEmpty()) {
                Eyebrow("Yêu cầu của bạn", Modifier.padding(top = 6.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(reqs, key = { it.getInt("id") }) { r -> RequestRow(r, close) { id -> scope.launch { runCatching { NovelRequests.remove(id) }; list.reload() } } }
                }
            }
        }
    }
}

@Composable
private fun RequestRow(r: JSONObject, close: () -> Unit, remove: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val st = r.optString("status")
    val (chip, color) = when (st) {
        "pending" -> "Đang tìm…" to cs.primary
        "done" -> "Đã thêm" to cs.live
        "notfound" -> "Không nguồn nào có" to cs.error
        else -> "Lỗi" to cs.error
    }
    val novelId = r.optInt("novel_id", -1).takeIf { st == "done" && it > 0 }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if (novelId != null) Modifier.clickable { close(); Nav.push(Screen.Detail(novelId)) } else Modifier).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (st == "pending") CircularProgressIndicator(Modifier.padding(end = 10.dp).size(14.dp), strokeWidth = 2.dp)
        Column(Modifier.weight(1f)) {
            val title = r.optJSONObject("novels")?.s("title_vi") ?: r.optString("query")
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${r.optString("query")} · ${ago(r.s("created_at"))} trước", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            chip, Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(50)).then(Modifier).padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold,
        )
        if (st != "pending") IconButton({ remove(r.getInt("id")) }, Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, "Xoá yêu cầu", Modifier.size(16.dp), tint = cs.onSurfaceVariant) }
    }
}
