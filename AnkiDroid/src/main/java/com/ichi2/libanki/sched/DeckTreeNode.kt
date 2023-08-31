/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
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
package com.ichi2.libanki.sched

import com.ichi2.libanki.Collection
import com.ichi2.libanki.DeckId
import net.ankiweb.rsdroid.RustCleanup

@RustCleanup("processChildren() can be removed after migrating to backend implementation")
class DeckTreeNode(name: String, did: DeckId) : AbstractDeckTreeNode(name, did) {
    override fun processChildren(col: Collection, children: List<AbstractDeckTreeNode>, addRev: Boolean) {
        // intentionally blank
    }
}
