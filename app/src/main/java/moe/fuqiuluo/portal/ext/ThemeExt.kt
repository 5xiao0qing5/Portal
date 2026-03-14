package moe.fuqiuluo.portal.ext

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat

fun Context.resolveThemeColor(@AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrRes, typedValue, true)
    return if (typedValue.resourceId != 0) {
        ContextCompat.getColor(this, typedValue.resourceId)
    } else {
        typedValue.data
    }
}
