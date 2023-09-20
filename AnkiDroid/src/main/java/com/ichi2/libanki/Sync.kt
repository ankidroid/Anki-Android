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

package com.ichi2.libanki

import anki.sync.SyncAuth
import anki.sync.SyncCollectionResponse
import anki.sync.fullUploadOrDownloadRequest
import anki.sync.syncLoginRequest

fun Collection.syncLogin(username: String, password: String, endpoint: String?): SyncAuth {
    val req = syncLoginRequest {
        this.username = username
        this.password = password
        // default endpoint used here, if it is null
        if (endpoint != null) {
            this.endpoint = endpoint
        }
    }
    return backend.syncLogin(req)
}

fun Collection.syncCollection(auth: SyncAuth, media: Boolean): SyncCollectionResponse {
    return backend.syncCollection(auth = auth, syncMedia = media)
}

fun Collection.fullUploadOrDownload(auth: SyncAuth, upload: Boolean, serverUsn: Int?) {
    return backend.fullUploadOrDownload(
        fullUploadOrDownloadRequest {
            this.auth = auth
            if (serverUsn != null) {
                this.serverUsn = serverUsn
            }
            this.upload = upload
        }
    )
}
