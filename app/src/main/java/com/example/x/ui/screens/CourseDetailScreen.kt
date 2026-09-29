package com.example.x.ui.screens


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.x.data.model.CourseTask
import com.example.x.data.repository.MockData
import com.example.x.ui.theme.KemsuTheme
import com.example.x.viewmodel.CourseDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(cId: String, onBack: () -> Unit, vm: CourseDetailViewModel = viewModel()) {
    val course by vm.course.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    LaunchedEffect(cId) { vm.loadByCId(cId) }
    CourseDetailContent(course = course, tasks = tasks, loading = loading, error = error, cId = cId, onBack = onBack, onRefresh = { vm.refresh(cId) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailContent(
    course: com.example.x.data.model.Course?,
    tasks: List<CourseTask> = emptyList(),
    loading: Boolean = false,
    error: String? = null,
    cId: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Детали курса") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = { IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, null) } }
        )
    }) { pad ->
        val c = course
        if (c == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Курс не найден в SQLite. c_id=$cId")
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(c.discipline, style = MaterialTheme.typography.headlineSmall)
                            HorizontalDivider()
                            DetailRow("Отчётность", c.report)
                            DetailRow("Учебный год", c.year)
                            DetailRow("Часы", c.hours.toString())
                            DetailRow("Период", c.period)
                            DetailRow("Преподаватель", c.teacher)
                            DetailRow("Баллы", c.points.toString())
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Лабораторные работы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${tasks.size} шт.", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (loading)
                {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                error?.let { item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer))
                        { Text(it, modifier = Modifier.padding(12.dp)) }
                    }
                }
                if (tasks.isEmpty() && !loading && error == null) {
                    item { Text("Нет заданий. Нажми Обновить. Запрос: POST /proc/stud/course_st/tasks_st.htm c_id=$cId (см. Logcat KEMSU_API)", style = MaterialTheme.typography.bodySmall) }
                }
                items(tasks) { t ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(t.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(onClick = {}, label = { Text(if (t.requiresSubmission == "да") "Требуется отправка" else t.requiresSubmission) })
                                val statusColor = when (t.flag) {
                                    "3" -> Color(0xFF2E7D32) // Оценено зеленый
                                    "13", "" -> Color(0xFFC62828) // Просмотрено/Не просмотрено красный
                                    "0", "1" -> Color(0xFF1565C0) // синий
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                AssistChip(onClick = {}, label = { Text(t.status, color = statusColor) })
                            }
                            if (t.comment.isNotBlank()) {
                                SelectionContainer {
                                    TextButton( onClick = { if (t.comment.startsWith("http")) try { uriHandler.openUri(t.comment) } catch (_: Exception) {} }, contentPadding = PaddingValues(0.dp), shape = RectangleShape) {
                                        val comment = if (t.comment.length > 100) "${t.comment.take(100)}..." else t.comment
                                        Text(comment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column { Text("Контрольная", style = MaterialTheme.typography.labelSmall); Text(t.controlDate, style = MaterialTheme.typography.bodySmall) }
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) { Text("Макс. балл", style = MaterialTheme.typography.labelSmall); Text(t.maxBall.toString(), fontWeight = FontWeight.Bold) }
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) { Text("Результат", style = MaterialTheme.typography.labelSmall); Text(t.result.ifBlank { "—" }, fontWeight = FontWeight.Bold, color = if (t.result.isNotBlank()) Color(0xFF2E7D32) else Color.Gray) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Detail")
@Composable
fun CourseDetailPreview() {
    val mockTasks = listOf(
        CourseTask("Интеллект-карты", "да", "https://vk.cc/cPkNHK", "13-09-2026 23:59:59", 2, "2", "Оценено", "3"),
        CourseTask("Багрепорты", "да", "https://vk.cc/cPkQck", "27-09-2026 23:59:59", 2, "", "Просмотрено", "13"),
        CourseTask("Charles Proxy", "да", "https://vk.cc/cPkNZA", "04-10-2026 23:59:59", 2, "", "Не просмотрено", "")
    )
    KemsuTheme { CourseDetailContent(course = MockData.courses.first(), tasks = mockTasks, cId = "9736", onBack = {}) }
}

@Preview(showBackground = true, name = "Detail NotFound")
@Composable
fun CourseDetailNotFoundPreview() {
    KemsuTheme { CourseDetailContent(course = null, tasks = emptyList(), cId = "9999", onBack = {}) }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
