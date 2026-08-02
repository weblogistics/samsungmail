package com.coursework.unifiedmail.ui.theme

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

private val LightColors = lightColorScheme(
    primary = SamsungPrimaryLight,
    onPrimary = Color.White,
    background = SamsungBackgroundLight,
    onBackground = SamsungOnBackgroundLight,
    surface = SamsungBackgroundLight,
    onSurface = SamsungOnBackgroundLight,
)

private val DarkColors = darkColorScheme(
    primary = SamsungPrimaryDark,
    onPrimary = Color.Black,
    background = SamsungBackgroundDark,
    onBackground = SamsungOnBackgroundDark,
    surface = SamsungBackgroundDark,
    onSurface = SamsungOnBackgroundDark,
)

@Composable
fun UnifiedMailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default: the point of this palette is to show Samsung Email's actual colors
    // consistently, not whatever Material You derives from the device wallpaper.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MailTypography,
        content = content,
    )
}
