package com.thanhnb.novelnative

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Thuật ngữ truyện (như screens/novel/glossary.dart, bỏ phần admin): bảng "chữ Trung → bản dịch đúng"
 * dùng cho mọi chương dịch SAU; gợi ý của LLM chờ duyệt; vá chương cũ; chọn nhiều để xoá.
 * Chưa có: tra bảng Hán-Việt, văn phong dịch (admin).
 */
private val termTypes = linkedMapOf("person" to "Nhân vật", "place" to "Địa danh", "sect" to "Môn phái", "item" to "Vật phẩm", "skill" to "Chiêu thức", "other" to "Khác")

@Composable
fun GlossaryScreen(novelId: Int) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val l = load("terms", novelId) { Api.terms(novelId) }
    val patch = load("patch", novelId) { Api.latestPatch(novelId) }
    var openPending by remember { mutableStateOf(true) }
    var openApproved by remember { mutableStateOf(true) }
    var selecting by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<Long>() }
    var editing by remember { mutableStateOf<Term?>(null) } // id = -1 → thêm mới
    var reporting by remember { mutableStateOf<Term?>(null) }
    var confirmPatch by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(msg) { delay(2500); msg = null }
    val terms = l.res?.getOrNull().orEmpty()

    fun act(ok: String? = null, job: suspend () -> Unit) = scope.launch {
        try {
            job()
            ok?.let { msg = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            msg = friendly(e)
        }
        l.reload()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Bar(if (selecting) "Đã chọn ${picked.size}" else "Thuật ngữ") {
                if (selecting) {
                    IconButton({ picked.clear(); picked.addAll(terms.map { it.id }) }) { Icon(Icons.Rounded.SelectAll, "Chọn tất cả") }
                    IconButton({ confirmDelete = true }, enabled = picked.isNotEmpty()) { Icon(Icons.Rounded.DeleteOutline, "Xoá đã chọn") }
                    TextButton({ selecting = false; picked.clear() }) { Text("Xong") }
                } else {
                    IconButton({ confirmPatch = true }) { Icon(Icons.Rounded.Healing, "Vá chương cũ") }
                    IconButton({ selecting = true; picked.clear() }) { Icon(Icons.Rounded.Checklist, "Chọn để xoá hàng loạt") }
                }
            }
            if (Session.user == null) { NeedLogin("Đăng nhập để quản lý thuật ngữ."); return@Column }
            PatchBanner(patch.res?.getOrNull())
            Loaded(l) { all ->
                val pending = all.filter { !it.approved }
                val approved = all.filter { it.approved }
                if (all.isEmpty()) Empty("Chưa có thuật ngữ nào.\nBấm + để thêm.")
                else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    if (pending.isNotEmpty()) {
                        item { Toggle("Gợi ý chờ duyệt (${pending.size})", openPending) { openPending = !openPending } }
                        if (openPending) items(pending, key = { "p${it.id}" }) { t ->
                            if (selecting) CheckRow(t, t.id in picked) { if (t.id in picked) picked.remove(t.id) else picked.add(t.id) }
                            else PendingRow(
                                t, { editing = t },
                                { act { Api.approveTerm(t.id) } }, { act { Api.deleteTerms(listOf(t.id)) } },
                            )
                        }
                    }
                    item { Toggle("Đã áp dụng (${approved.size})", openApproved) { openApproved = !openApproved } }
                    if (openApproved) items(approved, key = { "a${it.id}" }) { t ->
                        if (selecting) CheckRow(t, t.id in picked) { if (t.id in picked) picked.remove(t.id) else picked.add(t.id) }
                        else ApprovedRow(t, { reporting = t }, { editing = t })
                    }
                }
            }
        }
        if (!selecting && Session.user != null) ExtendedFloatingActionButton(
            { editing = Term(-1, null, null, "", "other", null, true, null) },
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
            containerColor = cs.primary, contentColor = cs.onPrimary,
        ) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Thêm") }
        msg?.let { Snackbar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) { Text(it) } }
    }

    editing?.let { t ->
        TermDialog(
            t, onDismiss = { editing = null },
            onDelete = if (t.id < 0) null else ({ editing = null; act { Api.deleteTerms(listOf(t.id)) } }),
        ) { zh, vi, wrong, type, narr ->
            editing = null
            // Chỉ sửa "bản đúng": giữ bản cũ làm vế "sai" để job vá biết phải thay chuỗi nào
            val old = t.correct.trim()
            val w = wrong.trim().ifEmpty { if (t.id >= 0 && old.isNotEmpty() && old != vi.trim()) old else "" }
            act { Api.saveTerm(novelId, t.id.takeIf { it >= 0 }, zh, vi, w, type, narr) }
        }
    }
    reporting?.let { t ->
        var reason by remember(t.id) { mutableStateOf("") }
        AlertDialog(
            { reporting = null }, containerColor = cs.card,
            title = { Text("Báo cáo dịch sai") },
            text = { OutlinedTextField(reason, { reason = it }, label = { Text("Lý do (không bắt buộc)") }, placeholder = { Text("vd: dịch sai nghĩa, sai tên nhân vật…") }) },
            confirmButton = { TextButton({ reporting = null; act("Đã gửi báo cáo — admin sẽ xem lại") { Api.reportTerm(t.id, novelId, reason.trim()) } }) { Text("Gửi") } },
            dismissButton = { TextButton({ reporting = null }) { Text("Huỷ") } },
        )
    }
    if (confirmDelete) AlertDialog(
        { confirmDelete = false }, containerColor = cs.card,
        title = { Text("Xoá ${picked.size} thuật ngữ?") },
        text = { Text("Chương đã dịch giữ nguyên; các chương dịch sau sẽ không dùng những term này nữa.") },
        confirmButton = {
            TextButton({
                confirmDelete = false
                val ids = picked.toList()
                selecting = false
                picked.clear()
                act("Đã xoá ${ids.size} thuật ngữ") { Api.deleteTerms(ids) }
            }) { Text("Xoá", color = cs.error) }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Huỷ") } },
    )
    if (confirmPatch) {
        // Vá chỉ thay được từ CÓ bản dịch sai; term chỉ duyệt (không bản sai) phải "Dịch lại" chương mới đổi
        val ok = terms.count { it.approved }
        val withWrong = terms.count { it.approved && !it.wrong.isNullOrBlank() }
        AlertDialog(
            { confirmPatch = false }, containerColor = cs.card,
            title = { Text("Vá chương đã dịch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("$withWrong/$ok thuật ngữ đã duyệt có “bản dịch sai”.")
                    Text(
                        if (withWrong == 0) "Vá chỉ thay từ CÓ bản dịch sai nên sẽ không đổi gì. Muốn chương cũ theo thuật ngữ đã duyệt, hãy “Dịch lại” chương trong lúc đọc."
                        else "Chỉ $withWrong từ này được thay trong chương cũ. Term chỉ được duyệt (không có bản sai) phải “Dịch lại” chương mới đổi theo.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton({
                    confirmPatch = false
                    act("Đã xếp hàng vá — theo dõi tiến trình ở thanh trên") {
                        Api.requestPatch(novelId)
                        patch.reload()
                        repeat(12) { // job vá chạy <2s nhưng có thể chờ hàng đợi: hỏi lại tới khi xong
                            delay(1500)
                            patch.reload()
                            if (runCatching { Api.latestPatch(novelId) }.getOrNull()?.status.let { it == "done" || it == "failed" }) return@act
                        }
                    }
                }) { Text(if (withWrong == 0) "Vẫn vá" else "Vá $withWrong từ") }
            },
            dismissButton = { TextButton({ confirmPatch = false }) { Text("Huỷ") } },
        )
    }
}

