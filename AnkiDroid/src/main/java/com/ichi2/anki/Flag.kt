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
import com.ichi2.libanki.Card
import com.ichi2.libanki.CardId
import com.ichi2.libanki.Collection

enum class Flag(val code: Int, @DrawableRes val drawableRes: Int, @ColorRes val browserColorRes: Int?) {
    NONE(0, R.drawable.ic_flag_transparent, null),
    RED(1, R.drawable.ic_flag_red, R.color.flag_red),
    ORANGE(2, R.drawable.ic_flag_orange, R.color.flag_orange),
    GREEN(3, R.drawable.ic_flag_green, R.color.flag_green),
    BLUE(4, R.drawable.ic_flag_blue, R.color.flag_blue),
    PINK(5, R.drawable.ic_flag_pink, R.color.flag_pink),
    TURQUOISE(6, R.drawable.ic_flag_turquoise, R.color.flag_turquoise),
    PURPLE(7, R.drawable.ic_flag_purple, R.color.flag_purple);

    companion object {
        fun fromCode(code: Int): Flag {
            return entries.first { it.code == code }
        }
    }
}
fun Collection.setUserFlag(flag: Flag, cids: List<CardId>) = this.setUserFlag(flag.code, cids)
fun Card.setUserFlag(flag: Flag) = this.setUserFlag(flag.code)
