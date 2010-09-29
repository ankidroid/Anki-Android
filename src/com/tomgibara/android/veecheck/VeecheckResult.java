/*
 * Copyright 2008 Tom Gibara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tomgibara.android.veecheck;

import static com.tomgibara.android.veecheck.Veecheck.XML_NAMESPACE;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Instances of this class are used to parse 'intentional' information for a specified version.
 * 
 * @author Tom Gibara
 */

class VeecheckResult extends DefaultHandler {

    /**
     * The local name of the version element in the {@link Veecheck#XML_NAMESPACE}.
     */

    private static final String VERSION_TAG = "version";

    /**
     * The local name of the intent element in the {@link Veecheck#XML_NAMESPACE}.
     */

    private static final String INTENT_TAG = "intent";

    /**
     * A pattern used to split an attribute value into key/value pairs.
     */

    private static final Pattern SEMI_SPLIT = Pattern.compile(";");

    /**
     * A pattern used to parse key/value pairs.
     */

    private static final Pattern KEY_VALUE = Pattern.compile("\\s*(\\S+)\\s*:(.*)");


    /**
     * Parses a string into a map of keys to values. The format is akin to that used for the CSS style attribute in HTML
     * (semicolon delimited, colon separated key/value pairs) but no escaping of colons or semicolons is supported.
     * 
     * @param str the string to be parsed, may be null
     * @return a map of key/value pairs, never null
     */

    public static Map<String, String> toMap(final String str) {
        HashMap<String, String> map = new HashMap<String, String>();
        if (str != null) {
            String[] propArr = SEMI_SPLIT.split(str);
            for (String propStr : propArr) {
                Matcher kvMatcher = KEY_VALUE.matcher(propStr);
                // we ignore non matches
                if (!kvMatcher.matches()) {
                    continue;
                }
                String key = kvMatcher.group(1);
                if (map.containsKey(key)) {
                    throw new IllegalArgumentException(String.format("Duplicate key: %s", key));
                }
                String value = kvMatcher.group(2).trim();
                map.put(key, value);
            }
        }
        return map;
    }

    /**
     * The version against which version elements will be compared until a matching version is found.
     */

    final VeecheckVersion version;

    /**
     * Whether the intent contained within the current version element should provide the return values.
     */
    private boolean recordNext = false;

    /**
     * Whether the match has been determined and all subsequent processing can be skipped.
     */

    private boolean skip = false;

    /**
     * Whether this result object found intent information that matched the specified version.
     */

    boolean matched;

    /**
     * The intent action, if any, for the specified version.
     */

    String action = null;

    /**
     * The content type, if any, for the specified version.
     */

    String type = null;

    /**
     * The data uri, if any, for the specified version.
     */

    String data = null;

    /**
     * The extra properties, if any, for the specified version.
     */

    Map<String, String> extras = null;


    /**
     * Constructs a new {@link ContentHandler} that can be supplied to a SAX parser for the purpose of identifying
     * intent information for a given application version
     * 
     * @param version information about an application
     */

    public VeecheckResult(VeecheckVersion version) {
        this.version = version;
    }


    @Override
    public void startElement(String uri, String localName, String qName, Attributes attrs) throws SAXException {
        if (skip) {
            return; // nothing else to do
        }
        if (!uri.equals(XML_NAMESPACE)) {
            return;
        }
        if (recordNext) {
            if (!localName.equals(INTENT_TAG)) {
                return;
            }
            action = attrs.getValue("action");
            data = attrs.getValue("data");
            type = attrs.getValue("type");
            extras = toMap(attrs.getValue("extras"));
            recordMatch(true);
        } else {
            if (!localName.equals(VERSION_TAG)) {
                return;
            }
            VeecheckVersion version = new VeecheckVersion(attrs);
            recordNext = this.version.matches(version);
        }
    }


    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (skip) {
            return; // nothing else to do
        }
        if (!uri.equals(XML_NAMESPACE)) {
            return;
        }
        if (localName.equals(VERSION_TAG)) {
            if (recordNext) {
                recordMatch(false);
            }
        }
    }


    private void recordMatch(boolean matched) {
        recordNext = false;
        this.matched = matched;
        skip = true;
    }

}
