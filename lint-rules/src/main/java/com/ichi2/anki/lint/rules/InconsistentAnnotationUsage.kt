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

package com.ichi2.anki.lint.rules;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.google.common.annotations.VisibleForTesting;
import com.ichi2.anki.lint.utils.Constants;
import com.ichi2.anki.lint.utils.ImportStatementDetector;

import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;

public class InconsistentAnnotationUsage extends ImportStatementDetector implements SourceCodeScanner {
    @VisibleForTesting
    static final String ID = "InconsistentAnnotationUsage";
    @VisibleForTesting
    static final String DESCRIPTION = "Use androidx.annotation.NonNull and androidx.annotation.Nullable. See explanation for IDE-level fix";
    private static final String EXPLANATION = "AnkiDroid uses androidx nullability annotations over JetBrains for nullability. " +
            "The annotations library can be specified in Settings - Inspections - Java - Probable Bugs - Nullability Problems - @NonNull/@Nullable problems. " +
            "Search in Settings for '@Nullable problems'";
    private static final Implementation implementation = new Implementation(InconsistentAnnotationUsage.class, Scope.JAVA_FILE_SCOPE);
    public static final Issue ISSUE = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Constants.ANKI_TIME_CATEGORY,
            Constants.ANKI_TIME_PRIORITY,
            Constants.ANKI_TIME_SEVERITY,
            implementation
    );

    public InconsistentAnnotationUsage() {

    }


    @Override
    public void visitImportStatement(@NonNull JavaContext context, @NonNull UImportStatement node) {

        UElement importReference = node.getImportReference();
        if (importReference != null && isJetbrains(importReference.asRenderString())) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    DESCRIPTION
            );
        }
    }


    private boolean isJetbrains(String importReference) {
        return importReference.equals("org.jetbrains.annotations.NotNull") || importReference.equals("org.jetbrains.annotations.Nullable");
    }
}
