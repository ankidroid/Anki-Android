package com.ichi2.anki.lint.rules;

import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import com.google.common.annotations.VisibleForTesting;
import com.ichi2.anki.lint.utils.Constants;

import org.w3c.dom.Element;

import java.util.Collection;

/**
 * A Lint check to prevent using the same string for the title and summary of a preference.
 */
public class DuplicateTextInPreferencesXml extends ResourceXmlDetector {

    @VisibleForTesting
    static final String ID = "DuplicateTextInPreferencesXml";
    @VisibleForTesting
    static final String DESCRIPTION = "Do not use the same string for the title and summary of a preference";
    private static final String EXPLANATION = "Use different strings for the title and summary of a preference to better " +
            "explain what that preference is for";
    private static final Implementation implementation = new Implementation(DuplicateTextInPreferencesXml.class, Scope.RESOURCE_FILE_SCOPE);
    public static final Issue ISSUE = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Constants.ANKI_TIME_CATEGORY,
            Constants.ANKI_TIME_PRIORITY,
            Constants.ANKI_TIME_SEVERITY,
            implementation
    );
    private static final String ATTR_TITLE = "android:title";
    private static final String ATTR_SUMMARY = "android:summary";


    public DuplicateTextInPreferencesXml() {
    }


    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }


    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.hasAttribute(ATTR_TITLE) && element.hasAttribute(ATTR_SUMMARY)) {
            String titleValue = element.getAttribute(ATTR_TITLE);
            String summaryValue = element.getAttribute(ATTR_SUMMARY);
            if (titleValue.equals(summaryValue)) {
                context.report(ISSUE, element, context.getLocation(element), DESCRIPTION);
            }
        }
    }


    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }
}
