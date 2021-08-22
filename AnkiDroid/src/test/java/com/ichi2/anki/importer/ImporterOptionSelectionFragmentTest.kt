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

import androidx.core.os.bundleOf
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragment
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.libanki.Collection
import com.ichi2.libanki.importer.TextImporter
import com.ichi2.utils.strictMock
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config
import timber.log.Timber

internal typealias ImporterOptionSelectionScenario = FragmentScenario<ImporterOptionSelectionFragmentTest.ImporterOptionSelectionFragmentForTesting>

@RunWith(AndroidJUnit4::class)
class ImporterOptionSelectionFragmentTest : RobolectricTest() {

    //region startup values

    @Test
    fun uses_anki_desktop_default_deck() {
        // https://github.com/ankitects/anki/blob/8830d338264017712a9c964121735574d3aa7f9b/qt/aqt/deckchooser.py#L27-L28
        val currentDeckId = col.decks.current()!!.getLong("id")

        onFragment {
            assertThat(it.importOptions.deck, equalTo(currentDeckId))
        }
    }

    @Test
    fun uses_anki_desktop_default_note_type() {
        // https://github.com/ankitects/anki/blob/8830d338264017712a9c964121735574d3aa7f9b/qt/aqt/importing.py#L115
        val currentModel = col.models.current()!!.getLong("id")

        onFragment {
            assertThat(it.importOptions.noteTypeId, equalTo(currentModel))
        }
    }

    @Test
    fun default_delimiter_comes_from_libAnki() {
        onFragment(libAnkiDelimiter = 'a') {
            assertThat(it.importOptions.delimiterChar, equalTo('a'))
        }
    }

