/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.libanki

import org.json.JSONArray
import org.json.JSONObject

class Config(configStr: String) : ConfigManager() {
    override var json = JSONObject(configStr)

    override fun has(key: String) = json.has(key)
    override fun isNull(key: String) = json.isNull(key)
    override fun getString(key: String) = json.getString(key)
    override fun getBoolean(key: String) = json.getBoolean(key)
    override fun getDouble(key: String) = json.getDouble(key)
    override fun getInt(key: String) = json.getInt(key)
    override fun getLong(key: String) = json.getLong(key)
    override fun getJSONArray(key: String) = json.getJSONArray(key)
    override fun getJSONObject(key: String) = json.getJSONObject(key)

    override fun put(key: String, value: Boolean) {
        json.put(key, value)
    }
    override fun put(key: String, value: Long) {
        json.put(key, value)
    }

    override fun put(key: String, value: Int) {
        json.put(key, value)
    }

    override fun put(key: String, value: Double) {
        json.put(key, value)
    }

    override fun put(key: String, value: String) {
        json.put(key, value)
    }

    override fun put(key: String, value: JSONArray) {
        json.put(key, value)
    }

    override fun put(key: String, value: JSONObject) {
        json.put(key, value)
    }

    override fun put(key: String, value: Any?) {
        json.put(key, value)
    }

    override fun remove(key: String) {
        json.remove(key)
    }
}
