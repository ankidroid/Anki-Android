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
package com.ichi2.libanki

import android.content.Context
import com.ichi2.async.CollectionTask
import com.ichi2.libanki.backend.RustConfigBackend
import com.ichi2.libanki.backend.RustDroidDeckBackend
import com.ichi2.libanki.backend.RustDroidV16Backend
import com.ichi2.libanki.backend.RustTagsBackend
import com.ichi2.libanki.backend.model.toProtoBuf
import com.ichi2.libanki.exception.InvalidSearchException
import com.ichi2.libanki.utils.Time
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendInvalidInputException

class CollectionV16(
    context: Context,
    db: DB,
    path: String?,
    server: Boolean,
    log: Boolean,
    time: Time,
    backend: RustDroidV16Backend
) : Collection(context, db, path, server, log, time, backend) {

    /** Workaround as we shouldn't be overriding members which are used in the constructor */
    override fun getBackend(): RustDroidV16Backend {
        return super.getBackend() as RustDroidV16Backend
    }

    override fun initTags(): TagManager {
        return TagsV16(this, RustTagsBackend(backend.backend))
    }

    override fun initDecks(deckConf: String?): DeckManager {
        return DecksV16(this, RustDroidDeckBackend(backend.backend))
    }

    override fun initModels(): ModelManager {
        return ModelsV16(this, backend.backend)
    }

    override fun initConf(conf: String): ConfigManager {
        return ConfigV16(RustConfigBackend(backend.backend))
    }

    /** col.conf is now unused, handled by [ConfigV16] which has a separate table */
    override fun flushConf(): Boolean = false

    @RustCleanup("Remove this once syncing is in the backend")
    override fun onCreate() {
        super.onCreate()
        // set USN to -1, as was previously done in AnkiDroid.
        // This shouldn't cause issues at 0, as it will either be the first sync, or a full sync.
        // but it's useful to match 100% for regression tests

        // we reverse so "Basic" is last and conf."curModel" is correct
        val all = models.all().reversed()
        for (m in all) {
            models.save(m) // equivalent to m.put("usn", -1)
        }
    }

    override fun render_output(c: Card, reload: Boolean, browser: Boolean): TemplateManager.TemplateRenderContext.TemplateRenderOutput {
        return TemplateManager.TemplateRenderContext.from_existing_card(c, browser).render()
    }

    override fun findCards(search: String?, order: SortOrder, task: CollectionTask.PartialSearch?): MutableList<Long> {
        val result = try {
            backend.backend.searchCards(search, order.toProtoBuf())
        } catch (e: BackendInvalidInputException) {
            throw InvalidSearchException(e)
        }

        val cardIdsList = result.cardIdsList

        task?.doProgress(cardIdsList)
        return cardIdsList
    }
}
