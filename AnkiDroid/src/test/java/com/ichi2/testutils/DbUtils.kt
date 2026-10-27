// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.content.Context
import com.ichi2.anki.backend.createDatabaseUsingAndroidFramework
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.libanki.DB

object DbUtils {
    /** performs a query on an unopened collection  */
    fun performQuery(
        context: Context,
        query: String,
    ) {
        var db: DB? = null
        try {
            db = createDatabaseUsingAndroidFramework(context, CollectionHelper.getCollectionPath(context))
            db.executeScript(query)
        } finally {
            db?.close()
        }
    }
}
