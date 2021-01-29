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
 *  this program.  If not
see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.testutils;

import com.ichi2.anki.R;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ResourceUtils {

    private static final HashMap<Integer, XmlType> xmlValues = new HashMap<>();

    static {
        xmlValues.put(R.xml.preferences_advanced, XmlType.PreferencesWithSettings);
        xmlValues.put(R.xml.preferences_appearance, XmlType.PreferencesWithSettings);
        xmlValues.put(R.xml.preferences_custom_buttons, XmlType.PreferencesWithSettings);
        xmlValues.put(R.xml.preferences_custom_sync_server, XmlType.PreferencesWithSettings);
        xmlValues.put(R.xml.preferences_advanced_statistics, XmlType.PreferencesWithSettings);
        xmlValues.put(R.xml.preferences_general, XmlType.PreferencesWithSettings);
        xmlValues.put(R.xml.preferences_gestures, XmlType.PreferencesWithSettings);
        xmlValues.put(R.xml.preferences_reviewing, XmlType.PreferencesWithSettings);

        // slightly different as it's a category page with no current preferences
        xmlValues.put(R.xml.preference_headers, XmlType.Unclassified);

        xmlValues.put(R.xml.deck_options, XmlType.DeckOptionsHack);
        xmlValues.put(R.xml.cram_deck_options, XmlType.DeckOptionsHack);


        xmlValues.put(R.xml.widget_provider_add_note, XmlType.Widget);
        xmlValues.put(R.xml.widget_provider_small, XmlType.Widget);

        xmlValues.put(R.xml.standalone_badge_offset, XmlType.Badge);
        xmlValues.put(R.xml.standalone_badge_gravity_top_start, XmlType.Badge);
        xmlValues.put(R.xml.standalone_badge_gravity_bottom_start, XmlType.Badge);
        xmlValues.put(R.xml.standalone_badge_gravity_bottom_end, XmlType.Badge);
        xmlValues.put(R.xml.standalone_badge, XmlType.Badge);


        xmlValues.put(R.xml.image_share_filepaths, XmlType.Paths);
        xmlValues.put(R.xml.filepaths, XmlType.Paths);


        xmlValues.put(R.xml.network_security_config, XmlType.NetSecurityConfig);

    }

    public static Collection<Integer> getPreferenceXml() {
        return xmlValues.entrySet().stream()
                .filter(x -> x.getValue() == XmlType.PreferencesWithSettings)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private enum XmlType {
        PreferencesWithSettings,
        DeckOptionsHack,
        Widget,
        Badge,
        Paths,
        NetSecurityConfig,
        Unclassified
    }



    public static Set<Integer> getAllKnownXml() {
        return xmlValues.keySet();
    }
}
