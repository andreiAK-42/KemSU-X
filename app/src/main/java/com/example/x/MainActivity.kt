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
        // Фон-напоминания о дедлайнах лабам
        com.example.x.utils.DeadlineScheduler.schedule(this)
    }
}
