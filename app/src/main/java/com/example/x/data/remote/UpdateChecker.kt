package com.example.x.data.remote

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

/**
 * Проверяет, является ли latest более новой версией, чем current.
 *
 * Поддерживает версии с префиксом "v" и разделителями ".", "-" и "_".
 */
fun isNewer(latest: String, current: String): Boolean {
    fun parts(version: String): List<Int> {
        return version
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .split(".", "-", "_")
            .map { part ->
                part.filter(Char::isDigit).toIntOrNull() ?: 0
            }
    }

    val latestParts = parts(latest)
    val currentParts = parts(current)
    val partCount = maxOf(latestParts.size, currentParts.size)

    for (index in 0 until partCount) {
        val latestPart = latestParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }

        if (latestPart != currentPart) {
            return latestPart > currentPart
        }
    }

    return false
}

object UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Получает информацию о последнем релизе GitHub.
     *
     * Возвращает null, если репозиторий не указан, релиз отсутствует,
     * запрос завершился ошибкой или текущая версия уже актуальна.
     */
    suspend fun check(
        owner: String,
        repo: String,
        current: String
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        if (owner.isBlank() || repo.isBlank()) {
            return@withContext null
        }

        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "KemsuX-App")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return@withContext null
                }

                if (!response.isSuccessful) {
                    return@withContext null
                }

                val body = response.body?.string()
                    ?: return@withContext null

                val json = JSONObject(body)
                val tag = json.optString("tag_name", "").trim()

                if (tag.isBlank()) {
                    return@withContext null
                }

                val updateInfo = UpdateInfo(
                    current = current,
                    latest = tag,
                    url = json.optString(
                        "html_url",
                        "https://github.com/$owner/$repo/releases"
                    ).trim(),
                    notes = json.optString("name", "").trim()
                )

                if (!updateInfo.hasUpdate) {
                    return@withContext null
                }

                updateInfo
            }
        } catch (_: Exception) {
            null
        }
    }
}
