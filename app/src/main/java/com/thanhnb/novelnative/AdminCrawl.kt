package com.thanhnb.novelnative

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** 1 model trong knob gemini_models: tên + trần free tier. */
class GeminiModel(val name: String, val rpm: Int, val tpm: Int, val rpd: Int)

/**
 * 'gemini-3.1-flash-lite 15/250000/500, gemma-4-31b-it' → danh sách model kèm trần.
 * MIRROR của parse_gemini_models (worker/novelworker/translator/providers.py) và parseGeminiModels bên Flutter.
 * Thiếu/hỏng trần → 5/250000/20 như worker.
 */
fun parseGeminiModels(raw: String): List<GeminiModel> = raw.split(',').mapNotNull { part ->
    val bits = part.trim().split(Regex("\\s+"))
    if (bits.first().isEmpty()) return@mapNotNull null
    val nums = bits.getOrNull(1)?.split('/')?.map { it.toIntOrNull() }.orEmpty()
    val ok = nums.size == 3 && nums.all { it != null && it >= 0 }
    GeminiModel(bits.first(), if (ok) nums[0]!! else 5, if (ok) nums[1]!! else 250000, if (ok) nums[2]!! else 20)
}

// Knob của translator (note bắt đầu 'DỊCH'); tên ngắn + thứ tự theo đường đi một request. Knob mới chưa có ở đây thì hiện note.
private val transLabels = linkedMapOf(
    "default_engine" to "Engine cho truyện MỚI",
    "gemini_models" to "Model Gemini · thứ tự ưu tiên",
    "gemini_metadata_models" to "Model Gemini cho tên + mô tả truyện",
    "llm_model" to "Model NVIDIA dự phòng",
    "llm_timeout_sec" to "Timeout 1 lượt gọi LLM (giây)",
    "translator_concurrency" to "Số luồng dịch",
    "nvidia_rpm_limit" to "Request/phút mỗi key NVIDIA",
    "nvidia_inflight_per_key" to "Request NVIDIA đang chạy mỗi key",
    "max_chapters_per_day" to "Trần chương dịch mỗi ngày",
    "audit_interval_min" to "Chu kỳ quét chương dịch hỏng (phút)",
)

private fun JSONObject.isTrans() = (s("note") ?: "").startsWith("DỊCH")
private fun JSONObject.settingTitle() = transLabels[optString("key")] ?: (s("note") ?: optString("key")).replace(Regex("^(DỊCH|CRAWL) · "), "")

/** Đầu mục bấm để thu gọn/mở. */
@Composable
internal fun Fold(title: String, open: Boolean, trailing: String? = null, toggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().bounceClick(onClick = toggle).padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow(title, Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 6.dp))
        Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = cs.onSurfaceVariant)
    }
}

