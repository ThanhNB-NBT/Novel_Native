package com.thanhnb.novelnative

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset

/** n ngày quota gần nhất, mới nhất trước, dạng yyyy-MM-dd (khớp cột day của RPC). Quota Google reset nửa đêm Pacific. */
// ponytail: lấy Pacific = UTC-7 (giờ mùa hè); mùa đông lệch 1 giờ quanh nửa đêm Pacific, cần chính xác thì lấy mốc từ RPC.
private fun lastDays(n: Int): List<String> = LocalDate.now(ZoneOffset.ofHours(-7)).let { b -> List(n) { b.minusDays(it.toLong()).toString() } }

private fun dm(day: String) = "${day.substring(8, 10)}/${day.substring(5, 7)}"

private fun List<JSONObject>.sum(f: String) = sumOf { it.optInt(f) }

/** Nhãn provider từ knob thong_ke_provider (mỗi dòng `provider | Nhãn | ghi chú`). */
private fun providerLabels(settings: List<JSONObject>): Map<String, Pair<String, String?>> {
    val raw = settings.firstOrNull { it.optString("key") == "thong_ke_provider" }?.optString("value").orEmpty()
    return raw.lines().filter { it.isNotBlank() }.map { l -> l.split('|').map { it.trim() } }.associate { p ->
        p[0] to ((p.getOrNull(1)?.ifEmpty { null } ?: p[0]) to p.getOrNull(2)?.ifEmpty { null })
    }
}

/**
 * Thống kê API LLM: cột request 14 ngày, lượt Gemini còn lại theo key × model, sức khỏe model.
 * "Ngày" là NGÀY QUOTA của Google (nửa đêm Pacific = 14:00 giờ VN) để khớp con số AI Studio dùng khi chặn.
 */
