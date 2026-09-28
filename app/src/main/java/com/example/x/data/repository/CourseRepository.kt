package com.example.x.data.repository

import android.content.Context
import android.util.Log
import com.example.x.data.local.AppDatabase
import com.example.x.data.local.CourseEntity
import com.example.x.data.local.EventEntity
import com.example.x.data.local.LabEntity
import com.example.x.data.local.PrefsManager
import com.example.x.data.local.StudentInfoEntity
import com.example.x.data.local.TaskCacheEntity
import com.example.x.data.local.UserEntity
import com.example.x.data.model.Course
import com.example.x.data.model.CourseTask
import com.example.x.data.model.Event
import com.example.x.data.model.Lab
import com.example.x.data.model.User
import com.example.x.data.remote.ApiConfig
import com.example.x.data.remote.AuthParser
import com.example.x.data.remote.KemsuApi
import com.example.x.data.remote.KemsuParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class CourseRepository(
    private val db: AppDatabase,
    private val api: KemsuApi,
    private val context: Context
) {
    private val TAG = "KEMSU_API"
    private val prefs = PrefsManager(context)

    companion object {
        /** Свежесть кеша заданий: совпадает с интервалом фоновой проверки (2 дня). */
        const val TASKS_CACHE_TTL = 2L * 24 * 60 * 60 * 1000
    }

    fun observeCourses(yearFilter: String): Flow<List<Course>> =
        if (yearFilter.isBlank()) db.courseDao().observeAll().map { it.map { e -> e.toModel() } }
        else db.courseDao().observeByYear(yearFilter).map { it.map { e -> e.toModel() } }

    /** Весь список для локальной фильтрации по реальному году курса (Course.year). */
    fun observeAllCourses(): Flow<List<Course>> =
        db.courseDao().observeAll().map { it.map { e -> e.toModel() } }

    suspend fun getAllCoursesOnce(): List<Course> =
        db.courseDao().observeAll().first().map { it.toModel() }

    suspend fun getLabsOnce(): List<Lab> =
        db.labDao().getAll().map { e -> Lab(e.id, e.discipline, e.title, e.deadline, e.status, e.points, e.maxPoints, e.changed) }

    fun observeLabs(): Flow<List<Lab>> = db.labDao().observeAll().map { it.map { e -> Lab(e.id, e.discipline, e.title, e.deadline, e.status, e.points, e.maxPoints, e.changed) } }
    fun observeUser(): Flow<User?> = db.userDao().observe().map { it?.toModel() }
    fun observeEvents(): Flow<List<Event>> = db.eventDao().observeAll().map { it.map { e -> Event(e.discipline, e.text, e.date) } }
    fun observeUnreadCount(): Flow<Int> = db.eventDao().unreadCount()
    suspend fun markEventsRead() = db.eventDao().markAllRead()
    fun observeStudentName(): Flow<String?> = db.studentInfoDao().observe().map { it?.name }

    suspend fun getCourseByCId(cId: String): Course? = db.courseDao().getByCId(cId)?.toModel()
    suspend fun getCourseById(id: Long): Course? = db.courseDao().getById(id)?.toModel()

    // Моков в боевом коде нет: MockData/KemsuStubApi используются только в @Preview.
    // fetchUser/fetchDisciplines/fetchLabs удалены — пользователь/дисциплины идут из
    // login()/refresh(), лабы — из scanDeadlines().

    suspend fun login(username: String, password: String): Result<Unit> {
        Log.d(TAG, "LOGIN attempt user=$username url=${ApiConfig.LOGIN_URL} lifetime=3m")
        // 1. Пробуем новый JSON API (api-next.kemsu.ru)
        if (ApiConfig.LOGIN_URL.contains("api-next.kemsu.ru") || ApiConfig.LOGIN_URL.contains("security/auth")) {
            val raw = api.loginJson(username, password)
            Log.d(TAG, "loginJson raw success=${raw.isSuccess}")
            if (raw.isFailure) {
                Log.e(TAG, "loginJson failed: ${raw.exceptionOrNull()?.message}")
                return Result.failure(raw.exceptionOrNull() ?: Exception("loginJson failed"))
            }
            val jsonStr = raw.getOrNull()!!
            Log.d(TAG, "loginJson json=$jsonStr")
            val parsed = AuthParser.parseLoginResponse(jsonStr)
            if (parsed.isFailure) {
                Log.e(TAG, "parseLoginResponse failed", parsed.exceptionOrNull())
                return Result.failure(parsed.exceptionOrNull()!!)
            }
            val (user, access) = parsed.getOrNull()!!
            val (_, refresh) = AuthParser.extractTokens(jsonStr)
            // сохраняем токены
            prefs.saveTokens(access, refresh)
            Log.d(TAG, "TOKENS saved access=${access.take(20)}... refresh=${refresh.take(20)}...")
            // сохраняем пользователя
            saveUser(user)
            prefs.saveUser(user)
            Log.d(TAG, "USER saved $user")
            // Сразу получаем JSESSIONID/xrealip для xiais через eios-bridge (иначе дисциплины вернут userId=-1)
            val bridgeOk = api.fetchEiosNextBridge()
            Log.d(TAG, "eios-bridge after login ok=$bridgeOk")
            return Result.success(Unit)
        }
        // 2. Fallback старый form login (xiais)
        return try {
            val ok = api.login(username, password)
            Log.d(TAG, "LOGIN form response ok=$ok")
            if (!ok) { Log.w(TAG, "LOGIN failed"); return Result.failure(Exception("Ошибка входа. Проверьте логин/пароль")) }
            val html = api.fetchDisciplinesHtml("")
            Log.d(TAG, "LOGIN verify page length=${html.length} contains tbl=${html.contains("tbl")}")
            if (!html.contains("tbl")) { Log.w(TAG, "LOGIN verify failed preview=${html.take(300)}"); return Result.failure(Exception("Не удалось пройти авторизацию.")) }
            val name = KemsuParser.parseStudentName(html)
            if (name != null) db.studentInfoDao().insert(StudentInfoEntity(name = name))
            Result.success(Unit)
        } catch (e: Exception) { Log.e(TAG, "LOGIN exception", e); Result.failure(e) }
    }

    suspend fun refresh(year: String = ""): Result<Unit> {
        Log.d(TAG, "REFRESH year=$year")
        return try {
            // Всегда POST /proc/stud/index.shtm как в дампе (с cookie accessToken) — GET /proc/stud/ отдает редирект userId=-1.
            // studyYear="" = все годы, сортировка/фильтр — локально в БД/UI
            val html = api.fetchDisciplinesHtml(year)
            Log.d(TAG, "REFRESH html len=${html.length} contains tbl=${html.contains("tbl")} contains Refresh=${html.contains("Refresh")}")
            if (!html.contains("tbl")) {
                Log.w(TAG, "REFRESH no tbl — возможно редирект/неавторизован. html preview=${html.take(500)}")
                if (html.contains("password", true) || html.contains("Refresh")) return Result.failure(Exception("Сессия истекла или нет доступа. Проверь токены."))
                // не затираем БД если пусто
                if (KemsuParser.parseCourses(html).isEmpty()) return Result.failure(Exception("Пустой список дисциплин (0)."))
            }
            val courses = KemsuParser.parseCourses(html); Log.d(TAG, "REFRESH parsed courses=${courses.size}"); courses.forEach { Log.v(TAG, "  course: $it") }
            val events = KemsuParser.parseEvents(html); val name = KemsuParser.parseStudentName(html)
            Log.d(TAG, "REFRESH events=${events.size} name=$name")
            saveCourses(courses, year); db.eventDao().deleteAll(); db.eventDao().insertAll(events.map { EventEntity(discipline = it.discipline, text = it.text, date = it.date) })
            if (name != null) db.studentInfoDao().insert(StudentInfoEntity(name = name))
            // Лабы подтягивает scanDeadlines() — моков больше нет
            Result.success(Unit)
        } catch (e: Exception) { Log.e(TAG, "REFRESH exception", e); Result.failure(e) }
    }

    private suspend fun saveCourses(courses: List<Course>, year: String) {
        if (courses.isEmpty()) { Log.w(TAG, "saveCourses skip — пустой список, БД не трогаем"); return }
        val filter = year.ifBlank { "all" }
        db.courseDao().deleteByYear(filter)
        if (filter == "all" && courses.isNotEmpty()) db.courseDao().deleteAll()
        db.courseDao().insertAll(courses.map { it.toEntity(filter) })
        Log.d(TAG, "DB saved courses ${courses.size} filter=$filter")
    }
    private suspend fun saveLabs(labs: List<Lab>) {
        db.labDao().deleteAll(); db.labDao().insertAll(labs.map { LabEntity(it.id, it.discipline, it.title, it.deadline, it.status, it.points, it.maxPoints, it.changed) })
        Log.d(TAG, "DB saved labs ${labs.size} changed=${labs.count { it.changed }}")
    }
    private suspend fun saveUser(user: User) {
        db.userDao().deleteAll(); db.userDao().insert(UserEntity(user.id.ifBlank { "1" }, user.name, user.group, user.faculty, user.avatarUrl))
        db.studentInfoDao().insert(StudentInfoEntity(name = user.name))
        Log.d(TAG, "DB saved user $user")
    }
    suspend fun fetchTasks(cId: String, force: Boolean = false): Result<List<CourseTask>> {
        Log.d(TAG, "fetchTasks c_id=$cId force=$force")
        // Кеш: свежие задания отдаём сразу без сети, чтобы не ждать
        if (!force) {
            try {
                val cached = db.taskCacheDao().getByCId(cId)
                if (cached.isNotEmpty() &&
                    System.currentTimeMillis() - (cached.maxOfOrNull { it.updatedAt } ?: 0L) < TASKS_CACHE_TTL
                ) {
                    Log.d(TAG, "fetchTasks cache hit c_id=$cId n=${cached.size}")
                    return Result.success(cached.map { it.toTask() })
                }
            } catch (e: Exception) { Log.w(TAG, "fetchTasks cache read failed $e") }
        }
        return try {
            val html = api.fetchTasks(cId)
            Log.d(TAG, "fetchTasks html len=${html.length}")
            val tasks = KemsuParser.parseTasks(html)
            Log.d(TAG, "fetchTasks parsed ${tasks.size}")
            tasks.forEach { Log.v(TAG, "  task: $it") }
            saveTasksCache(cId, tasks)
            Result.success(tasks)
        } catch (e: Exception) {
            Log.e(TAG, "fetchTasks error", e)
            Result.failure(e)
        }
    }

    private suspend fun saveTasksCache(cId: String, tasks: List<CourseTask>) {
        try {
            val now = System.currentTimeMillis()
            db.taskCacheDao().deleteByCId(cId)
            db.taskCacheDao().upsertAll(tasks.mapIndexed { i, t ->
                TaskCacheEntity(cId, i, t.title, t.requiresSubmission, t.comment, t.controlDate, t.maxBall, t.result, t.status, t.flag, now)
            })
            Log.d(TAG, "tasks cache saved c_id=$cId n=${tasks.size}")
        } catch (e: Exception) { Log.w(TAG, "tasks cache save failed $e") }
    }

    /**
     * Быстрый проход только по актуальному учебному году (не весь список).
     * Кеш: свежие курсы берутся из task_cache без сети; уникальные cId грузятся один раз.
     * Прогресс: onProgress(done, total) после каждого курса — для бара в UI.
     */
    suspend fun scanDeadlines(
        academicYear: String,
        force: Boolean = false,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<List<Lab>> {
        return try {
            val norm = com.example.x.utils.DeadlineNotifier.normalizeYear(academicYear)
            val courses = getAllCoursesOnce()
                .filter { com.example.x.utils.DeadlineNotifier.normalizeYear(it.year) == norm && !it.cId.isNullOrBlank() }
            Log.d(TAG, "scanDeadlines year=$academicYear courses=${courses.size} force=$force")
            // Один cId у двух дисциплин = баг маппинга (см. parseCourses DUP): грузим раз, лабы — каждой
            val byCId = courses.groupBy { it.cId!! }
            if (byCId.size < courses.size) {
                val dups = byCId.filter { it.value.size > 1 }
                    .mapValues { e -> e.value.map { it.discipline } }
                Log.w(TAG, "scanDeadlines DUP cId=$dups")
            }
            val total = byCId.size
            var done = 0
            // cId -> задания (из кеша или сети)
            val tasksByCId = mutableMapOf<String, List<CourseTask>>()
            for ((cId, group) in byCId) {
                var tasks: List<CourseTask>? = null
                if (!force) {
                    try {
                        val cached = db.taskCacheDao().getByCId(cId)
                        if (cached.isNotEmpty() &&
                            System.currentTimeMillis() - (cached.maxOfOrNull { it.updatedAt } ?: 0L) < TASKS_CACHE_TTL
                        ) {
                            tasks = cached.map { it.toTask() }
                            Log.d(TAG, "scanDeadlines cache hit c_id=$cId n=${tasks.size}")
                        }
                    } catch (e: Exception) { Log.w(TAG, "scanDeadlines cache read failed $e") }
                }
                if (tasks == null) {
                    try {
                        val html = api.fetchTasks(cId)
                        tasks = KemsuParser.parseTasks(html)
                        saveTasksCache(cId, tasks)
                    } catch (e: Exception) {
                        Log.w(TAG, "scanDeadlines c_id=$cId failed: ${e.message}")
                        tasks = emptyList()
                    }
                }
                tasksByCId[cId] = tasks
                done++
                try { onProgress(done, total) } catch (_: Exception) {}
                Log.d(TAG, "scanDeadlines progress $done/$total c_id=$cId n=${tasks.size} for=${group.map { it.discipline }}")
            }
            val labs = mutableListOf<Lab>()
            // Старые лабы — чтобы подсветить изменившиеся до следующего захода
            val oldById = try {
                db.labDao().getAll().associateBy { it.id }
            } catch (e: Exception) { Log.w(TAG, "scanDeadlines old labs read failed $e"); emptyMap<String, com.example.x.data.local.LabEntity>() }
            for (c in courses) {
                val tasks = tasksByCId[c.cId] ?: emptyList()
                tasks.forEachIndexed { i, t ->
                    val deadline = com.example.x.utils.DeadlineNotifier.toLabDate(t.controlDate)
                    if (deadline.isBlank()) return@forEachIndexed
                    val isDone = t.result.isNotBlank() || t.status.contains("ценен", true)
                    val id = "${c.cId}-$i"
                    val status = if (isDone) "Сдано" else t.status.ifBlank { "Не сдано" }
                    val points = t.result.toIntOrNull() ?: 0
                    val old = oldById[id]
                    // Новая лаба или поменялись статус/баллы/дедлайн/максимум/название
                    val changed = old == null ||
                        old.status != status || old.points != points || old.deadline != deadline ||
                        old.maxPoints != t.maxBall || old.title != t.title
                    labs += Lab(
                        id = id,
                        discipline = c.discipline,
                        title = t.title,
                        deadline = deadline,
                        status = status,
                        points = points,
                        maxPoints = t.maxBall,
                        changed = changed
                    )
                }
            }
            saveLabs(labs)
            // В события — лабы с дедлайном в ближайшие 7 дней (несданные), сортировка по дате
            val today = java.time.LocalDate.now()
            val soon = labs.mapNotNull { lab ->
                try {
                    val d = java.time.LocalDate.parse(lab.deadline, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    val days = java.time.temporal.ChronoUnit.DAYS.between(today, d)
                    if (days in 0..7 && lab.status != "Сдано") lab to days else null
                } catch (_: Exception) { null }
            }.sortedBy { it.second }
            val events = soon.map { (lab, days) ->
                val when_ = when (days) { 0L -> "сегодня"; 1L -> "завтра"; else -> "через $days дн." }
                EventEntity(discipline = lab.discipline, text = "${lab.title} — дедлайн $when_ (${lab.deadline})", date = lab.deadline)
            }
            if (events.isNotEmpty()) {
                db.eventDao().deleteAll()
                db.eventDao().insertAll(events)
                Log.d(TAG, "scanDeadlines events=${events.size}")
            }
            Log.d(TAG, "scanDeadlines labs=${labs.size}")
            Result.success(labs)
        } catch (e: Exception) {
            Log.e(TAG, "scanDeadlines error", e)
            Result.failure(e)
        }
    }

    /** Полный выход: чистим всё — базу, prefs, куки, фоновые напоминания. */
    suspend fun logoutFull() {
        try {
            db.courseDao().deleteAll()
            db.labDao().deleteAll()
            db.eventDao().deleteAll()
            db.userDao().deleteAll()
            db.taskCacheDao().deleteAll()
            db.studentInfoDao().insert(StudentInfoEntity(name = null))
            Log.d(TAG, "LOGOUT DB cleared")
        } catch (e: Exception) { Log.w(TAG, "LOGOUT DB clear failed $e") }
        try {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("deadline-reminders")
        } catch (e: Exception) { Log.w(TAG, "LOGOUT cancel worker failed $e") }
        api.clearCookies()
        prefs.clear()
        Log.d(TAG, "LOGOUT full wipe done")
    }

    fun logout() { api.clearCookies(); prefs.clear(); Log.d(TAG, "LOGOUT clearCookies + prefs") }

    private fun CourseEntity.toModel() = Course(num, discipline, report, year, hours, period, teacher, points, cId)
    private fun Course.toEntity(filter: String) = CourseEntity(num = num, discipline = discipline, report = report, year = year, hours = hours, period = period, teacher = teacher, points = points, cId = cId, studyYearFilter = filter)
    private fun UserEntity.toModel() = User(id = id, name = name, group = group, faculty = faculty, avatarUrl = avatarUrl)
    private fun TaskCacheEntity.toTask() = CourseTask(title, requires, comment, controlDate, maxBall, result, status, flag)
}
