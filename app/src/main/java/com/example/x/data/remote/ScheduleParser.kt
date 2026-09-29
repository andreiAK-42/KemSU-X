package com.example.x.data.remote

import android.util.Log
import com.example.x.data.model.ScheduleData
import com.example.x.data.model.ScheduleDay
import com.example.x.data.model.ScheduleDayInfo
import com.example.x.data.model.ScheduleLesson
import org.json.JSONArray
import org.json.JSONObject

object ScheduleParser {
    private const val TAG = "KEMSU_API"

    fun parseDayInfo(json: String): ScheduleDayInfo? {
        return try {
            val root = JSONObject(json)
            if (!root.optBoolean("success", false)) return null
            val c = root.getJSONObject("currentDay")
            ScheduleDayInfo(
                weekNum = c.optInt("weekNum", 0),
                weekType = c.optString("weekType", ""),
                currentDate = c.optString("currentDate", ""),
                currentDay = c.optString("currentDay", ""),
                currentDayNum = c.optInt("currentDayNum", 0),
                startOfWeek = c.optString("startOfWeek", ""),
                endOfWeek = c.optString("endOfWeek", "")
            )
        } catch (e: Exception) {
            Log.w(TAG, "parseDayInfo failed $e")
            null
        }
    }

    /**
     * ВАЖНО: table[couple][day] — строки это ПАРЫ (7 шт. = coupleList),
     * клетки это ДНИ (6 шт. = weekDayList Пн–Сб). НЕ наоборот!
     * В клетке {coupleAll, coupleEven, coupleOdd}.
     * Фильтр по текущей неделе: нечётная -> odd+all, чётная -> even+all,
     * плюс отсекаем пары вне [minWeekNum, maxWeekNum].
     */
    fun parseSchedule(json: String, dayInfo: ScheduleDayInfo?): ScheduleData? {
        return try {
            val root = JSONObject(json)
            if (!root.optBoolean("success", false)) return null
            val groupName = root.optString("groupName", "")

            data class WeekDay(val num: Int, val name: String, val short: String)
            val weekDays = mutableListOf<WeekDay>()
            root.optJSONArray("weekDayList")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    weekDays += WeekDay(o.optInt("DayNum", i + 1), o.optString("DayName", ""), o.optString("DayNameShort", ""))
                }
            }
            if (weekDays.isEmpty()) return null
            val coupleTimes = mutableMapOf<Int, String>()
            root.optJSONArray("coupleList")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    coupleTimes[o.optInt("Num", 0)] = o.optString("Description", "")
                }
            }

            val odd = dayInfo?.isOdd ?: true
            val weekNum = dayInfo?.weekNum ?: 0
            // День -> пары (в порядке пар)
            val byDay = weekDays.associate { it.num to mutableListOf<ScheduleLesson>() }
            val table = root.optJSONArray("table") ?: JSONArray()
            for (r in 0 until table.length()) {
                val row = table.optJSONArray(r) ?: continue
                val time = coupleTimes[r + 1] ?: ""
                for (c in 0 until row.length()) {
                    val dayNum = weekDays.getOrNull(c)?.num ?: continue
                    val cell = row.optJSONObject(c) ?: continue
                    val pool = mutableListOf<JSONObject>()
                    cell.optJSONArray("coupleAll")?.let { pool += it.toObjectList() }
                    cell.optJSONArray(if (odd) "coupleOdd" else "coupleEven")?.let { pool += it.toObjectList() }
                    for (o in pool) {
                        val minW = o.optInt("minWeekNum", 0)
                        val maxW = o.optInt("maxWeekNum", 99)
                        if (weekNum != 0 && (weekNum < minW || weekNum > maxW)) continue
                        byDay[dayNum]?.add(
                            ScheduleLesson(
                                discName = o.optString("DiscName", ""),
                                prepName = o.optString("PrepName", ""),
                                auditoryName = o.optString("AuditoryName", ""),
                                lessonType = o.optString("lessonType", ""),
                                periodTypeName = o.optString("periodTypeName", ""),
                                minWeekNum = minW,
                                maxWeekNum = maxW,
                                time = time
                            )
                        )
                    }
                }
            }
            val days = weekDays.map { w -> ScheduleDay(w.num, w.name, w.short, byDay[w.num] ?: emptyList()) }
            Log.d(TAG, "parseSchedule days=${days.size} lessons=${days.sumOf { it.lessons.size }} odd=$odd week=$weekNum")
            ScheduleData(dayInfo, groupName, days)
        } catch (e: Exception) {
            Log.w(TAG, "parseSchedule failed $e")
            null
        }
    }

    private fun JSONArray.toObjectList(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }
}
