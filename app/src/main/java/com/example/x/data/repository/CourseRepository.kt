package com.example.x.data.repository

import android.content.Context
import com.example.x.data.local.AppDatabase
import com.example.x.data.local.CourseEntity
import com.example.x.data.local.EventEntity
import com.example.x.data.local.LabEntity
import com.example.x.data.local.PrefsManager
import com.example.x.data.local.ScheduleCacheEntity
import com.example.x.data.local.StudentInfoEntity
import com.example.x.data.local.TaskCacheEntity
import com.example.x.data.local.UserEntity
import com.example.x.data.model.Course
import com.example.x.data.model.CourseTask
import com.example.x.data.model.Event
import com.example.x.data.model.Lab
import com.example.x.data.model.ScheduleData
import com.example.x.data.model.User
import com.example.x.data.remote.ApiConfig
import com.example.x.data.remote.AuthParser
import com.example.x.data.remote.KemsuApi
import com.example.x.data.remote.KemsuParser
import com.example.x.data.remote.ScheduleParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class CourseRepository(
    private val db: AppDatabase,
    private val api: KemsuApi,
    private val context: Context
) {
    private val prefs = PrefsManager(context)

    companion object {
        /** Свежесть кеша заданий: совпадает с интервалом фоновой проверки (2 дня). */
        const val TASKS_CACHE_TTL = 2L * 24 * 60 * 60 * 1000
    }

    fun observeAllCourses(): Flow<List<Course>> =
        db.courseDao().observeAll().map { courses -> courses.map { it.toModel() } }

    suspend fun getAllCoursesOnce(): List<Course> =
        db.courseDao().observeAll().first().map { it.toModel() }

    suspend fun getLabsOnce(): List<Lab> =
        db.labDao().getAll().map { it.toModel() }

    fun observeLabs(): Flow<List<Lab>> =
        db.labDao().observeAll().map { labs -> labs.map { it.toModel() } }

    fun observeUser(): Flow<User?> =
        db.userDao().observe().map { it?.toModel() }

    fun observeEvents(): Flow<List<Event>> =
        db.eventDao().observeAll().map { events -> events.map { it.toModel() } }

    fun observeUnreadCount(): Flow<Int> = db.eventDao().unreadCount()

    suspend fun markEventsRead() = db.eventDao().markAllRead()

    fun observeStudentName(): Flow<String?> =
        db.studentInfoDao().observe().map { it?.name }

    suspend fun getCourseByCId(cId: String): Course? =
        db.courseDao().getByCId(cId)?.toModel()

    suspend fun login(username: String, password: String): Result<Unit> {
        if (useJsonLogin()) {
            return loginWithJson(username, password)
        }

        return loginWithForm(username, password)
    }

    private fun useJsonLogin(): Boolean =
        ApiConfig.LOGIN_URL.contains("api-next.kemsu.ru") ||
                ApiConfig.LOGIN_URL.contains("security/auth")

    private suspend fun loginWithJson(username: String, password: String): Result<Unit> {
        val raw = api.loginJson(username, password)
        if (raw.isFailure) {
            return Result.failure(raw.exceptionOrNull() ?: Exception("loginJson failed"))
        }

        val json = raw.getOrNull() ?: return Result.failure(Exception("Пустой ответ авторизации"))
        val parsed = AuthParser.parseLoginResponse(json)

        if (parsed.isFailure) {
            return Result.failure(parsed.exceptionOrNull() ?: Exception("Ошибка разбора авторизации"))
        }

        val (user, accessToken) = parsed.getOrNull()
            ?: return Result.failure(Exception("Пустой результат авторизации"))
        val (_, refreshToken) = AuthParser.extractTokens(json)

        prefs.saveTokens(accessToken, refreshToken)
        saveUser(user)
        prefs.saveUser(user)

        // Получаем JSESSIONID/xrealip для xiais через eios-bridge.
        api.fetchEiosNextBridge()

        return Result.success(Unit)
    }

    private suspend fun loginWithForm(username: String, password: String): Result<Unit> {
        return try {
            if (!api.login(username, password)) {
                return Result.failure(Exception("Ошибка входа. Проверьте логин/пароль"))
            }

            val html = api.fetchDisciplinesHtml("")
            if (!html.contains("tbl")) {
                return Result.failure(Exception("Не удалось пройти авторизацию."))
            }

            val name = KemsuParser.parseStudentName(html)
            if (name != null) {
                db.studentInfoDao().insert(StudentInfoEntity(name = name))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refresh(year: String = ""): Result<Unit> {
        return try {
            val html = api.fetchDisciplinesHtml(year)

            if (!isValidDisciplinesPage(html)) {
                if (isSessionExpired(html)) {
                    return Result.failure(Exception("Сессия истекла или нет доступа. Проверь токены."))
                }

                if (KemsuParser.parseCourses(html).isEmpty()) {
                    return Result.failure(Exception("Пустой список дисциплин (0)."))
                }
            }

            val courses = KemsuParser.parseCourses(html)
            val events = KemsuParser.parseEvents(html)
            val name = KemsuParser.parseStudentName(html)

            saveCourses(courses, year)
            saveEvents(events)

            if (name != null) {
                db.studentInfoDao().insert(StudentInfoEntity(name = name))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isValidDisciplinesPage(html: String): Boolean =
        html.contains("tbl")

    private fun isSessionExpired(html: String): Boolean =
        html.contains("password", ignoreCase = true) || html.contains("Refresh")

    private suspend fun saveCourses(courses: List<Course>, year: String) {
        if (courses.isEmpty()) {
            return
        }

        val filter = year.ifBlank { "all" }
        db.courseDao().deleteByYear(filter)

        if (filter == "all") {
            db.courseDao().deleteAll()
        }

        db.courseDao().insertAll(courses.map { it.toEntity(filter) })
    }

    private suspend fun saveEvents(events: List<Event>) {
        db.eventDao().deleteAll()
        db.eventDao().insertAll(events.map { it.toEntity() })
    }

    private suspend fun saveLabs(labs: List<Lab>) {
        db.labDao().deleteAll()
        db.labDao().insertAll(labs.map { it.toEntity() })
    }

    private suspend fun saveUser(user: User) {
        db.userDao().deleteAll()
        db.userDao().insert(user.toEntity())
        db.studentInfoDao().insert(StudentInfoEntity(name = user.name))
    }

    suspend fun fetchTasks(cId: String, force: Boolean = false): Result<List<CourseTask>> {
        if (!force) {
            val cached = getFreshCachedTasks(cId)
            if (cached != null) {
                return Result.success(cached)
            }
        }

        return try {
            val tasks = KemsuParser.parseTasks(api.fetchTasks(cId))
            saveTasksCache(cId, tasks)
            Result.success(tasks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getFreshCachedTasks(cId: String): List<CourseTask>? {
        return try {
            val cached = db.taskCacheDao().getByCId(cId)
            if (cached.isEmpty()) {
                return null
            }

            val updatedAt = cached.maxOfOrNull { it.updatedAt } ?: return null
            if (System.currentTimeMillis() - updatedAt >= TASKS_CACHE_TTL) {
                return null
            }

            cached.map { it.toTask() }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun saveTasksCache(cId: String, tasks: List<CourseTask>) {
        try {
            val now = System.currentTimeMillis()
            db.taskCacheDao().deleteByCId(cId)
            db.taskCacheDao().upsertAll(
                tasks.mapIndexed { index, task ->
                    TaskCacheEntity(
                        cId,
                        index,
                        task.title,
                        task.requiresSubmission,
                        task.comment,
                        task.controlDate,
                        task.maxBall,
                        task.result,
                        task.status,
                        task.flag,
                        task.section,
                        now
                    )
                }
            )
        } catch (_: Exception) {
            // Ошибка кеша не должна ломать получение заданий.
        }
    }

    /**
     * Проверяет задания только для актуального учебного года.
     * Каждый уникальный cId загружается один раз.
     */
    suspend fun scanDeadlines(
        academicYear: String,
        force: Boolean = false,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<List<Lab>> {
        return try {
            val courses = getCoursesForYear(academicYear)
            val coursesByCId = courses.groupBy { it.cId!! }
            val tasksByCId = loadTasksForCourses(coursesByCId.keys, force, onProgress)
            val oldLabs = getOldLabs()
            val labs = buildLabs(courses, tasksByCId, oldLabs)

            saveLabs(labs)
            saveDeadlineEvents(labs)

            Result.success(labs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getCoursesForYear(academicYear: String): List<Course> {
        val normalizedYear = com.example.x.utils.DeadlineNotifier.normalizeYear(academicYear)

        return getAllCoursesOnce().filter { course ->
            com.example.x.utils.DeadlineNotifier.normalizeYear(course.year) == normalizedYear &&
                    !course.cId.isNullOrBlank()
        }
    }

    private suspend fun loadTasksForCourses(
        cIds: Set<String>,
        force: Boolean,
        onProgress: (done: Int, total: Int) -> Unit
    ): Map<String, List<CourseTask>> {
        val tasksByCId = mutableMapOf<String, List<CourseTask>>()
        val total = cIds.size
        var done = 0

        for (cId in cIds) {
            tasksByCId[cId] = loadTasksForScan(cId, force)
            done++

            try {
                onProgress(done, total)
            } catch (_: Exception) {
                // Ошибка UI-прогресса не должна прерывать сканирование.
            }
        }

        return tasksByCId
    }

    private suspend fun loadTasksForScan(cId: String, force: Boolean): List<CourseTask> {
        if (!force) {
            val cached = getFreshCachedTasks(cId)
            if (cached != null) {
                return cached
            }
        }

        return try {
            val tasks = KemsuParser.parseTasks(api.fetchTasks(cId))
            saveTasksCache(cId, tasks)
            tasks
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun getOldLabs(): Map<String, LabEntity> {
        return try {
            db.labDao().getAll().associateBy { it.id }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun buildLabs(
        courses: List<Course>,
        tasksByCId: Map<String, List<CourseTask>>,
        oldLabs: Map<String, LabEntity>
    ): List<Lab> {
        val labs = mutableListOf<Lab>()

        for (course in courses) {
            val tasks = tasksByCId[course.cId] ?: emptyList()

            tasks.forEachIndexed { index, task ->
                val deadline = com.example.x.utils.DeadlineNotifier.toLabDate(task.controlDate)
                if (deadline.isBlank()) {
                    return@forEachIndexed
                }

                val id = "${course.cId}-$index"
                val old = oldLabs[id]
                val isDone = task.result.isNotBlank() || task.status.contains("ценен", true)
                val status = if (isDone) {
                    "Сдано"
                } else {
                    task.status.ifBlank { "Не сдано" }
                }
                val points = task.result.toIntOrNull() ?: 0

                labs += Lab(
                    id = id,
                    discipline = course.discipline,
                    title = task.title,
                    deadline = deadline,
                    status = status,
                    points = points,
                    maxPoints = task.maxBall,
                    changed = hasLabChanged(old, status, points, deadline, task),
                    muted = old?.muted ?: false
                )
            }
        }

        return labs
    }

    private fun hasLabChanged(
        old: LabEntity?,
        status: String,
        points: Int,
        deadline: String,
        task: CourseTask
    ): Boolean {
        return old == null ||
                old.status != status ||
                old.points != points ||
                old.deadline != deadline ||
                old.maxPoints != task.maxBall ||
                old.title != task.title
    }

    private suspend fun saveDeadlineEvents(labs: List<Lab>) {
        val today = java.time.LocalDate.now()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val events = labs
            .filter { !it.muted && it.status != "Сдано" }
            .mapNotNull { lab ->
                try {
                    val deadline = java.time.LocalDate.parse(lab.deadline, formatter)
                    val days = java.time.temporal.ChronoUnit.DAYS.between(today, deadline)

                    if (days in 0..7) {
                        lab to days
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }
            .sortedBy { it.second }
            .map { (lab, days) ->
                EventEntity(
                    discipline = lab.discipline,
                    text = "${lab.title} — дедлайн ${formatDeadline(days)} (${lab.deadline})",
                    date = lab.deadline
                )
            }

        if (events.isNotEmpty()) {
            db.eventDao().deleteAll()
            db.eventDao().insertAll(events)
        }
    }

    private fun formatDeadline(days: Long): String =
        when (days) {
            0L -> "сегодня"
            1L -> "завтра"
            else -> "через $days дн."
        }

    /** Расписание из кеша без сети. */
    suspend fun getScheduleCached(): ScheduleData? {
        return try {
            val cached = db.scheduleCacheDao().get() ?: return null
            val dayInfo = ScheduleParser.parseDayInfo(cached.dayInfoJson)

            ScheduleParser.parseSchedule(cached.scheduleJson, dayInfo)
                ?.copy(updatedAt = cached.updatedAt)
        } catch (_: Exception) {
            null
        }
    }

    /** Расписание из сети с сохранением в кеш. */
    suspend fun refreshSchedule(): Result<ScheduleData> {
        return try {
            val dayInfoRaw = api.fetchCurrentDayInfo().getOrElse { return Result.failure(it) }
            val tableRaw = api.fetchScheduleTable().getOrElse { return Result.failure(it) }
            val dayInfo = ScheduleParser.parseDayInfo(dayInfoRaw)
            val data = ScheduleParser.parseSchedule(tableRaw, dayInfo)
                ?: return Result.failure(Exception("Не удалось разобрать расписание"))

            val now = System.currentTimeMillis()
            db.scheduleCacheDao().upsert(
                ScheduleCacheEntity(0, dayInfoRaw, tableRaw, now)
            )

            Result.success(data.copy(updatedAt = now))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Полный выход: очищает базу, prefs, куки и фоновые напоминания. */
    suspend fun logoutFull() {
        clearDatabase()
        cancelDeadlineReminders()
        api.clearCookies()
        prefs.clear()
    }

    private suspend fun clearDatabase() {
        try {
            db.courseDao().deleteAll()
            db.labDao().deleteAll()
            db.eventDao().deleteAll()
            db.userDao().deleteAll()
            db.taskCacheDao().deleteAll()
            db.scheduleCacheDao().deleteAll()
            db.studentInfoDao().insert(StudentInfoEntity(name = null))
        } catch (_: Exception) {
            // Выход продолжается даже при ошибке очистки БД.
        }
    }

    private fun cancelDeadlineReminders() {
        try {
            androidx.work.WorkManager
                .getInstance(context)
                .cancelUniqueWork("deadline-reminders")
        } catch (_: Exception) {
            // Отмена фоновой задачи не должна мешать выходу.
        }
    }

    fun logout() {
        api.clearCookies()
        prefs.clear()
    }

    private fun CourseEntity.toModel() =
        Course(num, discipline, report, year, hours, period, teacher, points, cId)

    private fun Course.toEntity(filter: String) =
        CourseEntity(
            num = num,
            discipline = discipline,
            report = report,
            year = year,
            hours = hours,
            period = period,
            teacher = teacher,
            points = points,
            cId = cId,
            studyYearFilter = filter
        )

    private fun UserEntity.toModel() =
        User(
            id = id,
            name = name,
            group = group,
            faculty = faculty,
            avatarUrl = avatarUrl,
            firstName = firstName,
            lastName = lastName,
            middleName = middleName,
            login = login,
            email = email
        )

    private fun User.toEntity() =
        UserEntity(
            id.ifBlank { "1" },
            name,
            group,
            faculty,
            avatarUrl,
            firstName,
            lastName,
            middleName,
            login,
            email
        )

    private fun Event.toEntity() =
        EventEntity(
            discipline = discipline,
            text = text,
            date = date
        )

    private fun LabEntity.toModel() =
        Lab(id, discipline, title, deadline, status, points, maxPoints, changed, muted)

    private fun Lab.toEntity() =
        LabEntity(id, discipline, title, deadline, status, points, maxPoints, changed, muted)

    private fun EventEntity.toModel() =
        Event(discipline, text, date)

    private fun TaskCacheEntity.toTask() =
        CourseTask(title, requires, comment, controlDate, maxBall, result, status, flag, section)
}
