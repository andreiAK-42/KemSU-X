package com.example.x.data.model

data class Course(
    val num: Int,
    val discipline: String,
    val report: String,
    val year: String,
    val hours: Int,
    val period: String,
    val teacher: String,
    val points: Int,
    val cId: String?
)

data class Lab(
    val id: String,
    val discipline: String,
    val title: String,
    val deadline: String,
    val status: String,
    val points: Int,
    val maxPoints: Int = 10,
    /** Изменилась относительно прошлого захода (новая или поменялись статус/баллы/дедлайн). */
    val changed: Boolean = false
)

/** Категория лабы для фильтра и цвета на главной. */
enum class LabCategory(val label: String) {
    DONE("Сданные"),
    ZERO("0 баллов"),
    REVIEW("На проверке"),
    TODO("Не сданные")
}

fun labCategoryOf(lab: Lab): LabCategory = when {
    // Оценена, но 0 баллов — красным
    lab.status == "Сдано" && lab.points == 0 -> LabCategory.ZERO
    lab.status == "Сдано" -> LabCategory.DONE
    // Только буквальный "На проверке" (и родственные без "не").
    // "Просмотрено" — это НЕ проверка, такие идут в несданные.
    lab.status.contains("провер", ignoreCase = true) &&
        !lab.status.trimStart().startsWith("не", ignoreCase = true) -> LabCategory.REVIEW
    else -> LabCategory.TODO
}

data class Event(val discipline: String, val text: String, val date: String)

data class User(
    val id: String = "",
    val login: String = "",
    val name: String,
    val firstName: String = "",
    val lastName: String = "",
    val middleName: String = "",
    val email: String = "",
    val group: String = "",
    val faculty: String = "",
    val town: String = "",
    val avatarUrl: String? = null
)

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String
)
