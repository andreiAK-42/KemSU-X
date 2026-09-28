package com.example.x.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val current: String,
    val latest: String,
    val url: String,
    val notes: String = ""
) {
    val hasUpdate: Boolean = isNewer(latest, current)
}

/** true, если latest новее current ("v1.2" vs "1.0", "1.0.1" vs "1.0"). */
fun isNewer(latest: String, current: String): Boolean {
    fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split(".", "-", "_")
            .map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
    val a = parts(latest)
    val b = parts(current)
    val n = maxOf(a.size, b.size)
    for (i in 0 until n) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

object UpdateChecker {
    private const val TAG = "KEMSU_API"
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Последний релиз GitHub. null — репозиторий не задан, сети нет или ошибка (молча). */
    suspend fun check(owner: String, repo: String, current: String): UpdateInfo? = withContext(Dispatchers.IO) {
        if (owner.isBlank() || repo.isBlank()) return@withContext null
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "KemsuX-App")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 404) {
                    Log.d(TAG, "update check: no releases yet")
                    return@withContext null
                }
                if (!resp.isSuccessful) {
                    Log.w(TAG, "update check HTTP ${resp.code}")
                    return@withContext null
                }
                val body = resp.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val tag = json.optString("tag_name", "").trim()
                if (tag.isBlank()) return@withContext null
                val info = UpdateInfo(
                    current = current,
                    latest = tag,
                    url = json.optString("html_url", "https://github.com/$owner/$repo/releases").trim(),
                    notes = json.optString("name", "").trim()
                )
                if (!info.hasUpdate) {
                    Log.d(TAG, "update check: up to date ($current)")
                    return@withContext null
                }
                info
            }
        } catch (e: Exception) {
            Log.w(TAG, "update check failed $e")
            null
        }
    }
}
