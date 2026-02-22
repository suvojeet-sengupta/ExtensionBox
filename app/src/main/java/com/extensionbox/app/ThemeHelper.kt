package com.extensionbox.app

import android.app.Activity

object ThemeHelper {
    const val MONET = 0
    const val GRUVBOX = 1
    const val CATPPUCCIN = 2
    const val NORD = 3
    const val AMOLED = 4
    const val SOLARIZED = 5
    const val DRACULA = 6

    val NAMES = arrayOf(
        "Material You (Monet)",
        "Gruvbox Dark",
        "Catppuccin Mocha",
        "Nord",
        "AMOLED",
        "Solarized Dark",
        "Dracula"
    )

    fun apply(activity: Activity) {
        val theme = Prefs.getInt(activity, "app_theme", MONET)
        val resId = when (theme) {
            GRUVBOX -> R.style.AppTheme_Gruvbox
            CATPPUCCIN -> R.style.AppTheme_Catppuccin
            NORD -> R.style.AppTheme_Nord
            AMOLED -> R.style.AppTheme_Amoled
            SOLARIZED -> R.style.AppTheme_Solarized
            DRACULA -> R.style.AppTheme_Dracula
            else -> R.style.AppTheme
        }
        activity.setTheme(resId)
    }
}
