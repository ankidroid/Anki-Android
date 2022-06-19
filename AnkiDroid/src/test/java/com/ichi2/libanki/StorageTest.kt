/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

import androidx.core.util.Pair
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.JsonUtils.toOrderedString
import com.ichi2.utils.fromMap
import com.ichi2.utils.toStringList
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*
import java.util.stream.Collectors
import kotlin.math.min

/** Regression test for Rust  */
@RunWith(AndroidJUnit4::class)
open class StorageTest : RobolectricTest() {
    override fun useLegacyHelper(): Boolean {
        return true
    }

    override fun setUp() {
        super.setUp()
    }

    @Test
    fun compareNewDatabases() {
        val expected = results

        // If you don't tear down the database you'll get unexpected IllegalStateExceptions related to connections
        CollectionHelper.instance.closeCollection(false, "compareNewDatabases")

        // After every test make sure the CollectionHelper is no longer overridden (done for null testing)
        disableNullCollection()
        val actual = results
        actual.assertEqualTo(expected)
    }

    private val results: CollectionData
        get() {
            val results = CollectionData()
            results.loadFromCollection(col)
            return results
        }

    open class CollectionData {
        companion object {
            const val CONF = 8
            const val MODELS = 9
            const val DECKS = 10
            const val DCONF = 11
            const val TAGS = 12
            val M_V_11_ONLY_COLUMNS = HashSet<Int>()

            init {
                M_V_11_ONLY_COLUMNS.add(CONF)
                M_V_11_ONLY_COLUMNS.add(MODELS)
                M_V_11_ONLY_COLUMNS.add(DECKS)
                M_V_11_ONLY_COLUMNS.add(DCONF)
                M_V_11_ONLY_COLUMNS.add(TAGS)
            }
        }

        lateinit var id: String
        private lateinit var crt: String
        lateinit var mod: String
        private lateinit var scm: String
        private lateinit var ver: String
        private lateinit var dty: String
        private lateinit var usn: String
        private lateinit var ls: String
        lateinit var conf: String
        lateinit var models: String
        lateinit var decks: String
        private lateinit var dConf: String
        lateinit var tags: String

        fun loadFromCollection(col: Collection) {
            if (col is CollectionV16) {
                loadV16(col)
            } else {
                loadV11(col)
            }
        }

        private fun loadV16(col: Collection) {
            col.db.query("select * from col").use { c ->
                c.moveToFirst()
                for (i in 0 until c.columnCount) {
                    if (M_V_11_ONLY_COLUMNS.contains(i)) {
                        MatcherAssert.assertThat(c.getString(i), Matchers.emptyOrNullString())
                        continue
                    }
                    loadV11(i, c.getString(i))
                }
            }
            conf = col.conf.toString()
            models = loadModelsV16(col)
            decks = loadDecksV16(col)
            dConf = loadDConf(col)
            tags = fromMap(
                col.tags.all().stream()
                    .map { x: String -> Pair(x, 0) }
                    .collect(Collectors.toMap({ x: Pair<String, Int> -> x.first }, { x: Pair<String, Int> -> x.second }))
            )
                .toString()
        }

        private fun loadDecksV16(col: Collection): String {
            val ret = JSONObject()
            for (deck in col.decks.all()) {
                ret.put(deck.getString("id"), deck)
            }
            return ret.toString(0)
        }

        private fun loadDConf(col: Collection): String {
            val ret = JSONObject()
            for (dconf in col.decks.allConf()) {
                ret.put(dconf.getString("id"), dconf)
            }
            return ret.toString(0)
        }

        /** Extract models from models.all() and reformat as the JSON style used in the `col.models` column  */
        private fun loadModelsV16(col: Collection): String {
            val ret = JSONObject()
            for (m in col.models.all()) {
                ret.put(m.getString("id"), m)
            }
            return ret.toString(0)
        }

        private fun loadV11(col: Collection) {
            col.db.query("select * from col").use { c ->
                c.moveToFirst()
                for (i in 0 until c.columnCount) {
                    loadV11(i, c.getString(i))
                }
            }
        }

        private fun loadV11(i: Int, string: String) {
            when (i) {
                0 -> id = string
                1 -> crt = string
                2 -> mod = string
                3 -> scm = string
                4 -> ver = string
                5 -> dty = string
                6 -> usn = string
                7 -> ls = string
                CONF -> conf = string
                MODELS -> models = string
                DECKS -> decks = string
                DCONF -> dConf = string
                TAGS -> tags = string
                else -> throw IllegalStateException("unknown i: $i")
            }
        }

        fun assertEqualTo(expected: CollectionData) {
            MatcherAssert.assertThat(id, Matchers.equalTo(expected.id))
            // ignore due to timestamp: mCrt
            // ignore due to timestamp: mMod
            // ignore due to timestamp: mScm
            MatcherAssert.assertThat(ver, Matchers.equalTo(expected.ver))
            MatcherAssert.assertThat(dty, Matchers.equalTo(expected.dty))
            MatcherAssert.assertThat(usn, Matchers.equalTo(expected.usn))
            MatcherAssert.assertThat(ls, Matchers.equalTo(expected.ls))
            assertConfEqual(expected)
            assertModelsEqual(expected)
            assertJsonEqual(decks, expected.decks, "mod")
            assertDConfEqual(dConf, expected.dConf)
            MatcherAssert.assertThat(tags, Matchers.equalTo(expected.tags))
        }

        private fun assertDConfEqual(actualConf: String, expectedConf: String) {
            val actualConfiguration = removeUnusedNewIntervalValue(actualConf)
            val expectedConfiguration = removeUnusedNewIntervalValue(expectedConf)
            assertJsonEqual(actualConfiguration, expectedConfiguration)
        }

