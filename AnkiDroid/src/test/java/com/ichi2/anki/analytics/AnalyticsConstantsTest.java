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
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(Enclosed.class)
public class AnalyticsConstantsTest {
    private static final List<String> listOfConstantFields = new ArrayList<>();


    @RunWith(Parameterized.class)
    public static class AnalyticsConstantsFieldValuesTest {
        private String analyticsString;


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


        @Test
        public void checkAnalyticsString() {
            assertEquals("There is no such string in Actions class, thus returning null.", analyticsString, getStringFromReflection(analyticsString));
        }


        public String getStringFromReflection(String analyticsStringToBeChecked) {
            String reflectedString = null;
            UsageAnalytics.Actions actions = new UsageAnalytics.Actions();
            Field[] field;
            field = actions.getClass().getDeclaredFields();
            for (int i = 0; i < field.length; i++) {
                if (field[i].isAnnotationPresent(AnalyticsConstant.class)) {
                    try {
                        if (field[i].get(actions).equals(analyticsStringToBeChecked)) {
                            reflectedString = (String) field[i].get(actions);
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
            assertEquals("Add the new constant here also in the list listOfConstantFields", listOfConstantFields.size(), getFieldSize());
        }


        /**
         * This method is used to get th size of fields in Actions Class.
         * Because whenever a new constant is added in Actions Class but not added to the list present in this
         * class (listOfConstantFields) the test must fail.
         */
        public static int getFieldSize() {
            UsageAnalytics.Actions actions = new UsageAnalytics.Actions();
            Field[] field;
            field = actions.getClass().getDeclaredFields();
            return field.length;
        }
    }
}