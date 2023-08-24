/****************************************************************************************
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Bruno Romero de Azevedo <brunodea@inf.ufsm.br>                    *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki

import com.ichi2.anki.reviewer.CardMarker

class FlagToDisplay(
    private val actualValue: Int,
    private val isOnAppBar: Boolean,
    private val isFullscreen: Boolean
) {

    fun get(): Int {
        if (actualValue == CardMarker.FLAG_NONE) {
            return CardMarker.FLAG_NONE
        }
        return if (isOnAppBar && !isFullscreen) {
            CardMarker.FLAG_NONE
        } else {
            actualValue
        }
    }
}
