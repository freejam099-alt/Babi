package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = CloudXDarkPrimary,
    onPrimary = Color(0xFF00325B),
    primaryContainer = CloudXDarkPrimaryContainer,
    secondary = CloudXDarkSecondary,
    tertiary = CloudXDarkTertiary,
    background = CloudXDarkBackground,
    surface = CloudXDarkSurface,
    surfaceVariant = CloudXDarkSurfaceVariant,
    onBackground = CloudXDarkOnSurface,
    onSurface = CloudXDarkOnSurface,
    onSurfaceVariant = CloudXDarkOnSurfaceVariant
  )

private val LightColorScheme =
  lightColorScheme(
    primary = CloudXPrimary,
    primaryContainer = CloudXPrimaryContainer,
    secondary = CloudXSecondary,
    tertiary = CloudXTertiary,
    background = CloudXBackground,
    surface = CloudXSurface,
    onBackground = Color(0xFF1E293B),
    onSurface = Color(0xFF1E293B),
  )

@Composable
fun CloudXTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false, // Set to false to enforce our hand-crafted brand identity
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
