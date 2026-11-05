package com.example.x

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    /**
     * Системное увеличение шрифта ломает вёрстку. По умолчанию игнорируем
     * (fontScale = 1.0), в настройках есть галочка чтобы вернуть системный размер.
     */
    override fun attachBaseContext(newBase: Context) {
        val ignore = com.example.x.data.local.PrefsManager(newBase).getIgnoreSystemFont()
        if (!ignore) {
            super.attachBaseContext(newBase)
            return
        }
        val fixed = Configuration(newBase.resources.configuration).apply { fontScale = 1f }
        super.attachBaseContext(newBase.createConfigurationContext(fixed))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        wipeDbIfUpdated()
        // Фон-напоминания о дедлайнах лабам
        com.example.x.utils.DeadlineScheduler.schedule(this)
        // Фон-проверка оценок (если включена пользователем; выключенная снимается внутри)
        com.example.x.utils.LabCheckScheduler.schedule(this)
    }

    /**
     * Обновление версии: старая БД целиком под нож (дисциплины, лабы, кеши —
     * структура могла измениться и старые данные дают ошибки), настройки
     * (SharedPreferences) не трогаем. На первом запуске просто запоминаем версию.
     * До создания AppDatabase: файл сносим целиком, чтобы Room открыла свежую.
     */
    private fun wipeDbIfUpdated() {
        val prefs = com.example.x.data.local.PrefsManager(this)
        val current = BuildConfig.VERSION_NAME
        val last = prefs.getAppVersion()
        when {
            last == null -> prefs.setAppVersion(current)
            last != current -> {
                android.util.Log.w("KEMSU_API", "app updated $last -> $current, wiping db")
                deleteDatabase(com.example.x.data.local.AppDatabase.DB_NAME)
                prefs.setAppVersion(current)
            }
        }
    }
}
