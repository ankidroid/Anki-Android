/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <https://apps.ankiweb.net>                      *
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

import timber.log.Timber

fun DeckPicker.handleDatabaseCheck() {
    val col = CollectionHelper.getInstance().getCol(baseContext).newBackend
    val deckPicker = this
    catchingLifecycleScope(this) {
        val problems = runInBackgroundWithProgress(col, {
            if (it.hasDatabaseCheck()) {
                // TODO: show progress in GUI
                it.databaseCheck.run {
                    if (stageTotal > 0) {
                        Timber.i("$stage: $stageCurrent/$stageTotal")
                    } else {
                        Timber.i("$stage")
                    }
                }
            }
        }) {
            col.fixIntegrity()
        }
        val message = if (problems.isNotEmpty()) {
            problems.joinToString("\n")
        } else {
            col.tr.databaseCheckRebuilt()
        }
        deckPicker.showSimpleMessageDialog(message)
    }
}
