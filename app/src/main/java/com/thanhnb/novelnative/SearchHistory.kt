package com.thanhnb.novelnative

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.json.JSONArray

/** Lịch sử tìm kiếm (tối đa 10, mới nhất trước), lưu trong SharedPreferences như app Flutter. */
object SearchHistory {
    private const val KEY = "search_history_list"
    private const val MAX = 10

    fun load(): List<String> = runCatching {
        JSONArray(Prefs.sp.getString(KEY, "[]")).let { a -> List(a.length()) { a.getString(it) } }
    }.getOrDefault(emptyList())

    private fun save(l: List<String>): List<String> {
        Prefs.sp.edit().putString(KEY, JSONArray(l).toString()).apply()
        return l
    }

    fun add(term: String): List<String> {
        val t = term.trim()
        if (t.isEmpty()) return load()
        return save((listOf(t) + load().filter { it != t }).take(MAX))
    }

    fun remove(term: String) = save(load() - term)
    fun clear() = save(emptyList())
}

private val HOT = listOf("Tiên hiệp", "Huyền huyễn", "Đô thị", "Kiếm hiệp", "Xuyên không", "Hệ thống", "Trọng sinh", "Khoa huyễn")

/** Khi chưa gõ gì: "Tìm kiếm gần đây" (chạm để tìm lại, × để bỏ) và chip "Từ khoá & Thể loại hot". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchSuggestions(history: List<String>, pick: (String) -> Unit, remove: (String) -> Unit, clear: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (history.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.History, null, Modifier.size(18.dp), tint = cs.primary)
                Text("Tìm kiếm gần đây", Modifier.weight(1f).padding(start = 6.dp), style = MaterialTheme.typography.titleSmall)
                TextButton(clear) { Text("Xoá tất cả") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                history.forEach { h ->
                    val shape = RoundedCornerShape(50)
                    Row(
                        Modifier.clip(shape).background(cs.card).clickable { pick(h) }.padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(h, style = MaterialTheme.typography.labelLarge)
                        IconButton({ remove(h) }, Modifier.size(30.dp)) { Icon(Icons.Rounded.Close, "Bỏ", Modifier.size(14.dp), tint = cs.onSurfaceVariant) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(18.dp), tint = cs.primary)
            Text("Từ khoá & Thể loại hot", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.titleSmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HOT.forEach { tag ->
                AssistChip({ pick(tag) }, { Text(tag) }, shape = RoundedCornerShape(50))
            }
        }
    }
}
