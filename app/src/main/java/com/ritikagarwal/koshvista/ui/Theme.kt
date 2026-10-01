package com.ritikagarwal.koshvista.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF176B4B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEFE4),
    background = Color(0xFFF6F5F1),
    surface = Color.White,
    onSurface = Color(0xFF17241C),
    onSurfaceVariant = Color(0xFF5B6A60),
    error = Color(0xFFC65B50),
    outline = Color(0xFFDFE5DE),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF6AD6A2),
    onPrimary = Color(0xFF111713),
    primaryContainer = Color(0xFF224634),
    background = Color(0xFF111713),
    surface = Color(0xFF1B241F),
    onSurface = Color(0xFFF2F6F1),
    onSurfaceVariant = Color(0xFFA8B8AD),
    error = Color(0xFFFF998B),
    outline = Color(0xFF36453B),
)

@Composable
fun KoshVistaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
