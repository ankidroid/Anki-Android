/*
 *  Copyright (c) 2021 Almas Ahmad <ahmadalmas.786.aa@gmail.com>
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
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.google.common.annotations.VisibleForTesting;
import com.ichi2.anki.lint.utils.Constants;
import com.intellij.psi.PsiElement;

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UReferenceExpression;

import java.util.ArrayList;
import java.util.List;


public class DirectSdkIntUsage extends Detector implements SourceCodeScanner {

    @VisibleForTesting
    static final String ID = "DirectSdkIntUsage";

    @VisibleForTesting
    static final String DESCRIPTION = "Check whether SdkInt is directly used";

    private static final String EXPLANATION = "To improve code consistency within the codebase you should use CompatHelper.getSdkVersion() "+
            "instead of Build.VERSION.SDK_INT";
    private static Implementation implementation = new Implementation(DirectSdkIntUsage.class, Scope.JAVA_FILE_SCOPE);
    public static final Issue ISSUE = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Constants.ANKI_CODE_STYLE_CATEGORY,
            Constants.ANKI_CODE_STYLE_PRIORITY,
            Constants.ANKI_CODE_STYLE_SEVERITY,
            implementation
    );


    public DirectSdkIntUsage() {

    }


    @Nullable
    @Override
    public List<String> getApplicableReferenceNames() {
        List<String> forbiddenReferenceNames = new ArrayList<>();
        forbiddenReferenceNames.add("SDK_INT");
        return forbiddenReferenceNames;
    }


    @Override
    public void visitReference(@NonNull JavaContext context, @NonNull UReferenceExpression reference, @NonNull PsiElement referenced) {
        super.visitReference(context, reference, referenced);
        UClass topClass = context.getUastFile().getClasses().get(0);
        PsiElement parent = referenced.getParent();
        PsiElement grandParent = parent.getParent();

        if (parent.toString().contains("VERSION") && grandParent.toString().contains("Build") && !topClass.toString().contains("CompatHelper")) {
            context.report(
                    ISSUE,
                    reference,
                    context.getLocation(referenced),
                    DESCRIPTION
            );
        }
    }


}
