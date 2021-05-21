/*
 * Copyright (c) 2021 Mrudul Tora <mrudultora@gmail.com>
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

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.google.common.annotations.VisibleForTesting;
import com.ichi2.anki.lint.utils.Constants;
import com.ichi2.anki.lint.utils.LintUtils;
import com.intellij.psi.PsiMethod;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;

import java.util.ArrayList;
import java.util.List;

/**
 * This custom Lint rules will raise an error if a developer uses the {com.google.android.material.snackbar.Snackbar#make(...)} method
 * instead of using the method provided by the UIUtils class {com.ichi2.anki.UIUtils#showSimpleSnackbar(...)}
 * or {com.ichi2.anki.UIUtils#showSnackbar(...)}.
 */
public class DirectSnackbarMakeUsage extends Detector implements SourceCodeScanner {

    @VisibleForTesting
    static final String ID = "DirectSnackbarMakeUsage";

    @VisibleForTesting
    static final String DESCRIPTION = "Use UIUtils.showSimpleSnackbar or UIUtils.showSnackbar instead of Snackbar.make";

    private static final String EXPLANATION = "To improve code consistency within the codebase you should use UIUtils.showSimpleSnackbar or UIUtils.showSnackbar " +
            "in place of the library Snackbar.make(...).show()";

    private static final Implementation implementation = new Implementation(DirectSnackbarMakeUsage.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Constants.ANKI_CODE_STYLE_CATEGORY,
            Constants.ANKI_CODE_STYLE_PRIORITY,
            Constants.ANKI_CODE_STYLE_SEVERITY,
            implementation
    );


    public DirectSnackbarMakeUsage() {

    }


    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        List<String> forbiddenMethods = new ArrayList<>();
        forbiddenMethods.add("make");
        return forbiddenMethods;
    }


    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        super.visitMethodCall(context, node, method);
        JavaEvaluator evaluator = context.getEvaluator();
        List<UClass> foundClasses = context.getUastFile().getClasses();
        if (!LintUtils.isAnAllowedClass(foundClasses, "UIUtils")
                && evaluator.isMemberInClass(method, "com.google.android.material.snackbar.Snackbar")) {
            context.report(
                    ISSUE,
                    node,
                    context.getCallLocation(node, true, true),
                    DESCRIPTION
            );
        }
    }

}