    @Test
    fun default_values_match_libAnki() {
        // https://github.com/ankitects/anki/blob/ce4dcab9e4382d6d53409c3522d49ec1b8f021f9/qt/aqt/importing.py#L104-L107
        onFragment {
            with(it.importOptions) {
                assertThat(allowHtml, equalTo(AllowHtml.INCLUDE_HTML))
                assertThat(importModeTag, equalTo(""))
                // 1 = IGNORE
                // https://github.com/ankitects/anki/blob/ce4dcab9e4382d6d53409c3522d49ec1b8f021f9/pylib/anki/importing/noteimp.py#L68
                assertThat(importMode, equalTo(ImportConflictMode.IGNORE))
            }
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun initial_mapping() {
        onFragment(fieldCount = 6) {
            val expected = listOf("Front", "Back", "_tags", null, null, null)

            val actual = it.csvMapping.csvMap.map { fld -> fld.fieldValue() }

            assertThat(actual, equalTo(expected))
        }
    }

    //endregion

    @Test
    @Config(qualifiers = "en")
    fun change_separator_test() {
        onFragment {
            it.setFieldsCallReturns(2)

            assertThat(it.fieldCount, equalTo(6))

            it.setFieldDelimiterOverride('_')

            // the delimiter of the libAnki class is called
            verify(it.importer).delimiter = '_'
            // and the resulting value of the fields() call is used
            assertThat(it.fieldCount, equalTo(2))

            // the text should be updated
            assertThat(it.fieldDelimiterButtonText, equalTo("Fields separated by: _"))
        }
    }

    @Test
    fun tag_override_test() {

        onFragment {
            for (
                (mode, isEnabled) in mapOf(
                    ImportConflictMode.UPDATE to true,
                    ImportConflictMode.IGNORE to false,
                    ImportConflictMode.DUPLICATE to false
                )
            ) {
                it.setImportMode(ImportConflictMode.DUPLICATE)
                assertThat("tag override should be disabled before this test executes", it.tagOverrideIsEnabled, equalTo(false))

                it.setImportMode(mode)

                assertThat("$mode should have override enabled: $isEnabled", it.tagOverrideIsEnabled, equalTo(isEnabled))
            }
        }
    }

    @Test
    fun change_note_type_test() {
        val noteTypeId = getClozeId()

        onFragment {
            val pastFieldName = it.csvMapping[0].fieldValue()
            it.csvMapping.setMapping(1, it.csvMapping[0])
            val oldFieldName = it.csvMapping[0].fieldValue()

            // perform the change
            it.setNoteTypeId(noteTypeId)

            // option is set
            assertThat(it.importOptions.noteTypeId, equalTo(noteTypeId))

            // control is set
            val indexOfSelectedNoteType = it.noteTypeIds.indexOf(noteTypeId)

            assertThat(it.noteTypeSpinner.selectedItemPosition, equalTo(indexOfSelectedNoteType))

            // csv mapping is reset
            assertThat(it.csvMapping[0].fieldValue(), not(equalTo(oldFieldName)))
            assertThat(it.csvMapping[0].fieldValue(), not(equalTo(pastFieldName)))
        }
    }

    @Test
    fun set_all_values_ensure_output_makes_sense() {
        val newDid = this.addDeck("deck2")
        val ntid = getClozeId()

        onFragment {
            // we modify csvMapping directly - this would normally be performed via a fragment

            val firstFieldName: String?

            // set values
            with(it) {
                setAllowHtml(false)
                setFieldDelimiterOverride('_')
                setImportMode(ImportConflictMode.UPDATE)
                setModifiedNotesTag("TAG")
                setDeckId(newDid)
                setNoteTypeId(ntid)

                // other properties reset this value
                firstFieldName = csvMapping[0].fieldValue()
                csvMapping.setMapping(1, csvMapping[0])
            }

            // note: closing the frame sets the mapping variable
            val bundle = it.closeFragmentWithResult(it.importOptions)

            // We also ensure that serde works here
            val restored = bundle.getParcelable<ImportOptions>(ImporterOptionSelectionFragment.RESULT_BUNDLE_OPTIONS)!!

            // check values
            with(restored) {
                assertThat("allowHtml", allowHtml, equalTo(AllowHtml.STRIP_HTML))
                assertThat("delimiterChar", delimiterChar, equalTo('_'))
                assertThat("importMode", importMode, equalTo(ImportConflictMode.UPDATE))
                assertThat("deck", deck, equalTo(newDid))
                assertThat("noteTypeId", noteTypeId, equalTo(ntid))
                assertThat("importModeTag", importModeTag, equalTo("TAG"))
                assertThat("mapping", mapping[1].fieldValue(), equalTo(firstFieldName))
            }
        }
    }

    private fun CsvFieldMappingBehavior.fieldValue(): String? = this.tolibAnkiString()

    private fun onFragment(
        path: String = "",
        fieldCount: Int = 6,
        libAnkiDelimiter: Char = ',',
        onFragment: ((ImporterOptionSelectionFragmentForTesting) -> Unit)? = null
    ): ImporterOptionSelectionScenario {
        val scenario = launchFragment<ImporterOptionSelectionFragmentForTesting>(bundleOf(ImporterOptionSelectionFragment.ARG_REQUIRED_PATH to path), initialState = Lifecycle.State.INITIALIZED)
        scenario.onFragment {
            fragment ->
            run {
                fragment.setFieldsCallReturns(fieldCount)
                fragment.setLibAnkiDelimiter(libAnkiDelimiter)
            }
        }
        scenario.moveToState(Lifecycle.State.STARTED)
        if (onFragment != null) {
            scenario.onFragment(onFragment)
        }
        return scenario
    }

    /** ntid of cloze */
    private fun getClozeId() = col.models.byName("Cloze")!!.getLong("id")

    internal class ImporterOptionSelectionFragmentForTesting : ImporterOptionSelectionFragment() {

        val tagOverrideIsEnabled: Boolean get() = modifiedNotesTag.isEnabled
        val fieldDelimiterButtonText: String get() = fieldDelimiterButton.text.toString()
        val fieldCount: Int get() = this.csvMapping.size

        val importer: TextImporter = strictMock()

        fun setFieldsCallReturns(value: Int) {
            doReturn(value).`when`(importer).fields()
        }

        fun setLibAnkiDelimiter(value: Char) {
            doReturn(value).`when`(importer).delimiter
        }

        override fun getImporterInstance(collection: Collection, filePath: String): TextImporter {
            Timber.i("return: $this")
            doNothing().`when`(importer).setMapping(any())
            doNothing().`when`(importer).delimiter = any()
            doNothing().`when`(importer).setModel(any())
            doReturn(true).`when`(importer).mappingOk()
            return importer
        }
    }
}
