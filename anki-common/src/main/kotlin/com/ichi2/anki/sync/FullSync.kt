// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import com.ichi2.anki.libanki.Collection

fun Collection.fullUploadOrDownload(
    auth: SyncAuth,
    serverUsn: Int?,
    upload: Boolean,
) = fullUploadOrDownload(auth.toProto(), serverUsn, upload)
