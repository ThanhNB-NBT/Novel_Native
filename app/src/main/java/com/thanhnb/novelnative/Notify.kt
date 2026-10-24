package com.thanhnb.novelnative

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.Instant

/**
 * Thông báo hệ thống "chương đã dịch xong" — bản native không có Realtime nên chỉ kiểm tra lúc mở app
 * (giống checkMissedChapters của bản Flutter): chương của truyện trong tủ/đang đọc dịch xong sau lần kiểm trước.
 * Chương do chính mình bấm dịch lại ("notify_mine") được báo dù truyện nằm ngoài tủ.
 */
object Notify {
    private const val CHANNEL = "chapters"
    private const val LAST = "notify_last_check"
    private const val MINE = "notify_mine"

    fun init(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Chương dịch xong", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Báo khi chương truyện trong tủ sách được dịch xong" },
        )
    }

    /** Android 13+ phải xin quyền hiện thông báo; xin một lần khi đã đăng nhập. */
    fun askPermission(act: Activity) {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(act, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        if (Prefs.sp.getBoolean("notify_asked", false)) return
        Prefs.sp.edit().putBoolean("notify_asked", true).apply()
        ActivityCompat.requestPermissions(act, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
    }

    fun mine(): Set<String> = Prefs.sp.getStringSet(MINE, emptySet()) ?: emptySet()

    /** Ghi nhớ chương mình yêu cầu dịch lại, để báo khi xong. */
    fun addMine(novelId: Int, indexes: Collection<Int>) {
        Prefs.sp.edit().putStringSet(MINE, mine() + indexes.map { "$novelId:$it" }).apply()
    }

    fun removeMine(keys: Collection<String>) {
        Prefs.sp.edit().putStringSet(MINE, mine() - keys.toSet()).apply()
    }

    /** Chạy lúc mở app; lần đầu chỉ đặt mốc để khỏi dội thông báo cũ. */
    suspend fun checkMissed(ctx: Context) {
        val u = Session.user?.id ?: return
        val last = Prefs.sp.getString(LAST, null)
        Prefs.sp.edit().putString(LAST, Instant.now().toString()).apply()
        if (last == null) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            fun ids(a: org.json.JSONArray) = List(a.length()) { a.getJSONObject(it).getInt("novel_id") }
            val followed = (ids(Api.get("library?select=novel_id&user_id=eq.$u")) + ids(Api.get("reading_progress?select=novel_id&user_id=eq.$u"))).toSet()
            val mineKeys = mine()
            val ids = followed + mineKeys.mapNotNull { it.substringBefore(':').toIntOrNull() }
            if (ids.isEmpty()) return
            val rows = Api.get(
                "chapters?select=chapter_index,novel_id,novels(title_vi,title_zh)&novel_id=in.(${ids.joinToString(",")})" +
                    "&translation_status=eq.done&translated_at=gt.${Api.enc(last)}&limit=500",
            )
            val byNovel = HashMap<Int, MutableList<org.json.JSONObject>>()
            val done = ArrayList<String>()
            for (i in 0 until rows.length()) {
                val r = rows.getJSONObject(i)
                val nid = r.getInt("novel_id")
                val key = "$nid:${r.getInt("chapter_index")}"
                if (nid !in followed && key !in mineKeys) continue
                done += key
                byNovel.getOrPut(nid) { ArrayList() } += r
            }
            val nm = ctx.getSystemService(NotificationManager::class.java)
            for ((nid, list) in byNovel) {
                val nv = list[0].optJSONObject("novels")
                val title = nv?.optString("title_vi")?.takeIf { it.isNotEmpty() && it != "null" } ?: nv?.optString("title_zh").orEmpty().ifEmpty { "Truyện" }
                val latest = list.maxOf { it.getInt("chapter_index") }
                val open = PendingIntent.getActivity(ctx, nid, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
                nm.notify(
                    nid,
                    NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_more).setContentTitle(title)
                        .setContentText(if (list.size == 1) "Chương $latest đã dịch xong" else "${list.size} chương mới dịch xong (mới nhất: chương $latest)")
                        .setContentIntent(open).setAutoCancel(true).build(),
                )
            }
            removeMine(done) // đã báo thì thôi, khỏi giữ mãi
        }
    }
}
