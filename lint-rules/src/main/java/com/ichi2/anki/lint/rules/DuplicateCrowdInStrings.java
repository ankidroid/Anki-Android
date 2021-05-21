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
 *
 *  This file incorporates work covered by the following copyright and
 *  permission notice:
 *
 *     Copyright (C) 2018 The Android Open Source Project
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 *  https://android.googlesource.com/platform/tools/base/+/studio-master-dev/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/StringCasingDetector.kt?autodive=0%2F
 */

package com.ichi2.anki.lint.rules;


import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.checks.StringCasingDetector.StringDeclaration;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.Pair;
import com.ichi2.anki.lint.utils.Constants;
import com.ichi2.anki.lint.utils.StringFormatDetector;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TRANSLATABLE;
import static com.android.SdkConstants.TAG_STRING;
import static com.android.SdkConstants.VALUE_FALSE;

public class DuplicateCrowdInStrings extends ResourceXmlDetector {

    private static Implementation IMPLEMENTATION_XML =
            new Implementation(DuplicateCrowdInStrings.class, Scope.ALL_RESOURCES_SCOPE);

    /**
     * Whether there are any duplicate strings, including capitalization adjustments.
     */
    public static Issue ISSUE = Issue.create(
            "DuplicateCrowdInStrings",
            "Duplicate Strings (CrowdIn)",
            "Duplicate strings are ambiguous for translators." +
                    "This lint check looks for duplicate strings, including differences for strings" +
                    "where the only difference is in capitalization. Title casing and all uppercase can" +
                    "all be adjusted in the layout or in code. Any duplicate strings should have a comment" +
                    "attribute added if they are intentional and required for translations.",
            Constants.ANKI_CROWDIN_CATEGORY,
            Constants.ANKI_CROWDIN_PRIORITY,
            Constants.ANKI_CROWDIN_SEVERITY,
            IMPLEMENTATION_XML);

    /*
     * Map of all locale,strings in lower case, to their raw elements to ensure that there are no
     * duplicate strings.
     */
    private final HashMap<Pair<String, String>, List<StringDeclaration>> allStrings = new HashMap<>();


    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }


    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_STRING);
    }


    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only check the golden copy - not the translated sources.
        if (!"values".equals(context.file.getParentFile().getName())) {
            return;
        }

        NodeList childNodes = element.getChildNodes();
        if (childNodes.getLength() > 0) {
            if (childNodes.getLength() == 1) {
                Node child = childNodes.item(0);
                if (child.getNodeType() == Node.TEXT_NODE) {
                    checkTextNode(
                            context,
                            element,
                            StringFormatDetector.stripQuotes(child.getNodeValue())
                    );
                }
            } else {
                StringBuilder sb = new StringBuilder();
                StringFormatDetector.addText(sb, element);
                if (sb.length() != 0) {
                    checkTextNode(context, element, sb.toString());
                }
            }
        }
    }


    private void checkTextNode(XmlContext context, Element element, String text) {
        if (VALUE_FALSE.equals(element.getAttribute(ATTR_TRANSLATABLE))) {
            return;
        }

        LocaleQualifier locale = LintUtils.getLocale(context);
        Pair<String, String> key = locale != null ?
                Pair.of(locale.getFull(), text.toLowerCase(Locale.forLanguageTag(locale.getTag()))) :
                Pair.of("default", text.toLowerCase(Locale.US));

        Location.Handle handle = context.createLocationHandle(element);
        handle.setClientData(element);
        List<StringDeclaration> handleList = allStrings.getOrDefault(key, new ArrayList<>());
        handleList.add(new StringDeclaration(element.getAttribute(ATTR_NAME), text, handle));
        allStrings.put(key, handleList);
    }


    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (List<StringDeclaration> duplicates : allStrings.values()) {
            if (duplicates.size() <= 1) {
                continue;
            }
            Location firstLocation = null;
            Location prevLocation = null;
            String prevString = "";
            boolean caseVaries = false;
            List<String> names = new ArrayList<>();

            if (duplicates.stream().allMatch(x -> {
                Element el = (Element) x.getLocation().getClientData();
                return el.hasAttribute("comment");
            })) {
                // skipping as all instances have a comment
                continue;
            }

            for (StringDeclaration duplicate : duplicates) {
                names.add(duplicate.getName());
                String string = duplicate.getText();
                Location location = duplicate.getLocation().resolve();
                if (prevLocation == null) {
                    firstLocation = location;
                } else {
                    prevLocation.setSecondary(location);
                    location.setMessage(String.format("Duplicates value in `%s`. Add a `comment` attribute on both strings to explain this duplication", names.get(0)));
                    location.setSelfExplanatory(false);
                    if (!string.equals(prevString)) {
                        caseVaries = true;
                        location.setMessage(location.getMessage() + " (case varies, but you can use " +
                                "`android:inputType` or `android:capitalize` in the " +
                                "presentation)");
                    }
                }
                prevLocation = location;
                prevString = string;
            }

            List<String> nameValues = new ArrayList<>();
            for (String name : names) {
                nameValues.add(String.format("`%s`", name));
            }


            String nameList = LintUtils.formatList(nameValues, nameValues.size(),true);
            // we use both quotes and code styling here so it appears in the console quoted
            String message = String.format("Duplicate string value \"`%s`\", used in %s", prevString, nameList);
            if (caseVaries) {
                message += ". Use `android:inputType` or `android:capitalize` " +
                        "to treat these as the same and avoid string duplication.";
            }
            context.report(ISSUE, firstLocation, message);
        }
    }
}