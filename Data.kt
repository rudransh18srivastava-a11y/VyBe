package com.vybe.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val folder: String,
    val width: Int,
    val height: Int,
    val dateAdded: Long
) {
    val resolution: String
        get() = when {
            height >= 2160 -> "4K"
            height >= 1440 -> "2K"
            height >= 1080 -> "1080p"
            height >= 720 -> "720p"
            height > 0 -> "${height}p"
            else -> ""
        }
}

data class FolderInfo(val name: String, val count: Int, val cover: Uri)

object VideoRepository {

    /** Reads every video known to MediaStore, newest first. Call off the main thread. */
    fun load(context: Context): List<VideoItem> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED
        )
        val out = ArrayList<VideoItem>()
        try {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Video.Media.SIZE} > 0",
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val iW = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val name = c.getString(iName) ?: "Video $id"
                    out += VideoItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        title = name.substringBeforeLast('.', name),
                        durationMs = c.getLong(iDur),
                        sizeBytes = c.getLong(iSize),
                        folder = c.getString(iBucket) ?: "Other",
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                        dateAdded = c.getLong(iDate)
                    )
                }
            }
        } catch (_: SecurityException) {
            // permission revoked while loading: return what we have
        }
        return out
    }
}

/** Small SharedPreferences wrapper: resume positions, sort order, view mode, recent streams. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("vybe_prefs", Context.MODE_PRIVATE)

    fun getPosition(key: String): Long = sp.getLong("pos:$key", 0L)

    fun setPosition(key: String, ms: Long) {
        sp.edit().putLong("pos:$key", ms).apply()
    }

    fun clearPosition(key: String) {
        if (sp.contains("pos:$key")) sp.edit().remove("pos:$key").apply()
    }

    var sort: Int
        get() = sp.getInt("sort", 0)
        set(v) = sp.edit().putInt("sort", v).apply()

    var grid: Boolean
        get() = sp.getBoolean("grid", false)
        set(v) = sp.edit().putBoolean("grid", v).apply()

    val recentUrls: List<String>
        get() = sp.getString("recent_urls", "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun addRecentUrl(url: String) {
        val list = (listOf(url) + recentUrls.filter { it != url }).take(10)
        sp.edit().putString("recent_urls", list.joinToString("\n")).apply()
    }

    fun clearRecentUrls() {
        sp.edit().remove("recent_urls").apply()
    }
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L)) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun formatSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> "%.2f GB".format(bytes / gb)
        bytes >= mb -> "%.1f MB".format(bytes / mb)
        else -> "%.0f KB".format(bytes / kb)
    }
}
