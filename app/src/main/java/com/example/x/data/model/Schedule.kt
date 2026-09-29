package com.example.x.data.model

/** Информация о текущей неделе/дне: /api/schedule/integration/currentDayInfo */
data class ScheduleDayInfo(
    val weekNum: Int,
    /** "нечетная" / "четная" */
    val weekType: String,
    val currentDate: String,
    val currentDay: String,
    val currentDayNum: Int,
    val startOfWeek: String,
    val endOfWeek: String
) {
    val isOdd: Boolean = weekType.contains("нечет", ignoreCase = true)
}

/** Одна пара: /api/schedule/integration/shedule -> table[day][couple].{coupleAll,coupleEven,coupleOdd} */
data class ScheduleLesson(
    val discName: String,
    val prepName: String,
    val auditoryName: String,
    /** "Лек" / "Лаб" / "Пр" */
    val lessonType: String,
    /** "каждую неделю" / "по четным неделям" / "по нечетным неделям" */
    val periodTypeName: String,
    val minWeekNum: Int,
    val maxWeekNum: Int,
    val time: String
)

data class ScheduleDay(
    val dayNum: Int,
    val dayName: String,
    val dayNameShort: String,
    val lessons: List<ScheduleLesson>
)

data class ScheduleData(
    val dayInfo: ScheduleDayInfo?,
    val groupName: String,
    val days: List<ScheduleDay>,
    val updatedAt: Long = 0L
)
