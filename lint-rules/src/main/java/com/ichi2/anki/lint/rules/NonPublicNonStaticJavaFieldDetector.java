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

package com.ichi2.anki.lint.rules;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.ichi2.anki.lint.utils.Constants;

import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UVariable;
import org.jetbrains.uast.UastVisibility;

import java.util.EnumSet;

/**
 * https://github.com/ankidroid/Anki-Android/wiki/Code-style#non-public-non-static-field-names-should-start-with-m
 */
public class NonPublicNonStaticJavaFieldDetector extends JavaFieldNamingPatternDetector {

    private static final Implementation implementation = new Implementation(NonPublicNonStaticJavaFieldDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES));

    public static Issue ISSUE = Issue.create(
            "NonPublicNonStaticFieldName",
            "non-public non-static naming",
            "Non-public, non-static field names should start with m: https://github.com/ankidroid/Anki-Android/wiki/Code-style#non-public-non-static-field-names-should-start-with-m",
            Constants.ANKI_CODE_STYLE_CATEGORY,
            Constants.ANKI_CODE_STYLE_PRIORITY,
            Constants.ANKI_CODE_STYLE_SEVERITY,
            implementation
    );


    @SuppressWarnings("RedundantIfStatement")
    @Override
    protected boolean isApplicable(@NonNull UVariable variable) {
        if (variable.isStatic()) {
            return false;
        }

        if (variable.getVisibility() == UastVisibility.PUBLIC) {
            return false;
        }

        return true;
    }


    @Override
    protected boolean meetsNamingStandards(@NonNull String variableName) {
        return variableName.length() >= 2 && variableName.startsWith("m") && Character.isUpperCase(variableName.charAt(1));
    }


    @Override
    protected void reportVariable(@NonNull JavaContext context, @NonNull UVariable node, @NonNull String variableName) {
        if (variableName.length() < 2) {
            // cast the node as it's ambiguous between two interfaces
            UElement uNode = node;
            context.report(ISSUE, uNode, context.getNameLocation(uNode), "Variable name is too short");
            return;
        }

        // we have a problem: either we don't have an m, or we do, and the next value is uppercase
        String prefix = "m";

        String replacement = prefix + Character.toUpperCase(variableName.charAt(0)) + variableName.substring(1);

        // TODO: A fix should be possible, but it requires a rename operation

        // cast the node as it's ambiguous between two interfaces
        UElement uNode = node;
        context.report(ISSUE, uNode, context.getNameLocation(uNode), "Field should be named: '" + replacement + "'");
    }
}
