package com.example.x.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.x.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Кнопка «Мут» прямо в уведомлении: мьютит конкретную лабу
 * (уведомлений и событий по ней больше нет) и закрывает уведомление.
 */
class MuteReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_MUTE = "com.example.x.action.MUTE_LAB"
        const val EXTRA_LAB_ID = "lab_id"
        const val EXTRA_NOTIF_ID = "notif_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MUTE) return
        val labId = intent.getStringExtra(EXTRA_LAB_ID) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        try {
            NotificationManagerCompat.from(context).cancel(notifId)
        } catch (_: Exception) {
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AppDatabase.get(context).labDao().setMuted(labId, true)
                Log.d("KEMSU_API", "lab muted from notification id=$labId")
            } catch (e: Exception) {
                Log.w("KEMSU_API", "mute from notification failed $e")
            }
        }
    }
}