@Composable
fun LlmScreen() {
    val usage = load("admin.llm") { AdminApi.llmUsageDaily() }
    val settings = load("admin.settings") { AdminApi.settings() }
    val tokens = load("admin.tokens") { AdminApi.tokenUsage() }
    var pick by rememberSaveable { mutableStateOf<String?>(null) } // null = ngày quota hiện tại
    val closed = remember { mutableStateListOf("tokens") } // mặc định đóng bảng chương theo model
    fun reload() { usage.reload(); settings.reload(); tokens.reload() }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Quản trị", "Thống kê API", "數", back = true)
        Loaded(usage) { d ->
            val cs = MaterialTheme.colorScheme
            val rows = d.optJSONArray("usage")?.let { a -> List(a.length()) { a.getJSONObject(it) } }.orEmpty()
            val chapters = d.optJSONArray("chapters")?.let { a -> List(a.length()) { a.getJSONObject(it) } }.orEmpty().associate { it.optString("day") to it.optInt("n") }
            val days = lastDays(14)
            val day = pick ?: days.first()
            val st = settings.res?.getOrNull().orEmpty()
            val labels = providerLabels(st)
            fun label(p: String) = labels[p]?.first ?: p.uppercase()
            fun of(dd: String, p: String? = null) = rows.filter { it.optString("day") == dd && (p == null || it.optString("provider") == p) }
            val today = of(day)
            val providers = rows.map { it.optString("provider") }.distinct().sortedWith(compareBy({ it != "gemini" }, { -of(day, it).sum("requests") }))
            val req = today.sum("requests")
            val ok = today.sum("ok")
            val health = rows.groupBy { it.optString("model") }.entries.sortedByDescending { it.value.sum("requests") }
            val gemRaw = st.firstOrNull { it.optString("key") == "gemini_models" }?.optString("value").orEmpty()
            val keys = rows.filter { it.optString("provider") == "gemini" }.map { it.optString("key_label") }.filter { Regex("^gemini#\\d+$").matches(it) }.distinct().sorted()

            @Composable
            fun section(id: String, title: String, trailing: String? = null, body: @Composable ColumnScope.() -> Unit) {
                val open = id !in closed
                Fold(title, open, trailing) { if (open) closed.add(id) else closed.remove(id) }
                if (open) Column(Modifier.padding(horizontal = 16.dp), content = body)
            }

            PullToRefreshBox(false, ::reload) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Legend(cs.primary, "Gemini"); Spacer(Modifier.width(14.dp)); Legend(cs.tertiary, "Khác")
                                Spacer(Modifier.weight(1f)); Text("reset 14:00 VN", style = MaterialTheme.typography.labelMedium)
                            }
                            DayBars(days.reversed(), day, { dd -> of(dd, "gemini").sum("requests") }, { dd -> of(dd).sum("requests") - of(dd, "gemini").sum("requests") }) { dd -> pick = if (dd == days.first()) null else dd }
                        }
                        section("day", if (day == days.first()) "Hôm nay · ${dm(day)}" else "Ngày ${dm(day)}") {
                            StatRow(
                                Triple(fmtK(req), "request", cs.primary), Triple(fmtK(chapters[day] ?: 0), "chương dịch", null),
                                Triple(if (req == 0) "—" else "${ok * 100 / req}%", "thành công", if (req > 0 && ok.toFloat() / req < .85f) cs.error else null),
                            )
                            Text(
                                providers.joinToString(" · ") { "${label(it)} ${fmtK(of(day, it).sum("requests"))}" } + " · 429: ${today.sum("rate_limited")} · lỗi khác: ${today.sum("failed")}\n" +
                                    "token vào ${fmtK(today.sum("prompt_tokens"))} · ra ${fmtK(today.sum("completion_tokens"))}",
                                Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        section("keys", "Lượt Gemini theo key") {
                            if (keys.isEmpty()) Text("Chưa có request Gemini nào — worker cần bản mới và key trong .env.", style = MaterialTheme.typography.bodySmall)
                            else GeminiQuota(parseGeminiModels(gemRaw), of(day, "gemini"), keys)
                        }
                        providers.filter { it != "gemini" && of(day, it).isNotEmpty() }.forEach { p ->
                            section(p, label(p).uppercase(), labels[p]?.second) { of(day, p).forEach { UsageRow(it) } }
                        }
                        if (health.isNotEmpty()) section("health", "Sức khỏe model", "từ bản Gemini") { health.forEach { HealthRow(it.key, it.value) } }
                        val tk = tokens.res?.getOrNull().orEmpty()
                        if (tk.isNotEmpty()) section("tokens", "Chương đã dịch theo model", "từ trước tới nay") {
                            tk.forEach { r ->
                                Row(Modifier.padding(vertical = 8.dp)) {
                                    Text(r.s("model_used") ?: "(?)", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${fmtK(r.optInt("chapters"))} ch · ${fmtK(r.optLong("prompt_tokens") + r.optLong("completion_tokens"))} tok", style = monoStyle(11))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(c: Color, s: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(10.dp).background(c, RoundedCornerShape(3.dp)))
    Text(s, Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelMedium)
}

/** 1 dòng request theo key × model: số lượt + OK/429/lỗi + latency trung bình. */
@Composable
private fun UsageRow(r: JSONObject) {
    val req = r.optInt("requests")
    val avg = if (req > 0) r.optLong("total_latency_ms") / req / 1000.0 else 0.0
    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.optString("model"), style = monoStyle(11, MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${r.optString("key_label")} · OK ${r.optInt("ok")} · 429 ${r.optInt("rate_limited")} · lỗi ${r.optInt("failed")} · ${"%.1f".format(avg)}s TB", style = MaterialTheme.typography.labelSmall)
        }
        Text(fmtK(req), style = MaterialTheme.typography.titleSmall)
    }
}

/** Sức khỏe model gộp từ sổ llm_usage: chấm sống/chậm/chết + latency TB + % OK. Dưới 5 lượt chưa đủ kết luận. */
@Composable
private fun HealthRow(model: String, rs: List<JSONObject>) {
    val cs = MaterialTheme.colorScheme
    val total = rs.sum("requests")
    val ok = rs.sum("ok")
    val rate = if (total > 0) ok.toFloat() / total else 0f
    val avgMs = if (total > 0) rs.sumOf { it.optLong("total_latency_ms") }.toDouble() / total else 0.0
    val (dot, label) = when {
        total < 5 -> cs.onSurfaceVariant to "ít dữ liệu"
        rate < .5f -> cs.error to "chết"
        avgMs > 90000 || rate < .85f -> cs.secondary to "chậm"
        else -> cs.live to "sống"
    }
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(10.dp).background(dot, CircleShape))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(model, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${"%.1f".format(avgMs / 1000)}s TB · ${(rate * 100).toInt()}% OK ($ok/$total) · 429: ${rs.sum("rate_limited")} · lỗi: ${rs.sum("failed")}", style = MaterialTheme.typography.labelSmall)
        }
        Text(label, color = dot, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
    }
}

/** Biểu đồ cột chồng 14 ngày (tự vẽ bằng Box); chạm cột để xem chi tiết ngày đó. */
@Composable
private fun DayBars(days: List<String>, selected: String, gemini: (String) -> Int, other: (String) -> Int, onTap: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val h = 96.dp
    val peak = days.maxOf { gemini(it) + other(it) }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { d ->
            Column(Modifier.weight(1f).clickable { onTap(d) }, horizontalAlignment = Alignment.CenterHorizontally) {
                val sel = d == selected
                Text(if (sel) fmtK(gemini(d) + other(d)) else "", style = monoStyle(9, cs.primary), maxLines = 1)
                Column(Modifier.height(h).padding(top = 3.dp), verticalArrangement = Arrangement.Bottom) {
                    listOf(other(d) to cs.tertiary, gemini(d) to cs.primary).forEach { (v, c) ->
                        if (v > 0) Box(Modifier.width(14.dp).height((h * v / peak).coerceAtLeast(2.dp)).alpha(if (sel) 1f else .45f).background(c, RoundedCornerShape(3.dp)))
                    }
                }
                Text(d.substring(8, 10), Modifier.padding(top = 5.dp), style = monoStyle(10, if (sel) cs.primary else cs.onSurfaceVariant))
            }
        }
    }
}

/** Mỗi model Gemini (theo thứ tự ưu tiên của knob) + thanh lượt đã dùng / trần RPD từng key. */
@Composable
private fun GeminiQuota(models: List<GeminiModel>, dayRows: List<JSONObject>, keys: List<String>) {
    val cs = MaterialTheme.colorScheme
    val names = models.map { it.name } + dayRows.map { it.optString("model") }.distinct().filter { n -> models.none { it.name == n } }
    names.forEachIndexed { i, name ->
        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = cs.outlineVariant.copy(.5f))
        val m = models.firstOrNull { it.name == name }
        val used = dayRows.filter { it.optString("model") == name }.sum("requests")
        Row {
            Text(name, Modifier.weight(1f), style = monoStyle(12, cs.onSurface), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (m == null) "$used · đã gỡ khỏi chuỗi" else "$used/${fmtK(m.rpd * keys.size)} · ${m.rpm} RPM", style = MaterialTheme.typography.labelSmall)
        }
        keys.forEach { k ->
            val u = dayRows.filter { it.optString("model") == name && it.optString("key_label") == k }.sum("requests")
            val rpd = m?.rpd
            Gauge(k, if (rpd == null || rpd == 0) null else u.toFloat() / rpd, if (rpd == null) "$u" else "$u/$rpd", Modifier.padding(top = 6.dp))
        }
    }
}
