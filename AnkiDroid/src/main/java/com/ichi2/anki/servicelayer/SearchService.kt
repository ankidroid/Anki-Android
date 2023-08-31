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

package com.ichi2.anki.servicelayer

import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CrashReportService
import com.ichi2.libanki.exception.InvalidSearchException
import net.ankiweb.rsdroid.RustCleanup

class SearchService {

    class SearchCardsResult(
        val result: List<CardBrowser.CardCache>?,
        val error: String?
    ) {

        @get:JvmName("hasResult")
        val hasResult = result != null

        @get:JvmName("hasError")
        val hasError = error != null
        fun size() = result?.size ?: 0

        companion object {
            @RustCleanup("error checking")
            @RustCleanup("i18n - we use an error message from the Rust")
            fun error(e: Exception): SearchCardsResult {
                if (e !is InvalidSearchException) {
                    // temporary check to see we haven't missed a backend exception
                    CrashReportService.sendExceptionReport(e, "unexpected error type")
                }
                val error = e.localizedMessage?.replace("net.ankiweb.rsdroid.exceptions.BackendInvalidInputException: ", "")
                return SearchCardsResult(null, error)
            }
            fun success(result: List<CardBrowser.CardCache>) = SearchCardsResult(result, null)
            fun invalidResult() = SearchCardsResult(null, null)
        }
    }
}
