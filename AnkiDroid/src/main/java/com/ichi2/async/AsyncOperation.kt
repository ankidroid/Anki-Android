/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.async

import com.ichi2.anki.Channel
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.dialogs.DialogHandlerMessage

abstract class AsyncOperation {
    abstract val notificationMessage: String?
    abstract val notificationTitle: String
    abstract val handlerMessage: DialogHandlerMessage
}

fun DeckPicker.sendNotificationForAsyncOperation(operation: AsyncOperation, channel: Channel) {
    // Store a persistent message instructing AnkiDroid to perform the operation
    DialogHandler.storeMessage(operation.handlerMessage.toMessage())
    // Show a basic notification to the user in the notification bar in the meantime
    val title = operation.notificationTitle
    val message = operation.notificationMessage
    showSimpleNotification(title, message, channel)
}

@Suppress("unused")
fun DeckPicker.performAsyncOperation(
    operation: AsyncOperation,
    channel: Channel,
) {
    if (mActivityPaused) {
        sendNotificationForAsyncOperation(operation, channel)
        return
    }

    operation.handlerMessage.handleAsyncMessage(this)
}
