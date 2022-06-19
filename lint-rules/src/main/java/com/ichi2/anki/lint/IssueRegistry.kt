/****************************************************************************************
 * Copyright (c) 2020 lukstbit <52494258+lukstbit@users.noreply.github.com>             *
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
@file:Suppress("UnstableApiUsage")

package com.ichi2.anki.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import com.ichi2.anki.lint.rules.CopyrightHeaderExists
import com.ichi2.anki.lint.rules.DirectCalendarInstanceUsage
import com.ichi2.anki.lint.rules.DirectDateInstantiation
import com.ichi2.anki.lint.rules.DirectGregorianInstantiation
import com.ichi2.anki.lint.rules.DirectSnackbarMakeUsage
import com.ichi2.anki.lint.rules.DirectSystemCurrentTimeMillisUsage
import com.ichi2.anki.lint.rules.DirectSystemTimeInstantiation
import com.ichi2.anki.lint.rules.DirectToastMakeTextUsage
import com.ichi2.anki.lint.rules.DuplicateCrowdInStrings
import com.ichi2.anki.lint.rules.DuplicateTextInPreferencesXml
import com.ichi2.anki.lint.rules.FixedPreferencesTitleLength
import com.ichi2.anki.lint.rules.HardcodedPreferenceKey
import com.ichi2.anki.lint.rules.InvalidStringFormatDetector
import com.ichi2.anki.lint.rules.JUnitNullAssertionDetector
import com.ichi2.anki.lint.rules.KotlinMigrationBrokenEmails
import com.ichi2.anki.lint.rules.KotlinMigrationFixLineBreaks
import com.ichi2.anki.lint.rules.NonPositionalFormatSubstitutions
import com.ichi2.anki.lint.rules.PrintStackTraceUsage
import com.ichi2.anki.lint.rules.VariableNamingDetector

class IssueRegistry : IssueRegistry() {
    // Keep this list lexicographically ordered.
    override val issues: List<Issue>
        get() {
            // Keep this list lexicographically ordered.
            return listOf(
                CopyrightHeaderExists.ISSUE,
                DirectCalendarInstanceUsage.ISSUE,
                DirectDateInstantiation.ISSUE,
                DirectGregorianInstantiation.ISSUE,
                DirectSnackbarMakeUsage.ISSUE,
                DirectSystemCurrentTimeMillisUsage.ISSUE,
                DirectSystemTimeInstantiation.ISSUE,
                DirectToastMakeTextUsage.ISSUE,
                DuplicateCrowdInStrings.ISSUE,
                DuplicateTextInPreferencesXml.ISSUE,
                HardcodedPreferenceKey.ISSUE,
                JUnitNullAssertionDetector.ISSUE,
                KotlinMigrationBrokenEmails.ISSUE,
                KotlinMigrationFixLineBreaks.ISSUE,
                PrintStackTraceUsage.ISSUE,
                NonPositionalFormatSubstitutions.ISSUE,
                FixedPreferencesTitleLength.ISSUE_MAX_LENGTH,
                FixedPreferencesTitleLength.ISSUE_TITLE_LENGTH,
                VariableNamingDetector.ISSUE,
                InvalidStringFormatDetector.ISSUE
            )
        }
    override val api: Int
        get() = CURRENT_API
    override val vendor: Vendor
        get() = Vendor(
            "AnkiDroid",
            "com.ichi2.anki:lint-rules",
            "https://github.com/ankidroid/Anki-Android/issues",
            "https://github.com/ankidroid/Anki-Android"
        )
}
