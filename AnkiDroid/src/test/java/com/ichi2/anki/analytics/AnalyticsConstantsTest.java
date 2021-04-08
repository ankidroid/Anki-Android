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

import static org.junit.Assert.assertEquals;

/**
 * This class contains two nested classes and is using the concept of Enclosed runner that internally works as a Suite.
 * The first inner class is a Parameterized class and will run according to the size of test case, while the second
 * inner class is non-parameterized and will run only once.
 */
@RunWith(Enclosed.class)
public class AnalyticsConstantsTest {
    private static final List<String> listOfConstantFields = new ArrayList<>();


    @RunWith(Parameterized.class)
    public static class AnalyticsConstantsFieldValuesTest {
        private final String analyticsString;


        public AnalyticsConstantsFieldValuesTest(String analyticsString) {
            this.analyticsString = analyticsString;
        }


        @Parameterized.Parameters
        public static List<String> addAnalyticsConstants() {
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
            listOfConstantFields.add("Exception Report");
            listOfConstantFields.add("aedict");
            listOfConstantFields.add("leo");
            listOfConstantFields.add("colordict");
            listOfConstantFields.add("fora");
            listOfConstantFields.add("nciku");
            listOfConstantFields.add("eijiro");
            return listOfConstantFields;
        }


        /**
         * The message here means that the string being checked cannot be found in Actions class.
         * If encountered with this message, re-check the list present here and constants in Actions class, to resolve
         * the test failure.
         */
        @Test
        public void checkAnalyticsString() {
            assertEquals("Re-check if you renamed any string in the analytics string constants of Actions class or AnalyticsConstantsTest.listOfConstantFields. If so, revert them as those string constants must not change as they are compared in analytics.",
                    analyticsString, getStringFromReflection(analyticsString));
        }


        public String getStringFromReflection(String analyticsStringToBeChecked) {
            String reflectedString = null;
            UsageAnalytics.Actions actions = new UsageAnalytics.Actions();
            Field[] field;
            field = actions.getClass().getDeclaredFields();
            for (Field value : field) {
                if (value.isAnnotationPresent(AnalyticsConstant.class)) {
                    try {
                        if (value.get(actions).equals(analyticsStringToBeChecked)) {
                            reflectedString = (String) value.get(actions);
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException();
                    }
                }
            }
            return reflectedString;
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
            return Arrays.stream(UsageAnalytics.Actions.class.getDeclaredFields())
                    .filter(x -> x.isAnnotationPresent(AnalyticsConstant.class))
                    .count();
        }
    }
}
