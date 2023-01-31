/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>                       *
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

package com.ichi2.libanki.sync

import anki.sync.SyncAuth
import anki.sync.SyncCollectionResponse
import anki.sync.SyncStatusResponse
import anki.sync.syncLoginRequest
import com.ichi2.libanki.CollectionV16

fun CollectionV16.syncLogin(username: String, password: String): SyncAuth {
    val req = syncLoginRequest {
        this.username = username
        this.password = password
    }
    return backend.syncLogin(req)
}

fun CollectionV16.syncCollection(auth: SyncAuth): SyncCollectionResponse {
    return backend.syncCollection(input = auth)
}

fun CollectionV16.fullUpload(auth: SyncAuth) {
    return backend.fullUpload(input = auth)
}

fun CollectionV16.fullDownload(auth: SyncAuth) {
    return backend.fullDownload(input = auth)
}

fun CollectionV16.syncMedia(auth: SyncAuth) {
    return backend.syncMedia(input = auth)
}

fun CollectionV16.syncStatus(auth: SyncAuth): SyncStatusResponse {
    return backend.syncStatus(input = auth)
}
