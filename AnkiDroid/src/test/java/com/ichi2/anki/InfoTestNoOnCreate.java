/*
 Copyright (c) 2021 Jesse Tham <github@jessetham.com>

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

package com.ichi2.anki;

import com.ichi2.testutils.AnkiAssert;
import com.ichi2.testutils.EmptyApplication;
import com.ichi2.utils.LanguageUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(application = EmptyApplication.class)
public class InfoTestNoOnCreate extends RobolectricTest {

    @Parameter
    public String locale;

    @Parameters(name = "{index} locale:{0}")
    public static java.util.Collection<String> initParameters() {
        return Arrays.asList(LanguageUtil.APP_LANGUAGES);
    }

    @Before
    public void before() {
        if (locale.contains("-")) {
            String[] localeParts = locale.split("-");
            RuntimeEnvironment.setQualifiers(localeParts[0] + "-r" + localeParts[1]);
        } else {
            RuntimeEnvironment.setQualifiers(locale);
        }
    }

    @Test
    public void testCreatingActivityWithLocale() {
        AnkiAssert.assertDoesNotThrow(() -> Info.getAboutAnkiDroidHtml(getTargetContext().getResources(), "#FFFFFF"));
    }
}
