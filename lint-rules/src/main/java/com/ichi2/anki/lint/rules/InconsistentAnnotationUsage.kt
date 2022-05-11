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
@file:Suppress("UnstableApiUsage")
package com.ichi2.anki.lint.rules

import com.android.tools.lint.detector.api.*
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.ImportStatementDetector
import org.jetbrains.uast.UImportStatement

class InconsistentAnnotationUsage : ImportStatementDetector(), SourceCodeScanner {

    companion object {
        @VisibleForTesting
        const val ID = "InconsistentAnnotationUsage"

        @VisibleForTesting
        const val DESCRIPTION = "Use androidx.annotation.NonNull and androidx.annotation.Nullable. See explanation for IDE-level fix"
        private const val EXPLANATION = "AnkiDroid uses androidx nullability annotations over JetBrains for nullability. " +
            "The annotations library can be specified in Settings - Inspections - Java - Probable Bugs - Nullability Problems - @NonNull/@Nullable problems. " +
            "Search in Settings for '@Nullable problems'"
        private val implementation = Implementation(InconsistentAnnotationUsage::class.java, Scope.JAVA_FILE_SCOPE)
        @JvmField
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

    override fun visitImportStatement(context: JavaContext, node: UImportStatement) {
        val importReference = node.importReference
        if (importReference != null && isJetbrains(importReference.asRenderString())) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                DESCRIPTION
            )
        }
    }

    private fun isJetbrains(importReference: String): Boolean {
        return importReference == "org.jetbrains.annotations.NotNull" || importReference == "org.jetbrains.annotations.Nullable"
    }
}
