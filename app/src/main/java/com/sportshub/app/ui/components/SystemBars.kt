package com.sportshub.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.sportshub.app.ui.theme.SportsHubTheme

/**
 * Sets the status bar icons for the screen on show: light icons over a dark, colored
 * header when [enabled], otherwise the theme's usual icons. Every screen states what it
 * wants when it appears, so nothing needs undoing when it leaves (the next screen's
 * call takes over, even while the old one is still animating out).
 */
@Composable
fun LightStatusBarIcons(enabled: Boolean) {
    val view = LocalView.current
    val darkTheme = SportsHubTheme.colors.isDark
    if (view.isInEditMode) return
    DisposableEffect(enabled, darkTheme, view) {
        val window = view.context.findActivity()?.window
        window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !enabled && !darkTheme }
        onDispose { }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
