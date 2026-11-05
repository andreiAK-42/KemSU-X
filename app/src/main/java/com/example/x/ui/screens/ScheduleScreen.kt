package com.example.x.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.x.data.model.ScheduleData
import com.example.x.data.model.ScheduleDay
import com.example.x.data.model.ScheduleDayInfo
import com.example.x.data.model.ScheduleLesson
import com.example.x.data.model.Teacher
import com.example.x.ui.theme.KemsuTheme
import com.example.x.viewmodel.CoursesViewModel
import com.example.x.viewmodel.CoursesViewModel.TeacherUiState

/** 0 = расписание группы, 1 = расписание преподавателей */
private const val MODE_GROUP = 0
private const val MODE_TEACHER = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(vm: CoursesViewModel, onBack: () -> Unit) {
    val state by vm.schedule.collectAsState()
    val teacherState by vm.teachers.collectAsState()
    var mode by rememberSaveable { mutableStateOf(MODE_GROUP) }
    LaunchedEffect(Unit) {
        vm.loadSchedule()
        vm.loadTeachers()
    }
    if (mode == MODE_GROUP) {
        ScheduleContent(
            data = state.data,
            isLoading = state.isLoading,
            error = state.error,
            parityOverride = state.parityOverride,
            onParity = { vm.setScheduleParity(it) },
            onRefresh = { vm.refreshSchedule() },
            onClearError = { vm.clearScheduleError() },
            modeHeader = { ScheduleModeChips(MODE_GROUP) { mode = it } },
            onBack = onBack
        )
    } else {
        TeacherScheduleContent(
            state = teacherState,
            onSelect = { vm.selectTeacher(it) },
            onBackToList = { vm.backToTeacherList() },
            onQuery = { vm.setTeacherQuery(it) },
            onParity = { vm.setTeacherParity(it) },
            onRefresh = {
                if (teacherState.selected != null) vm.refreshTeacherSchedule() else vm.refreshTeachers()
            },
            onClearError = { vm.clearTeacherError() },
            onModeGroup = { mode = MODE_GROUP }
        )
    }
}

