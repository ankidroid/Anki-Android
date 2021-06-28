/*
 *  Copyright (c) 2021 Prateek Singh <prateeksingh3212@gmail.com>
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

import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import com.android.utils.Pair;
import com.google.common.annotations.VisibleForTesting;
import com.ichi2.anki.lint.utils.Constants;

import com.android.annotations.NonNull;
import org.w3c.dom.Element;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FixedPreferencesTitleLength extends ResourceXmlDetector implements XmlScanner {

    @VisibleForTesting
    static final String ID_TITLE_LENGTH = "FixedPreferencesTitleLength";
    @VisibleForTesting
    static final String ID_MAX_LENGTH = "PreferencesTitleMaxLengthAttr";
    private static final int PREFERENCE_TITLE_MAX_LENGTH = 41;
    @VisibleForTesting
    static final String DESCRIPTION_TITLE_LENGTH = String.format("Preference titles should be less than %d characters", PREFERENCE_TITLE_MAX_LENGTH);
    @VisibleForTesting
    static final String DESCRIPTION_MAX_LENGTH = String.format("Preference titles should contain maxLength=\"%d\" attribute", PREFERENCE_TITLE_MAX_LENGTH);
    // Around 42 is a hard max on emulators, likely smaller in reality, so use a buffer
    private static final String EXPLANATION_TITLE_LENGTH = "A title with more than " + PREFERENCE_TITLE_MAX_LENGTH + " characters may fail to display on smaller screens";
    // Read More: https://support.crowdin.com/file-formats/android-xml/
    private static final String EXPLANATION_MAX_LENGTH = "Preference Title should contain maxLength attribute " +
            "because it fixes translated string length";
    private static final Implementation implementation = new Implementation(FixedPreferencesTitleLength.class, Scope.RESOURCE_FILE_SCOPE);
    public static final Issue ISSUE_TITLE_LENGTH = Issue.create(
            ID_TITLE_LENGTH,
            DESCRIPTION_TITLE_LENGTH,
            EXPLANATION_TITLE_LENGTH,
            Constants.ANKI_XML_CATEGORY,
            Constants.ANKI_XML_PRIORITY,
            Constants.ANKI_XML_SEVERITY,
            implementation
    );
    public static final Issue ISSUE_MAX_LENGTH = Issue.create(
            ID_MAX_LENGTH,
            DESCRIPTION_MAX_LENGTH,
            EXPLANATION_MAX_LENGTH,
            Constants.ANKI_XML_CATEGORY,
            Constants.ANKI_XML_PRIORITY,
            Constants.ANKI_XML_SEVERITY,
            implementation
    );
    private static final String ATTR_TITLE = "android:title";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_MAX_LENGTH = "maxLength";
    private final Set<String> xmlData = new HashSet<>();
    private final Map<String, Pair<Element, Location.Handle>> valuesData = new HashMap<>();


    public FixedPreferencesTitleLength() {
    }


    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }


    @Override
    public void visitElement(XmlContext context, Element element) {
        /* 1. This condition checks that current file has xml as a parent file.
           2. Checks that element contains title attribute or not.
           If both of conditions are true then set the value in xmlSet.
         */
        if ("xml".equals(context.file.getParentFile().getName()) && element.hasAttribute(ATTR_TITLE)) {
            String stringName = element.getAttribute(ATTR_TITLE).substring(8);
            xmlData.add(stringName);
            return;
        }

        if (!"values".equals(context.file.getParentFile().getName())) {
            return;
        }

        if (!"10-preferences.xml".equals(context.file.getName())) {
            return;
        }

        if ("resources".equals(element.getTagName())) {
            return;
        }

        Location.Handle handle = context.createLocationHandle(element);
        handle.setClientData(element);
        valuesData.put(element.getAttribute(ATTR_NAME), Pair.of(element, handle));
    }


    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return (folderType == ResourceFolderType.XML || folderType == ResourceFolderType.VALUES);
    }


    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (String title : xmlData) {
            if (!valuesData.containsKey(title)) {
                continue;
            }

            Pair<Element, Location.Handle> stringData = valuesData.get(title);
            Element element = stringData.getFirst();

            if (!element.hasAttribute(ATTR_MAX_LENGTH)) {
                String message = String.format(Locale.ENGLISH, "Preference title '%s' is missing \"maxLength=%d\" attribute", title, PREFERENCE_TITLE_MAX_LENGTH);
                context.report(ISSUE_MAX_LENGTH, stringData.getSecond().resolve(), message);

            } else if (!element.getAttribute(ATTR_MAX_LENGTH).equals(Integer.toString(PREFERENCE_TITLE_MAX_LENGTH))) {
                String message = String.format(Locale.ENGLISH, "Preference title '%s' is having maxLength=%s it should contain maxLength=%d", title, element.getAttribute(ATTR_MAX_LENGTH), PREFERENCE_TITLE_MAX_LENGTH);
                context.report(ISSUE_MAX_LENGTH, stringData.getSecond().resolve(), message);
            }

            if (element.getTextContent().length() > PREFERENCE_TITLE_MAX_LENGTH) {
                String message = String.format(Locale.ENGLISH, "Preference title '%s' must be less than %d characters (currently %d)", title, PREFERENCE_TITLE_MAX_LENGTH, element.getTextContent().length());
                context.report(ISSUE_TITLE_LENGTH, stringData.getSecond().resolve(), message);
            }
        }
    }
}