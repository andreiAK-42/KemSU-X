package com.example.x

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    /**
     * Игнорируем системное увеличение шрифта: при крупном fontScale
     * текст разъезжается и ломает вёрстку. Фиксируем масштаб 1.0.
     */
    override fun attachBaseContext(newBase: Context) {
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
