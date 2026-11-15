package com.freepakistancall.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green = Color(0xFF0B7A3E)
val GreenDark = Color(0xFF075A2D)
val GreenLight = Color(0xFFE6F4EA)
val Red = Color(0xFFD32F2F)

@Composable
fun FpcTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = lightColorScheme(
        primary = Green, onPrimary = Color.White, secondary = Color(0xFF2E9E5B),
        primaryContainer = GreenLight, onPrimaryContainer = GreenDark,
        background = Color.White, surface = Color.White,
    ),
    content = content,
)
