/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.preferences

import android.content.Context
import androidx.annotation.XmlRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.utils.getInstanceFromClassName
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.atomic.AtomicReference

object PreferenceTestUtils {
    private fun getAttrFromXml(
        context: Context,
        @XmlRes xml: Int,
        attrName: String,
        namespace: String = AnkiDroidApp.ANDROID_NAMESPACE,
    ): List<String> {
        val occurrences = mutableListOf<String>()

        val xrp =
            context.resources.getXml(xml).apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setFeature(XmlPullParser.FEATURE_REPORT_NAMESPACE_ATTRIBUTES, true)
            }

        while (xrp.eventType != XmlPullParser.END_DOCUMENT) {
            if (xrp.eventType == XmlPullParser.START_TAG) {
                val attr = xrp.getAttributeValue(namespace, attrName)
                if (attr != null) {
                    occurrences.add(attr)
                }
            }
            xrp.next()
        }
        return occurrences.toList()
    }

    /** @return fragments found on [xml] */
    private fun getFragmentsFromXml(
        context: Context,
        @XmlRes xml: Int,
    ): List<Fragment> {
        return getAttrFromXml(context, xml, "fragment").map { getInstanceFromClassName(it) }
    }

    /** @return recursively fragments found on [xml] and on their children **/
    private fun getFragmentsFromXmlRecursively(
        context: Context,
        @XmlRes xml: Int,
    ): List<Fragment> {
        val fragments = getFragmentsFromXml(context, xml).toMutableList()
        for (fragment in fragments.filterIsInstance<SettingsFragment>()) {
            fragments.addAll(getFragmentsFromXmlRecursively(context, fragment.preferenceResource))
        }
        return fragments.toList()
    }

    /** @return [List] of all the distinct preferences fragments **/
    fun getAllPreferencesFragments(context: Context): List<Fragment> {
        val fragments = getFragmentsFromXmlRecursively(context, R.xml.preference_headers) + HeaderFragment()
        return fragments.distinctBy { it::class } // and remove any repeated fragments
    }

    private fun attrValueToString(
        value: String,
        context: Context,
    ): String {
        return if (value.startsWith("@")) {
            context.getString(value.substring(1).toInt())
        } else {
            value
        }
    }

    fun getKeysFromXml(
        context: Context,
        @XmlRes xml: Int,
    ): List<String> {
        return getAttrFromXml(context, xml, "key").map { attrValueToString(it, context) }
    }

    fun getAllPreferenceKeys(context: Context): Set<String> {
        return getAllPreferencesFragments(context)
            .filterIsInstance<SettingsFragment>()
            .map { it.preferenceResource }
            .flatMapTo(hashSetOf()) { getKeysFromXml(context, it) }
    }

    fun getAllCustomButtonKeys(context: Context): Set<String> {
        val ret = AtomicReference<Set<String>>()
        val i = CustomButtonsSettingsFragment.getSubscreenIntent(context)
        ActivityScenario.launch<Preferences>(i).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { a: Preferences ->
                val customButtonsFragment =
                    a.supportFragmentManager
                        .findFragmentByTag(CustomButtonsSettingsFragment::class.java.name) as CustomButtonsSettingsFragment
                ret.set(customButtonsFragment.allKeys())
            }
        }
        val preferenceKeys = ret.get()?.toMutableSet() ?: throw IllegalStateException("no keys were set")
        preferenceKeys.remove("reset_custom_buttons")
        return preferenceKeys
    }
}
