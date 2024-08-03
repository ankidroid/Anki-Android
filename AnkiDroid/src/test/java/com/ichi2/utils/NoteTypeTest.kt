/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils

import anki.notetypes.StockNotetype
import com.ichi2.anki.utils.ext.getAllClozeTextFields
import com.ichi2.anki.utils.ext.templates
import com.ichi2.libanki.Collection
import com.ichi2.libanki.NotetypeJson
import com.ichi2.libanki.addNotetypeLegacy
import com.ichi2.libanki.backend.BackendUtils
import com.ichi2.libanki.getStockNotetypeLegacy
import com.ichi2.libanki.utils.set
import junit.framework.TestCase.assertEquals
import kotlin.test.Test

// link to a method in `NoteType.kt` for navigation as it contains no classes
/** Test of [NoteType][templates] */
class NoteTypeTest {

    private val noteType = """
        {
          "type":1,
          "tmpls":[
               {
                 "name":"Cloze",
                 "ord":0,
                 "qfmt":"{{type:cloze:Text}} {{type:cloze:Text2}} {{cloze:Text3}} {{Added field}}",
                 "afmt":"{{cloze:Text}}<br>\n{{Back Extra}}",
                 "bqfmt":"",
                 "bafmt":"",
                 "did":null,
                 "bfont":"",
                 "bsize":0,
                 "id":1716321740
              }
           ]
        }
    """

    @Test
    fun testQfmtField() {
        val notetypeJson = NotetypeJson(noteType)

        val expectedQfmt = "{{type:cloze:Text}} {{type:cloze:Text2}} {{cloze:Text3}} {{Added field}}"
        assertEquals(expectedQfmt, notetypeJson.templates[0].qfmt)
    }

    @Test
    fun testGetAllClozeTexts() {
        val notetypeJson = NotetypeJson(noteType)

        val expectedClozeTexts = listOf("Text", "Text2", "Text3")
        assertEquals(expectedClozeTexts, notetypeJson.getAllClozeTextFields())
    }

    @Test
    fun testNameField() {
        val notetypeJson = NotetypeJson(noteType)
        val expectedName = "Cloze"
        assertEquals(expectedName, notetypeJson.templates[0].name)
    }

    @Test
    fun testOrdField() {
        val notetypeJson = NotetypeJson(noteType)
        val expectedOrd = 0
        assertEquals(expectedOrd, notetypeJson.templates[0].ord)
    }

    @Test
    fun testAfmtField() {
        val notetypeJson = NotetypeJson(noteType)
        val expectedAfmt = "{{cloze:Text}}<br>\n{{Back Extra}}"
        assertEquals(expectedAfmt, notetypeJson.templates[0].afmt)
    }
}

const val BASIC_MODEL_NAME = "Basic"

/**
 * Creates a basic model.
 *
 * Note: changes to this model will propagate to [createBasicTypingModel] as that model is built on
 * top of the model returned by this function.
 *
 * @param name name of the new model
 * @return the new model
 */
fun Collection.createBasicModel(name: String = BASIC_MODEL_NAME): NotetypeJson {
    val m = BackendUtils.fromJsonBytes(
        getStockNotetypeLegacy(StockNotetype.Kind.KIND_BASIC)
    ).apply { set("name", name) }
    addNotetypeLegacy(BackendUtils.toJsonBytes(m))
    return notetypes.byName(name)!!
}

/**
 * Creates a basic typing model.
 *
 * @see createBasicModel
 */
fun Collection.createBasicTypingModel(name: String): NotetypeJson {
    val m = createBasicModel(name)
    val t = m.getJSONArray("tmpls").getJSONObject(0)
    t.put("qfmt", "{{Front}}\n\n{{type:Back}}")
    t.put("afmt", "{{Front}}\n\n<hr id=answer>\n\n{{type:Back}}")
    notetypes.save(m)
    return m
}
