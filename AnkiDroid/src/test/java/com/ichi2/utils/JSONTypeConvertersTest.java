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

import static com.ichi2.utils.JSONTypeConverters.sToBoolean;
import static com.ichi2.utils.JSONTypeConverters.sToDouble;
import static com.ichi2.utils.JSONTypeConverters.sToInteger;
import static com.ichi2.utils.JSONTypeConverters.sToLong;
import static com.ichi2.utils.JSONTypeConverters.sToString;
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
                    {false, sToBoolean, false},
                    {true, sToBoolean, true},
                    {"True", sToBoolean, true},
                    {"FaLse", sToBoolean, false},
                    {"123deas", sToBoolean, null},

                    {1L, sToDouble, 1D},
                    {23, sToDouble, 23D},
                    {"56.5", sToDouble, 56.5D},
                    {"as2", sToDouble, null},

                    {1L, sToInteger, 1},
                    {23, sToInteger, 23},
                    {232.2D, sToInteger, 232},
                    {"232", sToInteger, 232},
                    {"22.2", sToInteger, 22},
                    {"as2", sToInteger, null},

                    {1L, sToLong, 1L},
                    {23, sToLong, 23L},
                    {232.2D, sToLong, 232L},
                    {"232", sToLong, 232L},
                    {"22.2", sToLong, 22L},
                    {"as2", sToLong, null},


                    {1L, sToString, "1"},
                    {23, sToString, "23"},
                    {232.2D, sToString, "232.2"},
                    {"232", sToString, "232"},
                    {"22.2", sToString, "22.2"},
                    {"as2", sToString, "as2"},
                    {customObjectInstance, sToString, "CUSTOM_OBJECT"}

            });
        }


        @Parameter(0)
        public Object object;

        @Parameter(1)
        public JSONTypeConverters.ObjectToJsonValue<?> converter;

        @Parameter(2)
        public Object expected;


        @Test
        public void test() {
            Object result;
            try {
                result = converter.convert(INDEX_OR_NAME, object);
            } catch (JSONException e) {
                if (expected != null) {
                    fail("Didn't expect to throw JSONException for trying to convert " + object + " to type " + converter.getTypeName());
                }
                Object opt = converter.convertIfNecessary(object);
                assertNull(opt);
                return;
            }
            if (expected == null) {
                fail("Expected to throw JSONException for trying to convert " + object + " to type " + converter.getTypeName() + ", but got " + result + " as a value");
            }

            assertEquals(expected.getClass(), result.getClass());
            assertEquals(expected, result);
        }
    }

}