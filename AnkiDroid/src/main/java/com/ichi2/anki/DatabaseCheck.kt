// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Ankitects Pty Ltd <https://apps.ankiweb.net>

package com.ichi2.anki

import anki.collection.Progress
import com.ichi2.anki.CollectionManager.TR

fun DeckPicker.handleDatabaseCheck() {
    fun Progress.DatabaseCheck.toAmount() =
        if (stageTotal > 0) {
            ProgressContext.Amount(stageCurrent.toLong(), stageTotal.toLong())
        } else {
            null
        }

    val check = viewModel.checkDatabase()
    launchCatchingTask {
        viewModel.holdErrorsDuring {
            val problems =
                withProgress(
                    extractProgress = {
                        if (progress.hasDatabaseCheck()) {
                            progress.databaseCheck.let {
                                text = it.stage
                                amount = it.toAmount()
                            }
                        }
                    },
                    onCancel = null,
                ) {
                    check.await()
                } ?: return@holdErrorsDuring
            val message =
                if (problems.isNotEmpty()) {
                    problems.joinToString("\n")
                } else {
                    TR.databaseCheckRebuilt()
                }
            showSimpleMessageDialog(message)
        }
    }
}
