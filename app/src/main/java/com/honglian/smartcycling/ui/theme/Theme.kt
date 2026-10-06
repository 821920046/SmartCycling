package com.honglian.smartcycling.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.honglian.smartcycling.core.ThemeMode
import com.honglian.smartcycling.core.UnitSystem

/**
 * 当前生效的单位制。
 *
 * 与色板同理:单位是**全局显示偏好**,却被 HUD、成绩页、历史页等大量叶子组件读取。
 * 逐层透传参数会污染十几个函数签名,因此同样用 CompositionLocal 下发。
 */
val LocalUnitSystem = staticCompositionLocalOf { UnitSystem.METRIC }

/**
 * 应用主题入口。
 *
 * 与旧实现的区别:
 *  - 不再把颜色写成 `BrandCyan` 之类的"物理色",而是通过 [AppPalette] 暴露**语义色**;
 *  - 同时提供亮色与暗色两套完整色板,并支持"跟随系统 / 强制亮 / 强制暗"三态;
 *  - 统一注入排版与形状,避免各页面自行硬编码字号圆角。
 */
@Composable
fun SmartCyclingTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    unitSystem: UnitSystem = UnitSystem.METRIC,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = paletteFor(dark)
    val colorScheme = remember(palette) { palette.toColorScheme() }

    CompositionLocalProvider(
        LocalAppPalette provides palette,
        LocalUnitSystem provides unitSystem,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** 主题访问入口:`AppTheme.palette.textPrimary` / `AppTheme.units`。 */
object AppTheme {
    val palette: AppPalette
        @Composable @ReadOnlyComposable get() = LocalAppPalette.current

    /** 当前单位制。配合 [com.honglian.smartcycling.core.Units] 做显示层换算。 */
    val units: UnitSystem
        @Composable @ReadOnlyComposable get() = LocalUnitSystem.current
}

private fun AppPalette.toColorScheme() = if (isDark) {
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = textSecondary,
        onSecondary = background,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = textSecondary,
        surfaceContainer = surfaceVariant,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerLow = surface,
        outline = outline,
        outlineVariant = outlineStrong,
        error = danger,
        onError = Color.White,
        errorContainer = Color(0xFF4A1D1F),
        onErrorContainer = Color(0xFFFFDAD8),
        scrim = scrim,
    )
} else {
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = textSecondary,
        onSecondary = Color.White,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = textSecondary,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerLow = background,
        outline = outline,
        outlineVariant = outlineStrong,
        error = danger,
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD8),
        onErrorContainer = Color(0xFF410002),
        scrim = scrim,
    )
}

private val AppTypography = Typography(
    displayLarge = AppType.display,
    displayMedium = AppType.display,
    headlineLarge = AppType.title,
    headlineMedium = AppType.title,
    headlineSmall = AppType.subtitle,
    titleLarge = AppType.title,
    titleMedium = AppType.subtitle,
    titleSmall = AppType.label,
    bodyLarge = AppType.body,
    bodyMedium = AppType.body,
    bodySmall = AppType.caption,
    labelLarge = AppType.label,
    labelMedium = AppType.label,
    labelSmall = AppType.caption,
)
