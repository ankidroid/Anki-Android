/****************************************************************************************
 * Copyright (c) 2021 Dorrin Sotoudeh <dorrinsotoudeh123@gmail.com>                     *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki

import android.content.Intent
import android.widget.EditText
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.getActionButton
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.libanki.Model
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
class ModelFieldEditorTest(private val forbiddenCharacter: String) : RobolectricTest() {
    /**
     * Tests if field names with illegal characters get removed from beginning of field names when adding field
     */
    @Test
    fun testIllegalCharactersInFieldName_addField() {
        val fieldName = setupInvalidFieldName(forbiddenCharacter, FieldOperationType.ADD_FIELD)
        testForIllegalCharacters(fieldName)
    }

    /**
     * Tests if field names with illegal characters get removed from beginning of field names when renaming field
     */
    @Test
    fun testIllegalCharactersInFieldName_renameField() {
        val fieldName = setupInvalidFieldName(forbiddenCharacter, FieldOperationType.RENAME_FIELD)
        testForIllegalCharacters(fieldName)
    }

    /**
     * Assert that model's fields doesn't contain the forbidden field name
     *
     * @param forbiddenFieldName The forbidden field name to identify
     */
    private fun testForIllegalCharacters(forbiddenFieldName: String) {
        val modelFields = getCurrentDatabaseModelCopy("Basic").fieldsNames
        val fieldName = modelFields[modelFields.size - 1]
        MatcherAssert.assertThat("forbidden character detected!", fieldName, Matchers.not(Matchers.equalTo(forbiddenFieldName)))
    }

    /**
     * Builds a Dialog and an EditText for field name.
     * Inputs a forbidden field name in text edit and clicks confirm
     *
     * @param forbidden             Forbidden character to set
     * @param fieldOperationType    Field Operation Type to do (ADD_FIELD or EDIT_FIELD)
     * @return The forbidden field name created
     */
    private fun setupInvalidFieldName(forbidden: String, fieldOperationType: FieldOperationType): String {
        val fieldNameInput = EditText(targetContext)
        val fieldName = forbidden + "field"

        // build dialog for field name input
        advanceRobolectricLooperWithSleep()
        val dialog = buildAddEditFieldDialog(fieldNameInput, fieldOperationType)

        // set field name to forbidden string and click confirm
        fieldNameInput.setText(fieldName)
        dialog.getActionButton(WhichButton.POSITIVE)
            .performClick()
        advanceRobolectricLooperWithSleep()
        return fieldName
    }

    /**
     * Creates a dialog that adds a field with given field name to "Basic" model when its positive button is clicked
     *
     * @param fieldNameInput        EditText with field name inside
     * @param fieldOperationType    Field Operation Type to do (ADD_FIELD or EDIT_FIELD)
     * @return The dialog
     */
    @Throws(RuntimeException::class)
    private fun buildAddEditFieldDialog(fieldNameInput: EditText, fieldOperationType: FieldOperationType): MaterialDialog {
        return MaterialDialog(targetContext)
            .positiveButton {
                try {
                    val modelName = "Basic"

                    // start ModelFieldEditor activity
                    val intent = Intent()
                    intent.putExtra("title", modelName)
                    intent.putExtra("noteTypeID", findModelIdByName(modelName))
                    val modelFieldEditor = startActivityNormallyOpenCollectionWithIntent(
                        this, ModelFieldEditor::class.java, intent
                    )
                    when (fieldOperationType) {
                        FieldOperationType.ADD_FIELD -> modelFieldEditor.addField(fieldNameInput)
                        FieldOperationType.RENAME_FIELD -> modelFieldEditor.renameField(
                            fieldNameInput
                        )
                    }
                } catch (exception: ConfirmModSchemaException) {
                    throw RuntimeException(exception)
                }
            }
    }

    /**
     * Finds the model with specified name in {@link Models#getModels()} and returns its key
     *
     * @param modelName Name of the model
     * @return Key in {@link Models#getModels()} HashMap for the model
     */
    @Suppress("SameParameterValue")
    private fun findModelIdByName(modelName: String): Long {
        return col.models.getModels().filter { idModels: Map.Entry<Long?, Model> -> idModels.value.getString("name") == modelName }.keys.first()
    }

    companion object {
        private val sForbiddenCharacters = arrayOf("#", "^", "/", " ", "\t")
        @ParameterizedRobolectricTestRunner.Parameters(name = "\"{0}\"")
        @Suppress("unused")
        @JvmStatic // required: Parameters
        fun forbiddenCharacters(): Collection<*> {
            return listOf(*sForbiddenCharacters)
        }
    }
}

internal enum class FieldOperationType {
    ADD_FIELD, RENAME_FIELD
}
