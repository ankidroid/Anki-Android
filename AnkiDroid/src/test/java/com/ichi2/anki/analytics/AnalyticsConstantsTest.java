/*
 Copyright (c) 2021 Mrudul Tora <mrudultora@gmail.com>
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

package com.ichi2.anki.analytics;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import androidx.annotation.NonNull;

import static org.junit.Assert.assertEquals;

/**
 * This class contains two nested classes and is using the concept of Enclosed runner that internally works as a Suite.
 * The first inner class is a Parameterized class and will run according to the size of test case, while the second
 * inner class is non-parameterized and will run only once.
 */
@RunWith(Enclosed.class)
public class AnalyticsConstantsTest {
    private final static List<String> listOfConstantFields = new ArrayList<>();

    static {
        listOfConstantFields.add("Opened HelpDialogBox");
        listOfConstantFields.add("Opened Using AnkiDroid");
        listOfConstantFields.add("Opened Get Help");
        listOfConstantFields.add("Opened Support AnkiDroid");
        listOfConstantFields.add("Opened Community");
        listOfConstantFields.add("Opened AnkiDroid Manual");
        listOfConstantFields.add("Opened Anki Manual");
        listOfConstantFields.add("Opened AnkiDroid FAQ");
        listOfConstantFields.add("Opened Mailing List");
        listOfConstantFields.add("Opened Report a Bug");
        listOfConstantFields.add("Opened Donate");
        listOfConstantFields.add("Opened Translate");
        listOfConstantFields.add("Opened Develop");
        listOfConstantFields.add("Opened Rate");
        listOfConstantFields.add("Opened Other");
        listOfConstantFields.add("Opened Send Feedback");
        listOfConstantFields.add("Opened Anki Forums");
        listOfConstantFields.add("Opened Reddit");
        listOfConstantFields.add("Opened Discord");
        listOfConstantFields.add("Opened Facebook");
        listOfConstantFields.add("Opened Twitter");
        listOfConstantFields.add("Opened Privacy");
        listOfConstantFields.add("Opened AnkiDroid Privacy Policy");
        listOfConstantFields.add("Opened AnkiWeb Privacy Policy");
        listOfConstantFields.add("Opened AnkiWeb Terms and Conditions");
        listOfConstantFields.add("Exception Report");
        listOfConstantFields.add("aedict");
        listOfConstantFields.add("leo");
        listOfConstantFields.add("colordict");
        listOfConstantFields.add("fora");
        listOfConstantFields.add("nciku");
        listOfConstantFields.add("eijiro");
        listOfConstantFields.add("Import APKG");
        listOfConstantFields.add("Import COLPKG");
    }

    @NonNull
    protected static Stream<Field> getAnalyticsConstantFields() {
        return Arrays.stream(UsageAnalytics.Actions.class.getDeclaredFields())
                .filter(x -> x.isAnnotationPresent(AnalyticsConstant.class));
    }

    @RunWith(Parameterized.class)
    public static class AnalyticsConstantsFieldValuesTest {
        private final String mAnalyticsString;


        public AnalyticsConstantsFieldValuesTest(String analyticsString) {
            this.mAnalyticsString = analyticsString;
        }


        @Parameterized.Parameters
        public static List<String> addAnalyticsConstants() {
            return listOfConstantFields;
        }


        /**
         * The message here means that the string being checked cannot be found in Actions class.
         * If encountered with this message, re-check the list present here and constants in Actions class, to resolve
         * the test failure.
         */
        @Test
        public void checkAnalyticsString() throws IllegalAccessException {
            assertEquals("Re-check if you renamed any string in the analytics string constants of Actions class or AnalyticsConstantsTest.listOfConstantFields. If so, revert them as those string constants must not change as they are compared in analytics.",
                    mAnalyticsString, getStringFromReflection(mAnalyticsString));
        }


        public String getStringFromReflection(String analyticsStringToBeChecked) throws IllegalAccessException {
            for (Field value : getAnalyticsConstantFields().collect(Collectors.toList())) {
                Object reflectedValue = value.get(null);
                if (reflectedValue.equals(analyticsStringToBeChecked)) {
                    return (String) reflectedValue;
                }
            }
            return null;
        }

    }


    public static class AnalyticsConstantsFieldLengthTest {

        @Test
        public void fieldSizeEqualsListOfConstantFields() {
            if (getFieldSize() > listOfConstantFields.size()) {
                assertEquals("Add the newly added analytics constant to AnalyticsConstantsTest.listOfConstantFields. NOTE: Constants should not be renamed as we cannot compare these in analytics.",
                        listOfConstantFields.size(), getFieldSize());
            } else if (getFieldSize() < listOfConstantFields.size()) {
                assertEquals("If a constant is removed, it should be removed from AnalyticsConstantsTest.listOfConstantFields. NOTE: Constants should not be renamed as we cannot compare these in analytics.",
                        listOfConstantFields.size(), getFieldSize());
            } else {
                assertEquals(listOfConstantFields.size(), getFieldSize());
            }

        }


        /**
         * This method is used to get the size of fields in Actions Class.
         * Because whenever a new constant is added in Actions Class but not added to the list present in this
         * class (listOfConstantFields) the test must fail.
         */
        public static long getFieldSize() {
            return getAnalyticsConstantFields().count();
        }


        /**
         * This test is used to check whether all the string constants of Actions are annotated with @AnalyticsConstant.
         * If not, then a runtime exception is thrown.
         */
        @Test
        public void fieldAnnotatedOrNot(){
            UsageAnalytics.Actions actions = new UsageAnalytics.Actions();
            Field[] field;
            field = actions.getClass().getDeclaredFields();
            for (Field value : field) {
                if (!value.isAnnotationPresent(AnalyticsConstant.class) && !value.isSynthetic()) {
                    throw new RuntimeException("All the fields in Actions class must be annotated with @AnalyticsConstant. It seems " + value.getName() + " is not annotated.");
                }
            }
        }
    }
}
