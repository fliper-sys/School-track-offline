package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = PremiumPrimaryDark,
    onPrimary = PremiumOnPrimaryDark,
    primaryContainer = PremiumPrimaryDark,
    onPrimaryContainer = PremiumOnPrimaryDark,
    secondary = PremiumSecondaryDark,
    onSecondary = PremiumOnSecondaryDark,
    secondaryContainer = PremiumSecondaryContainerDark,
    onSecondaryContainer = PremiumOnSecondaryContainerDark,
    background = PremiumBackgroundDark,
    onBackground = PremiumOnBackgroundDark,
    surface = PremiumSurfaceDark,
    onSurface = PremiumOnSurfaceDark,
    surfaceVariant = PremiumSurfaceVariantDark,
    onSurfaceVariant = PremiumOnSurfaceVariantDark,
    outline = PremiumOutlineDark,
    outlineVariant = PremiumOutlineDark,
    tertiary = PremiumTertiaryDark,
    onTertiary = PremiumOnTertiaryDark,
    tertiaryContainer = PremiumTertiaryContainerDark,
    onTertiaryContainer = PremiumOnTertiaryContainerDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PremiumPrimaryLight,
    onPrimary = PremiumOnPrimaryLight,
    primaryContainer = PremiumPrimaryLight,
    onPrimaryContainer = PremiumOnPrimaryLight,
    secondary = PremiumSecondaryLight,
    onSecondary = PremiumOnSecondaryLight,
    secondaryContainer = PremiumSecondaryContainerLight,
    onSecondaryContainer = PremiumOnSecondaryContainerLight,
    background = PremiumBackgroundLight,
    onBackground = PremiumOnBackgroundLight,
    surface = PremiumSurfaceLight,
    onSurface = PremiumOnSurfaceLight,
    surfaceVariant = PremiumSurfaceVariantLight,
    onSurfaceVariant = PremiumOnSurfaceVariantLight,
    outline = PremiumOutlineLight,
    outlineVariant = PremiumOutlineLight,
    tertiary = PremiumTertiaryLight,
    onTertiary = PremiumOnTertiaryLight,
    tertiaryContainer = PremiumTertiaryContainerLight,
    onTertiaryContainer = PremiumOnTertiaryContainerLight
  )

private val AshColorScheme =
  darkColorScheme(
    primary = AshPrimary,
    onPrimary = AshOnPrimary,
    primaryContainer = AshPrimaryContainer,
    onPrimaryContainer = AshOnPrimaryContainer,
    secondary = AshSecondary,
    onSecondary = AshOnSecondary,
    secondaryContainer = AshSecondaryContainer,
    onSecondaryContainer = AshOnSecondaryContainer,
    background = AshBackground,
    onBackground = AshOnBackground,
    surface = AshSurface,
    onSurface = AshOnSurface,
    surfaceVariant = AshSurfaceVariant,
    onSurfaceVariant = AshOnSurfaceVariant,
    outline = AshOutline,
    outlineVariant = AshOutline,
    tertiary = AshTertiary,
    onTertiary = AshOnTertiary,
    tertiaryContainer = AshTertiaryContainer,
    onTertiaryContainer = AshOnTertiaryContainer
  )

@Composable
fun MyApplicationTheme(
  themeMode: String = "light",
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic colors by default so Natural Tones takes preference
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      themeMode == "ash" -> AshColorScheme
      themeMode == "dark" || (themeMode == "light" && darkTheme) -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
