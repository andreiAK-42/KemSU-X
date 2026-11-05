package com.example.x.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.x.R
import com.example.x.data.local.AppDatabase
import com.example.x.data.local.PrefsManager
import com.example.x.data.model.Lab
import com.example.x.data.remote.KemsuApi
import com.example.x.data.repository.CourseRepository
import java.util.concurrent.TimeUnit

/**
 * Фоновая проверка оценок: преподаватель оценил работу — уведомляем.
 *
 * Сессия к моменту запуска воркера гарантированно затухла, поэтому перед
 * проверкой перелогиниваемся по сохранённым логину/паролю.
 * Проверяем ТОЛЬКО дисциплины текущего учебного года — прошлые семестры
 * не трогаем, чтобы не ловить «новые» оценки за давно закрытые лабы.
 */
class LabCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = PrefsManager(applicationContext)
        if (!prefs.getLabCheckEnabled()) return Result.success()

        val login = prefs.getLogin()
        val password = prefs.getPassword()
        if (login.isBlank() || password.isBlank()) return Result.success()

        return try {
            val db = AppDatabase.get(applicationContext)
            val repo = CourseRepository(db, KemsuApi(applicationContext), applicationContext)
            val year = DeadlineNotifier.currentAcademicYear()

            val courses = repo.getAllCoursesOnce().filter {
                DeadlineNotifier.normalizeYear(it.year) == year && !it.cId.isNullOrBlank()
            }
            Log.d("KEMSU_API", "LabCheckWorker year=$year courses=${courses.size}")
            if (courses.isEmpty()) return Result.success()

            // Перелогин: куки процесса к этому моменту пусты. Нет сети/сервер недоступен —
            // тихо ждём следующего запуска (RETRY зациклил бы на недоступном сервере).
            val loginRes = repo.login(login, password)
            if (loginRes.isFailure) {
                Log.w("KEMSU_API", "LabCheckWorker login failed: ${loginRes.exceptionOrNull()?.message}")
                return Result.success()
            }
            // Задания лежат в старом dekanat (xiais) — нужна легаси-сессия,
            // в обычном потоке приложения её создаёт refresh() перед сканом
            repo.ensureDekanatSession()

            val oldLabs = db.labDao().getAll().associateBy { it.id }
            // force=true — TTL кеша заданий (2 дня) не подходит, оценки нужны свежие
            val res = repo.scanDeadlines(year, force = true)
            val labs = res.getOrNull()
            if (labs == null) {
                Log.w("KEMSU_API", "LabCheckWorker scan failed: ${res.exceptionOrNull()?.message}")
                return Result.success()
            }

            val graded = labs.filter { lab ->
                val old = oldLabs[lab.id]
                old != null && old.status != "Сдано" &&
                        (lab.status == "Сдано" || lab.points > old.points)
            }
            Log.d("KEMSU_API", "LabCheckWorker graded=${graded.size}")
            if (graded.isNotEmpty()) {
                LabCheckNotifier.notifyGraded(applicationContext, graded)
            }
            prefs.setLabCheckLastRun()
            Result.success()
        } catch (e: Exception) {
            Log.w("KEMSU_API", "LabCheckWorker failed $e")
            Result.success()
        }
    }
}

object LabCheckScheduler {
    private const val WORK_NAME = "lab-check"

    /**
     * Периодика = интервал из настроек (часы). Выключено — воркер снимается.
     * force=false — KEEP (не сбивать таймер при каждом запуске приложения),
     * force=true — REPLACE (настройки поменялись).
     */
    fun schedule(context: Context, force: Boolean = false) {
        try {
            val prefs = PrefsManager(context)
            if (!prefs.getLabCheckEnabled()) {
                cancel(context)
                return
            }
            val hours = prefs.getLabCheckIntervalHours().coerceAtLeast(1)
            val req = PeriodicWorkRequestBuilder<LabCheckWorker>(hours.toLong(), TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                if (force) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP,
                req
            )
            Log.d("KEMSU_API", "LabCheckScheduler every ${hours}h force=$force")
        } catch (e: Exception) {
            Log.w("KEMSU_API", "LabCheckScheduler failed $e")
        }
    }

    fun cancel(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d("KEMSU_API", "LabCheckScheduler cancelled")
        } catch (e: Exception) {
            Log.w("KEMSU_API", "LabCheckScheduler cancel failed $e")
        }
    }
}

object LabCheckNotifier {
    private const val CHANNEL_ID = "lab_grades"
    private const val CHANNEL_NAME = "Оценки за лабы"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            val ch = android.app.NotificationChannel(CHANNEL_ID, CHANNEL_NAME, android.app.NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Уведомления о проверенных преподавателем лабах"
                enableVibration(true)
                setSound(soundUri, attrs)
            }
            context.getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun notifyGraded(context: Context, labs: List<Lab>) {
        if (labs.isEmpty()) return
        ensureChannel(context)
        labs.forEach { lab ->
            val result = when {
                lab.points > 0 && lab.maxPoints > 0 -> "${lab.points} из ${lab.maxPoints} баллов"
                lab.points > 0 -> "${lab.points} баллов"
                else -> lab.status.ifBlank { "Сдано" }
            }
            val text = "«${lab.title}» — ${lab.discipline}\nРезультат: $result"
            notifyOne(context, "Лабораторная проверена", text, lab.id.hashCode())
        }
    }

    private fun notifyOne(context: Context, title: String, text: String, id: Int) {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSound(soundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {}
    }
}
