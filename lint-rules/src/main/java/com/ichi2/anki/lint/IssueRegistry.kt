@file:Suppress("UnstableApiUsage")
package com.ichi2.anki.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import com.ichi2.anki.lint.rules.ConstantJavaFieldDetector
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
import com.ichi2.anki.lint.rules.InconsistentAnnotationUsage
import com.ichi2.anki.lint.rules.InvalidStringFormatDetector
import com.ichi2.anki.lint.rules.JUnitNullAssertionDetector
import com.ichi2.anki.lint.rules.KotlinMigrationBrokenEmails
import com.ichi2.anki.lint.rules.KotlinMigrationFixLineBreaks
import com.ichi2.anki.lint.rules.NonPositionalFormatSubstitutions
import com.ichi2.anki.lint.rules.NonPublicNonStaticJavaFieldDetector
import com.ichi2.anki.lint.rules.PreferIsEmptyOverSizeCheck
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
                InconsistentAnnotationUsage.ISSUE,
                JUnitNullAssertionDetector.ISSUE,
                KotlinMigrationBrokenEmails.ISSUE,
                KotlinMigrationFixLineBreaks.ISSUE,
                PreferIsEmptyOverSizeCheck.ISSUE,
                PrintStackTraceUsage.ISSUE,
                NonPositionalFormatSubstitutions.ISSUE,
                NonPublicNonStaticJavaFieldDetector.ISSUE,
                ConstantJavaFieldDetector.ISSUE,
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
