package com.example.x.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.x.data.model.Course
import com.example.x.data.model.Lab
import com.example.x.data.repository.MockData
import com.example.x.ui.theme.KemsuTheme
import com.example.x.viewmodel.CoursesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    vm: CoursesViewModel,
    onCourseClick: (String?) -> Unit,
    onEventsClick: () -> Unit,
    onLoginClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onScheduleClick: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    LaunchedEffect(Unit) { vm.checkUpdates() }
    CoursesContent(
        courses = state.courses,
        labs = state.homeLabs,
        userName = state.user?.name ?: state.studentName,
        firstName = state.user?.firstName,
        userGroup = state.user?.group,
        avatarUrl = state.user?.avatarUrl,
        studyYear = state.studyYear,
        availableYears = state.availableYears,
        isLoading = state.isLoading,
        error = state.error,
        unreadEvents = state.unreadEvents,
        update = state.update,
        appVersion = com.example.x.BuildConfig.VERSION_NAME,
        scan = state.scan,
        lastScanLabel = state.lastScanLabel,
        labFilter = state.labFilter,
        offline = state.offline,
        onLabFilter = { vm.setLabFilter(it) },
        onYear = { vm.setYear(it) },
        onRefresh = { vm.refresh() },
        onRefreshAll = { vm.refreshAll() },
        onClearError = { vm.clearError() },
        onCourseClick = onCourseClick,
        onEventsClick = { vm.markEventsRead(); onEventsClick() },
        onLogout = { vm.logoutFull { onLoginClick() } },
        onSettingsClick = onSettingsClick,
        onScheduleClick = onScheduleClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesContent(
    courses: List<Course>,
    labs: List<Lab>,
    userName: String?,
    firstName: String? = null,
    userGroup: String?,
    avatarUrl: String?,
    studyYear: String,
    availableYears: List<String> = emptyList(),
    isLoading: Boolean,
    error: String?,
    unreadEvents: Int = 0,
    update: com.example.x.data.remote.UpdateInfo? = null,
    appVersion: String = "",
    scan: com.example.x.viewmodel.ScanProgress = com.example.x.viewmodel.ScanProgress(),
    lastScanLabel: String = "",
    labFilter: String = "Все",
    offline: Boolean = false,
    onLabFilter: (String) -> Unit = {},
    onYear: (String) -> Unit,
    onRefresh: () -> Unit,
    onRefreshAll: () -> Unit,
    onClearError: () -> Unit,
    onCourseClick: (String?) -> Unit,
    onEventsClick: () -> Unit,
    onLogout: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onScheduleClick: () -> Unit = {}
) {
    // Разрешение на уведомления для фоновых напоминаний
    if (Build.VERSION.SDK_INT >= 33) {
        val ctx = LocalContext.current
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Главная") },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Обновить") }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Filled.Settings, "Настройки") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Главная") })
                NavigationBarItem(selected = false, onClick = onScheduleClick, icon = { Icon(Icons.Filled.DateRange, null) }, label = { Text("Расписание") })
                NavigationBarItem(
                    selected = false,
                    onClick = onEventsClick,
                    icon = {
                        BadgedBox(badge = {
                            if (unreadEvents > 0) Badge(containerColor = MaterialTheme.colorScheme.error) { Text(unreadEvents.toString()) }
                        }) { Icon(Icons.Filled.Notifications, null) }
                    },
                    label = { Text("События") }
                )
            }
        }
    ) { pad ->
        var showProfile by remember { mutableStateOf(false) }
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefreshAll,
            modifier = Modifier.padding(pad).fillMaxSize()
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(
                        "Привет, ${greetingName(firstName, userName)}! 👋",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().clickable { showProfile = true }
                    )
                }
                if (update?.hasUpdate == true) {
                    item {
                        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Вышла новая версия: ${update.latest} (у вас $appVersion)", fontWeight = FontWeight.Bold)
                                if (update.notes.isNotBlank()) Text(update.notes, style = MaterialTheme.typography.bodySmall)
                                Button(onClick = {
                                    try { uriHandler.openUri(update.url) } catch (_: Exception) {}
                                }) { Text("Открыть на GitHub") }
                            }
                        }
                    }
                }
                if (offline) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Офлайн-режим: сервер недоступен, показаны сохранённые данные",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        item { Text("Год:") }
                        items(availableYears) { y ->
                            FilterChip(selected = studyYear == y, onClick = { onYear(y) }, label = { Text(y) })
                        }
                    }
                }
                if (isLoading || scan.scanning) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text("Обновление данных…", fontWeight = FontWeight.Bold)
                                }
                                if (isLoading) {
                                    Text("Дисциплины…", style = MaterialTheme.typography.bodySmall)
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                                if (scan.scanning) {
                                    Text(
                                        if (scan.total > 0) "Лабы: ${scan.done}/${scan.total}" else "Лабы…",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    LinearProgressIndicator(
                                        progress = { if (scan.total > 0) scan.done.toFloat() / scan.total else 0f },
                                        modifier = Modifier.fillMaxWidth(),
                                        // Убираем точку-ограничитель на конце трека
                                        drawStopIndicator = {}
                                    )
                                }
                            }
                        }
                    }
                }
                if (isLoading && courses.isEmpty()) {
                    items(3) { ShimmerCourseCard() }
                }
                error?.let {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(it, modifier = Modifier.weight(1f))
                                TextButton(onClick = onClearError) { Text("OK") }
                            }
                        }
                    }
                }
                item { Text("Дисциплины", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                if (courses.isEmpty() && !isLoading) {
                    item { Text("Нет дисциплин") }
                } else if (!isLoading || courses.isNotEmpty()) {
                    items(courses) { c -> CourseCard(c, onCourseClick) }
                }
                item { Text("Лабораторные", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                item {
                    val filters = listOf("Все") + com.example.x.data.model.LabCategory.entries.map { it.label }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        items(filters) { f ->
                            FilterChip(selected = labFilter == f, onClick = { onLabFilter(f) }, label = { Text(f) })
                        }
                    }
                }
                // Прогресс скана уже в верхней карточке "Обновление данных…", здесь только метка
                if (!scan.scanning && lastScanLabel.isNotBlank()) {
                    item {
                        Text(
                            lastScanLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (labs.isEmpty()) {
                    item { Text(if (labFilter == "Все") "Лаб нет" else "Нет лаб: $labFilter") }
                } else {
                    items(labs) { lab ->
                        val cat = com.example.x.data.model.labCategoryOf(lab)
                        val bg = when (cat) {
                            com.example.x.data.model.LabCategory.DONE -> androidx.compose.ui.graphics.Color(0xFFDFF2E1)
                            com.example.x.data.model.LabCategory.ZERO -> androidx.compose.ui.graphics.Color(0xFFFDE8EA)
                            com.example.x.data.model.LabCategory.REVIEW -> androidx.compose.ui.graphics.Color(0xFFE2F0FD)
                            com.example.x.data.model.LabCategory.TODO -> androidx.compose.ui.graphics.Color(0xFFFFF0BE)
                            com.example.x.data.model.LabCategory.EDIT -> androidx.compose.ui.graphics.Color(0xFFB983FD)
                        }
                        val accent = when (cat) {
                            com.example.x.data.model.LabCategory.DONE -> androidx.compose.ui.graphics.Color(0xFF1B5E20)
                            com.example.x.data.model.LabCategory.ZERO -> androidx.compose.ui.graphics.Color(0xFFB3261E)
                            com.example.x.data.model.LabCategory.REVIEW -> androidx.compose.ui.graphics.Color(0xFF0D47A1)
                            com.example.x.data.model.LabCategory.TODO -> androidx.compose.ui.graphics.Color(0xFF7A5C00)
                            com.example.x.data.model.LabCategory.EDIT -> androidx.compose.ui.graphics.Color(0xFF7D2AE8)
                        }
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = bg)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Весь текст тёмный в цвет статуса — на пастели белый/серый не читается
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(lab.title, fontWeight = FontWeight.SemiBold, color = accent, modifier = Modifier.weight(1f))
                                    if (lab.changed) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text("Обновлено") },
                                            colors = AssistChipDefaults.assistChipColors(labelColor = accent)
                                        )
                                    }
                                }
                                Text("${lab.discipline} • дедлайн ${lab.deadline}", style = MaterialTheme.typography.bodySmall, color = accent)
                                Text(
                                    // Шкала у лаб разная (2, 10, 20...) — показываем реальный максимум с сервера;
                                    // если максимум неизвестен, только баллы без "/0"
                                    if (lab.maxPoints > 0) "${cat.label} • ${lab.points}/${lab.maxPoints}"
                                    else "${cat.label} • ${lab.points}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Версия приложения: ${appVersion.ifBlank { "?" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (showProfile) {
            AlertDialog(
                onDismissRequest = { showProfile = false },
                title = { Text(userName ?: "Студент") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (avatarUrl != null) {
                            AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.size(64.dp).clip(CircleShape))
                        }
                        Text(userGroup?.ifBlank { "Группа не указана" } ?: "Группа не указана", style = MaterialTheme.typography.bodyMedium)
                        Text("Кемеровский государственный университет", style = MaterialTheme.typography.labelSmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showProfile = false; onLogout() }) { Text("Выйти") }
                },
                dismissButton = {
                    TextButton(onClick = { showProfile = false }) { Text("Закрыть") }
                }
            )
        }
    }
}

