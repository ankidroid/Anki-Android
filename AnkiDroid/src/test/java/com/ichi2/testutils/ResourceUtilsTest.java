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

package com.ichi2.testutils;

import com.ichi2.anki.R;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

public class ResourceUtilsTest {
    @Test
    public void allFilesAreCategorised() throws IllegalAccessException {
        // This exists to ensure a user cannot add a Preference screen and forget to add it in PreferencesTest
        HashMap<Integer, String> allXml = getFieldValues(R.xml.class);

        Collection<Integer> knownXml = ResourceUtils.getAllKnownXml();

        for (Integer key : knownXml) {
            allXml.remove(key);
        }

        assertThat("Some XML files are not classified in ResourceUtils", allXml.values(), empty());
    }

    @NotNull
    private static <T> HashMap<Integer, String> getFieldValues(@SuppressWarnings("SameParameterValue") Class<T> clazz) throws IllegalAccessException {
        Field[] badFields = clazz.getFields();
        HashMap<Integer, String> resFields = new HashMap<>();
        for (Field f : badFields) {
            resFields.put((Integer) f.get(clazz), f.getName());
        }
        return resFields;
    }

}
