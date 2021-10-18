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

import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.ichi2.anki.lint.utils.Constants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

@SuppressWarnings("UnstableApiUsage")
@Beta
public class CopyrightHeaderExists extends Detector implements SourceCodeScanner {
    // This string matches GPLv3 under all current circumstances. It does not currently work if split over two lines
    private static final Pattern COPYRIGHT_PATTERN = Pattern.compile("version 3 of the License, or \\(at");
    // Suppressing this lint doesn't seem to work as it's the first statement, so allow a
    private static final Pattern IGNORE_CHECK_PATTERN = Pattern.compile("MissingCopyrightHeader");

    @VisibleForTesting
    static final String ID = "MissingCopyrightHeader";
    @VisibleForTesting
    static final String DESCRIPTION = "All files in AnkiDroid must contain a GPLv3-compatible copyright header";
    private static final String EXPLANATION = "All files in AnkiDroid must contain a " +
            "GPLv3-compatible copyright header" +
            "The copyright header can be set in " +
            "Settings - Editor - Copyright - Copyright Profiles - Add Profile - AnkiDroid. " +
            "Or search in Settings for 'Copyright'" +
            "A GPLv3 template is available:\n" +
            "https://github.com/ankidroid/Anki-Android/issues/8211#issuecomment-825269673 \n\n" +
            "If the file is under a GPL-Compatible License (https://www.gnu.org/licenses/license-list.en.html#GPLCompatibleLicenses) " +
            "then this warning may be suppressed either via adding a GPL header added alongside the license: " +
            "https://softwarefreedom.org/resources/2007/gpl-non-gpl-collaboration.html#x1-40002.2 + or " +
            "\"//noinspection MissingCopyrightHeader <reason>\" may be added as the first line of the file.";
    private static final Implementation implementation = new Implementation(CopyrightHeaderExists.class, EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES));
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
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public void afterCheckFile(@NotNull Context context) {

        CharSequence contents = context.getContents();
        if (contents == null
                || COPYRIGHT_PATTERN.matcher(contents).find()
                || IGNORE_CHECK_PATTERN.matcher(contents).find())
        {
            return;
        }

        // select from the start to the first line with content
        int end = 0;
        boolean foundChar = false;
        for (int i = 0; i < contents.length(); i++) {
            foundChar |= !Character.isWhitespace(contents.charAt(i));
            if (foundChar && contents.charAt(i) == '\n') {
                end = i;
                break;
            }
        }

        // If there is no line break, highlight the contents
        int endOffset = end == 0 ? contents.length() : end;

        Location location = Location.create(context.file, contents.subSequence(0, endOffset), 0, endOffset);
        context.report(ISSUE, location, DESCRIPTION);
    }
}