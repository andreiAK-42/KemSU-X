package com.example.x.data.model

data class CourseTask(
    val title: String,
    val requiresSubmission: String,
    val comment: String, // https://...
    val controlDate: String, // 13-09-2026 23:59:59, может быть пустым — тогда не лаба
    val maxBall: Int,
    val result: String, // может быть "" или числом
    val status: String, // Оценено / Просмотрено / Не просмотрено
    val flag: String, // data-flag
    /** Раздел из серой строки-заголовка ("Теоретические основы..."), "" если нет. */
    val section: String = ""
)
