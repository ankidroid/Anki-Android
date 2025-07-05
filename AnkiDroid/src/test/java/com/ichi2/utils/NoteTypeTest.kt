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
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.addNotetypeLegacy
import com.ichi2.anki.libanki.backend.BackendUtils
import com.ichi2.anki.libanki.getStockNotetype
import com.ichi2.anki.utils.ext.getAllClozeTextFields
import junit.framework.TestCase.assertEquals
import org.json.JSONObject
import kotlin.test.Test

// link to a method in `NoteType.kt` for navigation as it contains no classes

/** Test of [NoteType][templates] */
class NoteTypeTest {
    private val noteType =
        JSONObject(
            """
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
    """,
        )

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

const val BASIC_NOTE_TYPE_NAME = "Basic"

/**
 * Creates a basic model.
 *
 * Note: changes to this model will propagate to [createBasicTypingNoteType] as that model is built on
 * top of the model returned by this function.
 *
 * @param name name of the new model
 * @return the new model
 */
fun Collection.createBasicNoteType(name: String = BASIC_NOTE_TYPE_NAME): NotetypeJson {
    val noteType =
        getStockNotetype(StockNotetype.Kind.KIND_BASIC).apply { this.name = name }
    addNotetypeLegacy(BackendUtils.toJsonBytes(noteType))
    return notetypes.byName(name)!!
}

/**
 * Creates a basic typing model.
 *
 * @see createBasicNoteType
 */
fun Collection.createBasicTypingNoteType(name: String): NotetypeJson {
    val noteType = createBasicNoteType(name)
    noteType.templates[0].apply {
        qfmt = "{{Front}}\n\n{{type:Back}}"
        afmt = "{{Front}}\n\n<hr id=answer>\n\n{{type:Back}}"
    }
    notetypes.save(noteType)
    return noteType
}
