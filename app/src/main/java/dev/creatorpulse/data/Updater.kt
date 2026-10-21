package dev.creatorpulse.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.creatorpulse.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long, val pageUrl: String)

/** Checks GitHub Releases of [BuildConfig.UPDATE_REPO] for a newer version and installs it via the system installer. */
class Updater(private val context: Context, private val client: OkHttpClient, private val json: Json) {
    @Serializable private data class GhAsset(val name: String = "", val browser_download_url: String = "", val size: Long = 0)
    @Serializable private data class GhRelease(
        val tag_name: String = "", val name: String = "", val body: String = "", val html_url: String = "",
        val assets: List<GhAsset> = emptyList(), val prerelease: Boolean = false, val draft: Boolean = false,
    )

    val repo: String get() = BuildConfig.UPDATE_REPO
    val currentVersion: String get() = BuildConfig.VERSION_NAME
    val releasesUrl: String get() = "https://github.com/$repo/releases"

    /** Returns the newest release if it is newer than the installed version, null when up to date. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "CreatorPulse/${BuildConfig.VERSION_NAME}")
            .build()
        val release = client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            when {
                resp.code == 404 -> return@withContext null // no releases yet
                resp.code == 403 || resp.code == 429 -> throw ApiException(resp.code, "GitHub rate limit, try again later")
                !resp.isSuccessful -> throw ApiException(resp.code, "GitHub returned HTTP ${resp.code}")
                else -> json.decodeFromString(GhRelease.serializer(), body)
            }
        }
        if (release.draft) return@withContext null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return@withContext null
        val version = release.tag_name.removePrefix("v").trim()
        if (compareVersions(version, currentVersion) <= 0) return@withContext null
        UpdateInfo(version, release.body.ifBlank { release.name }, apk.browser_download_url, apk.size, release.html_url)
    }

    /** Streams the APK into the app cache; [onProgress] gets 0..100. */
    suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "CreatorPulse-${info.version}.apk")
        val req = Request.Builder().url(info.apkUrl).header("User-Agent", "CreatorPulse/${BuildConfig.VERSION_NAME}").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException(resp.code, "Download failed: HTTP ${resp.code}")
            val body = resp.body ?: throw ApiException(resp.code, "Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    var last = -1
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0) { val p = (done * 100 / total).toInt(); if (p != last) { last = p; onProgress(p) } }
                    }
                }
            }
        }
        out
    }

    /** Hands the APK to Android's package installer (asks for "install unknown apps" the first time). */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        /** Compares dotted versions numerically ("1.10.0" > "1.9.2"); trailing qualifiers are ignored. */
        fun compareVersions(a: String, b: String): Int {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val pa = parts(a); val pb = parts(b)
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val x = pa.getOrElse(i) { 0 }; val y = pb.getOrElse(i) { 0 }
                if (x != y) return x.compareTo(y)
            }
            return 0
        }
    }
}
