/*
 *  Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>
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
package com.ichi2.utils;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

import androidx.annotation.NonNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@RunWith(Enclosed.class)
public class JSONTypeConvertersTest {

    public static final String INDEX_OR_NAME = "INDEX_OR_NAME";


    @RunWith(Parameterized.class)
    public static class ConversionTest<T> {

        public static class CustomObject {
            @NonNull
            @Override
            public String toString() {
                return "CUSTOM_OBJECT";
            }
        }

        public static CustomObject customObjectInstance = new CustomObject();

        @Parameters(name = "{index}: ({0}, {1}, {2})")
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                    // object, type, expected
                    {false, Boolean.class, false},
                    {true, Boolean.class, true},
                    {"True", Boolean.class, true},
                    {"FaLse", Boolean.class, false},
                    {"123deas", Boolean.class, null},

                    {1L, Double.class, 1D},
                    {23, Double.class, 23D},
                    {"56.5", Double.class, 56.5D},
                    {"as2", Double.class, null},

                    {1L, Integer.class, 1},
                    {23, Integer.class, 23},
                    {232.2D, Integer.class, 232},
                    {"232", Integer.class, 232},
                    {"22.2", Integer.class, 22},
                    {"as2", Integer.class, null},

                    {1L, Long.class, 1L},
                    {23, Long.class, 23L},
                    {232.2D, Long.class, 232L},
                    {"232", Long.class, 232L},
                    {"22.2", Long.class, 22L},
                    {"as2", Long.class, null},


                    {1L, String.class, "1"},
                    {23, String.class, "23"},
                    {232.2D, String.class, "232.2"},
                    {"232", String.class, "232"},
                    {"22.2", String.class, "22.2"},
                    {"as2", String.class, "as2"},
                    {customObjectInstance, String.class, "CUSTOM_OBJECT"},

                    {customObjectInstance, CustomObject.class, customObjectInstance},
                    {22, CustomObject.class, null},
                    {"customObjectInstance", CustomObject.class, null},

            });
        }


        @Parameter(0)
        public Object object;

        @Parameter(1)
        public Class<?> type;

        @Parameter(2)
        public Object expected;


        @Test
        public void test() {
            Object result;
            try {
                result = JSONTypeConverters.convert(INDEX_OR_NAME, object, type);
            } catch (JSONException e) {
                if (expected != null) {
                    fail("Didn't expect to throw JSONException for trying to convert " + object + " to type " + type.getName());
                }
                Object opt = JSONTypeConverters.convertOr(object, type, null);
                assertNull(opt);
                return;
            }
            if (expected == null) {
                fail("Expected to throw JSONException for trying to convert " + object + " to type " + type.getName() + ", but got " + result + " as a value");
            }

            assertEquals(expected.getClass(), result.getClass());
            assertEquals(expected, result);
        }
    }

}