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
import androidx.annotation.VisibleForTesting;

/**
 * Same as org.json.JSON but all exceptions are unchecked
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

    protected abstract static class ObjectToJsonValue<T> {
        /**
         * This should be T exactly and not a subtype
         */
        private final Class<T> mType;
        private final T mDefaultValue;

        protected ObjectToJsonValue(Class<T> clazz, T defaultValue) {
            mType = clazz;
            mDefaultValue = defaultValue;
        }

        @VisibleForTesting
        public String getTypeName() {
            return mType.getName();
        }


        /**
         * @param value Any value not of type T
         * @param defaultValue The value to return if transformation is impossible
         * @return      An equivalent value of type T, or defaultValue if impossible
         */
        protected abstract @Nullable T convertFromDistinctType(@NonNull Object value, T defaultValue);


        /**
         * @param value Any value not of type T
         * @return  An equivalent value of type T, or defaultValue if impossible
         */
        protected @Nullable T convertFromDistinctType(@NonNull Object value){
            return convertFromDistinctType(value, mDefaultValue);
        }

         /**
         * @param value A value of any type
         * @return      An equivalent value of type T, using transform if necessary.
         * mDefault if impossible to convert or null
         */
        protected @Nullable T convertIfNecessaryOrNull(@NonNull Object value) {
            return convertIfNecessaryOrNull(value, mDefaultValue);
        }

        /**
         * @param value A value of any type
         * @param defaultValue The value to return if transformation is impossible
         * @return      An equivalent value of type T, using transform if necessary.
         * Default if impossible to convert or null
         */
        protected @Nullable T convertIfNecessaryOrNull(@NonNull Object value, T defaultValue) {
            if (value == null) {
                return defaultValue;
            }
            if (value.getClass().equals(mType)) {
                return (T) value;
            }
            return convertFromDistinctType(value, defaultValue);
        }


        /**
         * @param value A value of any type
         * @param defaultValue The value to return if transformation is impossible
         * @return      An equivalent value of type T, using transform if necessary.
         * Default if impossible or if input is null
         */
        protected @Nullable T convertIfNecessary(@NonNull Object value, T defaultValue) {
            if (value.getClass().equals(mType)) {
                return (T) value;
            }
            return convertFromDistinctType(value, defaultValue);
        }


        /**
         * @param value A value of any type
         * @return      An equivalent value of type T, using transform if necessary
         */
        protected @Nullable T convertIfNecessary(@NonNull Object value) {
            return convertIfNecessary(value, mDefaultValue);
        }

        /**
         * Try to convert <code>value<code/> to type <code>type<code/>
         *
         * @param indexOrName used to give the thrown exception more context
         * @param value       A value received from a JsonNode
         * @return converted value
         * @throws JSONException if value couldn't be converted
         */
        public @NonNull T convert(@NonNull Object indexOrName, @NonNull Object value) {
            T converted = convertIfNecessary(value, null);
            if (converted == null) {
                throw JSONTypeConverters.typeMismatch(indexOrName, value, mType.getName());
            }
            return converted;
        }
    }

    public static ObjectToJsonValue<Boolean> sToBoolean = new ObjectToJsonValue<Boolean>(Boolean.class, false) {
        @Nullable protected Boolean convertFromDistinctType(Object value, Boolean defaultValue) {
            if (value instanceof String) {
                String stringValue = (String) value;
                if ("true".equalsIgnoreCase(stringValue)) {
                    return true;
                } else if ("false".equalsIgnoreCase(stringValue)) {
                    return false;
                }
            }
            return defaultValue;
        }
    };

    public static ObjectToJsonValue<Double> sToDouble = new ObjectToJsonValue<Double>(Double.class, Double.NaN) {
        @Nullable protected Double convertFromDistinctType(Object value, Double defaultValue) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            } else if (value instanceof String) {
                try {
                    return Double.valueOf((String) value);
                } catch (NumberFormatException ignored) {
                }
            }
            return defaultValue;
        }
    };

    public static ObjectToJsonValue<Integer> sToInteger = new ObjectToJsonValue<Integer>(Integer.class, 0) {
        protected @Nullable Integer convertFromDistinctType(Object value, Integer defaultValue) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                try {
                    return (int) Double.parseDouble((String) value);
                } catch (NumberFormatException ignored) {
                }
            }
            return defaultValue;
        }
    };

    public static ObjectToJsonValue<Long> sToLong = new ObjectToJsonValue<Long>(Long.class, 0L) {
        protected @Nullable Long convertFromDistinctType(Object value, Long defaultValue) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            } else if (value instanceof String) {
                try {
                    return (long) Double.parseDouble((String) value);
                } catch (NumberFormatException ignored) {
                }
            }
            return defaultValue;
        }
    };

    private static class ObjectToJsonValueNoConversion<T> extends ObjectToJsonValue<T> {
        protected ObjectToJsonValueNoConversion(Class<T> clazz) {
            super(clazz, null);
        }

        @Override
        protected T convertFromDistinctType(@NonNull Object value, T defaultValue) {
            return defaultValue;
        }
    }
    public static ObjectToJsonValue<JSONObject> sToObject = new ObjectToJsonValueNoConversion<>(JSONObject.class);
    public static ObjectToJsonValue<JSONArray> sToArray = new ObjectToJsonValueNoConversion<>(JSONArray.class);


    public static ObjectToJsonValue<String> sToString = new ObjectToJsonValue<String>(String.class, "") {
        protected @Nullable String convertFromDistinctType(Object value, String defaultValue) {
            if (value != null) {
                return String.valueOf(value);
            }
            return defaultValue;
        }
    };

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