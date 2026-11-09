package com.thanhnb.novelnative

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

private const val DAYS = 14

private fun JSONArray?.objs() = this?.let { a -> List(a.length()) { a.getJSONObject(it) } }.orEmpty()

private fun pct(k: Int, n: Int) = if (n > 0) "${k * 100 / n}%" else "–"

/** Một dòng tóm tắt nhóm: "120 chương · TB 96,4 · 8 dưới 90". */
private fun JSONObject.brief() = "${optInt("n")} chương · TB ${optString("avg", "–")} · ${optInt("bad")} dưới 90"

/**
 * Chất lượng dịch: điểm 0–100 worker chấm bằng luật lúc dịch xong (worker/translator/diem_dich.py).
 * Chỉ bắt lỗi máy đo được (lệch dòng, sai thuật ngữ, cụt/phình, còn Hán, lặp cụm, mã sót) — văn dở thì không.
 */
@Composable
fun QualityTab() {
    val l = load("admin.quality") { AdminApi.quality(DAYS) }
    AdminPane(l) { (o, worst) ->
        val w = o.optJSONObject("window") ?: JSONObject()
        val n = w.optInt("n")
        item {
            SectionLabel("$DAYS ngày qua", "$n chương")
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Điểm TB ${w.optString("avg", "–")} / 100", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Dưới 90: ${w.optInt("bad")} (${pct(w.optInt("bad"), n)}) · lệch dòng: ${w.optInt("lech")} · sai thuật ngữ: ${w.optInt("term")}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                o.optJSONObject("all")?.let {
                    Text("Toàn kho: ${it.optInt("n")} chương đã chấm · TB ${it.optString("avg", "–")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (n == 0) item { Empty("Chưa có chương nào được chấm trong $DAYS ngày qua.") }

        val models = o.optJSONArray("models").objs()
        if (models.isNotEmpty()) item {
            SectionLabel("Theo model")
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                models.forEach { m -> Gauge("${m.optString("model")} · ${m.optInt("n")} ch", (m.optDouble("avg", 0.0) / 100).toFloat(), "${m.optString("avg")} · ${pct(m.optInt("bad"), m.optInt("n"))} dưới 90", danger = m.optDouble("avg", 0.0) < 90) }
            }
        }

        val daily = o.optJSONArray("daily").objs()
        if (daily.isNotEmpty()) item {
            SectionLabel("Theo ngày")
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                daily.forEach { d -> Gauge(d.optString("day").takeLast(5).split('-').reversed().joinToString("/") + " · ${d.optInt("n")} ch", (d.optDouble("avg", 0.0) / 100).toFloat(), d.optString("avg"), danger = d.optDouble("avg", 0.0) < 90) }
            }
        }

        val novels = o.optJSONArray("novels").objs()
        if (novels.isNotEmpty()) item { SectionLabel("Truyện điểm thấp", "${novels.size}") }
        items(novels, key = { "n" + it.optInt("novel_id") }) { v ->
            val id = v.optInt("novel_id")
            AdminRow(null, v.s("title") ?: "Truyện #$id", v.brief(), onClick = { Nav.push(Screen.AdminNovel(id)) })
        }

        if (worst.isNotEmpty()) item { SectionLabel("Chương dưới 90 điểm", "${worst.size}") }
        items(worst, key = { "c" + it.optLong("chapter_id") }) { c ->
            val nid = c.optInt("novel_id")
            val title = c.s("title") ?: "Truyện #$nid"
            val idx = c.optInt("chapter_index")
            val why = c.optJSONArray("reasons")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty().joinToString(", ")
            AdminRow(
                null, "${c.optInt("score")} · $title · Ch.$idx", listOfNotNull(why.ifEmpty { null }, c.s("model")).joinToString("\n"),
                subColor = if (c.optInt("score") < 70) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { Nav.push(Screen.Read(nid, title, idx)) },
            )
        }
    }
}
