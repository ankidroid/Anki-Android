//noinspection MissingCopyrightHeader #8659
/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ichi2.utils

import org.json.JSONException

object JSON {
    /**
     * Returns the input if it is a JSON-permissible value; throws otherwise.
     */
    @Throws(JSONException::class)
    fun checkDouble(d: Double): Double {
        if (java.lang.Double.isInfinite(d) || java.lang.Double.isNaN(d)) {
            throw JSONException("Forbidden numeric value: $d")
        }
        return d
    }

    fun toString(value: Any?): String? {
        if (value is String) {
            return value
        } else if (value != null) {
            return value.toString()
        }
        return null
    }
}
