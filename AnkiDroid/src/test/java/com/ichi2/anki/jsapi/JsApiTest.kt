/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.con>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.jsapi

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.JvmTest
import com.ichi2.utils.FileOperation.Companion.getFileResource
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.collections.iterator
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class JsApiTest : JvmTest() {
    @Test
    fun `Return types match the API`() =
        runTest {
            val file = File(getFileResource("js-api-endpoints.json"))
            val endpointsJson = JSONObject(file.readText()).getJSONObject("endpoints")
            val note = addBasicNote()
            val topCard = note.cards()[0]

            fun configureData(
                data: JSONObject,
                params: JSONObject,
            ): JSONObject {
                if (params.length() < 1) return data
                params.keys().forEach { param ->
                    val paramValue =
                        when (param) {
                            "flag" -> 1
                            "search" -> "deck:current"
                            "text" -> "foo"
                            "rating" -> 1
                            "cardId" -> topCard.id
                            "colorHex" -> "#FF00FF"
                            "queueMode" -> 0
                            "locale" -> "en"
                            "pitch" -> 1F
                            "speechRate" -> 1F
                            "tags" -> "foo bar"
                            else -> throw IllegalArgumentException("Unhandled param: $param")
                        }
                    data.put(param, paramValue)
                }
                return data
            }

            for (serviceBase in endpointsJson.keys()) {
                val serviceObject = endpointsJson.getJSONObject(serviceBase)

                for (endpointString in serviceObject.keys()) {
                    val endpoint = Endpoint.from(serviceBase, endpointString)
                    if (endpoint is Endpoint.StudyScreen || endpoint is Endpoint.Android) continue

                    val methodObject = serviceObject.getJSONObject(endpointString)
                    val params = methodObject.getJSONObject("params")
                    val returnType = methodObject.getString("return")
                    val data = JSONObject()
                    configureData(data, params)
                    assertNotNull(endpoint)

                    val result = JsApi.handleEndpointRequest(endpoint, data, topCard)
                    val resultJson = JSONObject(String(result, Charsets.UTF_8))
                    assertTrue(resultJson.getBoolean("success"))

                    val resultValue = resultJson.opt("value")
                    val expectedReturnType = getJsType(resultValue)
                    assertEquals(expectedReturnType, returnType, "Unexpected return type in $serviceBase/$endpointString")
                }
            }
        }

    private fun getJsType(value: Any?): String {
        if (value == null) return "void"
        return when (value::class) {
            String::class -> "string"
            Int::class, Long::class, Double::class -> "number"
            Boolean::class -> "boolean"
            JSONArray::class -> {
                val element = (value as JSONArray).get(0)
                val type = getJsType(element)
                "$type[]"
            }
            else -> throw IllegalArgumentException("Unhandled ${value::class}")
        }
    }
}
