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
package com.ichi2.anki.lint.rules

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.JavaContext
import com.ichi2.anki.lint.utils.KotlinCleanup
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UVariable

@KotlinCleanup("IDE Lint")
abstract class JavaFieldNamingPatternDetector : Detector(), Detector.UastScanner {
    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return VariableNamingHandler(context)
    }

    override fun getApplicableUastTypes(): List<Class<out UElement?>>? {
        return listOf(UVariable::class.java)
    }

    private inner class VariableNamingHandler(private val mContext: JavaContext) :
        UElementHandler() {
        override fun visitVariable(node: UVariable) {
            // Only apply naming patterns to Java
            if (mContext.file.absolutePath.endsWith(".kt")) {
                return
            }

            // HACK: Using visitField didn't return any results
            if (node !is UField) {
                return
            }
            if (!isApplicable(node)) {
                return
            }
            val variableName = node.name
            if (meetsNamingStandards(variableName)) {
                return
            }
            reportVariable(mContext, node, variableName)
        }
    }

    /** If the lint check is applicable to the given variable  */
    protected abstract fun isApplicable(variable: UVariable): Boolean
    protected abstract fun meetsNamingStandards(variableName: String): Boolean

    /** Report the problematic variable to the lint checker  */
    protected abstract fun reportVariable(
        context: JavaContext,
        node: UVariable,
        variableName: String
    )
}
