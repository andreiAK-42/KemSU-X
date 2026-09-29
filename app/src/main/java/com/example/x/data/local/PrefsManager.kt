package com.example.x.data.local

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("kemsu_prefs", Context.MODE_PRIVATE)

    fun saveCredentials(login: String, password: String) {
        prefs.edit().putString("login", login).putString("password", password).putBoolean("has_saved", true).apply()
    }
    fun getLogin(): String = prefs.getString("login", "") ?: ""
    fun getPassword(): String = prefs.getString("password", "") ?: ""
    fun hasSaved(): Boolean = prefs.getBoolean("has_saved", false)

    fun saveTokens(access: String, refresh: String) {
        prefs.edit().putString("accessToken", access).putString("refreshToken", refresh).apply()
    }
    fun getAccessToken(): String? = prefs.getString("accessToken", null)
    fun getRefreshToken(): String? = prefs.getString("refreshToken", null)

    fun saveUser(user: com.example.x.data.model.User) {
        prefs.edit()
            .putString("user_id", user.id)
            .putString("user_name", user.name)
            .putString("user_login", user.login)
            .putString("user_email", user.email)
            .putString("avatar", user.avatarUrl)
            .putString("user_group", user.group)
            .apply()
    }
    fun getUserName(): String? = prefs.getString("user_name", null)
    fun getAvatar(): String? = prefs.getString("avatar", null)

    // Последняя проверка дедлайнов (scanDeadlines). Чаще раза в 2 дня не сканируем.
    fun getLastDeadlineScan(): Long = prefs.getLong("last_deadline_scan", 0L)
    fun setLastDeadlineScan(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("last_deadline_scan", now).apply()
    }

    /** За сколько дней до дедлайна напоминать. Дефолт — 1 день. */
    fun getNotifyDays(): Int = prefs.getInt("notify_days", 1).coerceIn(0, 30)
    fun setNotifyDays(days: Int) {
        prefs.edit().putInt("notify_days", days.coerceIn(0, 30)).apply()
    }

    /**
     * Сколько раз напомнить за окно [getNotifyDays]. Напоминания равномерно
     * распределяются: 2 дня × 2 раза = каждые 24 ч; 2 дня × 4 раза = каждые 12 ч.
     */
    fun getNotifyCount(): Int = prefs.getInt("notify_count", 1).coerceIn(1, 8)
    fun setNotifyCount(count: Int) {
        prefs.edit().putInt("notify_count", count.coerceIn(1, 8)).apply()
    }

    /** Интервал между напоминаниями в миллисекундах (окно / количество). */
    fun getNotifyIntervalMs(): Long {
        val windowMs = getNotifyDays().toLong() * 24 * 60 * 60 * 1000
        if (windowMs <= 0L) return 24 * 60 * 60 * 1000L
        return (windowMs / getNotifyCount()).coerceAtLeast(15 * 60 * 1000L)
    }

    /** Когда последний раз напоминали про конкретную лабу (защита от спама). */
    fun getLastNotified(labId: String): Long = prefs.getLong("notified_at_$labId", 0L)
    fun setLastNotified(labId: String, now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("notified_at_$labId", now).apply()
    }

    /** Игнорировать системное увеличение шрифта (дефолт — да, чтобы не ломалась вёрстка). */
    fun getIgnoreSystemFont(): Boolean = prefs.getBoolean("ignore_system_font", true)
    fun setIgnoreSystemFont(v: Boolean) {
        prefs.edit().putBoolean("ignore_system_font", v).apply()
    }

    fun clear() { prefs.edit().clear().apply() }
}
