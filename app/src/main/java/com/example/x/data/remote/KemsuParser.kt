package com.example.x.data.remote

import com.example.x.data.model.Course
import com.example.x.data.model.CourseTask
import com.example.x.data.model.Event
import org.jsoup.Jsoup

object KemsuParser {

    fun parseCourses(html: String): List<Course> {
        val doc = Jsoup.parse(html)

        // Выбираем таблицу с фильтром года или таблицу со списком дисциплин.
        val table = doc.select("table.tbl").firstOrNull {
            it.selectFirst("select#studyYearFilter") != null
        } ?: doc.select("table.tbl").firstOrNull {
            it.text().contains("Дисциплина") && it.text().contains("Преподаватель")
        } ?: doc.selectFirst("table.tbl")
        ?: return emptyList()

        return table.select("tr").mapNotNull { row ->
            // Строки курсов отличаются onMouseOver и содержат c_id.
            if (!row.attr("onMouseOver").contains("E6E6FA") &&
                !row.html().contains("c_id")
            ) {
                return@mapNotNull null
            }

            val cells = row.children()
                .filter { it.tagName() == "td" }
                .map { it.text().replace('\u00A0', ' ').trim() }

            if (cells.size < 8) return@mapNotNull null

            val number = cells[0].toIntOrNull() ?: return@mapNotNull null
            // cId берём строго из формы ЭТОЙ строки (form[action*=tasks_st] > input[name=c_id]),
            // а не первым regex по outerHtml — иначе при битой вёрстке cId одной строки
            // прилипает к соседней и лабы дублируются сразу в двух дисциплинах.
            val cId = row.selectFirst("form[action*=tasks_st] input[name=c_id]")?.attr("value")?.ifBlank { null }
                ?: Regex("""name="c_id"\s+value="(\d+)"""")
                    .find(row.outerHtml())
                    ?.groupValues
                    ?.getOrNull(1)

            Course(
                num = number,
                discipline = cells[1],
                report = cells[2],
                year = cells[3],
                hours = cells[4].toIntOrNull() ?: 0,
                period = cells[5],
                teacher = cells[6],
                points = cells[7].toIntOrNull() ?: 0,
                cId = cId
            )
        }.also { list ->
            // Диагностика дублей: один cId у двух дисциплин = лабы покажутся в обеих
            val dups = list.mapNotNull { it.cId }.groupingBy { it }.eachCount().filter { it.value > 1 }
            if (dups.isNotEmpty()) {
                val who = dups.keys.associateWith { id -> list.filter { it.cId == id }.map { it.discipline } }
                android.util.Log.w("KEMSU_API", "parseCourses DUP cId=$who — проверь disciplines.html в кэше")
            }
            android.util.Log.d("KEMSU_API", "parseCourses found ${list.size}")
        }
    }

    fun parseEvents(html: String): List<Event> {
        val doc = Jsoup.parse(html)

        return doc.select("tr").mapNotNull { row ->
            if (!row.attr("onmouseover").contains("#FFDEAD", ignoreCase = true)) {
                return@mapNotNull null
            }

            val cells = row.select("td")
            if (cells.size < 3) return@mapNotNull null

            Event(
                discipline = cells[0].text().trim(),
                text = cells[1].text().trim(),
                date = cells[2].text().trim()
            )
        }
    }

    fun parseStudentName(html: String): String? {
        val doc = Jsoup.parse(html)

        return doc.selectFirst("td[style*=text-align: right] font")
            ?.text()
            ?.trim()
            ?: doc.selectFirst("font")
                ?.text()
                ?.trim()
    }

    fun parseLoginForm(html: String): Pair<String, Map<String, String>>? {
        val doc = Jsoup.parse(html)
        val form = doc.selectFirst("form") ?: return null

        val action = form.attr("action").ifBlank { "/" }
        val inputs = form.select("input[name]")
            .associate { it.attr("name") to it.attr("value") }

        return action to inputs
    }

    fun extractStudyYears(html: String): List<String> {
        val doc = Jsoup.parse(html)

        return doc.select("select#studyYearFilter option")
            .mapNotNull { it.attr("value").ifBlank { null } }
    }

    fun parseTasks(html: String): List<CourseTask> {
        val doc = Jsoup.parse(html)
        val tables = doc.select("table.tbl")

        // Ищем таблицу заданий по её заголовкам. Последняя таблица — запасной вариант.
        val target = tables.firstOrNull {
            it.text().contains("Назначенные задания")
        } ?: tables.firstOrNull {
            it.text().contains("Название") && it.text().contains("Требуется")
        } ?: tables.lastOrNull()
        ?: return emptyList()

        val result = mutableListOf<CourseTask>()
        var section = ""
        var groups = 0

        for (row in target.select("tr")) {
            val cells = row.select("td")
            if (cells.isEmpty()) continue // шапка из th

            // Строка-раздел ("Теоретические основы...", "Обоснование ценности..."):
            // серая подложка #dcdcdc, жирный текст, НЕТ statusBox. Это НЕ лаба,
            // а заголовок сворачивающейся группы — запоминаем для следующих заданий.
            val hasStatusBox = row.selectFirst("i[data-id=statusBox]") != null
            val isGroup = row.attr("style").contains("#dcdcdc", ignoreCase = true) ||
                (!hasStatusBox && row.selectFirst("td b") != null)
            if (isGroup) {
                section = cells[0].text().replace('\u00A0', ' ').trim()
                groups++
                continue
            }
            if (cells.size < 7 || cells[0].hasAttr("colspan")) continue

            val title = cells[0].text().replace('\u00A0', ' ').trim()
            if (title.isBlank() || title == "Название") continue
            if (cells[1].text().contains("Требуется")) continue

            val requires = cells[1].text().trim()
            val comment = cells[2].text().trim()
            val controlDate = cells[3].text().replace('\u00A0', ' ').trim()
            val maxBall = cells[4].text().trim().toIntOrNull() ?: 0
            val resultValue = cells[5].text().trim()
            val statusCell = cells[6]
            val status = statusCell.text().trim()
            val flag = statusCell.selectFirst("i[data-flag]")?.attr("data-flag")
                ?: statusCell.attr("data-flag")

            result += CourseTask(
                title = title,
                requiresSubmission = requires,
                comment = comment,
                controlDate = controlDate,
                maxBall = maxBall,
                result = resultValue,
                status = status,
                flag = flag,
                section = section
            )
        }

        // Если структура таблицы изменилась, используем более мягкий разбор
        // (с тем же правилом разделов).
        if (result.isNotEmpty()) {
            android.util.Log.d("KEMSU_API", "parseTasks tasks=${result.size} sections=$groups")
            return result
        }

        section = ""
        for (row in target.select("tr")) {
            val cells = row.select("td")
            if (cells.isEmpty()) continue
            val hasStatusBox = row.selectFirst("i[data-id=statusBox]") != null
            if (row.attr("style").contains("#dcdcdc", ignoreCase = true) ||
                (!hasStatusBox && row.selectFirst("td b") != null)
            ) {
                section = cells[0].text().replace('\u00A0', ' ').trim()
                groups++
                continue
            }
            if (cells.size < 4 || cells[0].hasAttr("colspan")) continue

            val title = cells[0].text().replace('\u00A0', ' ').trim()
            if (title.isBlank() || title == "Название") continue

            result += CourseTask(
                title = title,
                requiresSubmission = cells.getOrNull(1)?.text()?.trim().orEmpty(),
                comment = cells.getOrNull(2)?.text()?.trim().orEmpty(),
                controlDate = cells.getOrNull(3)?.text()?.replace('\u00A0', ' ')?.trim().orEmpty(),
                maxBall = cells.getOrNull(4)?.text()?.trim()?.toIntOrNull() ?: 0,
                result = cells.getOrNull(5)?.text()?.trim().orEmpty(),
                status = cells.getOrNull(6)?.text()?.trim()
                    ?: cells.lastOrNull()?.text()?.trim().orEmpty(),
                flag = "",
                section = section
            )
        }

        android.util.Log.d("KEMSU_API", "parseTasks fallback tasks=${result.size} sections=$groups")
        return result
    }
}