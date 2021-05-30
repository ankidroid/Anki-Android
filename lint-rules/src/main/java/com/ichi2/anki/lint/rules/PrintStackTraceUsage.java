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

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.google.common.annotations.VisibleForTesting;
import com.ichi2.anki.lint.utils.Constants;
import com.intellij.psi.PsiMethod;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;

import java.util.ArrayList;
import java.util.List;

public class PrintStackTraceUsage extends Detector implements SourceCodeScanner {

    @VisibleForTesting
    static final String ID = "PrintStackTraceUsage";
    @VisibleForTesting
    static final String DESCRIPTION = "Use Timber to log exceptions (typically Timber.w if non-fatal)";
    private static final String EXPLANATION = "AnkiDroid exclusively uses Timber for logging exceptions. See: https://github.com/ankidroid/Anki-Android/wiki/Code-style#logging";
    private static final Implementation implementation = new Implementation(PrintStackTraceUsage.class, Scope.JAVA_FILE_SCOPE);
    public static final Issue ISSUE = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Constants.ANKI_CODE_STYLE_CATEGORY,
            Constants.ANKI_CODE_STYLE_PRIORITY,
            Constants.ANKI_CODE_STYLE_SEVERITY,
            implementation
    );

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        List<String> forbiddenMethods = new ArrayList<>();
        forbiddenMethods.add("printStackTrace");
        return forbiddenMethods;
    }


    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        super.visitMethodCall(context, node, method);
        JavaEvaluator evaluator = context.getEvaluator();

        // if we have arguments, we're not writing to stdout, so it's an OK call
        boolean hasArguments = node.getValueArgumentCount() != 0;
        if (hasArguments || !evaluator.isMemberInSubClassOf(method, "java.lang.Throwable", false)) {
            return;
        }

        LintFix fix = LintFix.create()
                .replace()
                .select(node.asSourceString())
                // We don't need a semicolon here
                .with("Timber.w(" + node.getReceiver().asSourceString() + ")")
                .autoFix()
                .build();

        context.report(
                ISSUE,
                context.getCallLocation(node, true, true),
                DESCRIPTION,
                fix
        );
    }

}
