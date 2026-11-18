/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.indusy55.featuremanager.ui.theme

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
import io.github.indusy55.featuremanager.data.AccentStyle
import io.github.indusy55.featuremanager.data.AppearanceSettings
import io.github.indusy55.featuremanager.data.ThemeMode

private data class AccentPalette(
    val lightPrimary: Color,
    val lightContainer: Color,
    val darkPrimary: Color,
    val darkContainer: Color
)

private val ACCENTS: Map<AccentStyle, AccentPalette> = mapOf(
    AccentStyle.INDIGO to AccentPalette(
        Color(0xFF435E97), Color(0xFFD9E2FF),
        Color(0xFFAFC2F5), Color(0xFF2B3B62)
    ),
    AccentStyle.TEAL to AccentPalette(
        Color(0xFF006B63), Color(0xFFB1F0E4),
        Color(0xFF76D9C9), Color(0xFF005047)
    ),
    AccentStyle.AMBER to AccentPalette(
        Color(0xFF8A5A00), Color(0xFFFFDFA2),
        Color(0xFFFFCB6B), Color(0xFF634100)
    ),
    AccentStyle.CRIMSON to AccentPalette(
        Color(0xFF9C2542), Color(0xFFFFD3DC),
        Color(0xFFFFA9B9), Color(0xFF70132C)
    ),
    AccentStyle.VIOLET to AccentPalette(
        Color(0xFF6740A0), Color(0xFFE8DDFF),
        Color(0xFFCFB6FF), Color(0xFF4B2D78)
    )
)

@Composable
fun FeatureManagerTheme(
    settings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit
) {
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val palette = ACCENTS[settings.accentStyle] ?: ACCENTS.getValue(AccentStyle.INDIGO)

    val baseScheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColorScheme(
            primary = palette.darkPrimary,
            onPrimary = Color(0xFF101622),
            primaryContainer = palette.darkContainer,
            onPrimaryContainer = Color(0xFFE3EAFF),
            secondary = palette.darkPrimary,
            secondaryContainer = palette.darkContainer
        )

        else -> lightColorScheme(
            primary = palette.lightPrimary,
            onPrimary = Color.White,
            primaryContainer = palette.lightContainer,
            onPrimaryContainer = Color(0xFF101A33),
            secondary = palette.lightPrimary,
            secondaryContainer = palette.lightContainer
        )
    }

    val colorScheme = if (darkTheme && settings.pureBlackDark) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color(0xFF08090B),
            surfaceVariant = Color(0xFF16181D),
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF1A1C20),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0B0D),
            surfaceContainer = Color(0xFF0F1115),
            surfaceContainerHigh = Color(0xFF14161A),
            surfaceContainerHighest = Color(0xFF1A1C20)
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
