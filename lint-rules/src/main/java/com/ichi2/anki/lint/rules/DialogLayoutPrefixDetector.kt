/*
 * Copyright (c) 2026 Sonal Yadav <sonal.y6390@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
@file:Suppress("UnstableApiUsage")

package com.ichi2.anki.lint.rules

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.ALL_PREFIXES
import com.ichi2.anki.lint.utils.ComponentType
import com.ichi2.anki.lint.utils.bindings
import com.ichi2.anki.lint.utils.getComponentType
import com.ichi2.anki.lint.utils.getLayoutName
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement

/**
 * Lint rule to enforce that layouts used in Dialog classes are prefixed with `dialog_`.
 *
 * This rule checks ViewBinding fields in classes extending DialogFragment or Dialog.
 * Only raises an error if the association is certain.
 */
class DialogLayoutPrefixDetector :
    Detector(),
    SourceCodeScanner {
    companion object {
        private const val REQUIRED_PREFIX = "dialog_"
        private val SKIP_PREFIXES = ALL_PREFIXES - REQUIRED_PREFIX

        @VisibleForTesting
        const val ID = "DialogLayoutPrefix"

        @VisibleForTesting
        const val DESCRIPTION = "Dialog layout file should be prefixed with dialog_"
        private const val EXPLANATION =
            "Layouts associated with Dialog classes must be named with the `dialog_` prefix. " +
                "For example, use `dialog_confirm.xml` instead of `confirm_dialog.xml`."
        private val IMPLEMENTATION =
            Implementation(
                DialogLayoutPrefixDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            )
        val ISSUE: Issue =
            Issue.create(
                ID,
                DESCRIPTION,
                EXPLANATION,
                Category.CORRECTNESS,
                6,
                Severity.ERROR,
                IMPLEMENTATION,
            )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitClass(node: UClass) {
                if (node.getComponentType() != ComponentType.DIALOG) return

                for (field in node.bindings) {
                    val layoutName = field.getLayoutName() ?: continue

                    // Skip if already correctly prefixed or belongs to another component type
                    if (layoutName.startsWith(REQUIRED_PREFIX)) continue
                    if (SKIP_PREFIXES.any { layoutName.startsWith(it) }) continue

                    context.report(
                        ISSUE,
                        field,
                        context.getLocation(field),
                        "Dialog layout should be prefixed with `$REQUIRED_PREFIX`: `$layoutName`",
                    )
                }
            }
        }
}
