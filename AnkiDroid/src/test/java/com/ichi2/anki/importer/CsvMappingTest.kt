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

package com.ichi2.anki.importer

import com.ichi2.anki.importer.CsvFieldMappingBehavior.MapToField
import com.ichi2.testutils.AnkiAssert
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class CsvMappingTest {

    @Test
    fun zero_csv_fields_is_error() {
        val exception = AnkiAssert.assertThrows({ createMapping(0, "Front") }, IllegalArgumentException::class.java)
        assertThat(exception.message, equalTo("more than one CSV column must be provided"))
    }

    @Test
    fun zero_note_fields_is_error() {
        val ex = AnkiAssert.assertThrows({ createMapping(1) }, IllegalArgumentException::class.java)
        assertThat(ex.message, equalTo("more than one note type field must be provided"))
    }

    @Test
    fun test_exact_field_match() {
        val mapping = createMapping(csvFields = 2, "Front", "Back")

        mapping.assertFieldValue(0, "Front")
        mapping.assertFieldValue(1, "Back")
    }

    @Test
    fun test_missing_field_match() {
        val mapping = createMapping(csvFields = 1, "Front", "Back")
        mapping.assertFieldValue(0, "Front")
    }

    @Test
    fun test_extra_field_is_tags() {
        val mapping = createMapping(csvFields = 3, "Front", "Back")
        mapping.assertFieldValue(0, "Front")
        mapping.assertFieldValue(1, "Back")

        assertThat(mapping[2], instanceOf(CsvFieldMappingBehavior.MapToTags::class.java))
    }

    @Test
    fun test_extra_two_fields_are_tags_and_nothing() {
        val mapping = createMapping(csvFields = 4, "Front", "Back")
        mapping.assertFieldValue(0, "Front")
        mapping.assertFieldValue(1, "Back")
        assertThat(mapping[2], instanceOf(CsvFieldMappingBehavior.MapToTags::class.java))
        assertThat(mapping[3], instanceOf(CsvFieldMappingBehavior.MapToNothing::class.java))
    }

    @Test
    // test that moving a field sets the previous value to nothing
    fun test_already_set_sets_to_nothing() {
        // we use "4" to ensure we have two "nothing" fields
        val mapping = createMapping(csvFields = 4, "Front")
        mapping.assertMappedToNothing(2)

        val optionToSelect = mapping.availableOptions[0]
        optionToSelect.assertIsFieldWithValue("Front")

        mapping.setMapping(2, optionToSelect)

        // index 2 should now be "Front"
        mapping.assertFieldValue(2, "Front")

        // "Front" should be removed from field 0
        mapping.assertMappedToNothing(0)
    }

    @Test
    fun set_mapping_event_test() {
        val mapping = createMapping(csvFields = 4, "Front", "Back")

        var changed = false
        var majorChange = false
        mapping.onChange.add(Runnable { changed = true })
        mapping.onMajorChange = Runnable { majorChange = true }

        mapping.setMapping(2, mapping[0])

        assertThat("a mapping change should fire a change event", changed, equalTo(true))
        assertThat("a mapping change is not a major change", majorChange, equalTo(false))
    }

    @Test
    fun set_model_integration_test() {
        val mapping = createMapping(csvFields = 4, "Front", "Back")

        var changed = false
        var majorChange = false
        mapping.onChange.add(Runnable { changed = true })
        mapping.onMajorChange = Runnable { majorChange = true }

        mapping.setModel(TestNoteType("Cloze", "Extra"))

        assertThat("a model change fires a minor change", changed, equalTo(true))
        assertThat("a model change fires a major change", majorChange, equalTo(true))

        // ensure fields are changed
        mapping.csvMap[0].assertIsFieldWithValue("Cloze")
        mapping.csvMap[1].assertIsFieldWithValue("Extra")
    }

    @Test
    fun set_field_count_integration_test() {
        val mapping = createMapping(csvFields = 4, "Front", "Back")

        var changed = false
        var majorChange = false
        mapping.onChange.add(Runnable { changed = true })
        mapping.onMajorChange = Runnable { majorChange = true }

        mapping.setFieldCount(3)

        assertThat("a field count change fires a minor change", changed, equalTo(true))
        assertThat("a field count change fires a major change", majorChange, equalTo(true))
    }

    private fun createMapping(csvFields: Int, vararg fields: String): CsvMapping {
        val mapping = CsvMapping(csvFields, TestNoteType(*fields))
        assertThat(mapping.size, equalTo(csvFields))
        return mapping
    }

    private fun CsvMapping.assertMappedToNothing(i: Int) {
        assertThat(this[i], instanceOf(CsvFieldMappingBehavior.MapToNothing::class.java))
    }

    private fun CsvMapping.assertFieldValue(i: Int, expectedFieldName: String) {
        val csvFieldMappingBehavior = this[i]
        csvFieldMappingBehavior.assertIsFieldWithValue(expectedFieldName)
    }

    private fun CsvFieldMappingBehavior.assertIsFieldWithValue(expectedFieldName: String) {
        assertThat(this, instanceOf(MapToField::class.java))
        val mapToField = this as MapToField
        assertThat(mapToField.field, equalTo(expectedFieldName))
    }

    private class TestNoteType(vararg fieldInput: String) : CsvMapping.NoteType() {
        override val name: String = ""
        override val fields: List<FieldName> = fieldInput.toList()
    }
}
