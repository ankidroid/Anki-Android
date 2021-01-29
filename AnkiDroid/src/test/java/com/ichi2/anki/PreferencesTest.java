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

package com.ichi2.anki;

import android.content.SharedPreferences;

import com.ichi2.preferences.PreferenceKeys;
import com.ichi2.preferences.Prefs;
import com.ichi2.testutils.ResourceUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.XmlRes;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;

@RunWith(AndroidJUnit4.class)
public class PreferencesTest extends RobolectricTest {

    @SuppressWarnings("unchecked")
    @Test
    public void preferenceDefaultsAreNotChangedWhenOpeningPreferences() throws IllegalAccessException {
        // 5346
        setAllDefaultPreferences();

        Field[] fields = PreferenceKeys.class.getDeclaredFields();

        List<Field> staticFields = Arrays.stream(fields)
                .filter(PreferencesTest::isPublicStaticField)
                .collect(Collectors.toList());
        assertThat(staticFields.isEmpty(), is(false));

        Prefs prefs = new Prefs(getSharedPrefs());

        HashSet<String> errors = new HashSet<>();

        for (Field f : staticFields) {
            PreferenceKeys.PreferenceKey<?> k = (PreferenceKeys.PreferenceKey<?>) f.get(null);

            assertThat(k, notNullValue());

            try {
                switch (k.defaultValue.getClass().getSimpleName()) {
                    case "Boolean":
                        assertThat(k.key, prefs.getBoolean((PreferenceKeys.PreferenceKey<Boolean>) k), is(k.defaultValue));
                        break;
                    case "Integer":
                        assertThat(k.key, prefs.getInt((PreferenceKeys.PreferenceKey<Integer>) k), is(k.defaultValue));
                        break;
                    case "Long":
                        assertThat(k.key, prefs.getLong((PreferenceKeys.PreferenceKey<Long>) k), is(k.defaultValue));
                        break;
                    case "String":
                        assertThat(k.key, prefs.getString((PreferenceKeys.PreferenceKey<String>) k), is(k.defaultValue));
                        break;
                    default: throw new IllegalStateException("Not supported: " + k.defaultValue.getClass().getSimpleName());
                }
            } catch (AssertionError e) {
                errors.add(e.getMessage().replace('\n', ' ') + "\n");
            }
        }

        assertThat(errors, empty());
    }

    @Test
    public void testSettingPreferencesChangesData() {
        // Ensures that opening settings would set the wrong default
        boolean syncFetchesMedia = getSharedPrefs().getBoolean(PreferenceKeys.DoubleScrolling.key, !PreferenceKeys.DoubleScrolling.defaultValue);

        assertThat("A value should not be set", syncFetchesMedia, not(is(PreferenceKeys.DoubleScrolling.defaultValue)));

        setAllDefaultPreferences();

        boolean newPrefValue = getSharedPrefs().getBoolean(PreferenceKeys.DoubleScrolling.key, !PreferenceKeys.DoubleScrolling.defaultValue);

        assertThat(
                "Value should be set and is the default",
                newPrefValue,
                is(PreferenceKeys.DoubleScrolling.defaultValue));
    }


    protected SharedPreferences getSharedPrefs() {
        return AnkiDroidApp.getSharedPrefs(getTargetContext());
    }


    @SuppressWarnings( {"deprecation", "RedundantSuppression"})
    private void setAllDefaultPreferences() {

        for (@XmlRes int resource : ResourceUtils.getPreferenceXml()) {
            android.preference.PreferenceManager.setDefaultValues(getTargetContext(), resource, true);
        }
    }

    private static boolean isPublicStaticField(Field f) {
        int modifiers = f.getModifiers();
        // Unit tests on CI have a private static transient field
        return Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers);
    }
}
