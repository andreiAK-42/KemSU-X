package com.example.x.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.x.ui.theme.KemsuTheme
import com.example.x.viewmodel.CoursesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: CoursesViewModel, onBack: () -> Unit, onFontChanged: () -> Unit = {}) {
    val days by vm.notifyDays.collectAsState()
    val count by vm.notifyCount.collectAsState()
    val ignoreFont by vm.ignoreFont.collectAsState()
    val notificationsOn by vm.notificationsEnabled.collectAsState()
    SettingsContent(
        days = days, count = count,
        onDays = { vm.setNotifyDays(it) }, onCount = { vm.setNotifyCount(it) },
        ignoreFont = ignoreFont,
        onIgnoreFont = { vm.setIgnoreFont(it); onFontChanged() },
        notificationsOn = notificationsOn,
        onNotifications = { vm.setNotificationsEnabled(it) },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    days: Int, count: Int, onDays: (Int) -> Unit, onCount: (Int) -> Unit,
    ignoreFont: Boolean, onIgnoreFont: (Boolean) -> Unit,
    notificationsOn: Boolean, onNotifications: (Boolean) -> Unit, onBack: () -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Настройки") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("Уведомления", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Напоминать о дедлайнах", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Switch(checked = notificationsOn, onCheckedChange = onNotifications)
                        }
                        Text("Напоминать о дедлайне за:", style = MaterialTheme.typography.titleMedium)
                        val options = listOf(1, 2, 3, 7)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            options.forEach { d ->
                                FilterChip(
                                    selected = days == d,
                                    enabled = notificationsOn,
                                    onClick = { onDays(d) },
                                    label = { Text(if (d == 1) "1 день" else "$d дн.") }
                                )
                            }
                        }
                        Text(
                            "Сейчас: за $days ${dayWord(days)} до дедлайна (и в день сдачи), только несданные лабы.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()
                        Text("Сколько раз напомнить:", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf(1, 2, 3, 4).forEach { c ->
                                FilterChip(
                                    selected = count == c,
                                    enabled = notificationsOn,
                                    onClick = { onCount(c) },
                                    label = { Text("$c") }
                                )
                            }
                        }
                        Text(
                            "Напоминания равномерно: ${intervalHint(days, count)}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item { Text("Экран", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Свой размер текста", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Игнорировать системное увеличение шрифта, чтобы не ломалась вёрстка",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = ignoreFont, onCheckedChange = onIgnoreFont)
                    }
                }
            }
        }
    }
}

private fun dayWord(d: Int): String = when {
    d % 10 == 1 && d % 100 != 11 -> "день"
    d % 10 in 2..4 && (d % 100 < 10 || d % 100 >= 20) -> "дня"
    else -> "дней"
}

/** "2 дня × 4 раза = каждые 12 ч" — как в примере пользователя. */
private fun intervalHint(days: Int, count: Int): String {
    if (days <= 0) return "только в день сдачи"
    val mins = (days * 24 * 60 / count.coerceAtLeast(1)).coerceAtLeast(15)
    val every = when {
        mins % (24 * 60) == 0 -> "каждые ${mins / (24 * 60)} дн."
        mins % 60 == 0 -> "каждые ${mins / 60} ч"
        else -> "примерно каждые ${mins / 60} ч ${mins % 60} мин"
    }
    return "за $days ${dayWord(days)} $count ${countWord(count)} = $every"
}

private fun countWord(c: Int): String = when {
    c % 10 == 1 && c % 100 != 11 -> "раз"
    c % 10 in 2..4 && (c % 100 < 10 || c % 100 >= 20) -> "раза"
    else -> "раз"
}

@Preview(showBackground = true, name = "Settings")
@Composable
fun SettingsPreview() {
    KemsuTheme { SettingsContent(days = 2, count = 4, onDays = {}, onCount = {}, ignoreFont = true, onIgnoreFont = {}, notificationsOn = true, onNotifications = {}, onBack = {}) }
}
