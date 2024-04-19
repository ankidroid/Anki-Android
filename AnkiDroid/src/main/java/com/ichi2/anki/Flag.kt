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

import android.content.res.Resources
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.utils.ext.getStringOrNull
import com.ichi2.libanki.Card
import com.ichi2.libanki.CardId
import com.ichi2.libanki.Collection
import org.json.JSONObject

enum class Flag(
    val code: Int,
    @DrawableRes val drawableRes: Int,
    @ColorRes val browserColorRes: Int?,
    private val defaultNameRes: Int
) {
    NONE(0, R.drawable.ic_flag_transparent, null, R.string.menu_flag_card_zero),
    RED(1, R.drawable.ic_flag_red, R.color.flag_red, R.string.menu_flag_card_one),
    ORANGE(2, R.drawable.ic_flag_orange, R.color.flag_orange, R.string.menu_flag_card_two),
    GREEN(3, R.drawable.ic_flag_green, R.color.flag_green, R.string.menu_flag_card_three),
    BLUE(4, R.drawable.ic_flag_blue, R.color.flag_blue, R.string.menu_flag_card_four),
    PINK(5, R.drawable.ic_flag_pink, R.color.flag_pink, R.string.menu_flag_card_five),
    TURQUOISE(6, R.drawable.ic_flag_turquoise, R.color.flag_turquoise, R.string.menu_flag_card_six),
    PURPLE(7, R.drawable.ic_flag_purple, R.color.flag_purple, R.string.menu_flag_card_seven);

    /**
     * Retrieves the name associated with the flag.
     * If an override for the flag name is provided in the configuration, it is fetched; otherwise,
     * the default name resource ID is used to fetch the name from the application resources.
     *
     * @param resources The Resources object used to access application resources.
     * @return The name associated with the flag, either fetched from overrides or default resources.
     */
    suspend fun getName(resources: Resources): String {
        val overrides = withCol { config.getObject("flagLabels", JSONObject()) }
        return overrides.getStringOrNull(code.toString()) ?: resources.getString(defaultNameRes)
    }

    companion object {
        fun fromCode(code: Int): Flag {
            return entries.first { it.code == code }
        }
    }
}
fun Collection.setUserFlag(flag: Flag, cids: List<CardId>) = this.setUserFlag(flag.code, cids)
fun Card.setUserFlag(flag: Flag) = this.setUserFlag(flag.code)
