package com.example.x.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme

private val Light = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF3964FE),
    secondary = androidx.compose.ui.graphics.Color(0xFF5B6EF5),
    tertiary = androidx.compose.ui.graphics.Color(0xFF7B8CFE)
)
private val Dark = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF8AA1FF),
    secondary = androidx.compose.ui.graphics.Color(0xFFB7C8FE),
    tertiary = androidx.compose.ui.graphics.Color(0xFFD3E2FF)
)

@Composable
fun KemsuTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
