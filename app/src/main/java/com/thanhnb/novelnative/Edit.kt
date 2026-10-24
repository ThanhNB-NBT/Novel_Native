package com.thanhnb.novelnative

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Sửa bản dịch (như reader_edit_form.dart): nhấn giữ một đoạn → sheet có đoạn đó trong ô chữ.
 * Bôi từ sai + gõ bản đúng → "Thay từ" (chỉ trong đoạn này, tên riêng thì học vào thuật ngữ);
 * hoặc sửa thẳng trong ô rồi "Lưu cả đoạn". [zh] là câu gốc để hỏi AI (null = không khớp dòng).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSheet(novelId: Int, index: Int, para: String, zh: String?, onDone: (String, Boolean) -> Unit, onReport: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var tf by remember { mutableStateOf(TextFieldValue(para)) }
    var correct by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var ai by remember { mutableStateOf<Suggest?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val untouched = tf.text == para
    val wrong = if (untouched) tf.text.substring(tf.selection.min, tf.selection.max).trim() else ""
    val wordOk = wrong.isNotEmpty() && correct.isNotBlank() && correct.trim() != wrong
    val paraOk = !untouched && tf.text.isNotBlank()

    fun run(msg: (Boolean) -> String, job: suspend () -> Boolean) {
        if (Session.user == null) { err = "Đăng nhập để sửa bản dịch"; return }
        busy = true
        err = null
        scope.launch {
            try {
                val name = job()
                onDone(msg(name), name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                err = "Chưa lưu được: ${friendly(e)}" // giữ nguyên sheet để không mất bản đang gõ
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).imePadding().padding(20.dp, 0.dp, 20.dp, 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Sửa bản dịch", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            tf, { tf = it }, Modifier.fillMaxWidth(), minLines = 3, maxLines = 7,
            label = { Text("Đoạn văn — bôi từ cần sửa") },
            supportingText = { Text(if (untouched) "Bôi chọn từ/cụm sai, gõ bản đúng bên dưới" else "Đang sửa cả đoạn — bấm Lưu cả đoạn") },
        )
        if (untouched) {
            Text(
                if (wrong.isEmpty()) "Chưa bôi từ nào" else "Từ sai: “$wrong”",
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(cs.error.copy(alpha = 0.08f))
                    .border(1.dp, cs.error.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).padding(12.dp, 9.dp),
                style = MaterialTheme.typography.bodyMedium, color = cs.error, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            OutlinedTextField(correct, { correct = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Bản sửa") })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Hoa Từng Chữ") { correct = titleCase(correct.ifEmpty { wrong }) }
                Chip("Hoa chữ đầu") { correct = correct.ifEmpty { wrong }.let { it.take(1).uppercase() + it.drop(1).lowercase() } }
                Chip("thường") { correct = correct.ifEmpty { wrong }.lowercase() }
                if (zh != null && wrong.isNotEmpty()) Chip("Gợi ý AI", true) {
                    aiBusy = true
                    ai = null
                    scope.launch {
                        ai = runCatching { Api.suggest(zh, para, wrong) }.getOrElse { Suggest(wrong, false, emptyList()) }
                        aiBusy = false
                    }
                }
            }
            if (aiBusy) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("  AI đang đọc câu gốc…", style = MaterialTheme.typography.labelMedium)
            }
            ai?.let { r ->
                Text(
                    when {
                        r.items.isEmpty() && !r.dung -> "AI không đưa ra được cách dịch khác"
                        r.dung -> "AI: “${r.thay}” đã dịch đúng" + if (r.items.isEmpty()) "" else " — cách nói tương đương:"
                        else -> "AI gợi ý thay “${r.thay}”:"
                    },
                    style = MaterialTheme.typography.labelMedium, color = if (r.dung) cs.primary else cs.onSurfaceVariant,
                )
                r.items.forEach { (vi, y) ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { correct = vi }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = cs.primary)
                        Text(if (y.isBlank()) vi else "$vi · $y", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        err?.let { Text(it, color = cs.error, style = MaterialTheme.typography.labelMedium) }
        Button(
            {
                val w = wrong
                val v = correct.trim()
                val name = looksLikeName(w) && looksLikeName(v) // chỉ tên riêng mới vào thuật ngữ: từ thường áp cả truyện sẽ vá nhầm
                run({ if (it) "Đã sửa" else "Đã sửa trong chương này" }) {
                    Api.editWord(novelId, index, w, v, para)
                    if (name) Api.learnTerm(novelId, w, v)
                    name
                }
            },
            Modifier.fillMaxWidth(), enabled = untouched && wordOk && !busy,
        ) { Text("Thay từ trong đoạn") }
        OutlinedButton(
            { run({ "Đã sửa đoạn" }) { Api.editPara(novelId, index, para, tf.text.trim()); false } },
            Modifier.fillMaxWidth(), enabled = paraOk && !busy, border = BorderStroke(1.dp, cs.outlineVariant),
        ) { Text("Lưu cả đoạn") }
        TextButton(onReport, Modifier.fillMaxWidth()) { Text("Báo lỗi, không sửa chương") }
    }
}

@Composable
private fun Chip(label: String, ai: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(17.dp)
    Row(
        Modifier.height(34.dp).clip(shape).border(1.dp, if (ai) cs.primary.copy(alpha = 0.6f) else cs.outlineVariant, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ai) Icon(Icons.Rounded.AutoAwesome, null, Modifier.padding(end = 6.dp).size(15.dp), tint = cs.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (ai) cs.primary else cs.onSurfaceVariant)
    }
}

private fun titleCase(s: String) = s.split(' ').joinToString(" ") { w -> w.take(1).uppercase() + w.drop(1).lowercase() }

/** Mọi từ viết hoa chữ đầu → trông như tên riêng. */
private fun looksLikeName(s: String): Boolean {
    val ws = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return ws.isNotEmpty() && ws.all { it[0].isUpperCase() }
}
