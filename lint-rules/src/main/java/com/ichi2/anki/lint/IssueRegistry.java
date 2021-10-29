package com.ichi2.anki.lint;

import com.android.tools.lint.detector.api.ApiKt;
import com.android.tools.lint.detector.api.Issue;
import com.ichi2.anki.lint.rules.ConstantJavaFieldDetector;
import com.ichi2.anki.lint.rules.CopyrightHeaderExists;
import com.ichi2.anki.lint.rules.DirectCalendarInstanceUsage;
import com.ichi2.anki.lint.rules.DirectSnackbarMakeUsage;
import com.ichi2.anki.lint.rules.DirectSystemTimeInstantiation;
import com.ichi2.anki.lint.rules.DirectSystemCurrentTimeMillisUsage;
import com.ichi2.anki.lint.rules.DirectDateInstantiation;
import com.ichi2.anki.lint.rules.DirectGregorianInstantiation;
import com.ichi2.anki.lint.rules.DirectToastMakeTextUsage;
import com.ichi2.anki.lint.rules.DuplicateCrowdInStrings;
import com.ichi2.anki.lint.rules.DuplicateTextInPreferencesXml;
import com.ichi2.anki.lint.rules.FixedPreferencesTitleLength;
import com.ichi2.anki.lint.rules.InconsistentAnnotationUsage;
import com.ichi2.anki.lint.rules.KotlinMigrationBrokenEmails;
import com.ichi2.anki.lint.rules.NonPublicNonStaticJavaFieldDetector;
import com.ichi2.anki.lint.rules.PreferIsEmptyOverSizeCheck;
import com.ichi2.anki.lint.rules.PrintStackTraceUsage;

import com.android.annotations.NonNull;
import com.ichi2.anki.lint.rules.VariableNamingDetector;

import java.util.ArrayList;
import java.util.List;

public class IssueRegistry extends com.android.tools.lint.client.api.IssueRegistry {
    @NonNull
    @Override
    public List<Issue> getIssues() {
        // Keep this list lexicographically ordered.
        List<Issue> issues = new ArrayList<>();
        issues.add(CopyrightHeaderExists.ISSUE);
        issues.add(DirectCalendarInstanceUsage.ISSUE);
        issues.add(DirectDateInstantiation.ISSUE);
        issues.add(DirectGregorianInstantiation.ISSUE);
        issues.add(DirectSnackbarMakeUsage.ISSUE);
        issues.add(DirectSystemCurrentTimeMillisUsage.ISSUE);
        issues.add(DirectSystemTimeInstantiation.ISSUE);
        issues.add(DirectToastMakeTextUsage.ISSUE);
        issues.add(DuplicateCrowdInStrings.ISSUE);
        issues.add(DuplicateTextInPreferencesXml.ISSUE);
        issues.add(InconsistentAnnotationUsage.ISSUE);
        issues.add(KotlinMigrationBrokenEmails.ISSUE);
        issues.add(PreferIsEmptyOverSizeCheck.ISSUE);
        issues.add(PrintStackTraceUsage.ISSUE);
        issues.add(NonPublicNonStaticJavaFieldDetector.ISSUE);
        issues.add(ConstantJavaFieldDetector.ISSUE);
        issues.add(FixedPreferencesTitleLength.ISSUE_MAX_LENGTH);
        issues.add(FixedPreferencesTitleLength.ISSUE_TITLE_LENGTH);
        issues.add(VariableNamingDetector.ISSUE);
        return issues;
    }

    @Override
    public int getApi() {
        return ApiKt.CURRENT_API;
    }
}