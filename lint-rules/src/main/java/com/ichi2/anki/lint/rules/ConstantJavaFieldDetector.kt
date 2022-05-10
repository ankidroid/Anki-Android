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

import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.KotlinCleanup
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastVisibility
import java.util.*

/**
 * https://github.com/ankidroid/Anki-Android/wiki/Code-style#constant-final-variables-names-must-be-all-uppercase-using-underscore-to-separate-words
 * Constant (final variables) names must be all uppercase using underscore to separate words.
 */
@KotlinCleanup("IDE warnings")
@KotlinCleanup("Ignore unstable API warning")
@KotlinCleanup("Remove after all are converted")
class ConstantJavaFieldDetector : JavaFieldNamingPatternDetector() {
    override fun isApplicable(variable: UVariable): Boolean {
        // TODO: The code style here is ambiguous - we'll only flag public static final for now
        // For instance: the only thing that matters is final - are these constants:
        // public final int - no
        // private static final - maybe?
        return (
            variable.isStatic &&
                variable.visibility == UastVisibility.PUBLIC &&
                variable.isFinal &&
                "Companion" != variable.name // #9223 - fix for kotlin companion objects
            )
    }

    @KotlinCleanup("use filter/any")
    override fun meetsNamingStandards(variableName: String): Boolean {
        var foundLower = false
        for (c in variableName.toCharArray()) {
            if (Character.isLowerCase(c)) {
                foundLower = true
                break
            }
        }

        // if 0-length, or no lowercase letters - should be OK
        return !foundLower
    }

    @KotlinCleanup("extract method: setting 'variableWithoutPrefix'")
    @KotlinCleanup("define replacement after setting 'variableWithoutPrefix'")
    override fun reportVariable(context: JavaContext, node: UVariable, variableName: String) {
        var variableWithoutPrefix = variableName
        val replacement = StringBuilder()
        // If the s prefix was accidentally applied, remove it.
        if ((variableWithoutPrefix.startsWith("s") || variableWithoutPrefix.startsWith("m")) && variableWithoutPrefix.length > 1 && Character.isUpperCase(variableWithoutPrefix[1])) {
            variableWithoutPrefix = variableWithoutPrefix.substring(1)
        }
        replacement.append(variableWithoutPrefix.uppercase(Locale.ROOT))

        // explicitly skip 0.
        // Work from the end to the start so we can handle string length changes
        for (i in variableWithoutPrefix.length - 1 downTo 1) {
            val c = variableWithoutPrefix[i]
            if (Character.isUpperCase(c)) {
                replacement.insert(i, '_')
            }
        }

        // TODO: A fix should be possible, but it requires a rename operation

        // cast the node as it's ambiguous between two interfaces
        val uNode: UElement = node
        context.report(ISSUE, uNode, context.getNameLocation(uNode), "Field should be named: '$replacement'")
    }

    companion object {
        private val implementation = Implementation(ConstantJavaFieldDetector::class.java, Scope.JAVA_FILE_SCOPE)
        @JvmField
        var ISSUE: Issue = Issue.create(
            "ConstantFieldName",
            "Constant field naming",
            "Constant (final variables) names must be all uppercase using underscore to separate words: https://github.com/ankidroid/Anki-Android/wiki/Code-style#constant-final-variables-names-must-be-all-uppercase-using-underscore-to-separate-words",
            Constants.ANKI_CODE_STYLE_CATEGORY,
            Constants.ANKI_CODE_STYLE_PRIORITY,
            Constants.ANKI_CODE_STYLE_SEVERITY,
            implementation
        )
    }
}
