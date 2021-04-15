package com.ichi2.anki;

import android.content.Intent;
import android.widget.EditText;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Models;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.afollestad.materialdialogs.DialogAction.POSITIVE;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(AndroidJUnit4.class)
public class ModelFieldEditorTest extends RobolectricTest {
    /**
     * Tests if field names with illegal characters get removed from beginning of field names when adding field
     */
    @Test
    public void testIllegalCharactersInFieldName_addField() {
        // iterate through the forbidden characters and test
        for (String forbidden : new String[] {"#", "^", "/", " ", "\t"}) {
            String fieldName = setupInvalidFieldName(forbidden, true);

            testForIllegalCharacters(fieldName);
        }
    }


    /**
     * Tests if field names with illegal characters get removed from beginning of field names when renaming field
     */
    @Test
    public void testIllegalCharactersInFieldName_renameField() {

        // iterate through the forbidden characters and test
        for (String forbidden : new String[] {"#", "^", "/", " ", "\t"}) {
            String fieldName = setupInvalidFieldName(forbidden, false);

            testForIllegalCharacters(fieldName);
        }
    }


    /**
     * Assert that model's fields doesn't contain the forbidden field name
     *
     * @param forbiddenFieldName The forbidden field name to identify
     */
    private void testForIllegalCharacters(String forbiddenFieldName) {
        List<String> modelFields = getCurrentDatabaseModelCopy("Basic").getFieldsNames();
        String fieldName = getCurrentDatabaseModelCopy("Basic").getFieldsNames()
                .get(modelFields.size() - 1);

        assertThat("forbidden character detected!", !fieldName.equals(forbiddenFieldName));
    }


    /**
     * Builds a Dialog and an EditText for field name.
     * Inputs a forbidden field name in text edit and clicks confirm
     *
     * @param forbidden     Forbidden character to set
     * @param isForAddField True    if dialog is for adding field.
     *                      False   if dialog is for renaming field.
     * @return              The forbidden field name created
     */
    private String setupInvalidFieldName(String forbidden, boolean isForAddField) {
        EditText fieldNameInput = new EditText(getTargetContext());

        String fieldName = forbidden + "field";

        // build dialog for field name input
        advanceRobolectricLooperWithSleep();
        MaterialDialog dialog = buildAddEditFieldDialog(fieldNameInput, isForAddField);

        // set field name to forbidden string and click confirm
        fieldNameInput.setText(fieldName);
        dialog.getActionButton(POSITIVE)
                .performClick();

        return fieldName;
    }


    /**
     * Creates a dialog that adds a field with given field name to "Basic" model when its positive button is clicked
     *
     * @param fieldNameInput EditText with field name inside
     * @param isForAddField  True    if dialog is for adding field.
     *                       False   if dialog is for renaming field.
     * @return               The dialog
     */
    private MaterialDialog buildAddEditFieldDialog(EditText fieldNameInput, boolean isForAddField) {

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
                        if (isForAddField) {
                            modelFieldEditor.addField(fieldNameInput);
                        } else {
                            modelFieldEditor.renameField(fieldNameInput);
                        }
                    } catch (ConfirmModSchemaException exception) {
                        exception.log();
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

        HashMap<Long, Model> idModels = getCol().getModels().getModels();
        Iterator<Map.Entry<Long, Model>> iterator = idModels.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Long, Model> idModel = iterator.next();
            long id = idModel.getKey();
            Model model = idModel.getValue();

            if (model.getString("name").equals(modelName)) {
                return id;
            }
        }
        return 0;
    }
}
