/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.libanki.backend

import BackendProto.Backend
import android.content.Context
import com.ichi2.libanki.Collection
import com.ichi2.libanki.CollectionV16
import com.ichi2.libanki.DB
import com.ichi2.libanki.TemplateManager
import com.ichi2.libanki.backend.BackendUtils.to_json_bytes
import com.ichi2.libanki.backend.model.to_backend_note
import com.ichi2.libanki.utils.Time
import com.ichi2.utils.JSONObject
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.BackendV1
import net.ankiweb.rsdroid.database.RustVNextSQLiteOpenHelperFactory

/**
 * Requires [com.ichi2.anki.AnkiDroidApp.TESTING_SCOPED_STORAGE]
 *
 * Signifies that the AnkiDroid backend should be used when accessing the JSON columns in `col`
 * as these have moved to separate tables
 */
class RustDroidV16Backend(private val backendFactory: BackendFactory) : RustDroidBackend(backendFactory) {
    val backend: BackendV1
        get() = backendFactory.backend

    override fun databaseCreationInitializesData(): Boolean = true

    override fun createCollection(context: Context, db: DB, path: String?, server: Boolean, log: Boolean, time: Time): Collection =
        CollectionV16(context, db, path, server, log, time, this)

    override fun openCollectionDatabase(path: String?): DB {
        // This Helper factory updates the database schema on open
        return DB(path) { RustVNextSQLiteOpenHelperFactory(backendFactory) }
    }

    override fun closeCollection(db: DB?, downgradeToSchema11: Boolean) {
        backend.closeCollection(downgradeToSchema11)
        super.closeCollection(db, downgradeToSchema11)
    }

    override fun extract_av_tags(text: String, question_side: Boolean): Backend.ExtractAVTagsOut {
        return backend.extractAVTags(text, question_side)
    }

    override fun renderCardForTemplateManager(context: TemplateManager.TemplateRenderContext): Backend.RenderCardOut {
        return if (context._template != null) {
            // card layout screen
            backend.renderUncommittedCard(
                context._note.to_backend_note(),
                context._card.ord,
                to_json_bytes(JSONObject(context._template!!)),
                context._fill_empty,
            )
        } else {
            // existing card (eg study mode)
            backend.renderExistingCard(context._card.id, context._browser)
        }
    }
}