@Composable
fun ScheduleModeChips(mode: Int, onMode: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item { FilterChip(selected = mode == MODE_GROUP, onClick = { onMode(MODE_GROUP) }, label = { Text("Моя группа") }) }
        item { FilterChip(selected = mode == MODE_TEACHER, onClick = { onMode(MODE_TEACHER) }, label = { Text("Преподаватели") }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleContent(
    data: ScheduleData?,
    isLoading: Boolean,
    error: String?,
    parityOverride: Boolean? = null,
    onParity: (Boolean?) -> Unit = {},
    onRefresh: () -> Unit,
    onClearError: () -> Unit,
    modeHeader: (@Composable () -> Unit)? = null,
    onBack: () -> Unit
) {
    // День по умолчанию — текущий, но только при заходе на экран.
    // Выбор пользователя живёт отдельно и НЕ сбрасывается при смене чётности.
    var userDay by remember { mutableStateOf<Int?>(null) }
    val defaultDay = data?.dayInfo?.currentDayNum?.takeIf { n -> data.days.any { it.dayNum == n } }
        ?: data?.days?.firstOrNull()?.dayNum ?: 0
    val selectedDay = userDay?.takeIf { n -> data?.days?.any { it.dayNum == n } == true } ?: defaultDay
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Расписание") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = { IconButton(onClick = onRefresh, enabled = !isLoading) { Icon(Icons.Default.Refresh, "Обновить") } }
        )
    }) { pad ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.padding(pad).fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                modeHeader?.let { header -> item { header() } }
                val info = data?.dayInfo
                if (info != null) {
                    item { WeekInfoCard(data!!, parityOverride) }
                    item { ParityChips(parityOverride, onParity) }
                }
                if (data != null && data.days.isNotEmpty()) {
                    item {
                        DayChips(data.days, selectedDay, onDay = { userDay = it })
                    }
                    DayLessonsItems(data, selectedDay)
                    if (data.updatedAt > 0L) {
                        item { UpdatedAtText(data.updatedAt) }
                    }
                } else if (!isLoading) {
                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Расписания пока нет")
                                Button(onClick = onRefresh) { Text("Обновить") }
                            }
                        }
                    }
                }
                if (isLoading && data == null) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                error?.let {
                    item { ErrorCard(it, onClearError) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherScheduleContent(
    state: TeacherUiState,
    onSelect: (Teacher) -> Unit,
    onBackToList: () -> Unit,
    onQuery: (String) -> Unit,
    onParity: (Boolean?) -> Unit,
    onRefresh: () -> Unit,
    onClearError: () -> Unit,
    onModeGroup: () -> Unit
) {
    val selected = state.selected
    // Системный назад: из расписания преподавателя — к списку, из списка — к группе
    BackHandler(enabled = selected != null) { onBackToList() }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (selected == null) "Расписание преподавателей" else selected.fio) },
            navigationIcon = {
                IconButton(
                    onClick = if (selected == null) onModeGroup else onBackToList
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            },
            actions = {
                IconButton(
                    onClick = onRefresh,
                    enabled = !state.listLoading && !state.scheduleLoading
                ) { Icon(Icons.Default.Refresh, "Обновить") }
            }
        )
    }) { pad ->
        val isRefreshing = if (selected == null) state.listLoading else state.scheduleLoading
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(pad).fillMaxSize()
        ) {
            if (selected == null) {
                TeacherListItems(state, onSelect, onQuery, onModeGroup)
            } else {
                TeacherScheduleItems(state, selected, onBackToList, onParity)
            }
            state.error?.let {
                // Ошибка поверх списка, чтобы её было видно и был доступ к кнопке обновления
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    ErrorCard(it, onClearError, Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TeacherListItems(
    state: TeacherUiState,
    onSelect: (Teacher) -> Unit,
    onQuery: (String) -> Unit,
    onModeGroup: () -> Unit
) {
    val filtered = state.teachers.filter { it.fio.contains(state.query.trim(), ignoreCase = true) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScheduleModeChips(MODE_TEACHER) { m -> if (m == MODE_GROUP) onModeGroup() } }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Поиск по ФИО") }
            )
        }
        if (state.teachers.isEmpty() && state.listLoading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } else if (state.teachers.isEmpty()) {
            item { Text("Список преподавателей пуст. Потяните вниз, чтобы обновить.") }
        } else {
            item { Text(pluralRu(filtered.size, "преподаватель", "преподавателя", "преподавателей"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(filtered) { t ->
                Column(
                    Modifier.fillMaxWidth().clickable { onSelect(t) }
                ) {
                    Text(t.fio, Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
                    HorizontalDivider()
                }
            }
            if (filtered.isEmpty()) {
                item { Text("Никого не нашли по «${state.query.trim()}»") }
            }
        }
    }
}

@Composable
private fun TeacherScheduleItems(
    state: TeacherUiState,
    selected: Teacher,
    onBackToList: () -> Unit,
    onParity: (Boolean?) -> Unit
) {
    val data = state.schedule
    var userDay by remember(selected.prepId) { mutableStateOf<Int?>(null) }
    val defaultDay = data?.dayInfo?.currentDayNum?.takeIf { n -> data.days.any { it.dayNum == n } }
        ?: data?.days?.firstOrNull()?.dayNum ?: 0
    val selectedDay = userDay?.takeIf { n -> data?.days?.any { it.dayNum == n } == true } ?: defaultDay
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onBackToList) { Text("← К списку") }
            }
        }
        val info = data?.dayInfo
        if (info != null) {
            item { WeekInfoCard(data!!, state.parityOverride) }
            item { ParityChips(state.parityOverride, onParity) }
        }
        if (state.scheduleLoading && data == null) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } else if (data != null && data.days.isNotEmpty()) {
            item { DayChips(data.days, selectedDay, onDay = { userDay = it }) }
            DayLessonsItems(data, selectedDay)
            if (data.updatedAt > 0L) {
                item { UpdatedAtText(data.updatedAt) }
            }
        } else if (!state.scheduleLoading) {
            item { Text("Занятий не найдено") }
        }
    }
}

@Composable
fun WeekInfoCard(data: ScheduleData, parityOverride: Boolean?) {
    val info = data.dayInfo ?: return
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Неделя ${info.weekNum} • ${info.weekType}" +
                    if (parityOverride != null) " (показана ${if (parityOverride) "нечётная" else "чётная"})" else "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${info.currentDay}, ${info.currentDate} • ${info.startOfWeek} — ${info.endOfWeek}",
                style = MaterialTheme.typography.bodySmall
            )
            if (data.groupName.isNotBlank()) Text(
                "Группа ${data.groupName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ParityChips(parityOverride: Boolean?, onParity: (Boolean?) -> Unit) {
    // Свич чётности: на чётной посмотреть нечётную и наоборот, без сети
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        item { Text("Неделя:") }
        item { FilterChip(selected = parityOverride == null, onClick = { onParity(null) }, label = { Text("Авто") }) }
        item { FilterChip(selected = parityOverride == false, onClick = { onParity(false) }, label = { Text("Чётная") }) }
        item { FilterChip(selected = parityOverride == true, onClick = { onParity(true) }, label = { Text("Нечётная") }) }
    }
}

@Composable
fun DayChips(days: List<ScheduleDay>, selectedDay: Int, onDay: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(days) { d ->
            FilterChip(
                selected = selectedDay == d.dayNum,
                onClick = { onDay(d.dayNum) },
                label = { Text(d.dayNameShort.ifBlank { d.dayName }) }
            )
        }
    }
}