/**
 * Имя для приветствия. С API приходит firstName; запасной вариант — строка
 * "Фамилия Имя Отчество" (именно в таком порядке её собирает сервер),
 * поэтому если первое слово похоже на фамилию — берём второе.
 */
private fun greetingName(firstName: String?, displayName: String?): String {
    if (!firstName.isNullOrBlank()) return firstName.trim()
    val parts = displayName?.split(" ")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    if (parts.isEmpty()) return "Студент"
    if (parts.size >= 2 && looksLikeSurname(parts[0])) return parts[1]
    return parts[0]
}

private fun looksLikeSurname(w: String): Boolean {
    if (w.length < 4) return false
    val endings = listOf(
        "ов", "ев", "ёв", "ин", "ын", "ский", "ской", "цкий",
        "ая", "яя", "ова", "ева", "ёва", "ина", "ых", "их"
    )
    return endings.any { w.endsWith(it, ignoreCase = true) }
}

@Composable
fun CourseCard(c: Course, onClick: (String?) -> Unit) {    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onClick(c.cId) }) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${c.num}. ${c.discipline}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(c.report) })
            }
            Spacer(Modifier.height(6.dp))
            Text("Год: ${c.year} • ${c.hours}ч", style = MaterialTheme.typography.bodySmall)
            Text("Период: ${c.period}", style = MaterialTheme.typography.bodySmall)
            Text("Преподаватель: ${c.teacher}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Баллы: ${c.points}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ShimmerCourseCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.7f).height(18.dp).background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape))
            Box(Modifier.fillMaxWidth(0.5f).height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape))
            Box(Modifier.fillMaxWidth().height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape))
        }
    }
}

@Preview(showBackground = true, name = "Home")
@Composable
fun CoursesScreenPreview() {
    KemsuTheme {
        CoursesContent(
            courses = MockData.courses,
            labs = MockData.labs,
            userName = MockData.user.name,
            userGroup = MockData.user.group,
            avatarUrl = null,
            studyYear = "2026-2027",
            availableYears = listOf("2023-2024","2024-2025","2025-2026","2026-2027"),
            isLoading = false, error = null, unreadEvents = 3,
            onYear = {}, onRefresh = {}, onRefreshAll = {}, onClearError = {},
            onCourseClick = {}, onEventsClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Home Loading")
@Composable
fun CoursesLoadingPreview() {
    KemsuTheme {
        CoursesContent(
            courses = emptyList(),
            labs = emptyList(),
            userName = MockData.user.name,
            userGroup = MockData.user.group,
            avatarUrl = null,
            studyYear = "2026-2027",
            availableYears = listOf("2023-2024","2024-2025","2025-2026","2026-2027"),
            isLoading = true, error = null, unreadEvents = 0,
            onYear = {}, onRefresh = {}, onRefreshAll = {}, onClearError = {},
            onCourseClick = {}, onEventsClick = {}
        )
    }
}
