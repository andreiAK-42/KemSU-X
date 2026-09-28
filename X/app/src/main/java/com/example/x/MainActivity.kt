package com.example.x

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Фон-напоминания о дедлайнах лабам
        com.example.x.utils.DeadlineScheduler.schedule(this)
    }
}
