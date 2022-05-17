/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

import BackendProto.Backend.ExtractAVTagsOut
import BackendProto.Backend.RenderCardOut
import android.content.Context
import androidx.annotation.VisibleForTesting
import com.ichi2.libanki.Collection
import com.ichi2.libanki.DB
import com.ichi2.libanki.DeckConfig
import com.ichi2.libanki.Decks
import com.ichi2.libanki.TemplateManager.TemplateRenderContext
import com.ichi2.libanki.backend.exception.BackendNotSupportedException
import com.ichi2.libanki.backend.model.SchedTimingToday
import com.ichi2.libanki.utils.Time
import com.ichi2.utils.KotlinCleanup
import net.ankiweb.rsdroid.RustV1Cleanup

/**
 * Interface to the rust backend listing all currently supported functionality.
 */
@KotlinCleanup("priority to convert to kotlin for named arguments" + "needs better nullable definitions")
interface DroidBackend {
    /** Should only be called from "Storage.java"  */
    fun createCollection(context: Context, db: DB, path: String, server: Boolean, log: Boolean, time: Time): Collection?
    fun openCollectionDatabase(path: String): DB
    fun closeCollection(db: DB?, downgradeToSchema11: Boolean)

    /** Whether a call to [DroidBackend.openCollectionDatabase] will generate a schema and indices for the database  */
    fun databaseCreationCreatesSchema(): Boolean
    fun databaseCreationInitializesData(): Boolean
    fun isUsingRustBackend(): Boolean

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun debugEnsureNoOpenPointers()

    /**
     * Obtains Timing information for the current day.
     *
     * @param createdSecs A UNIX timestamp of the collection creation time
     * @param createdMinsWest The offset west of UTC at the time of creation (eg UTC+10 hours is -600)
     * @param nowSecs timestamp of the current time
     * @param nowMinsWest The current offset west of UTC
     * @param rolloverHour The hour of the day the rollover happens (eg 4 for 4am)
     * @return Timing information for the current day. See [SchedTimingToday].
     */
    @Throws(BackendNotSupportedException::class)
    fun sched_timing_today(createdSecs: Long, createdMinsWest: Int, nowSecs: Long, nowMinsWest: Int, rolloverHour: Int): SchedTimingToday?

    /**
     * For the given timestamp, return minutes west of UTC in the local timezone.
     *
     * eg, Australia at +10 hours is -600.<br>
     * Includes the daylight savings offset if applicable.
     *
     * @param timestampSeconds The timestamp in seconds
     * @return minutes west of UTC in the local timezone
     */
    @Throws(BackendNotSupportedException::class)
    fun local_minutes_west(timestampSeconds: Long): Int

    @RustV1Cleanup("backend.newDeckConfigLegacy")
    fun new_deck_config_legacy(): DeckConfig? {
        return DeckConfig(Decks.DEFAULT_CONF, DeckConfig.Source.DECK_CONFIG)
    }

    fun useNewTimezoneCode(col: Collection)

    @Throws(BackendNotSupportedException::class)
    fun extract_av_tags(text: String, question_side: Boolean): ExtractAVTagsOut

    @Throws(BackendNotSupportedException::class)
    fun renderCardForTemplateManager(templateRenderContext: TemplateRenderContext): RenderCardOut
}
