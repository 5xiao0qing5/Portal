package moe.fuqiuluo.portal.ui.theme

import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import moe.fuqiuluo.portal.R

enum class ThemePreset(
    val key: String,
    @StringRes val titleRes: Int,
    @StyleRes val styleRes: Int,
    val isLightTheme: Boolean,
) {
    MinimalistBusiness(
        key = "minimalist_business",
        titleRes = R.string.theme_minimalist_business,
        styleRes = R.style.AppTheme_MinimalistBusiness,
        isLightTheme = true,
    ),
    ModernDark(
        key = "modern_dark",
        titleRes = R.string.theme_modern_dark,
        styleRes = R.style.AppTheme_ModernDark,
        isLightTheme = false,
    ),
    FuturisticVibrant(
        key = "futuristic_vibrant",
        titleRes = R.string.theme_futuristic_vibrant,
        styleRes = R.style.AppTheme_FuturisticVibrant,
        isLightTheme = false,
    ),
    SoftNature(
        key = "soft_nature",
        titleRes = R.string.theme_soft_nature,
        styleRes = R.style.AppTheme_SoftNature,
        isLightTheme = true,
    );

    companion object {
        val default = MinimalistBusiness

        fun fromKey(key: String?): ThemePreset {
            return entries.firstOrNull { it.key == key } ?: default
        }
    }
}
