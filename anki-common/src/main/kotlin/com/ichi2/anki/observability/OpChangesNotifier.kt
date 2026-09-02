// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>

package com.ichi2.anki.observability

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Collection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps a routine that returns OpChanges* or similar undo info,
 * notifying [ChangeManager] subscribers of the changes.
 *
 * **[block] must return an OpChanges subtype as its last expression.**
 * [T] is inferred from [block]'s return type. If the last expression returns `Unit`
 * (e.g. a `Timber` call), [ChangeManager.notifySubscribers] throws
 * "unhandled change type of class 'class kotlin.Unit'" at runtime.
 */
suspend fun <T : Any> undoableOp(
    handler: Any? = null,
    block: Collection.() -> T,
): T =
    withCol {
        block()
    }.also {
        withContext(Dispatchers.Main) {
            ChangeManager.notifySubscribers(it, handler)
        }
    }
