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

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class AnalyticsConstantsTest {
    private final List<String> listOfConstantFields = new ArrayList<>();


    @Before
    public void addAnalyticsConstants() {
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
    }


    @Test
    public void checkAnalyticsString() {
        UsageAnalytics.Actions actions = new UsageAnalytics.Actions();
        Field[] field;
        field = actions.getClass().getDeclaredFields();
        for (int i = 0; i < field.length; i++) {
            if (field[i].isAnnotationPresent(AnalyticsConstant.class)) {
                try {
                    assertThat(field[i].get(actions), is(listOfConstantFields.get(i)));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}