/** Crawl: nhịp 24 giờ + cấu hình dịch/crawler + nguồn (bật/tắt) + truyện mới về. */
@Composable
fun CrawlTab() {
    val pulse = load("admin.pulse") { AdminApi.crawlPulse() }
    val beats = load("admin.beats") { AdminApi.heartbeat() }
    val settings = load("admin.settings") { AdminApi.settings() }
    val sources = load("admin.sources") { AdminApi.sources() }
    val fresh = load("admin.fresh") { AdminApi.newNovels24h() }
    // mặc định thu gọn hết: tab dài, thẻ nhịp ở đầu luôn hiện
    var openTrans by rememberSaveable { mutableStateOf(false) }
    var openCfg by rememberSaveable { mutableStateOf(false) }
    var openSrc by rememberSaveable { mutableStateOf(false) }
    var openFresh by rememberSaveable { mutableStateOf(false) }
    var act by remember { mutableStateOf<Act?>(null) }
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { while (true) { delay(30_000); pulse.reload(); beats.reload() } }
    AdminPane(Loader(Result.success(Unit)) { pulse.reload(); beats.reload(); settings.reload(); sources.reload(); fresh.reload() }) {
        item {
            val p = pulse.res?.getOrNull()
            fun v(k: String) = p?.get(k)?.let { fmtK(it) } ?: "—"
            val failedCh = p?.get("failedChapters") ?: 0
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                StatRow(
                    Triple(v("novels24h"), "truyện mới 24h", cs.primary), Triple(v("chapters24h"), "chương về 24h", null),
                    Triple(v("done24h"), "dịch xong 24h", cs.live), Triple(v("failedChapters"), "chương lỗi", if (failedCh > 0) cs.error else null),
                )
                Text(
                    "${v("done1h")} chương/giờ · chờ dịch ${v("queued")} · chưa dịch giới thiệu ${v("metaPending")} · job lỗi ${v("failedJobs")} · đang theo dõi ${v("tracked")} truyện",
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp),
                )
                val b = beats.res?.getOrNull().orEmpty()
                Pulse(b, "crawler", "Crawler")
                Pulse(b, "translator", "Translator")
            }
            HorizontalDivider(Modifier.padding(top = 8.dp), color = cs.outlineVariant.copy(.5f))
        }
        val st = settings.res?.getOrNull().orEmpty()
        item { Fold("Cấu hình dịch", openTrans) { openTrans = !openTrans } }
        if (openTrans) {
            item { Text("Gemini xoay key × model theo trần free tier, hết sạch lượt mới rơi về NVIDIA. Sửa xong worker tự nhận trong ~1 phút.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium) }
            items(st.filter { it.isTrans() }.sortedBy { transLabels.keys.indexOf(it.optString("key")).let { i -> if (i < 0) 99 else i } }, key = { "t" + it.optString("key") }) { s ->
                SettingRow(s, { act = it }, { edit = it })
            }
        }
        item { Fold("Cấu hình crawler", openCfg) { openCfg = !openCfg } }
        if (openCfg) {
            item { Text("Sửa xong worker tự nhận ở chu kỳ kế — không cần restart.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium) }
            items(st.filter { !it.isTrans() }, key = { "c" + it.optString("key") }) { s -> SettingRow(s, { act = it }, { edit = it }) }
        }
        val src = sources.res?.getOrNull().orEmpty()
        item { Fold("Nguồn crawl", openSrc, if (src.isEmpty()) null else "${src.count { it.optBoolean("enabled") && it.optInt("fail_count") == 0 }}/${src.size} sống") { openSrc = !openSrc } }
        if (openSrc) items(src, key = { "s" + it.getInt("id") }) { s ->
            val on = s.optBoolean("enabled")
            val failing = s.optInt("fail_count") > 0
            val host = s.optString("base_url").substringAfter("://").substringBefore('/').removePrefix("www.")
            val (chip, color) = if (!on) "TẮT" to cs.onSurfaceVariant else if (failing) "LỖI" to cs.error else "SỐNG" to cs.live
            val name = s.optString("name")
            AdminRow(
                null, name, listOfNotNull(chip, host, if (failing) "fail ${s.optInt("fail_count")} chu kỳ" else null, if (on) s.s("last_ok_at")?.let { "OK ${ago(it)} trước" } else null).joinToString(" · "),
                subColor = if (failing) cs.error else cs.onSurfaceVariant,
                trailing = {
                    Switch(on, {
                        act = Act(if (it) "Bật nguồn $name?" else "Tắt nguồn $name?", if (it) "Crawler sẽ tự nhận nguồn này trong khoảng 10 giây." else "Crawler ngừng lấy truyện/chương từ nguồn này; job dịch cần bản gốc của nguồn sẽ chuyển sang \"nguồn không khả dụng\".", if (it) "Bật" else "Tắt") {
                            AdminApi.setSourceEnabled(s.getInt("id"), it); if (it) "Đã bật $name" else "Đã tắt $name"
                        }
                    })
                },
            )
        }
        val fr = fresh.res?.getOrNull().orEmpty()
        item { Fold("Truyện mới về · 24 giờ", openFresh, if (fresh.res == null) null else "${fr.size} truyện") { openFresh = !openFresh } }
        if (openFresh) {
            if (fr.isEmpty()) item { Empty("Chưa có truyện mới trong 24 giờ.") }
            items(fr, key = { "f" + it.getInt("id") }) { n ->
                val id = n.getInt("id")
                val title = n.novelTitle(id)
                val rank = n.takeIf { !it.isNull("source_rank") }?.optInt("source_rank")
                val srcName = n.optJSONObject("sources")?.s("name")
                Row(Modifier.fillMaxWidth().bounceClick { Nav.push(Screen.AdminNovel(id)) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(n.s("cover_url"), 38.dp, 52.dp, label = title, flat = true)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("về ${ago(n.s("created_at"))} trước · ${n.optInt("chapter_count_source")} chương · ${if (n.optString("status") == "completed") "hoàn thành" else "đang ra"}", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (rank != null) Text("Top #${rank + 1}" + (srcName?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelMedium, color = cs.primary)
                    else if (srcName != null) Text(srcName, style = MaterialTheme.typography.labelMedium)
                }
                HorizontalDivider(Modifier.padding(start = 66.dp, end = 16.dp), color = cs.outlineVariant.copy(.5f))
            }
        }
    }
    ConfirmDialog(act, { act = null }) { sources.reload(); settings.reload() }
    edit?.let { s -> EditSettingDialog(s, { edit = null }) { settings.reload() } }
}

/** Một knob: tên thân thiện + key mono + giá trị (model dài thì xuống từng dòng). Engine là nút chọn. */
@Composable
private fun SettingRow(s: JSONObject, ask: (Act) -> Unit, edit: (JSONObject) -> Unit) {
    val key = s.optString("key")
    val value = s.optString("value")
    val cs = MaterialTheme.colorScheme
    if (key == "default_engine") {
        val llm = value == "llm" || value == "nvidia"
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(s.settingTitle(), style = MaterialTheme.typography.titleSmall)
            Text(if (llm) "Gemini (hết lượt thì NVIDIA) — khá hơn ở câu rắc rối, tốn quota" else "Hachimi — model local trên box, miễn phí. Truyện cũ giữ engine của nó.", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "Hachimi", true to "LLM").forEach { (isLlm, label) ->
                    FilterChip(llm == isLlm, {
                        if (llm != isLlm) ask(Act("Đổi engine mặc định?", "Truyện MỚI sẽ dịch bằng $label. Truyện cũ giữ engine của nó.", "Đổi") {
                            AdminApi.saveSetting(key, if (isLlm) "llm" else "hachimi"); "Đã đổi engine sang $label"
                        })
                    }, { Text(label) })
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant.copy(.5f))
        return
    }
    val long = value.length > 16 || ',' in value
    Column(Modifier.fillMaxWidth().bounceClick { edit(s) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.settingTitle(), style = MaterialTheme.typography.titleSmall)
                Text(key, style = monoStyle(10))
            }
            if (!long) Text(value, Modifier.padding(start = 10.dp), style = monoStyle(13, cs.primary))
        }
        // giá trị dài (chuỗi model): mỗi model một dòng
        if (long) value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
            val txt = if (key == "gemini_models") parseGeminiModels(part).first().let { m -> if (m.rpd == 0) "${m.name} · tắt" else "${m.name} · ${m.rpm} RPM · ${fmtK(m.rpd)}/ngày" } else part
            Text(txt, Modifier.padding(top = 4.dp), style = monoStyle(11, cs.primary))
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant.copy(.5f))
}

@Composable
private fun EditSettingDialog(s: JSONObject, close: () -> Unit, saved: () -> Unit) {
    val key = s.optString("key")
    var v by remember { mutableStateOf(s.optString("value")) }
    val isNumber = s.optString("value").toIntOrNull() != null
    val note = (s.s("note") ?: "").replace(Regex("^(DỊCH|CRAWL) · "), "")
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val bad = v.trim().isEmpty() || (isNumber && (v.trim().toIntOrNull() ?: -1) < 0) // số không âm (0 = tắt với knob hỗ trợ)
    AlertDialog(
        close, title = { Text(s.settingTitle()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (transLabels.containsKey(key) && note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    v, { v = it }, Modifier.fillMaxWidth(), label = { Text(key) }, singleLine = isNumber,
                    keyboardOptions = KeyboardOptions(keyboardType = if (isNumber) KeyboardType.Number else KeyboardType.Text),
                    supportingText = if (isNumber) null else ({ Text("Nhiều giá trị thì ngăn bằng dấu phẩy.") }),
                )
            }
        },
        dismissButton = { TextButton(close) { Text("Huỷ") } },
        confirmButton = {
            TextButton({
                close()
                scope.launch {
                    runCatching { AdminApi.saveSetting(key, v.trim()) }.onSuccess { toast(ctx, "Đã lưu — worker tự nhận ở chu kỳ kế") }.onFailure { toast(ctx, friendly(it)) }
                    saved()
                }
            }, enabled = !bad) { Text("Lưu") }
        },
    )
}
