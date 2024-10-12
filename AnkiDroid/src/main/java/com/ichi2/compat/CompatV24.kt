/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.compat

import android.annotation.TargetApi
import android.app.Activity
import android.icu.util.ULocale
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.MotionEvent
import android.view.View
import androidx.annotation.StringRes
import androidx.core.view.OnReceiveContentListener
import androidx.draganddrop.DropHelper
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.AnkiActivityProvider
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.isRobolectric
import net.ankiweb.rsdroid.Translations
import timber.log.Timber
import java.util.Locale

/** Implementation of [Compat] for SDK level 24 and higher. Check [Compat]'s for more detail.  */
@TargetApi(24)
open class CompatV24 : CompatV23() {
    override fun normalize(locale: Locale): Locale {
        // ULocale isn't currently handled by Robolectric
        if (isRobolectric) {
            return super.normalize(locale)
        }
        return try {
            val uLocale = ULocale(locale.language, locale.country, locale.variant)
            Locale(uLocale.language, uLocale.country, uLocale.variant)
        } catch (e: Exception) {
            Timber.w("Failed to normalize locale %s", locale, e)
            locale
        }
    }

    override fun configureView(
        activity: Activity,
        view: View,
        mimeTypes: Array<String>,
        options: DropHelper.Options,
        onReceiveContentListener: OnReceiveContentListener
    ) {
        DropHelper.configureView(
            activity,
            view,
            mimeTypes,
            options,
            onReceiveContentListener
        )
    }

    override fun showKeyboardShortcutsDialog(activity: AnkiActivity) {
        val shortcutsGroup = getShortcuts(activity)
        // Don't show keyboard shortcuts dialog if there is no available shortcuts and also
        // if there's 1 item because shortcutsGroup always includes generalShortcutGroup.
        if (shortcutsGroup.size <= 1) return
        Timber.i("displaying keyboard shortcut screen")
        activity.requestShowKeyboardShortcuts()
    }

    override fun getShortcuts(activity: AnkiActivity): List<KeyboardShortcutGroup> {
        val generalShortcutGroup = ShortcutGroup(
            listOf(
                activity.shortcut("Alt+K", R.string.show_keyboard_shortcuts_dialog),
                activity.shortcut("Ctrl+Z", R.string.undo)
            ),
            R.string.pref_cat_general
        ).toShortcutGroup(activity)

        return listOfNotNull(activity.shortcuts?.toShortcutGroup(activity), generalShortcutGroup)
    }

    /**
     * Data class representing a keyboard shortcut.
     *
     * @param shortcut The string representation of the keyboard shortcut (e.g., "Ctrl+Alt+S").
     * @param label The string resource for the shortcut label.
     */
    data class Shortcut(val shortcut: String, val label: String) {

        /**
         * Converts the shortcut string into a KeyboardShortcutInfo object.
         *
         * @param context The context used to retrieve the string label resource.
         * @return A KeyboardShortcutInfo object representing the keyboard shortcut.
         */
        fun toShortcutInfo(): KeyboardShortcutInfo {
            val parts = shortcut.split("+")
            val key = parts.last()
            val keycode: Int = getKey(key)
            val modifierFlags: Int = parts.dropLast(1).sumOf { getModifier(it) }

            return KeyboardShortcutInfo(label, keycode, modifierFlags)
        }

        /**
         * Maps a modifier string to its corresponding KeyEvent meta flag.
         *
         * @param modifier The modifier string (e.g., "Ctrl", "Alt", "Shift").
         * @return The corresponding KeyEvent meta flag.
         */
        private fun getModifier(modifier: String): Int {
            return when (modifier) {
                "Ctrl" -> KeyEvent.META_CTRL_ON
                "Alt" -> KeyEvent.META_ALT_ON
                "Shift" -> KeyEvent.META_SHIFT_ON

                else -> 0
            }
        }

        /**
         * Maps a key string to its corresponding keycode.
         *
         * @param key The key string.
         * @return The corresponding keycode, or 0 if the key string is invalid or not recognized.
         */
        private fun getKey(key: String): Int {
            return when (key) {
                "/" -> KeyEvent.KEYCODE_SLASH
                "Esc" -> KeyEvent.KEYCODE_ESCAPE
                in "0".."9" -> KeyEvent.KEYCODE_0 + (key.toInt() - 0) // Handle number keys
                else -> KeyEvent.keyCodeFromString(key)
            }
        }
    }

    data class ShortcutGroup(val shortcuts: List<Shortcut>, @StringRes val id: Int) {
        fun toShortcutGroup(activity: AnkiActivity): KeyboardShortcutGroup {
            val shortcuts = shortcuts.map { it.toShortcutInfo() }
            val groupLabel = activity.getString(id)
            return KeyboardShortcutGroup(groupLabel, shortcuts)
        }
    }

    override val AXIS_RELATIVE_X: Int = MotionEvent.AXIS_RELATIVE_X
    override val AXIS_RELATIVE_Y: Int = MotionEvent.AXIS_RELATIVE_Y
}

interface ShortcutGroupProvider {
    /**
     * Lists of shortcuts for this fragment, and the IdRes of the name of this shortcut group.
     */
    val shortcuts: CompatV24.ShortcutGroup?
}

/**
 * Provides a [CompatV24.Shortcut], from the shortcut keys and the resource id of its description.
 */
fun AnkiActivityProvider.shortcut(shortcut: String, @StringRes labelRes: Int) =
    CompatV24.Shortcut(shortcut, ankiActivity.getString(labelRes))

/**
 * Provides a [CompatV24.Shortcut], from the shortcut keys and the function from anki strings.
 */
fun shortcut(shortcut: String, getTranslation: Translations.() -> String) =
    CompatV24.Shortcut(shortcut, getTranslation(TR))
