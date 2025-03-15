/*
 *  Copyright (c) 2024 Spencer Poisseroux <me@spoisseroux.com>
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
@file:Suppress("UnstableApiUsage")

package com.ichi2.anki.lint.rules

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.util.isMethodCall

/**
 * This custom Lint rule raises an error if a developer uses Locale.ROOT for user-facing text formatting
 */
class LocaleRootDetector :
    Detector(),
    SourceCodeScanner {
    companion object {
        @VisibleForTesting
        const val ID = "LocaleRootUsage"

        @VisibleForTesting
        const val DESCRIPTION = "Avoid using Locale.ROOT for user-facing text"

        private const val EXPLANATION =
            "Using Locale.ROOT for user-facing text formatting is discouraged. " +
                "Please use Locale.getDefault() or the device's locale for proper localization."

        private val implementation = Implementation(LocaleRootDetector::class.java, Scope.JAVA_FILE_SCOPE)

        val ISSUE: Issue =
            Issue.create(
                ID,
                DESCRIPTION,
                EXPLANATION,
                Constants.ANKI_TIME_CATEGORY,
                Constants.ANKI_TIME_PRIORITY,
                Constants.ANKI_TIME_SEVERITY,
                implementation,
            )
    }

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.isMethodCall() && isLocaleRootUsage(node)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        DESCRIPTION,
                        createFix(),
                    )
                }
            }
        }

    private fun isLocaleRootUsage(node: UCallExpression): Boolean =
        node.methodName?.let { methodName ->
            (methodName == "format" || methodName == "getInstance" || methodName == "newInstance") &&
                node.valueArguments.any {
                    it.asRenderString().contains("Locale.ROOT")
                }
        } ?: false

    private fun createFix(): LintFix =
        fix()
            .name("Replace with Locale.getDefault()")
            .replace()
            .text("Locale.ROOT")
            .with("Locale.getDefault()")
            .build()
}