/**
 * Заголовок дня с карточками пар. Расширение LazyListScope: вставляет свои item'ы
 * во внешний LazyColumn.
 */
private fun LazyListScope.DayLessonsItems(data: ScheduleData, selectedDay: Int) {
    val day = data.days.firstOrNull { it.dayNum == selectedDay }
    if (day == null || day.lessons.isEmpty()) {
        item { Text("В этот день пар нет 🎉") }
    } else {
        val n = day.lessons.size
        val word = when {
            n % 10 == 1 && n % 100 != 11 -> "пара"
            n % 10 in 2..4 && (n % 100 < 10 || n % 100 >= 20) -> "пары"
            else -> "пар"
        }
        item { Text("${day.dayName}: $n $word.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(day.lessons) { l -> LessonCard(l) }
    }
}

@Composable
fun UpdatedAtText(updatedAt: Long) {
    Text(
        "Обновлено: " + try {
            java.time.Instant.ofEpochMilli(updatedAt)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"))
        } catch (_: Exception) { "" },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun ErrorCard(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

fun pluralRu(n: Int, one: String, few: String, many: String): String = when {
    n % 10 == 1 && n % 100 != 11 -> "$n $one"
    n % 10 in 2..4 && (n % 100 < 10 || n % 100 >= 20) -> "$n $few"
    else -> "$n $many"
}

@Composable
fun LessonCard(l: ScheduleLesson) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    l.time.ifBlank { "Время?" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                if (l.lessonType.isNotBlank()) AssistChip(onClick = {}, label = { Text(l.lessonType) })
            }
            Text(l.discName, style = MaterialTheme.typography.titleMedium)
            if (l.prepName.isNotBlank()) Text(l.prepName, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (l.auditoryName.isNotBlank()) Text("Ауд. ${l.auditoryName}", style = MaterialTheme.typography.bodySmall)
                if (l.periodTypeName.isNotBlank()) Text(
                    l.periodTypeName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Schedule")
@Composable
fun SchedulePreview() {
    val info = ScheduleDayInfo(5, "нечетная", "29.09.2026", "Вторник", 2, "28.09.2026", "04.10.2026")
    val day = ScheduleDay(
        2, "Вторник", "Вт", listOf(
            ScheduleLesson("Разработка на платформе 1С:Предприятие", "Пасютин А.С.", "2220", "Лаб", "по нечетным неделям", 1, 17, "9:45 - 11:20"),
            ScheduleLesson("Принципы цифровой трансформации бизнеса", "Бурмин Л.Н.", "2141", "Лек", "по нечетным неделям", 1, 17, "11:45 - 13:20"),
            ScheduleLesson("Философия", "Киркин А.А.", "5220", "Пр", "каждую неделю", 1, 16, "13:30 - 15:05")
        )
    )
    KemsuTheme {
        ScheduleContent(data = ScheduleData(info, "ПИ-231", listOf(day), System.currentTimeMillis()), isLoading = false, error = null, onRefresh = {}, onClearError = {}, onBack = {})
    }
}

@Preview(showBackground = true, name = "Teacher list")
@Composable
fun TeacherListPreview() {
    KemsuTheme {
        TeacherScheduleContent(
            state = TeacherUiState(
                teachers = listOf(Teacher(114, "Абдуллаева Ф. Э."), Teacher(2599, "Абросимова А. В."), Teacher(2943, "Авдеев К. С.")),
                query = ""
            ),
            onSelect = {}, onBackToList = {}, onQuery = {}, onParity = {}, onRefresh = {}, onClearError = {}, onModeGroup = {}
        )
    }
}

@Preview(showBackground = true, name = "Teacher schedule")
@Composable
fun TeacherSchedulePreview() {
    val info = ScheduleDayInfo(5, "нечетная", "29.09.2026", "Вторник", 2, "28.09.2026", "04.10.2026")
    val day = ScheduleDay(
        2, "Вторник", "Вт", listOf(
            ScheduleLesson("Математический анализ", "", "5106", "Лек", "", 1, 15, "9:45 - 11:20"),
            ScheduleLesson("Функциональный анализ", "", "5316а", "Лек", "", 1, 17, "13:30 - 15:05")
        )
    )
    KemsuTheme {
        TeacherScheduleContent(
            state = TeacherUiState(
                selected = Teacher(279, "Иванов И. И."),
                schedule = ScheduleData(info, "", listOf(day), System.currentTimeMillis())
            ),
            onSelect = {}, onBackToList = {}, onQuery = {}, onParity = {}, onRefresh = {}, onClearError = {}, onModeGroup = {}
        )
    }
}
