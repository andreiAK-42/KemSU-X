package com.example.x.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.x.data.model.Lab
import com.example.x.data.repository.MockData
import com.example.x.ui.theme.KemsuTheme
import com.example.x.viewmodel.CoursesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(vm: CoursesViewModel, onBack: () -> Unit) {
    val state by vm.uiState.collectAsState()
    LaunchedEffect(Unit) {
        // при открытии события считаются прочитанными — бейдж пропадает
        if (state.unreadEvents > 0) vm.markEventsRead()
    }
    EventsContent(events = state.events, unread = state.unreadEvents, labs = state.labs, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsContent(events: List<com.example.x.data.model.Event>, unread: Int = 0, labs: List<Lab> = emptyList(), onBack: () -> Unit) {
    // Лабы с ближайшим дедлайном (несданные, немьюченные, ближайшие 14 дней)
    val soonLabs = remember(labs) {
        val today = java.time.LocalDate.now()
        labs.filter { !it.muted }.mapNotNull { lab ->
            try {
                val d = java.time.LocalDate.parse(lab.deadline, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, d)
                if (days >= 0 && days <= 14 && lab.status != "Сдано") lab to days else null
            } catch (_: Exception) { null }
        }.sortedBy { it.second }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("События")
                    if (unread > 0) {
                        Spacer(Modifier.width(8.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.error) { Text("$unread новых") }
                    }
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (soonLabs.isNotEmpty()) {
                item { Text("Скоро дедлайн", style = MaterialTheme.typography.titleLarge) }
                items(soonLabs) { (lab, days) ->
                    val when_ = when (days) { 0L -> "сегодня"; 1L -> "завтра"; else -> "через $days дн." }
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(lab.discipline, style = MaterialTheme.typography.titleMedium)
                            Text("${lab.title} — дедлайн $when_ (${lab.deadline})", style = MaterialTheme.typography.bodyMedium)
                            Text("Статус: ${lab.status} • ${lab.points}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                item { Text("Все события", style = MaterialTheme.typography.titleLarge) }
            }
            if (unread > 0) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text("Есть непрочитанные: $unread — новые объекты появятся здесь", modifier = Modifier.padding(12.dp))
                    }
                }
            }
            items(events) { e ->
                val isNew = unread > 0 // упрощённо: пока есть непрочитанные — подсвечиваем
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = if (isNew) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            if (isNew) Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape))
                            if (isNew) Spacer(Modifier.width(6.dp))
                            Text(e.discipline, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(e.text, style = MaterialTheme.typography.bodyMedium)
                        Text(e.date, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (events.isEmpty()) item { Text("Событий нет (оранжевая таблица #FFDEAD пуста)") }
        }
    }
}

@Preview(showBackground = true, name = "Events")
@Composable
fun EventsPreview() {
    KemsuTheme { EventsContent(events = MockData.events, onBack = {}) }
}

@Preview(showBackground = true, name = "Events Empty")
@Composable
fun EventsEmptyPreview() {
    KemsuTheme { EventsContent(events = emptyList(), onBack = {}) }
}
