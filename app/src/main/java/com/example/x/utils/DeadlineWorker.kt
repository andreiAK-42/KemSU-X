package com.example.x.utils

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.x.data.local.AppDatabase
import com.example.x.data.model.Lab
import java.util.concurrent.TimeUnit

/**
 * Фоновая проверка дедлайнов ТОЛЬКО по сохранённой базе (labs).
 * Без входа в аккаунт и без сетевых запросов: что запомнили при последнем
 * скане, о том и напоминаем. Работает, даже если пользователь не открывал приложение.
 */
class DeadlineWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val labs = AppDatabase.get(applicationContext).labDao().getAll().map { e ->
                Lab(e.id, e.discipline, e.title, e.deadline, e.status, e.points, e.maxPoints, e.changed)
            }
            Log.d("KEMSU_API", "DeadlineWorker labs=${labs.size}")
            if (labs.isNotEmpty()) {
                val prefs = com.example.x.data.local.PrefsManager(applicationContext)
                DeadlineNotifier.notifyIfNeeded(applicationContext, labs, prefs.getNotifyDays(), prefs.getNotifyCount())
            }
            Result.success()
        } catch (e: Exception) {
            Log.w("KEMSU_API", "DeadlineWorker failed $e")
            // Не retry-им бесконечно — следующая проверка по расписанию
            Result.success()
        }
    }
}

object DeadlineScheduler {
    private const val WORK_NAME = "deadline-reminders"

    /**
     * Периодика = интервал между напоминаниями (окно / количество), минимум 15 минут.
     * force=false — KEEP (не сбивать таймер при каждом запуске приложения),
     * force=true — REPLACE (настройки поменялись).
     */
    fun schedule(context: Context, force: Boolean = false) {
        try {
            val prefs = com.example.x.data.local.PrefsManager(context)
            val intervalMin = (prefs.getNotifyIntervalMs() / 60000L).coerceAtLeast(15L)
            val req = PeriodicWorkRequestBuilder<DeadlineWorker>(intervalMin, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                if (force) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP,
                req
            )
            Log.d("KEMSU_API", "DeadlineScheduler every ${intervalMin}min force=$force")
        } catch (e: Exception) {
            Log.w("KEMSU_API", "DeadlineScheduler failed $e")
        }
    }
}
