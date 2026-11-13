package com.thanhnb.novelnative

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Widget "Đọc tiếp": chỉ vẽ lại từ snapshot trong SharedPreferences, không tự gọi API. */
object ContinueWidget {
    const val EXTRA_INDEX = "read_index" // MainActivity đọc cùng Notify.EXTRA_NOVEL để mở thẳng chương
    const val EXTRA_TITLE = "read_title"

    private const val PREF = "widget"
    private val io = Executors.newSingleThreadExecutor() // một luồng: tải bìa và vẽ lại không giẫm nhau
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    }

    /** chapter = số chương như Screen.Read.index (đếm từ 1); gọi từ luồng nào cũng được. */
    fun update(ctx: Context, novelId: Int, title: String, chapter: Int, total: Int, coverUrl: String?) {
        val c = ctx.applicationContext
        val sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val same = sp.getInt("novel", 0) == novelId // vào từ chính widget thì nơi gọi chưa biết bìa/tổng chương → giữ số đã lưu của cùng truyện
        sp.edit().putInt("novel", novelId).putString("title", title).putInt("chapter", chapter)
            .putInt("total", if (total > 0 || !same) total else sp.getInt("total", 0))
            .putString("cover", coverUrl ?: if (same) sp.getString("cover", null) else null).apply()
        refresh(c)
    }

    fun clear(ctx: Context) {
        val c = ctx.applicationContext
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
        refresh(c)
    }

    internal fun refresh(ctx: Context) {
        val c = ctx.applicationContext
        io.execute {
            runCatching {
                val mgr = AppWidgetManager.getInstance(c)
                val ids = mgr.getAppWidgetIds(ComponentName(c, ContinueWidgetProvider::class.java))
                if (ids.isEmpty()) return@runCatching
                val sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                val bmp = sp.getString("cover", null)?.let { coverBitmap(c, it) }
                val rv = build(c, sp, bmp)
                ids.forEach { mgr.updateAppWidget(it, rv) }
            }
        }
    }

    private fun build(c: Context, sp: SharedPreferences, cover: Bitmap?): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.widget_continue)
        val id = sp.getInt("novel", 0)
        val open = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (id <= 0) { // chưa có snapshot / chưa đăng nhập: chỉ mời mở app
            rv.setViewVisibility(R.id.w_cover, View.GONE)
            rv.setViewVisibility(R.id.w_sub, View.GONE)
            rv.setViewVisibility(R.id.w_bar, View.GONE)
            rv.setTextViewText(R.id.w_title, c.getString(R.string.widget_continue_empty))
        } else {
            val idx = sp.getInt("chapter", 0)
            val total = sp.getInt("total", 0)
            val pct = if (total > 0) (idx * 100 / total).coerceIn(0, 100) else 0
            open.putExtra(Notify.EXTRA_NOVEL, id).putExtra(EXTRA_INDEX, idx).putExtra(EXTRA_TITLE, sp.getString("title", ""))
            rv.setViewVisibility(R.id.w_cover, View.VISIBLE)
            rv.setViewVisibility(R.id.w_sub, View.VISIBLE)
            rv.setTextViewText(R.id.w_title, sp.getString("title", ""))
            rv.setTextViewText(R.id.w_sub, if (total > 0) "Chương $idx/$total · $pct%" else "Chương $idx")
            rv.setViewVisibility(R.id.w_bar, if (total > 0) View.VISIBLE else View.GONE)
            rv.setProgressBar(R.id.w_bar, 100, pct, false)
            rv.setImageViewBitmap(R.id.w_cover, cover) // null = lộ nền màu của drawable
        }
        rv.setOnClickPendingIntent(
            R.id.w_root,
            PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )
        return rv
    }

    // bìa ~200px lưu cacheDir, tên theo hash URL: đổi bìa thì tải lại, cùng bìa thì dùng file; lỗi → null (nền màu)
    private fun coverBitmap(c: Context, url: String): Bitmap? = runCatching {
        val dir = File(c.cacheDir, "widget").apply { mkdirs() }
        val f = File(dir, "c${url.hashCode()}.jpg")
        if (!f.exists()) {
            val bytes = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@runCatching null
                r.body.bytes()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return@runCatching null
            val opt = BitmapFactory.Options().apply { inSampleSize = (bounds.outWidth / 200).coerceAtLeast(1) }
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opt) ?: return@runCatching null
            val small = if (raw.width > 200) Bitmap.createScaledBitmap(raw, 200, raw.height * 200 / raw.width, true) else raw
            dir.listFiles()?.forEach { it.delete() } // chỉ giữ bìa hiện tại
            f.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
        BitmapFactory.decodeFile(f.path)
    }.getOrNull()
}

class ContinueWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        ContinueWidget.refresh(context)
    }
}
