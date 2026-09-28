package com.example.x.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.x.R
import com.example.x.data.model.Lab
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object DeadlineNotifier {
    private const val CHANNEL_ID = "deadlines"
    private const val CHANNEL_NAME = "Дедлайны лаб"

    /** Учебный год: сен–дек 2026 -> 2026-2027; янв–июл 2027 -> 2026-2027; с 1 сен 2027 -> 2027-2028. */
    fun currentAcademicYear(today: LocalDate = LocalDate.now()): String {
        val y = today.year
        return if (today.monthValue >= 9) "$y-${y + 1}" else "${y - 1}-$y"
    }

    /** "2026 - 2027" -> "2026-2027" для сравнения года из таблицы с фильтром. */
    fun normalizeYear(raw: String): String = raw.replace("\\s".toRegex(), "")

    /** "13-09-2026 23:59:59" | "13-09-2026" | "yyyy-MM-dd" -> LocalDate? */
    fun parseControlDate(raw: String): LocalDate? {
        val s = raw.trim()
        if (s.isBlank()) return null
        val patterns = listOf("dd-MM-yyyy HH:mm:ss", "dd-MM-yyyy", "yyyy-MM-dd", "dd.MM.yyyy")
        for (p in patterns) {
            try { return LocalDate.parse(s.take(10), DateTimeFormatter.ofPattern(p.take(10))) } catch (_: Exception) {}
            try { return java.time.LocalDateTime.parse(s, DateTimeFormatter.ofPattern(p)).toLocalDate() } catch (_: Exception) {}
        }
        return null
    }

    fun toLabDate(raw: String): String =
        parseControlDate(raw)?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) ?: raw.trim()

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Уведомления о приближающихся дедлайнах со звуком"
                enableVibration(true)
                setSound(soundUri, attrs)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    /**
     * Напоминания за daysBefore дней, всего count раз за окно (равномерно).
     * Дедупликация по лабе: повтор только через интервал (окно / count) — без спама
     * при каждом заходе. Только несданные.
     */
    fun notifyIfNeeded(context: Context, labs: List<Lab>, daysBefore: Int = 1, count: Int = 1) {
        ensureChannel(context)
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val today = LocalDate.now()
        val window = daysBefore.coerceIn(0, 30)
        val intervalMs = if (window <= 0) 24 * 60 * 60 * 1000L
        else (window.toLong() * 24 * 60 * 60 * 1000 / count.coerceIn(1, 8)).coerceAtLeast(15 * 60 * 1000L)
        val now = System.currentTimeMillis()
        val prefs = com.example.x.data.local.PrefsManager(context)
        labs.forEach { lab ->
            try {
                val deadline = LocalDate.parse(lab.deadline, fmt)
                val days = ChronoUnit.DAYS.between(today, deadline)
                if (days in 0..window && lab.status != "Сдано") {
                    // Уже напоминали недавно — пропускаем, следующий слот позже
                    if (now - prefs.getLastNotified(lab.id) < intervalMs * 0.9) return@forEach
                    val when_ = when (days) {
                        0L -> "сегодня"
                        1L -> "завтра"
                        else -> "через $days дн."
                    }
                    notifyOne(context, playfulTitle(lab, days), "${lab.title} — ${lab.discipline}, сдача $when_ (${lab.deadline})", lab.id.hashCode())
                    prefs.setLastNotified(lab.id, now)
                }
            } catch (_: Exception) {}
        }
    }

    private val tomorrowTitles = listOf(
        "Эта лаба сама себя не сделает",
        "Пора что-то делать",
        "Эта лаба решения просит",
        "Завтра сдача — ты успеешь",
        "Лаба ждёт своего героя"
    )
    private val todayTitles = listOf(
        "Сдача сегодня — последний рывок!",
        "Эта лаба сама себя не сделает",
        "Пора что-то делать",
        "Эта лаба решения просит"
    )
    private val soonTitles = listOf(
        "Эта лаба сама себя не сделает",
        "Пора что-то делать",
        "Эта лаба решения просит",
        "Дедлайн на горизонте"
    )

    private fun playfulTitle(lab: Lab, days: Long): String {
        val pool = when (days) {
            0L -> todayTitles
            1L -> tomorrowTitles
            else -> soonTitles
        }
        // Стабильно для одной лабы, чтобы не мелькало при каждом показе
        val idx = (lab.id.hashCode() and Int.MAX_VALUE) % pool.size
        return pool[idx]
    }

    fun sendTestNotification(context: Context) {
        ensureChannel(context)
        notifyOne(context, "Тест уведомления", "Это тестовое уведомление о дедлайне со звуком \uD83D\uDD14", 9999)
    }

    private fun notifyOne(context: Context, title: String, text: String, id: Int) {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSound(soundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {}
    }
}
