package com.ichi2.utils

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes

object ColorUtil {
    fun getThemeColor(context: Context, @AttrRes themeAttributeId: Int): Int {
        val outValue = TypedValue()
        context.theme.resolveAttribute(themeAttributeId, outValue, true)
        return outValue.data
    }
}