package com.example.x.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.x.data.model.ScheduleData
import com.example.x.data.model.ScheduleDay
import com.example.x.data.model.ScheduleDayInfo
import com.example.x.data.model.ScheduleLesson
import com.example.x.ui.theme.KemsuTheme
import com.example.x.viewmodel.CoursesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(vm: CoursesViewModel, onBack: () -> Unit) {
    val state by vm.schedule.collectAsState()
    LaunchedEffect(Unit) { vm.loadSchedule() }
    ScheduleContent(
        data = state.data,
        isLoading = state.isLoading,
        error = state.error,
        onRefresh = { vm.refreshSchedule() },
        onClearError = { vm.clearScheduleError() },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleContent(
    data: ScheduleData?,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit
) {
    var selectedDay by remember(data) {
        mutableStateOf(data?.dayInfo?.currentDayNum?.takeIf { n -> data.days.any { it.dayNum == n } }
            ?: data?.days?.firstOrNull()?.dayNum ?: 0)
    }
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
                val info = data?.dayInfo
                if (info != null) {
                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Неделя ${info.weekNum} • ${info.weekType}",
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
                }
                if (data != null && data.days.isNotEmpty()) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(data.days) { d ->
                                FilterChip(
                                    selected = selectedDay == d.dayNum,
                                    onClick = { selectedDay = d.dayNum },
                                    label = { Text(d.dayNameShort.ifBlank { d.dayName }) }
                                )
                            }
                        }
                    }
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
                    if (data.updatedAt > 0L) {
                        item {
                            Text(
                                "Обновлено: " + try {
                                    java.time.Instant.ofEpochMilli(data.updatedAt)
                                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                                        .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"))
                                } catch (_: Exception) { "" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(it, modifier = Modifier.weight(1f))
                                TextButton(onClick = onClearError) { Text("OK") }
                            }
                        }
                    }
                }
            }
        }
    }
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
