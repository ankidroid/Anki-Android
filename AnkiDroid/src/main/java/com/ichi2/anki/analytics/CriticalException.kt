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
package com.ichi2.anki.analytics

import com.ichi2.utils.ExceptionUtil
import timber.log.Timber

/** #9075 - Error Reporting methods for issues which need additional user feedback  */
// TODO: ToString()
class CriticalException(exception: Throwable, issue: KnownIssue) {
    private val mException: Throwable = exception
    private val mIssue: KnownIssue = issue
    fun getException() = mException
    fun getIssue() = mIssue

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CriticalException

        if (mIssue != other.mIssue) return false
        if (mIssue.onlyReportOnce()) return true
        if (mException.message != other.mException.message) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mIssue.hashCode()
        if (mIssue.onlyReportOnce()) {
            return result
        }
        result = 31 * result + mException.message.hashCode()
        return result
    }

    companion object {
        @JvmStatic
        private var sPendingExceptionReport: CriticalException? = null

        @JvmStatic
        private val sRegisteredExceptions: HashSet<CriticalException> = HashSet()

        /** Generates a CriticalException from an exception */
        @JvmStatic
        fun from(e: Throwable): CriticalException {
            val issue = KnownIssue.from(e)
            return CriticalException(e, issue)
        }

        @JvmStatic
        fun get() = sPendingExceptionReport

        /** Registers a critical exception to be accessed after startup
         * We do this because it's not feasible to immediately show a dialog
         * as the exceptions can occur inside libAnki code, where getting a reference to the view
         * logic is infeasible.
         *
         * For now, we check this in the DeckPicker
         * */
        @JvmStatic
        fun register(ex: CriticalException) {
            // If the same issue is reported twice, we do not want a second dialog
            // .equals/.hashCode have been overridden
            if (!sRegisteredExceptions.add(ex)) {
                Timber.d("Exception '%s' already known - not displaying dialog", ex)
                return
            }

            sPendingExceptionReport = ex
        }

        @JvmStatic
        fun onDisplayedToUser() {
            sPendingExceptionReport = null
        }

        fun ignore(criticalException: CriticalException) {
        }

        fun reset() {
            sPendingExceptionReport = null
            sRegisteredExceptions.clear()
        }
    }

    /**
     * Represents a critical issue which warrants informing the user
     *
     * A "Known Issue" in analytics should allow a dev to query "how many of exception X occurred".
     *
     * Note: some exceptions are unknown/unspecified, (UNHANDLED_EXCEPTION/RUST_UNSATISFIED_LINK)
     * In these cases we need to avoid deduplication logic based on this type, as one instance of
     * this enum can encompass multiple exceptions
     *
     * @param mAnalyticsOrdinal A unique ordinal used for analytics hits. We can't use .getOrdinal()
     * as this is changed if a value is removed.
     * */
    enum class KnownIssue(private val mAnalyticsOrdinal: Int, private val mGitHubIssue: Int?) {

        UNHANDLED_EXCEPTION(0, null),
        /** An issue which is not handled by the cases below  */
        RUST_UNSATISFIED_LINK(1, null),
        /** RustBackendFailedException: .dynamic section header was not found  */
        RUST_UNSATISFIED_LINK_NO_DYNAMIC_SECTION(2, 9064),
        /** RustBackendFailedException: dlopen failed: cannot find "" from verneed[0] in DT_NEEDED list  */
        RUST_UNSATISFIED_LINK_DT_NEEDED(3, 9065),
        /** RustBackendFailedException: java.lang.UnsatisfiedLinkError: dalvik.system.PathClassLoader\[DexPathList\]
         * couldn't find "librsdroid.so"   */
        RUST_UNSATISFIED_LINK_FILE_NOT_FOUND(4, 9067);

        fun onlyReportOnce(): Boolean {
            // If we know the issue is a duplicate, then we don't want to display a dialog more than once
            return mGitHubIssue != null
        }

        val gitHubIssueId: Int?
            get() = mGitHubIssue

        companion object {
            @JvmStatic
            fun from(ex: Throwable): KnownIssue {

                fun hasMessage(s: String): Boolean = ExceptionUtil.containsMessage(ex, s)

                return when {
                    hasMessage("couldn't find \"librsdroid.so\"") -> RUST_UNSATISFIED_LINK_FILE_NOT_FOUND
                    hasMessage("cannot find \"\" from verneed[0] in DT_NEEDED list") -> RUST_UNSATISFIED_LINK_DT_NEEDED
                    hasMessage(".dynamic section header was not found") -> RUST_UNSATISFIED_LINK_NO_DYNAMIC_SECTION
                    ExceptionUtil.containsCause(ex, UnsatisfiedLinkError::class.java) -> RUST_UNSATISFIED_LINK
                    else -> UNHANDLED_EXCEPTION
                }
            }
        }
    }
}
