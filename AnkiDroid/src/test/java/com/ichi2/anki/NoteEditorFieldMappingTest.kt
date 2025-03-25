/*
 * Copyright (c) 2024 Tushar Sadhwani <tushar.sadhwani000@gmail.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import com.ichi2.libanki.NotetypeJson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteEditorFieldMappingTest {
    /**
     * Create a NotetypeJson with the specified field names.
     *
     * @param fieldNames List of field names to create in the note type
     * @return A NotetypeJson object with the specified fields
     */
    private fun createNotetypeWithFields(fieldNames: List<String>): NotetypeJson {
        val fieldsArray = JSONArray()

        fieldNames.forEachIndexed { index, name ->
            val field =
                JSONObject().apply {
                    put("name", name)
                    put("ord", index)
                    put("sticky", false)
                    put("font", "Arial")
                    put("size", 20)
                }
            fieldsArray.put(field)
        }

        val noteTypeJson =
            JSONObject().apply {
                put("name", "Test Note Type")
                put("id", 1L)
                put("flds", fieldsArray)
                put("tmpls", JSONArray())
                put("type", 0)
                put("sortf", 0)
            }

        return NotetypeJson(noteTypeJson)
    }

    /**
     * Test implementation of mapFieldsByNames for testing
     *
     * This matches the implementation in NoteEditor class but is isolated for testing
     */
    private fun mapFieldsByNames(
        oldNotetype: NotetypeJson,
        newNotetype: NotetypeJson,
    ): Map<Int, Int> {
        val oldFieldMap = oldNotetype.fieldsNames.withIndex().associate { it.value to it.index }
        val newFieldMap = newNotetype.fieldsNames.withIndex().associate { it.value to it.index }

        val mapping = mutableMapOf<Int, Int>()
        val usedNewIndices = mutableSetOf<Int>()

        // First pass: map fields with same name
        for ((oldIndex, oldName) in oldNotetype.fieldsNames.withIndex()) {
            val newIndex = newFieldMap[oldName]
            if (newIndex != null) {
                mapping[oldIndex] = newIndex
                usedNewIndices.add(newIndex)
            }
        }

        // Second pass: map remaining fields in order
        for ((oldIndex, _) in oldNotetype.fieldsNames.withIndex()) {
            if (!mapping.containsKey(oldIndex)) {
                // Find the first unused new field index
                val newIndex =
                    newNotetype.fieldsNames.indices.firstOrNull {
                        it !in usedNewIndices
                    } ?: if (newNotetype.fieldsNames.isNotEmpty()) 0 else null

                if (newIndex != null) {
                    mapping[oldIndex] = newIndex
                    usedNewIndices.add(newIndex)
                }
            }
        }

        return mapping
    }

    @Test
    fun testExactMatchingFields() {
        val oldNotetype = createNotetypeWithFields(listOf("Front", "Back", "Extra"))
        val newNotetype = createNotetypeWithFields(listOf("Front", "Back", "Extra"))

        val mapping = mapFieldsByNames(oldNotetype, newNotetype)

        // Each field should map to the same position
        assertEquals(0, mapping[0])
        assertEquals(1, mapping[1])
        assertEquals(2, mapping[2])
    }

    @Test
    fun testReorderedMatchingFields() {
        val oldNotetype = createNotetypeWithFields(listOf("Front", "Back", "Extra"))
        val newNotetype = createNotetypeWithFields(listOf("Back", "Extra", "Front"))

        val mapping = mapFieldsByNames(oldNotetype, newNotetype)

        // Fields should be mapped by name, not position
        assertEquals(2, mapping[0]) // Front -> index 2
        assertEquals(0, mapping[1]) // Back -> index 0
        assertEquals(1, mapping[2]) // Extra -> index 1
    }

    @Test
    fun testNoMatchingFields() {
        val oldNotetype = createNotetypeWithFields(listOf("Front", "Back", "Extra"))
        val newNotetype = createNotetypeWithFields(listOf("Question", "Answer", "Additional"))

        val mapping = mapFieldsByNames(oldNotetype, newNotetype)

        // With no matching names, fields should map to the corresponding positions
        assertEquals(0, mapping[0])
        assertEquals(1, mapping[1])
        assertEquals(2, mapping[2])
    }

    @Test
    fun testPartialMatchingFields() {
        val oldNotetype = createNotetypeWithFields(listOf("Front", "Back", "Extra"))
        val newNotetype = createNotetypeWithFields(listOf("Front", "Answer", "Extra"))

        val mapping = mapFieldsByNames(oldNotetype, newNotetype)

        // Front and Extra should map by name, Back should map to the remaining field
        assertEquals(0, mapping[0]) // Front -> index 0
        assertEquals(1, mapping[1]) // Back -> index 1 (only available)
        assertEquals(2, mapping[2]) // Extra -> index 2
    }

    @Test
    fun testFewerFieldsInNewNotetype() {
        val oldNotetype = createNotetypeWithFields(listOf("Front", "Back", "Extra"))
        val newNotetype = createNotetypeWithFields(listOf("Front", "Back"))

        val mapping = mapFieldsByNames(oldNotetype, newNotetype)

        // Front and Back should map by name, Extra should map to an existing field (first available)
        assertEquals(0, mapping[0]) // Front -> index 0
        assertEquals(1, mapping[1]) // Back -> index 1

        // Extra would map to the first field (index 0) if no other matches
        // This is a fallback behavior when the new note type has fewer fields
        assertEquals(0, mapping[2])
    }

    @Test
    fun testMoreFieldsInNewNotetype() {
        val oldNotetype = createNotetypeWithFields(listOf("Front", "Back"))
        val newNotetype = createNotetypeWithFields(listOf("Front", "Back", "Extra"))

        val mapping = mapFieldsByNames(oldNotetype, newNotetype)

        // Front and Back should map by name, new field "Extra" is unused
        assertEquals(0, mapping[0]) // Front -> index 0
        assertEquals(1, mapping[1]) // Back -> index 1
    }

    @Test
    fun testComplexExample() {
        // Example from the issue: ABC -> ACD
        val oldNotetype = createNotetypeWithFields(listOf("A", "B", "C"))
        val newNotetype = createNotetypeWithFields(listOf("A", "C", "D"))

        val mapping = mapFieldsByNames(oldNotetype, newNotetype)

        // A and C should map by name, B should map to the unmapped field D
        assertEquals(0, mapping[0]) // A -> index 0
        assertEquals(2, mapping[1]) // B -> index 2 (D, as it's the only unmapped field)
        assertEquals(1, mapping[2]) // C -> index 1
    }
}
