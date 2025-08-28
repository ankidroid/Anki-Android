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

import com.ichi2.utils.FileOperation.Companion.getFileResource
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.json.JSONObject
import org.junit.Test
import timber.log.Timber
import java.io.File
import kotlin.test.assertEquals

class EndpointsTest {
    private val endpointsJson =
        run {
            val file = File(getFileResource("js-api-endpoints.json"))
            JSONObject(file.readText())
        }

    @Test
    fun `CURRENT_VERSION matches API version`() {
        val apiVersion = endpointsJson.getString("version")
        assertEquals(apiVersion, "1.0.0")
    }

    @Test
    fun `endpoints JSON file matches Kotlin enums`() {
        val endpoints = endpointsJson.getJSONObject("endpoints")
        val topLevelKeys =
            endpoints
                .keys()
                .asSequence()
                .toList()
                .toTypedArray()

        val endpointEnums =
            Endpoint::class.sealedSubclasses.associate { kClass ->
                val entries = kClass.java.enumConstants!!
                entries.first().base to entries
            }
        assertThat(endpointEnums.keys, containsInAnyOrder(*topLevelKeys))

        endpointEnums.forEach { (base, entries) ->
            Timber.i("Verifying endpoints for: $base")
            val jsonEndpoints =
                endpoints
                    .getJSONObject(base)
                    .keys()
                    .asSequence()
                    .toList()
                    .toTypedArray()
            val enumEndpoints = entries.map { it.value }

            assertThat(
                "Enum endpoints for '$base' should match the JSON keys",
                enumEndpoints,
                containsInAnyOrder(*jsonEndpoints),
            )
        }
    }
}
