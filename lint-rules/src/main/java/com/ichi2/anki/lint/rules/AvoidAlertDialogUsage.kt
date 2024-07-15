/*
 *  Copyright (c) 2024 Sumit Singh <sumitsinghkoranga7@gmail.com>
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
import com.ichi2.anki.lint.utils.LintUtils
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement

/**
 * This custom Lint rule raises a warning if a developer uses the `android.app.AlertDialog` class.
 */
class AvoidAlertDialogUsage : Detector(), SourceCodeScanner {

    companion object {
        @VisibleForTesting
        const val ID = "AvoidAlertDialogUsage"

        @VisibleForTesting
        const val DESCRIPTION = "Use androidx.appcompat.app.AlertDialog instead of android.app.AlertDialog"
        private const val EXPLANATION = "Using `android.app.AlertDialog` is discouraged. " +
            "Please use `androidx.appcompat.app.AlertDialog` instead for better compatibility and features."
        private val implementation = Implementation(AvoidAlertDialogUsage::class.java, Scope.JAVA_FILE_SCOPE)
        val ISSUE: Issue = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Constants.ANKI_TIME_CATEGORY,
            Constants.ANKI_TIME_PRIORITY,
            Constants.ANKI_TIME_SEVERITY,
            implementation
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UImportStatement::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val importReference = node.asSourceString()
                if (importReference.contains("android.app.AlertDialog") &&
                    !LintUtils.isAnAllowedClass(context.uastFile!!.classes, "AnkiDroidCrashReportDialog")
                ) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        DESCRIPTION,
                        createFix()
                    )
                }
            }
        }
    }

    private fun createFix(): LintFix {
        return fix()
            .name("Replace with androidx.appcompat.app.AlertDialog")
            .replace()
            .text("android.app.AlertDialog")
            .with("androidx.appcompat.app.AlertDialog")
            .build()
    }
}
