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

package com.ichi2.anki;

import android.content.Intent;
import android.widget.EditText;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.libanki.Models;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.afollestad.materialdialogs.DialogAction.POSITIVE;
import static com.ichi2.anki.FieldOperationType.ADD_FIELD;
import static com.ichi2.anki.FieldOperationType.RENAME_FIELD;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@RunWith(ParameterizedRobolectricTestRunner.class)
public class ModelFieldEditorTest extends RobolectricTest {
    private static final String[] sForbiddenCharacters = new String[] {"#", "^", "/", " ", "\t"};
    private String mForbiddenCharacter;


    public ModelFieldEditorTest(String forbiddenCharacter) {
        mForbiddenCharacter = forbiddenCharacter;
    }


    @Parameters(name = "\"{0}\"")
    public static Collection forbiddenCharacters() {
        return Arrays.asList(sForbiddenCharacters);
    }


    /**
     * Tests if field names with illegal characters get removed from beginning of field names when adding field
     */
    @Test
    public void testIllegalCharactersInFieldName_addField() {
        String fieldName = setupInvalidFieldName(mForbiddenCharacter, ADD_FIELD);

        testForIllegalCharacters(fieldName);
    }


    /**
     * Tests if field names with illegal characters get removed from beginning of field names when renaming field
     */
    @Test
    public void testIllegalCharactersInFieldName_renameField() {
        String fieldName = setupInvalidFieldName(mForbiddenCharacter, RENAME_FIELD);

        testForIllegalCharacters(fieldName);
    }


    /**
     * Assert that model's fields doesn't contain the forbidden field name
     *
     * @param forbiddenFieldName The forbidden field name to identify
     */
    private void testForIllegalCharacters(String forbiddenFieldName) {
        List<String> modelFields = getCurrentDatabaseModelCopy("Basic").getFieldsNames();
        String fieldName = modelFields.get(modelFields.size() - 1);

        assertThat("forbidden character detected!", fieldName, not(equalTo(forbiddenFieldName)));
    }


    /**
     * Builds a Dialog and an EditText for field name.
     * Inputs a forbidden field name in text edit and clicks confirm
     *
     * @param forbidden             Forbidden character to set
     * @param fieldOperationType    Field Operation Type to do (ADD_FIELD or EDIT_FIELD)
     * @return                      The forbidden field name created
     */
    private String setupInvalidFieldName(String forbidden, FieldOperationType fieldOperationType) {

        EditText fieldNameInput = new EditText(getTargetContext());

        String fieldName = forbidden + "field";

        // build dialog for field name input
        advanceRobolectricLooperWithSleep();
        MaterialDialog dialog = buildAddEditFieldDialog(fieldNameInput, fieldOperationType);

        // set field name to forbidden string and click confirm
        fieldNameInput.setText(fieldName);
        dialog.getActionButton(POSITIVE)
                .performClick();

        return fieldName;
    }


    /**
     * Creates a dialog that adds a field with given field name to "Basic" model when its positive button is clicked
     *
     * @param fieldNameInput        EditText with field name inside
     * @param fieldOperationType    Field Operation Type to do (ADD_FIELD or EDIT_FIELD)
     * @return                      The dialog
     */
    private MaterialDialog buildAddEditFieldDialog(EditText fieldNameInput, FieldOperationType fieldOperationType)
            throws RuntimeException {

        return new MaterialDialog.Builder(getTargetContext())
                .onPositive((dialog, which) -> {
                    try {
                        String modelName = "Basic";

                        // start ModelFieldEditor activity
                        Intent intent = new Intent();
                        intent.putExtra("title", modelName);
                        intent.putExtra("noteTypeID", findModelIdByName(modelName));
                        ModelFieldEditor modelFieldEditor = startActivityNormallyOpenCollectionWithIntent(
                                this, ModelFieldEditor.class, intent
                        );

                        // add or rename field
                        switch (fieldOperationType) {
                            case ADD_FIELD:
                                modelFieldEditor.addField(fieldNameInput);
                                break;

                            case RENAME_FIELD:
                                modelFieldEditor.renameField(fieldNameInput);
                                break;

                            default:
                                throw new IllegalStateException("Unexpected value: " + fieldOperationType);
                        }
                    } catch (ConfirmModSchemaException exception) {
                        throw new RuntimeException(exception);
                    }
                })
                .build();
    }


    /**
     * Finds the model with specified name in {@link Models#getModels()} and returns its key
     *
     * @param modelName Name of the model
     * @return          Key in {@link Models#getModels()} HashMap for the model
     */
    private long findModelIdByName(String modelName) {
        return getCol().getModels().getModels().entrySet()
                .stream()
                .filter(idModels -> idModels.getValue().getString("name").equals(modelName))
                .map(Map.Entry::getKey) // get the ID
                .findFirst()
                .orElse(0L);
    }
}



enum FieldOperationType {
    ADD_FIELD,
    RENAME_FIELD
}