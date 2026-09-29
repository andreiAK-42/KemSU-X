package com.example.x.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.x.data.local.AppDatabase
import com.example.x.data.local.PrefsManager
import com.example.x.data.model.Course
import com.example.x.data.model.Event
import com.example.x.data.model.Lab
import com.example.x.data.model.User
import com.example.x.data.remote.KemsuApi
import com.example.x.data.remote.UpdateChecker
import com.example.x.data.remote.UpdateInfo
import com.example.x.data.repository.CourseRepository
import com.example.x.utils.DeadlineNotifier
import com.example.x.utils.DeadlineScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScanProgress(val scanning: Boolean = false, val done: Int = 0, val total: Int = 0)

data class CoursesUiState(
    val isLoading: Boolean = false,
    val courses: List<Course> = emptyList(),
    val labs: List<Lab> = emptyList(),
    val events: List<Event> = emptyList(),
    val unreadEvents: Int = 0,
    val user: User? = null,
    val studentName: String? = null,
    val error: String? = null,
    val studyYear: String = "",
    val availableYears: List<String> = emptyList(),
    val update: UpdateInfo? = null,
    val scan: ScanProgress = ScanProgress(),
    val lastScanLabel: String = "",
    val labFilter: String = "Все",
    val homeLabs: List<Lab> = emptyList()
)

class CoursesViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val api = KemsuApi(app)
    val repository = CourseRepository(db, api, app)
    val prefs = PrefsManager(app)

    fun currentAcademicYear(): String = DeadlineNotifier.currentAcademicYear()

    fun generateYears(): List<String> {
        // Ранжировка от текущего учебного года вниз: напр. 2026-2027, 2025-2026, ...
        val cur = currentAcademicYear().substringBefore("-").toIntOrNull() ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        return (cur downTo cur - 3).map { y -> "$y-${y + 1}" }
    }

    private val _year = MutableStateFlow(currentAcademicYear())
    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _update = MutableStateFlow<UpdateInfo?>(null)
    private val _scan = MutableStateFlow(ScanProgress())
    private val _labFilter = MutableStateFlow("Все")
    private val _notifyDays = MutableStateFlow(prefs.getNotifyDays())
    private val _notifyCount = MutableStateFlow(prefs.getNotifyCount())
    private val _ignoreFont = MutableStateFlow(prefs.getIgnoreSystemFont())

    fun setLabFilter(f: String) { _labFilter.value = f; Log.d("KEMSU_API", "lab filter=$f") }

    /** За сколько дней до дедлайна напоминать (дефолт 1). Читается воркером и сканом. */
    val notifyDays: StateFlow<Int> = _notifyDays
    fun setNotifyDays(d: Int) {
        prefs.setNotifyDays(d)
        _notifyDays.value = prefs.getNotifyDays()
        DeadlineScheduler.schedule(getApplication(), force = true)
        Log.d("KEMSU_API", "notify days=${_notifyDays.value}")
    }

    /** Сколько раз напомнить за окно. Интервал = окно / количество. */
    val notifyCount: StateFlow<Int> = _notifyCount
    fun setNotifyCount(c: Int) {
        prefs.setNotifyCount(c)
        _notifyCount.value = prefs.getNotifyCount()
        DeadlineScheduler.schedule(getApplication(), force = true)
        Log.d("KEMSU_API", "notify count=${_notifyCount.value}")
    }

    /** Игнорировать системное увеличение шрифта (дефолт — да). */
    val ignoreFont: StateFlow<Boolean> = _ignoreFont
    fun setIgnoreFont(v: Boolean) {
        prefs.setIgnoreSystemFont(v)
        _ignoreFont.value = v
        Log.d("KEMSU_API", "ignore font=$v")
    }

    private val base = combine(
        repository.observeAllCourses(),
        repository.observeLabs(),
        repository.observeUser(),
        repository.observeStudentName(),
        _loading
    ) { courses: List<Course>, labs: List<Lab>, user: User?, name: String?, loading: Boolean ->
        Triple(Pair(courses, labs), Pair(user, name), loading)
    }

    private val base2 = combine(base, repository.observeEvents(), repository.observeUnreadCount(), _error) { b, events, unread, err ->
        Triple(b, Pair(events, unread), err)
    }

    val uiState: StateFlow<CoursesUiState> = combine(base2, _year, _update, _scan, _labFilter) { triple, year, update, scan, labFilter ->
        val (b, pairEU, err) = triple
        val (events, unread) = pairEU
        val (pair1, pair2, loading) = b
        val (courses, labs) = pair1
        val (user, name) = pair2
        val norm = DeadlineNotifier.normalizeYear(year)
        val filtered = if (norm.isBlank()) courses.sortedBy { it.num }
        else courses.filter { DeadlineNotifier.normalizeYear(it.year) == norm }.sortedBy { it.num }
        val homeLabs = if (labFilter == "Все") labs
        else labs.filter { com.example.x.data.model.labCategoryOf(it).label == labFilter }
        CoursesUiState(loading, filtered, labs, events, unread, user, name, err, year, generateYears(), update, scan, lastScanLabel(), labFilter, homeLabs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoursesUiState(availableYears = generateYears()))

    private fun lastScanLabel(): String {
        val ts = prefs.getLastDeadlineScan()
        if (ts == 0L) return ""
        return try {
            val dt = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            "Обновлено: " + dt.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"))
        } catch (_: Exception) { "" }
    }

    fun setYear(y: String) { _year.value = y; Log.d("KEMSU_API", "filter year=$y") }

    fun savedLogin(): String = prefs.getLogin()
    fun savedPassword(): String = prefs.getPassword()
    fun hasSaved(): Boolean = prefs.hasSaved()

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true; _error.value = null
            // Грузим ВЕСЬ список дисциплин (studyYear=""), фильтр по году — локально
            Log.d("KEMSU_API", "refresh() all, uiYear=${_year.value}")
            val res = repository.refresh("")
            Log.d("KEMSU_API", "refresh result success=${res.isSuccess} err=${res.exceptionOrNull()?.message}")
            if (res.isFailure) {
                _error.value = res.exceptionOrNull()?.message
            } else {
                // Состояние лаб обновляем при каждом заходе (force — свежая сеть),
                // изменившиеся подсветятся флагом changed до следующего захода.
                // Прогресс виден в верхней карточке и в баре под "Лабораторные".
                val targetYear = _year.value.ifBlank { currentAcademicYear() }
                _scan.value = ScanProgress(true, 0, 0)
                val scan = repository.scanDeadlines(targetYear, force = true) { done, total ->
                    _scan.value = ScanProgress(true, done, total)
                }
                _scan.value = ScanProgress(false, 0, 0)
                Log.d("KEMSU_API", "scanDeadlines success=${scan.isSuccess} labs=${scan.getOrNull()?.size}")
                if (scan.isSuccess) {
                    prefs.setLastDeadlineScan()
                    DeadlineNotifier.notifyIfNeeded(getApplication(), scan.getOrNull() ?: emptyList(), prefs.getNotifyDays(), prefs.getNotifyCount())
                }
            }
            DeadlineScheduler.schedule(getApplication())
            _loading.value = false
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _loading.value = true
            Log.d("KEMSU_API", "refreshAll()")
            refresh()
        }
    }

    fun login(user: String, pass: String, remember: Boolean, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            Log.d("KEMSU_API", "login user=$user remember=$remember")
            val res = repository.login(user, pass)
            Log.d("KEMSU_API", "login result $res")
            if (res.isSuccess) {
                // Всегда сохраняем для dekanat bridge, has_saved отдельно для автоподстановки
                prefs.saveCredentials(user, pass)
                if (!remember) {
                    // если не хочет запоминать для UI — сбросим has_saved но оставим логин для bridge через отдельные ключи
                    // Но проще оставить has_saved=true чтобы bridge не скипал; UI всё равно подставит
                }
                onDone(true, null)
                DeadlineScheduler.schedule(getApplication())
                refresh()
            } else {
                onDone(false, res.exceptionOrNull()?.message)
            }
            _loading.value = false
        }
    }

    fun checkDeadlines(context: Context) {
        viewModelScope.launch {
            // Читаем сохранённые лабы из базы (без сети и без входа) — то, что запомнили
            val currentLabs = try { repository.getLabsOnce() } catch (_: Exception) { uiState.value.labs }
            Log.d("KEMSU_API", "checkDeadlines labs=${currentLabs.size} days=${prefs.getNotifyDays()} count=${prefs.getNotifyCount()}")
            DeadlineNotifier.notifyIfNeeded(context, currentLabs, prefs.getNotifyDays(), prefs.getNotifyCount())
        }
    }

    fun markEventsRead() { viewModelScope.launch { repository.markEventsRead() } }
    fun clearError() { _error.value = null }
    fun logout() { prefs.clear(); repository.logout() }

    /** Полный выход: стирает базу, prefs, куки и фоновые напоминания. */
    fun logoutFull(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try { repository.logoutFull() } catch (e: Exception) {
                Log.w("KEMSU_API", "logoutFull failed $e")
            }
            onDone()
        }
    }

    /** Проверка новой версии на GitHub (молча пропускается, если репозиторий не задан). */
    fun checkUpdates() {
        viewModelScope.launch {
            try {
                val owner = com.example.x.data.remote.ApiConfig.GITHUB_OWNER
                val repo = com.example.x.data.remote.ApiConfig.GITHUB_REPO
                if (owner.isBlank() || repo.isBlank()) return@launch
                val current = com.example.x.BuildConfig.VERSION_NAME
                val info = UpdateChecker.check(owner, repo, current)
                Log.d("KEMSU_API", "update check current=$current info=$info")
                if (info?.hasUpdate == true) _update.value = info
            } catch (e: Exception) {
                Log.w("KEMSU_API", "update check failed $e")
            }
        }
    }
}
