/*
 * Copyright (c) 2021 Nicola Dardanis <nicdard@gmail.com>
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
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.google.common.annotations.VisibleForTesting;
import com.ichi2.anki.lint.utils.Constants;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.PsiMethod;

import org.jetbrains.uast.UBinaryExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastBinaryOperator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PreferIsEmptyOverSizeCheck extends Detector implements Detector.UastScanner {

    @VisibleForTesting
    static final String ID = "PreferIsEmptyOverSize";
    @VisibleForTesting
    static final String DESCRIPTION = "Always prefer `isEmpty` instead of size comparison when possible";
    private static final String EXPLANATION = "To improve code consistency within the codebase you should use `isEmpty` when possible in place" +
            " of checking that the size is zero.";
    private static final Implementation implementation = new Implementation(PreferIsEmptyOverSizeCheck.class, Scope.JAVA_FILE_SCOPE);
    public static final Issue ISSUE = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Constants.ANKI_CODE_STYLE_CATEGORY,
            Constants.ANKI_CODE_STYLE_PRIORITY,
            Constants.ANKI_CODE_STYLE_SEVERITY,
            implementation
    );

    private static final Pattern pattern = Pattern.compile("\\.size\\(\\)([^=]*)(==|>|<|>=|<=).*\\z");

    public PreferIsEmptyOverSizeCheck() {

    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new PreferIsEmptyOverSizeCheck.BinaryExpressionHandler(context);
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> allowed = new ArrayList<>(2);
        allowed.add(UBinaryExpression.class);
        allowed.add(UCallExpression.class);
        return allowed;
    }

    /**
     * Report the problematic comparison expression to the lint checker and compute the fix to apply
     * since the pattern().with() seems to behave strangely.
     */
    public void reportVariable(
            @NonNull JavaContext context,
            @NonNull UBinaryExpression node,
            boolean isAffirmative
    ) {
        String source = node.asSourceString();
        Matcher matcher = pattern.matcher(source);
        String fixedSource = matcher.replaceFirst(".isEmpty()$1").trim();
        if (!isAffirmative) {
            fixedSource = String.format("!%s", fixedSource);
        }
        LintFix fix = LintFix
                .create()
                .replace()
                .range(context.getLocation(node))
                .text(source)
                .with(fixedSource)
                .reformat(true)
                .build();
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                DESCRIPTION,
                fix
        );
    }

    private class BinaryExpressionHandler extends UElementHandler {

        private JavaContext mJavaContext;
        private UBinaryExpression parentNode;
        private Boolean isAffirmative = null;

        public BinaryExpressionHandler(JavaContext context) {
            this.mJavaContext = context;
        }

        @Override
        public void visitCallExpression(@NonNull UCallExpression node) {
            // Needed to check if the class has an `isEmpty` method.
            if (this.parentNode == null || this.isAffirmative == null || !"size".equals(node.getMethodName())) {
                return;
            }
            // Check that this is a child of the parent we have already seen.
            UElement n = node;
            while (n != null && n != this.parentNode) {
                n = n.getUastParent();
            }
            if (n == null) {
                // Optimisation: since the current node is not in the subtree of parentNode we are done
                // searching for this parentNode (we have already visited all the subtree and exited from it)
                this.parentNode = null;
                this.isAffirmative = null;
                return;
            }
            PsiMethod[] methods = Objects.requireNonNull(this.mJavaContext.getEvaluator()
                    .getTypeClass(node.getReceiverType()))
                    .getAllMethods();
            if (Arrays.stream(methods).anyMatch(m -> "isEmpty".equals(m.getName()) && m.hasModifier(JvmModifier.PUBLIC))) {
                reportVariable(this.mJavaContext, this.parentNode, this.isAffirmative);
                this.parentNode = null;
                this.isAffirmative = null;
            }
        }

        @Override
        public void visitBinaryExpression(@NonNull UBinaryExpression node) {
            UElement left = node.getLeftOperand();
            if (!left.asSourceString().contains(".size()")) {
                return;
            }
            UElement right = node.getRightOperand();
            if (!(right instanceof ULiteralExpression)) {
                return;
            }
            ULiteralExpression rightOperand = (ULiteralExpression) right;
            if (!(rightOperand.getValue() instanceof Number)) {
                return;
            }
            double rightValue = ((Number) rightOperand.getValue()).doubleValue();
            if (UastBinaryOperator.ComparisonOperator.IDENTITY_EQUALS.equals(node.getOperator()) && rightValue == 0.0
                    || UastBinaryOperator.ComparisonOperator.LESS.equals(node.getOperator()) && rightValue == 1.0
                    || UastBinaryOperator.ComparisonOperator.LESS_OR_EQUALS.equals(node.getOperator()) && rightValue == 0.0) {
                // Memoize the node to check later and provide the fix also.
                this.parentNode = node;
                this.isAffirmative = true;
            } else if (UastBinaryOperator.ComparisonOperator.GREATER.equals(node.getOperator()) && rightValue == 0.0
                    || UastBinaryOperator.ComparisonOperator.GREATER_OR_EQUALS.equals(node.getOperator()) && rightValue == 1.0) {
                this.parentNode = node;
                this.isAffirmative = false;
            }
        }
    }
}
