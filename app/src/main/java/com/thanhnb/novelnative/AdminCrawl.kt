package com.thanhnb.novelnative

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
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
    "translate_ahead" to "Số chương dịch sẵn khi người đọc mở chương",
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

/** Đầu mục bấm để thu gọn/mở: thẻ bo tròn, mũi tên xoay, số tóm tắt dạng viên thuốc. */
@Composable
internal fun Fold(title: String, open: Boolean, trailing: String? = null, toggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val rot by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, androidx.compose.animation.core.spring(dampingRatio = .8f, stiffness = 400f), label = "fold")
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(shape).background(if (open) cs.primary.copy(.09f) else cs.card)
            .bounceClick(onClick = toggle).padding(start = 16.dp, end = 10.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = if (open) cs.primary else cs.onSurface)
        if (trailing != null) Text(trailing, Modifier.clip(CircleShape).background(cs.onSurface.copy(.07f)).padding(horizontal = 9.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium)
        Icon(Icons.Rounded.ExpandMore, null, Modifier.padding(start = 4.dp).graphicsLayer { rotationZ = rot }, tint = cs.onSurfaceVariant)
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
            fun v(k: String) = p?.get(k)?.let { fmtK(it) } ?: "…"
            val failedCh = p?.get("failedChapters") ?: 0
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                StatRow(
                    Triple(v("novels24h"), "truyện mới 24h", cs.primary), Triple(v("chapters24h"), "chương về 24h", null),
                    Triple(v("done24h"), "dịch xong 24h", cs.live), Triple(v("failedChapters"), "chương lỗi", if (failedCh > 0) cs.error else null),
                )
                MetaChips(
                    "${v("done1h")}" to "chương/giờ", "${v("queued")}" to "chờ dịch", "${v("metaPending")}" to "chưa dịch giới thiệu",
                    "${v("failedJobs")}" to "job lỗi", "${v("tracked")}" to "truyện theo dõi",
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
    edit?.let { s ->
        val catKey = when (s.optString("key")) { "gemini_models", "gemini_metadata_models" -> "gemini_catalog"; "llm_model" -> "nvidia_catalog"; else -> null }
        val cat = catKey?.let { k -> settings.res?.getOrNull()?.firstOrNull { it.optString("key") == k }?.optString("value") }?.let(::parseCatalog).orEmpty()
        // danh mục trống/thiếu → sửa bằng ô chữ như cũ
        if (cat.isEmpty()) EditSettingDialog(s, { edit = null }) { settings.reload() }
        else ModelPickerDialog(s, cat, { edit = null }) { act = it }
    }
}

/** 1 dòng danh mục model: `id | RPM/TPM/RPD | ghi chú` (mirror knob gemini_catalog / nvidia_catalog, mig 137). */
class CatalogItem(val id: String, val limits: String, val note: String)

fun parseCatalog(raw: String): List<CatalogItem> = raw.lines().mapNotNull { line ->
    val f = line.split('|').map { it.trim() }
    if (f.first().isEmpty()) null else CatalogItem(f.first(), f.getOrElse(1) { "" }, f.drop(2).joinToString(" | "))
}

/**
 * Chọn model từ danh mục. Gemini (knob `gemini_*`): chọn nhiều, thứ tự = ưu tiên (nút ↑↓); NVIDIA (`llm_model`): chọn một.
 * Ghi giá trị qua hộp xác nhận. Model đang dùng mà không có trong danh mục vẫn hiện (nhãn "ngoài danh mục") để không bị mất âm thầm.
 */
@Composable
private fun ModelPickerDialog(s: JSONObject, cat: List<CatalogItem>, close: () -> Unit, ask: (Act) -> Unit) {
    val key = s.optString("key")
    val multi = key != "llm_model"
    val cs = MaterialTheme.colorScheme
    val cur = s.optString("value")
    // id → token đang lưu (giữ nguyên trần tuỳ chỉnh/số 0 "tắt" của model có sẵn)
    val tokens = remember { cur.split(',').map { it.trim() }.filter { it.isNotEmpty() }.associateBy { it.split(Regex("\\s+")).first() } }
    val picked = remember { mutableStateListOf<String>().apply { addAll(if (multi) tokens.keys else tokens.keys.take(1)) } }
    val byId = cat.associateBy { it.id }
    val all = picked + cat.map { it.id }.filter { it !in picked }
    fun token(id: String) = tokens[id] ?: byId[id]?.let { if (it.limits.isEmpty()) it.id else "${it.id} ${it.limits}" } ?: id
    val newValue = picked.joinToString(", ") { token(it) }
    AlertDialog(
        close, title = { Text(s.settingTitle()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (multi) "Tick để dùng; model ở trên được gọi trước, hết lượt mới tới model dưới." else "Chọn một model.", style = MaterialTheme.typography.bodySmall)
                all.forEach { id ->
                    val on = id in picked
                    val item = byId[id]
                    val i = picked.indexOf(id)
                    Row(
                        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(if (on) cs.primary.copy(.09f) else Color.Transparent)
                            .bounceClick { if (multi) { if (on) picked.remove(id) else picked.add(id) } else { picked.clear(); picked.add(id) } }.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (multi) Checkbox(on, null, Modifier.padding(8.dp)) else RadioButton(on, null, Modifier.padding(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(id, style = monoStyle(12, if (on) cs.primary else cs.onSurface))
                            val sub = listOfNotNull(if (on && multi) "ưu tiên ${i + 1}" else null, item?.limits?.split("/")?.takeIf { it.size == 3 }?.let { "${it[0]} RPM · ${it[2].toIntOrNull()?.let(::fmtK) ?: it[2]}/ngày" }, item?.note?.ifEmpty { null }, if (item == null) "ngoài danh mục" else null).joinToString(" · ")
                            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                        if (multi && on) {
                            IconButton({ if (i > 0) java.util.Collections.swap(picked, i, i - 1) }, enabled = i > 0, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.KeyboardArrowUp, "Lên") }
                            IconButton({ if (i < picked.lastIndex) java.util.Collections.swap(picked, i, i + 1) }, enabled = i < picked.lastIndex, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.KeyboardArrowDown, "Xuống") }
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(close) { Text("Huỷ") } },
        confirmButton = {
            TextButton({
                close()
                ask(Act("Lưu danh sách model?", "${s.settingTitle()}:\n" + picked.mapIndexed { i, id -> "${i + 1}. $id" }.joinToString("\n"), "Lưu") {
                    AdminApi.saveSetting(key, newValue); "Đã lưu — worker tự nhận ở chu kỳ kế"
                })
            }, enabled = picked.isNotEmpty() && newValue != cur) { Text("Lưu") }
        },
    )
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
    val lines = value.count { it == '\n' } + 1
    val long = (value.length > 16 || ',' in value) && lines == 1
    val summary = if (lines > 1) "$lines dòng" else null
    Column(Modifier.fillMaxWidth().bounceClick { edit(s) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.settingTitle(), style = MaterialTheme.typography.titleSmall)
                Text(key, style = monoStyle(10))
            }
            if (!long) Text(summary ?: value, Modifier.padding(start = 10.dp), style = monoStyle(13, cs.primary))
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