@Composable
private fun Toggle(title: String, open: Boolean, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(20.dp, 18.dp, 12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }

private fun head(t: Term) = "${t.zh ?: "(?)"} → ${t.correct}"
private fun meta(t: Term) = listOfNotNull(
    termTypes[t.type] ?: t.type, t.wrong?.let { "không dịch: “$it”" }, t.narrator?.let { "kể: $it" }, if (t.scope == "global") "toàn cục" else null,
).joinToString(" · ")

@Composable
private fun ApprovedRow(t: Term, report: () -> Unit, edit: () -> Unit) =
    Row(Modifier.fillMaxWidth().clickable(onClick = edit).padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(head(t), style = MaterialTheme.typography.titleMedium)
            Text(meta(t), style = MaterialTheme.typography.labelMedium)
        }
        IconButton(report) { Icon(Icons.Rounded.OutlinedFlag, "Báo cáo dịch sai", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton(edit) { Icon(Icons.Rounded.Edit, "Sửa", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }

/** Gợi ý LLM chưa duyệt: duyệt nhanh hoặc bỏ; bấm vào dòng để sửa trước rồi mới duyệt. */
@Composable
private fun PendingRow(t: Term, edit: () -> Unit, approve: () -> Unit, discard: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 8.dp).clip(RoundedCornerShape(16.dp)).background(cs.primaryContainer.copy(alpha = 0.55f))
            .clickable(onClick = edit).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(head(t), style = MaterialTheme.typography.titleMedium)
            Text(termTypes[t.type] ?: t.type, style = MaterialTheme.typography.labelMedium)
        }
        IconButton(approve) { Icon(Icons.Rounded.CheckCircleOutline, "Duyệt — dùng cho các chương dịch sau", tint = cs.primary) }
        IconButton(discard) { Icon(Icons.Rounded.Close, "Bỏ gợi ý", tint = cs.onSurfaceVariant) }
    }
}

@Composable
private fun CheckRow(t: Term, on: Boolean, flip: () -> Unit) =
    Row(Modifier.fillMaxWidth().clickable(onClick = flip).padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(on, { flip() })
        Column(Modifier.padding(start = 4.dp)) {
            Text(head(t), style = MaterialTheme.typography.titleMedium)
            Text(termTypes[t.type] ?: t.type, style = MaterialTheme.typography.labelMedium)
        }
    }

/** Thanh trạng thái vá: đang vá / đã vá N/M chương / lỗi; ẩn khi truyện chưa vá lần nào. */
@Composable
private fun PatchBanner(job: PatchJob?) {
    job ?: return
    val cs = MaterialTheme.colorScheme
    val running = job.status == "pending" || job.status == "running"
    val failed = job.status == "failed"
    val color = if (failed) cs.error else cs.primary
    Row(Modifier.fillMaxWidth().background(color.copy(alpha = 0.10f)).padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (running) Icons.Rounded.Healing else if (failed) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircleOutline, null, Modifier.size(16.dp), tint = color)
        Text(
            when { running -> "Đang vá chương…"; failed -> "Vá lỗi — thử lại"; else -> "Đã vá ${job.result ?: "xong"}" },
            Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, color = color,
        )
        if (running) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TermDialog(t: Term, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (String, String, String, String, String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var zh by remember { mutableStateOf(t.zh.orEmpty()) }
    var vi by remember { mutableStateOf(t.correct) }
    var wrong by remember { mutableStateOf(t.wrong.orEmpty()) }
    var type by remember { mutableStateOf(t.type) }
    var narr by remember { mutableStateOf(t.narrator.orEmpty()) }
    AlertDialog(
        onDismiss, containerColor = cs.card,
        title = { Text(if (t.id < 0) "Thêm thuật ngữ" else "Sửa thuật ngữ") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(zh, { zh = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Từ gốc (tiếng Trung)") })
                OutlinedTextField(vi, { vi = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Bản dịch đúng *") })
                OutlinedTextField(
                    wrong, { wrong = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Bản dịch sai (nếu có)") },
                    supportingText = { Text("Dùng để vá chương cũ: sai → đúng") },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    termTypes.forEach { (k, v) -> FilterChip(type == k, { type = k }, { Text(v) }) }
                }
                if (type == "person") OutlinedTextField(
                    narr, { narr = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Người kể gọi (không bắt buộc)") },
                    placeholder = { Text("hắn / nàng / y / lão / tên riêng") },
                    supportingText = { Text("Cách LỜI KỂ gọi nhân vật này — bỏ trống = mặc định") },
                )
            }
        },
        confirmButton = { TextButton({ onSave(zh, vi, wrong, type, narr) }, enabled = vi.isNotBlank()) { Text("Lưu") } },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(it) { Text("Xoá", color = cs.error) } }
                TextButton(onDismiss) { Text("Huỷ") }
            }
        },
    )
}
