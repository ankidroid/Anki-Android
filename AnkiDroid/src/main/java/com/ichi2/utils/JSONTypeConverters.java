/*
 *  Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>
 *
 *  This file is free software: you may copy, redistribute and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or (at your
 *  option) any later version.
 *
 *  This file is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  This file incorporates work covered by the following copyright and
 *  permission notice:
 *
 *    Copyright (C) 2010 The Android Open Source Project
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.ichi2.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Same as org.json.JSON but all exceptions are unchecked
 * <p>
 * extra method not found in `org.json.JSON` is:
 * - {@link #convert(Object, Object, Class)}
 * - {@link #convertOr(Object, Class, Object)}
 */
public class JSONTypeConverters {
    /**
     * Returns the input if it is a JSON-permissible value; throws otherwise.
     */
    public static double checkDouble(double d) throws JSONException {
        if (Double.isInfinite(d) || Double.isNaN(d)) {
            throw new JSONException("Forbidden numeric value: " + d);
        }
        return d;
    }


    /**
     * Try to convert <code>value<code/> to type <code>type<code/>
     *
     * @param indexOrName used to give the thrown exception more context
     * @return converted value
     * @throws JSONException if value couldn't be converted
     */
    @NonNull
    public static <T> T convert(@NonNull Object indexOrName, @NonNull Object value, @NonNull Class<T> type) {
        T converted = convertOr(value, type, null);
        if (converted == null) {
            throw JSONTypeConverters.typeMismatch(indexOrName, value, type.getName());
        }
        return converted;
    }


    /**
     * Try to convert <code>value<code/> to type <code>type<code/>
     *
     * @param type class of the type to convert to, don't use primitive classes
     *             for example instead of <code>int.class<code/> use <code>Integer.class<code/>
     *
     * @return converted value, or <code>defaultValue<code/>
     * if it couldn't be converted
     */
    @SuppressWarnings("unchecked")
    public static <T> T convertOr(@NonNull Object value, @NonNull Class<T> type, T defaultValue) {
        if (value.getClass().equals(type)) {
            return (T) value;
        }

        T converted = null;

        if (type.equals(Boolean.class)) {
            converted = (T) JSONTypeConverters.toBoolean(value);
        } else if (type.equals(Double.class)) {
            converted = (T) JSONTypeConverters.toDouble(value);
        } else if (type.equals(Integer.class)) {
            converted = (T) JSONTypeConverters.toInteger(value);
        } else if (type.equals(Long.class)) {
            converted = (T) JSONTypeConverters.toLong(value);
        } else if (type.equals(String.class)) {
            converted = (T) JSONTypeConverters.toString(value);
        }

        if (converted == null) {
            return defaultValue;
        }

        return converted;
    }




    /**
     * @return <code>value<code/> converted to {@link Boolean}, or null if
     * value couldn't be converted
     */
    @Nullable
    public static Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            String stringValue = (String) value;
            if ("true".equalsIgnoreCase(stringValue)) {
                return true;
            } else if ("false".equalsIgnoreCase(stringValue)) {
                return false;
            }
        }
        return null;
    }


    /**
     * @return <code>value<code/> converted to {@link Double}, or null if
     * value couldn't be converted
     */
    @Nullable
    public static Double toDouble(Object value) {
        if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.valueOf((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }


    /**
     * @return <code>value<code/> converted to {@link Integer}, or null if
     * value couldn't be converted
     */
    @Nullable
    public static Integer toInteger(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return (int) Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }


    /**
     * @return <code>value<code/> converted to {@link Long}, or null if
     * value couldn't be converted
     */
    @Nullable
    public static Long toLong(Object value) {
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            try {
                return (long) Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }


    /**
     * @return <code>value<code/> converted to {@link String}, or null if
     * value couldn't be converted
     */
    @Nullable
    public static String toString(Object value) {
        if (value instanceof String) {
            return (String) value;
        } else if (value != null) {
            return String.valueOf(value);
        }
        return null;
    }


    @NonNull
    public static JSONException typeMismatch(@Nullable Object indexOrName, @Nullable Object actual,
                                             @NonNull String requiredType) throws JSONException {
        String objLocation = indexOrName == null ? "" : ("at " + indexOrName);
        if (actual == null) {
            throw new JSONException("Value " + objLocation + " is null.");
        } else {
            throw new JSONException("Value " + actual + " " + objLocation
                    + " of type " + actual.getClass().getName()
                    + " cannot be converted to " + requiredType);
        }
    }


    @NonNull
    public static JSONException typeMismatch(Object actual, String requiredType)
            throws JSONException {
        return typeMismatch(null, actual, requiredType);
    }
}