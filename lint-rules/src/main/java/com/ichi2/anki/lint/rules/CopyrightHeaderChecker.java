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

import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.google.common.annotations.VisibleForTesting;
import com.ichi2.anki.lint.utils.Constants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CopyrightHeaderChecker extends Detector implements SourceCodeScanner {
    @VisibleForTesting
    static final String ID = "CopyrightHeaderChecker";
    @VisibleForTesting
    static final String DESCRIPTION = "Add Copyright Header in each file";
    private static final String EXPLANATION = "All files in AnkiDroid must contain a " +
            "GPLv3-compatible copyright header" +
            "The copyright header can be set in " +
            "Settings - Editor - Copyright - Copyright Profiles - Add Profile - AnkiDroid. " +
            "Search in Settings for 'Copyright'" +
            "Add Licence Notice given in this page " +
            "https://www.gnu.org/licenses/gpl-faq.en.html#GPLIncompatibleLibs";
    private static final Implementation implementation = new Implementation(CopyrightHeaderChecker.class, Scope.JAVA_FILE_SCOPE);
    public static final Issue ISSUE = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Constants.ANKI_TIME_CATEGORY,
            Constants.ANKI_TIME_PRIORITY,
            Constants.ANKI_TIME_SEVERITY,
            implementation
    );

    public CopyrightHeaderChecker() {

    }


    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }


    @Override
    public void afterCheckFile(@NotNull Context context) {
        CharSequence source = context.getContents();
        Pattern pattern = Pattern.compile("/*\n" +
                " *  This program is free software; you can redistribute it and/or modify it under\n" +
                " *  the terms of the GNU General Public License as published by the Free Software\n" +
                " *  Foundation; either version 3 of the License, or (at your option) any later\n" +
                " *  version.\n" +
                " *\n" +
                " *  This program is distributed in the hope that it will be useful, but WITHOUT ANY\n" +
                " *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A\n" +
                " *  PARTICULAR PURPOSE. See the GNU General Public License for more details.\n" +
                " *\n" +
                " *  You should have received a copy of the GNU General Public License along with\n" +
                " *  this program.  If not, see <http://www.gnu.org/licenses/>.\n" +
                " */");
        Matcher matcher = pattern.matcher(source);
        boolean found = false;
        if (matcher.find()) {
            found = true;
        }
        if (!found) {
            context.report(
                    ISSUE,
                    null,
                    DESCRIPTION
            );
        }
    }
}