        private fun removeUnusedNewIntervalValue(actualDecks: String): String {
            // remove ints[2] - this is unused. And Anki Desktop is inconsistent with the initial value

            // permalinks for defaults (0 is used):
            // 0: https://github.com/ankitects/anki/blob/7ba35b7249e1ac829843f365105a13c6209d4f57/rslib/src/deckconfig/schema11.rs#L340
            // 7: https://github.com/ankitects/anki/blob/7ba35b7249e1ac829843f365105a13c6209d4f57/rslib/src/deckconfig/schema11.rs#L92
            val obj = JSONObject(actualDecks)
            for (key in obj.names()!!.toStringList()) {
                obj.getJSONObject(key).getJSONObject("new").getJSONArray("ints").remove(2)
            }
            return obj.toString()
        }

        private fun assertJsonEqual(actual: String, expected: String, vararg keysToRemove: String) {
            val expectedRawJson = JSONObject(expected)
            val actualRawJson = JSONObject(actual)
            for (k in keysToRemove) {
                removeFromAllObjects(expectedRawJson, actualRawJson, k)
            }
            val expectedJson = expectedRawJson.toOrderedString()
            val actualJson = actualRawJson.toOrderedString()
            MatcherAssert.assertThat(actualJson, Matchers.equalTo(expectedJson))
        }

        /** Removes a given key from all sub-objects, example: for all deck ids, remove the "name"  */
        private fun removeFromAllObjects(actualJson: JSONObject, expectedJson: JSONObject, key: String) {
            for (id in actualJson.keys()) {
                actualJson.getJSONObject(id).remove(key)
            }
            for (id in expectedJson.keys()) {
                expectedJson.getJSONObject(id).remove(key)
            }
        }

        private fun assertModelsEqual(expectedData: CollectionData) {
            val actualJson = JSONObject(models)
            val expectedJson = JSONObject(expectedData.models)
            renameKeys(actualJson)
            renameKeys(expectedJson)
            for (k in actualJson.keys()) {
                val actualJsonModel = actualJson.getJSONObject(k)
                val expectedJsonModel = expectedJson.getJSONObject(k)
                remove(actualJsonModel, expectedJsonModel, "id")
                // mod is set in V11, but not in V16
                remove(actualJsonModel, expectedJsonModel, "mod")
                val name = actualJsonModel.getString("name")
                if ("Basic (type in the answer)" == name || "Cloze" == name) {
                    remove(actualJsonModel, expectedJsonModel, "req")
                }
                removeSingletonReq(actualJsonModel, expectedJsonModel)
            }
            val actual = actualJson.toOrderedString()
            val expected = expectedJson.toOrderedString()
            MatcherAssert.assertThat(actual, Matchers.equalTo(expected))
        }

        /** A req over a singleton can either be "any" or "all". Remove singletons which match  */
        private fun removeSingletonReq(actualJson: JSONObject, expectedJson: JSONObject) {
            val areq = actualJson.optJSONArray("req")
            val ereq = expectedJson.optJSONArray("req")
            if (areq == null || ereq == null) {
                return
            }
            val toRemove: MutableList<Int> = ArrayList()
            for (i in 0 until min(areq.length(), ereq.length())) {
                val a = areq.getJSONArray(i)
                val e = ereq.getJSONArray(i)
                if (areEqualSingletonReqs(a, e)) {
                    toRemove.add(i)
                }
            }
            toRemove.reverse()
            for (i in toRemove) {
                areq.remove(i)
                ereq.remove(i)
            }
        }

        private fun areEqualSingletonReqs(a: JSONArray, e: JSONArray): Boolean {
            val areq = a.getJSONArray(2)
            val breq = e.getJSONArray(2)
            return if (areq.length() != 1 || breq.length() != 1) {
                false
            } else {
                areq.getInt(0) == breq.getInt(0)
            }
        }

        private fun assertConfEqual(expectedData: CollectionData) {
            val actualJson = JSONObject(conf)
            val expectedJson = JSONObject(expectedData.conf)
            val curModel = actualJson.getLong("curModel")
            val curModelEx = expectedJson.getLong("curModel")
            assertModelIdsEqual(curModel, curModelEx, expectedData)
            remove(actualJson, expectedJson, "curModel")
            remove(actualJson, expectedJson, "creationOffset")
            remove(actualJson, expectedJson, "localOffset")
            val actual = actualJson.toOrderedString()
            val expected = expectedJson.toOrderedString()
            MatcherAssert.assertThat(actual, Matchers.equalTo(expected))

            // regression: curModel
        }

        private fun assertModelIdsEqual(actualMid: Long, expectedMid: Long, expectedData: CollectionData) {
            val actual = JSONObject(models).getJSONObject(actualMid.toString()).getString("name")
            val expected = JSONObject(expectedData.models).getJSONObject(expectedMid.toString()).getString("name")
            MatcherAssert.assertThat("current model", actual, Matchers.equalTo(expected))
        }
    }

    companion object {
        protected fun remove(actualJson: JSONObject, expectedJson: JSONObject, key: String?) {
            actualJson.remove(key)
            expectedJson.remove(key)
        }

        protected fun renameKeys(actualJson: JSONObject) {
            val keys: MutableList<Pair<String, String>> = ArrayList()
            val keyIt = actualJson.keys()
            while (keyIt.hasNext()) {
                val name = keyIt.next()
                keys.add(Pair(name, actualJson.getJSONObject(name).getString("name")))
            }
            Collections.sort(keys, Comparator.comparing { x: Pair<String, String> -> x.second })
            for (i in keys.indices) {
                val keyName = keys[i].first
                actualJson.put((i + i).toString(), actualJson[keyName])
                actualJson.remove(keyName)
            }
        }
    }
}
