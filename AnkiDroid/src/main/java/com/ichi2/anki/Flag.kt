/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.utils.ext.getStringOrNull
import org.json.JSONObject

enum class Flag(
    val code: Int,
    /**
     * A Unique ID representing this flag in a menu.
     */
    @IdRes val id: Int,
    /**
     * Flag drawn to represents this flag.
     */
    @DrawableRes val drawableRes: Int,
    /**
     * Color for the background of cards with this flag in the card browser.
     */
    @ColorRes val browserColorRes: Int?,
    /**
     * Flag drawn to represents this flagInTheReviewer if it differs from [drawableRes].
     * @TODO: Checks whether we can use colorControlNormal everywhere.
     */
    @DrawableRes val drawableReviewerRes: Int? = null
) {
    NONE(0, R.id.flag_none, R.drawable.ic_flag_lightgrey, null, R.drawable.ic_flag_transparent),
    RED(1, R.id.flag_red, R.drawable.ic_flag_red, R.color.flag_red),
    ORANGE(
        2,
        R.id.flag_orange,
        R.drawable.ic_flag_orange,
        R.color.flag_orange
    ),
    GREEN(3, R.id.flag_green, R.drawable.ic_flag_green, R.color.flag_green),
    BLUE(4, R.id.flag_blue, R.drawable.ic_flag_blue, R.color.flag_blue),
    PINK(5, R.id.flag_pink, R.drawable.ic_flag_pink, R.color.flag_pink),
    TURQUOISE(
        6,
        R.id.flag_turquoise,
        R.drawable.ic_flag_turquoise,
        R.color.flag_turquoise
    ),
    PURPLE(
        7,
        R.id.flag_purple,
        R.drawable.ic_flag_purple,
        R.color.flag_purple
    );

    /**
     * Flag drawn to represents this flagInTheReviewer.
     */
    @DrawableRes fun drawableReviewerRes() = drawableReviewerRes ?: drawableRes

    /**
     * Retrieves the name associated with the flag. This may be user-defined
     *
     * @see queryDisplayNames - more efficient
     */
    private fun displayName(labels: FlagLabels): String {
        // NONE may not be renamed
        if (this == NONE) return defaultDisplayName()
        return labels.getLabel(this) ?: defaultDisplayName()
    }

    private fun defaultDisplayName(): String = when (this) {
        NONE -> TR.browsingNoFlag()
        RED -> TR.actionsFlagRed()
        ORANGE -> TR.actionsFlagOrange()
        GREEN -> TR.actionsFlagGreen()
        BLUE -> TR.actionsFlagBlue()
        PINK -> TR.actionsFlagPink()
        TURQUOISE -> TR.actionsFlagTurquoise()
        PURPLE -> TR.actionsFlagPurple()
    }

    /**
     * Renames the flag
     *
     * @param newName The new name for the flag.
     */
    suspend fun rename(newName: String) {
        val labels = FlagLabels.loadFromColConfig()
        labels.updateName(this, newName)
    }

    companion object {
        fun fromCode(code: Int) = Flag.entries.first { it.code == code }

        /**
         * @return A mapping from each [Flag] to its display name (optionally user-defined)
         */
        suspend fun queryDisplayNames(): Map<Flag, String> {
            // load user-defined flag labels from the collection
            val labels = FlagLabels.loadFromColConfig()
            // either map to user-provided name, or translated name
            return Flag.entries.associateWith { it.displayName(labels) }
        }
    }
}

/**
 * User-defined labels for flags. Stored in the collection optionally as `{ "1": "Redd" }`
 * [Flag.NONE] does not have a label
 */
@JvmInline
private value class FlagLabels(val value: JSONObject) {
    /**
     * @return the user-defined label for the provided flag, or null if undefined
     * This is not supported for [Flag.NONE] and is validated outside this method
     */
    fun getLabel(flag: Flag): String? = value.getStringOrNull(flag.code.toString())
    suspend fun updateName(flag: Flag, newName: String) {
        value.put(flag.code.toString(), newName)
        withCol {
            config.set("flagLabels", value)
        }
    }

    companion object {
        suspend fun loadFromColConfig() =
            FlagLabels(withCol { config.getObject("flagLabels", JSONObject()) })
    }
}
