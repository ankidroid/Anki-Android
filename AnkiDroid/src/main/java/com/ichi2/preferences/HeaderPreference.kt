// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.preferences

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.ThemeUtils
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.view.ViewCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.ichi2.anki.CommonString
import com.ichi2.anki.LanguageUtils
import com.ichi2.anki.R
import com.ichi2.ui.BadgeDrawable
import com.ichi2.anki.common.android.R as CommonR

/**
 * Preference used on the headers of [com.ichi2.anki.preferences.HeaderFragment]
 */
class HeaderPreference : Preference {
    private var isHighlighted = false

    /**
     * While this returns `true`, a dot is drawn at the top-right of the icon to draw the user's
     * attention to this header: for example, to point them towards a new feature.
     * Re-checked by [refreshBadge].
     */
    var showBadgeWhen: (() -> Boolean)? = null
        set(value) {
            field = value
            refreshBadge()
        }

    /** Whether the badge is drawn, see [showBadgeWhen] */
    var isBadged = false
        private set(value) {
            if (field == value) return
            field = value
            val unbadgedIcon = (icon as? BadgeDrawable)?.drawable ?: icon
            icon = if (value) unbadgedIcon?.let(::withBadge) else unbadgedIcon
        }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, androidx.preference.R.attr.preferenceStyle)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : this(context, attrs, defStyleAttr, androidx.preference.R.style.Preference)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        context.withStyledAttributes(attrs, R.styleable.HeaderPreference) {
            val entries = getTextArray(R.styleable.HeaderPreference_summaryEntries)
            if (entries != null) {
                summary = buildHeaderSummary(*entries)
            }
        }
    }

    /** Shows or hides the badge, depending on [showBadgeWhen] */
    fun refreshBadge() {
        isBadged = showBadgeWhen?.invoke() == true
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        if (isHighlighted) {
            val color = ThemeUtils.getThemeAttrColor(context, CommonR.attr.currentDeckBackgroundColor)
            holder.itemView.setBackgroundColor(color)
        }
        // only announce a badge which is drawn
        val stateDescription = if (icon is BadgeDrawable) context.getString(CommonString.settings_header_badge_state) else null
        // views are recycled, so a description from a previous binding must be cleared
        ViewCompat.setStateDescription(holder.itemView, stateDescription)
    }

    private fun withBadge(icon: Drawable): Drawable {
        val dot = requireNotNull(ContextCompat.getDrawable(context, R.drawable.badge_drawable)).mutate()
        dot.setTint(ThemeUtils.getThemeAttrColor(context, CommonR.attr.badgeWarningColor))
        return BadgeDrawable(icon).apply { setBadgeDrawable(dot) }
    }

    fun setHighlighted(highlight: Boolean) {
        isHighlighted = highlight
        notifyChanged()
    }

    companion object {
        /**
         * Join [entries] with ` • ` as separator
         * to build a summary string for some preferences categories
         * e.g. `foo`, `bar`, `hi` ->  `foo • bar • hi`
         */
        fun buildHeaderSummary(vararg entries: CharSequence): String =
            if (!LanguageUtils.appLanguageIsRTL()) {
                entries.joinToString(separator = " • ")
            } else {
                entries.reversed().joinToString(separator = " • ")
            }
    }
}
