package chat.schildi.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import chat.schildi.lib.preferences.ScPrefs
import chat.schildi.lib.preferences.scPrefs
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.theme.ForcedDarkElementTheme
import io.element.android.compound.tokens.generated.SemanticColors

object ScTheme {
    val exposures: ScThemeExposures
        @Composable
        @ReadOnlyComposable
        get() = LocalScExposures.current
}

// Element defaults to light compound colors, so follow that as fallback default for exposures as well
internal val LocalScExposures = staticCompositionLocalOf { elementLightScExposures }

fun getThemeExposures(darkTheme: Boolean, useScTheme: Boolean) = when {
    darkTheme && useScTheme -> scdExposures
    !darkTheme && useScTheme -> elementLightScExposures // TODO sclExposures
    darkTheme && !useScTheme -> elementDarkScExposures
    else -> elementLightScExposures
}

@Composable
fun ScTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    lightStatusBar: Boolean = !darkTheme,
    dynamicColor: Boolean = false, /* true to enable MaterialYou */
    useScTheme: Boolean = scPrefs().settingState(ScPrefs.SC_THEME).value,
    useElTypography: Boolean = scPrefs().settingState(ScPrefs.EL_TYPOGRAPHY).value,
    content: @Composable () -> Unit,
) {
    val compoundColors: SemanticColors
    val materialLightColors: ColorScheme
    val materialDarkColors: ColorScheme
    if (useScTheme) {
        compoundColors = if (darkTheme) scdSemanticColors else elColorsLight
        materialLightColors = elMaterialColorSchemeLight
        materialDarkColors = scdMaterialColorScheme
    } else {
        compoundColors = if (darkTheme) elColorsDark else elColorsLight
        materialLightColors = elMaterialColorSchemeLight
        materialDarkColors = elMaterialColorSchemeDark
    }
    val typography = if (useElTypography) elTypography else scTypography

    val currentExposures = remember {
        // EleLight is default
        elementLightScExposures.copy()
    }.apply { updateColorsFrom(getThemeExposures(darkTheme, useScTheme)) }

    CompositionLocalProvider(
        LocalScExposures provides currentExposures
    ) {
        ElementTheme(
            darkTheme = darkTheme,
            lightStatusBar = lightStatusBar,
            dynamicColor = dynamicColor,
            compoundColors = compoundColors,
            materialLightColors = materialLightColors,
            materialDarkColors = materialDarkColors,
            typography = typography,
            content = content,
        )
    }
}

/**
 * Can be used to force a composable in dark theme.
 * It will automatically change the system ui colors back to normal when leaving the composition.
 */
@Composable
fun ForcedDarkScTheme(
    lightStatusBar: Boolean = false,
    useScTheme: Boolean = scPrefs().settingState(ScPrefs.SC_THEME).value,
    content: @Composable () -> Unit,
) {
    val currentExposures = remember {
        // EleLight is default
        elementLightScExposures.copy()
    }.apply { updateColorsFrom(getThemeExposures(true, useScTheme)) }
    CompositionLocalProvider(
        LocalScExposures provides currentExposures
    ) {
        ForcedDarkElementTheme(
            lightStatusBar = lightStatusBar,
            content = content,
        )
    }
    /* TODO if !useScTheme do other stuffs
    val systemUiController = rememberSystemUiController()
    val colorScheme = MaterialTheme.colorScheme
    val wasDarkTheme = !ElementTheme.colors.isLight
    DisposableEffect(Unit) {
        onDispose {
            systemUiController.applyTheme(colorScheme, wasDarkTheme)
        }
    }
    ElementTheme(darkTheme = true, lightStatusBar = lightStatusBar, content = content)
     */
}