/*
 *  Copyright (c) 2024 Ben Wicks <benjaminlwicks@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.api

/*
 * [value] should be kept in sync with the [com.ichi2.anki.Ease] enum.
 *
 * param value The so called value of the button. For the sake of consistency with upstream and our API
 * the buttons are numbered from 1 to 4.
 */
public enum class Ease(public val value: Int) {
    EASE_1(1),
    EASE_2(2),
    EASE_3(3),
    EASE_4(4);
}
