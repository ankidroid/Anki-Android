/****************************************************************************************
 * Copyright (c) 2022 lukstbit <52494258+lukstbit@users.noreply.github.com>             *
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
package com.ichi2.anki.notetype

import anki.notetypes.NotetypeNameId
import anki.notetypes.NotetypeNameIdUseCount

/**
 * Data holder class which contains the data to display a single note type in [ManageNotetypes]'s
 * list of notetypes.
 */
internal data class NoteTypeUiModel(
    val id: Long,
    val name: String,
    val useCount: Int,
)

internal fun NotetypeNameIdUseCount.toUiModel(): NoteTypeUiModel = NoteTypeUiModel(id, name, useCount)

/**
 * Simplest data holder class which contains only the id and name of a notetype.
 */
internal data class NotetypeBasicUiModel(
    val id: Long,
    val name: String,
    val isStandard: Boolean = false,
)

internal fun NotetypeNameId.toUiModel(isStandard: Boolean = false): NotetypeBasicUiModel = NotetypeBasicUiModel(id, name, isStandard)
