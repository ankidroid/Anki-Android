/****************************************************************************************
 * Copyright (c) 2015 Ryan Annis <squeenix@live.ca>                                     *
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.dialogs.ConfirmationDialog;
import com.ichi2.anki.dialogs.ModelEditorContextMenu;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.async.DeckTask;
import com.ichi2.libanki.Collection;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.widget.WidgetStatus;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class ModelFieldEditor extends AnkiActivity {

    private final static int NORMAL_EXIT = 100001;

    //Position of the current field selected
    private int mCurrentPos;

    private ListView mFieldLabelView;
    private ArrayList<String> mFieldLabels;
    private MaterialDialog mProgressDialog;

    private Collection mCol;
    private JSONArray mNoteFields;
    private JSONObject mMod;

    private ModelEditorContextMenu mContextMenu;
    private EditText mFieldNameInput;

    private Runnable mConfirmDialogCancel = new Runnable() {
        @Override
        public void run() {
            dismissContextMenu();
        }
    };

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.model_field_editor);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        mFieldLabelView = (ListView) findViewById(R.id.note_type_editor_fields);

        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.model_field_editor_title);
            getSupportActionBar().setSubtitle(getIntent().getStringExtra("title"));
        }
        startLoadingCollection();
    }


    @Override
    protected void onStop() {
        super.onStop();
        if (!isFinishing()) {
            WidgetStatus.update(this);
            UIUtils.saveCollectionInBackground(this);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.model_editor, menu);
        return true;
    }


    // ----------------------------------------------------------------------------
    // ANKI METHODS
    // ----------------------------------------------------------------------------


    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        this.mCol = col;
        setupLabels();
        createfieldLabels();
    }


    // ----------------------------------------------------------------------------
    // UI SETUP
    // ----------------------------------------------------------------------------


    /*
     * Sets up the main ListView and ArrayAdapters
     * Containing clickable labels for the fields
     */
    private void createfieldLabels() {
        ArrayAdapter<String> mFieldLabelAdapter = new ArrayAdapter<>(this, R.layout.model_field_editor_list_item, mFieldLabels);
        mFieldLabelView.setAdapter(mFieldLabelAdapter);
        mFieldLabelView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mContextMenu = ModelEditorContextMenu.newInstance(mFieldLabels.get(position), mContextMenuListener);
                showDialogFragment(mContextMenu);
                mCurrentPos = position;
            }
        });
    }


    /*
      * Sets up the ArrayList containing the text for the main ListView
      */
    private void setupLabels() {
        long noteTypeID = getIntent().getLongExtra("noteTypeID", 0);
        mMod = mCol.getModels().get(noteTypeID);

        mFieldLabels = new ArrayList<>();
        try {
            mNoteFields = mMod.getJSONArray("flds");
            for (int i = 0; i < mNoteFields.length(); i++) {
                JSONObject o = mNoteFields.getJSONObject(i);
                mFieldLabels.add(o.getString("name"));
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    // ----------------------------------------------------------------------------
    // CONTEXT MENU DIALOGUES
    // ----------------------------------------------------------------------------


    /*
    * Creates a dialog to rename the currently selected field, short loading ti
    * Processing time scales with number of items
    */
    private void addFieldDialog() {
        mFieldNameInput = new EditText(this);
        mFieldNameInput.setSingleLine(true);

        new MaterialDialog.Builder(this)
                .title(R.string.model_field_editor_add)
                .positiveText(R.string.dialog_ok)
                .customView(mFieldNameInput, true)
                .callback(new MaterialDialog.ButtonCallback() {

                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        String fieldName = mFieldNameInput.getText().toString()
                                .replaceAll("[\'\"\\n\\r\\[\\]\\(\\)]", "");

                        if (fieldName.length() == 0) {
                            showToast(getResources().getString(R.string.toast_empty_name));
                        } else if (containsField(fieldName)) {
                            showToast(getResources().getString(R.string.toast_duplicate_field));
                        } else {
                            //Name is valid, now field is added
                            try {
                                mCol.modSchema();
                                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ADD_FIELD, mChangeFieldHandler,
                                        new DeckTask.TaskData(new Object[]{mMod, fieldName}));
                            } catch (ConfirmModSchemaException e) {

                                //Create dialogue to for schema change
                                ConfirmationDialog c = new ConfirmationDialog();
                                c.setArgs(getResources().getString(R.string.full_sync_confirmation));
                                Runnable confirm = new Runnable() {
                                    @Override
                                    public void run() {
                                        mCol.modSchemaNoCheck();
                                        String fieldName = mFieldNameInput.getText().toString()
                                                .replaceAll("[\'\"\\n\\r\\[\\]\\(\\)]", "");
                                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ADD_FIELD, mChangeFieldHandler,
                                                new DeckTask.TaskData(new Object[]{mMod, fieldName}));
                                        dismissContextMenu();
                                    }
                                };

                                c.setConfirm(confirm);
                                c.setCancel(mConfirmDialogCancel);
                                ModelFieldEditor.this.showDialogFragment(c);
                            }
                            mCol.getModels().update(mMod);
                            fullRefreshList();
                        }
                    }
                })
                .negativeText(R.string.dialog_cancel)
                .show();
    }


    /*
     * Creates a dialog to rename the currently selected field, short loading ti
     * Processing time scales with number of items
     */
    private void deleteFieldDialog() {
        Runnable confirm = new Runnable() {
            @Override
            public void run() {
                try {
                    mCol.modSchema(false);
                    deleteField();
                } catch (ConfirmModSchemaException e) {
                    //This should never be reached because modSchema() didn't throw an exception
                }
                dismissContextMenu();
            }
        };


        if (mFieldLabels.size() < 2) {
            showToast(getResources().getString(R.string.toast_last_field));
        } else {
            try {
                mCol.modSchema();
                ConfirmationDialog d = new ConfirmationDialog();
                d.setArgs(getResources().getString(R.string.field_delete_warning));
                d.setConfirm(confirm);
                d.setCancel(mConfirmDialogCancel);
                showDialogFragment(d);
            } catch (ConfirmModSchemaException e) {
                ConfirmationDialog c = new ConfirmationDialog();
                c.setConfirm(confirm);
                c.setCancel(mConfirmDialogCancel);
                c.setArgs(getResources().getString(R.string.full_sync_confirmation));
                showDialogFragment(c);
            }
        }
    }

    private void deleteField() {
        try {
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DELETE_FIELD, mChangeFieldHandler,
                    new DeckTask.TaskData(new Object[]{mMod, mNoteFields.getJSONObject(mCurrentPos)}));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /*
     * Creates a dialog to rename the currently selected field, short loading ti
     * Processing time is constant
     */
    private void renameFieldDialog() {
        mFieldNameInput = new EditText(this);
        mFieldNameInput.setSingleLine(true);
        mFieldNameInput.setText(mFieldLabels.get(mCurrentPos));
        mFieldNameInput.setSelection(mFieldNameInput.getText().length());
        new MaterialDialog.Builder(this)
                .title(R.string.rename_model)
                .positiveText(R.string.rename)
                .customView(mFieldNameInput, true)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {

                        String fieldLabel = mFieldNameInput.getText().toString()
                                .replaceAll("[\'\"\\n\\r\\[\\]\\(\\)]", "");
                        if (fieldLabel.length() == 0) {
                            showToast(getResources().getString(R.string.toast_empty_name));
                        } else if (containsField(fieldLabel)) {
                            showToast(getResources().getString(R.string.toast_duplicate_field));
                        } else {
                            //Field is valid, now rename
                            try {
                                renameField();
                            } catch (ConfirmModSchemaException e) {

                                // Handler mod schema confirmation
                                ConfirmationDialog c = new ConfirmationDialog();
                                c.setArgs(getResources().getString(R.string.full_sync_confirmation));
                                Runnable confirm = new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            mCol.modSchema(false);
                                            renameField();
                                        } catch (ConfirmModSchemaException e) {
                                            //This should never be thrown
                                        }
                                        dismissContextMenu();
                                    }
                                };
                                c.setConfirm(confirm);
                                c.setCancel(mConfirmDialogCancel);
                                ModelFieldEditor.this.showDialogFragment(c);
                            }
                        }
                    }
                })
                .negativeText(R.string.dialog_cancel)
                .show();
    }


    /*
     * Allows the user to select a number less than the number of fields in the current model to
     * reposition the current field to
     * Processing time is scales with number of items
     */
    private void repositionFieldDialog() {
        mFieldNameInput = new EditText(this);
        mFieldNameInput.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        new MaterialDialog.Builder(this)
                .title(String.format(getResources().getString(R.string.model_field_editor_reposition), 1, mFieldLabels.size()))
                .positiveText(R.string.dialog_ok)
                .customView(mFieldNameInput, true)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        String newPosition = mFieldNameInput.getText().toString();
                        int pos;
                        try {
                            pos = Integer.parseInt(newPosition);
                        } catch (NumberFormatException n) {
                            showToast(getResources().getString(R.string.toast_out_of_range));
                            return;
                        }

                        if (pos < 1 || pos > mFieldLabels.size()) {
                            showToast(getResources().getString(R.string.toast_out_of_range));
                        } else {
                            // Input is valid, now attempt to modify
                            try {
                                mCol.modSchema();
                                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REPOSITION_FIELD, mChangeFieldHandler,
                                        new DeckTask.TaskData(new Object[]{mMod,
                                                mNoteFields.getJSONObject(mCurrentPos), pos - 1}));
                            } catch (ConfirmModSchemaException e) {

                                // Handle mod schema confirmation
                                ConfirmationDialog c = new ConfirmationDialog();
                                c.setArgs(getResources().getString(R.string.full_sync_confirmation));
                                Runnable confirm = new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            mCol.modSchemaNoCheck();
                                            String newPosition = mFieldNameInput.getText().toString();
                                            int pos = Integer.parseInt(newPosition);
                                            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REPOSITION_FIELD,
                                                    mChangeFieldHandler, new DeckTask.TaskData(new Object[]{mMod,
                                                            mNoteFields.getJSONObject(mCurrentPos),pos - 1}));
                                            dismissContextMenu();
                                        } catch (JSONException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                };
                                c.setConfirm(confirm);
                                c.setCancel(mConfirmDialogCancel);
                                ModelFieldEditor.this.showDialogFragment(c);
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                })
                .negativeText(R.string.dialog_cancel)
                .show();
    }


    // ----------------------------------------------------------------------------
    // HELPER METHODS
    // ----------------------------------------------------------------------------


    /*
     * Useful when a confirmation dialog is created within another dialog
     */
    private void dismissContextMenu() {
        if (mContextMenu != null) {
            mContextMenu.dismiss();
            mContextMenu = null;
        }
    }


    private void dismissProgressBar() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        mProgressDialog = null;
    }


    /*
     * Renames the current field
     */
    private void renameField() throws ConfirmModSchemaException {
        try {
            String fieldLabel = mFieldNameInput.getText().toString()
                    .replaceAll("[\'\"\\n\\r\\[\\]\\(\\)]", "");
            JSONObject field = (JSONObject) mNoteFields.get(mCurrentPos);
            mCol.getModels().renameField(mMod, field, fieldLabel);
            mCol.getModels().save();
            fullRefreshList();
        } catch (JSONException e) {
            throw new RuntimeException();
        }
    }


    /*
     * Changes the sort field (that displays in card browser) to the current field
     */
    private void sortByField() {
        try {
            mCol.modSchema();
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_CHANGE_SORT_FIELD, mChangeFieldHandler,
                    new DeckTask.TaskData(new Object[]{mMod, mCurrentPos}));
        } catch (ConfirmModSchemaException e) {
            // Handler mMod schema confirmation
            ConfirmationDialog c = new ConfirmationDialog();
            c.setArgs(getResources().getString(R.string.full_sync_confirmation));
            Runnable confirm = new Runnable() {
                @Override
                public void run() {
                    mCol.modSchemaNoCheck();
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_CHANGE_SORT_FIELD, mChangeFieldHandler,
                            new DeckTask.TaskData(new Object[]{mMod, mCurrentPos}));
                    dismissContextMenu();
                }
            };
            c.setConfirm(confirm);
            c.setCancel(mConfirmDialogCancel);
            ModelFieldEditor.this.showDialogFragment(c);
        }
    }


    /*
     * Reloads everything
     */
    private void fullRefreshList() {
        setupLabels();
        createfieldLabels();
    }


    /*
     * Checks if there exists a field with this name in the current model
     */
    private boolean containsField(String field) {
        for (String s : mFieldLabels) {
            if (field.compareTo(s) == 0) {
                return true;
            }
        }
        return false;
    }


    private void showToast(CharSequence text) {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(this, text, duration);
        toast.show();
    }


    // ----------------------------------------------------------------------------
    // HANDLERS
    // ----------------------------------------------------------------------------


    /*
     * Called during the desk task when any field is modified
     */
    private DeckTask.TaskListener mChangeFieldHandler = new DeckTask.TaskListener() {

        @Override
        public void onCancelled() {
            //This decktask can not be interrupted
        }

        @Override
        public void onPreExecute() {
            if (mProgressDialog == null) {
                mProgressDialog = StyledProgressDialog.show(ModelFieldEditor.this, getIntent().getStringExtra("title"),
                        getResources().getString(R.string.model_field_editor_changing), false);
            }
        }

        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (!result.getBoolean()) {
                closeActivity(DeckPicker.RESULT_DB_ERROR);
            }

            dismissProgressBar();
            fullRefreshList();
        }

        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            //This decktask does not publish updates
        }
    };


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_add_new_model:
                addFieldDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    public void closeActivity() {
        closeActivity(NORMAL_EXIT);
    }


    private void closeActivity(int reason) {
        switch (reason) {
            case NORMAL_EXIT:
                finishWithAnimation(ActivityTransitionAnimation.RIGHT);
                break;
            default:
                finishWithAnimation(ActivityTransitionAnimation.RIGHT);
                break;
        }
    }


    @Override
    public void onBackPressed() {
        closeActivity();
    }


    private MaterialDialog.ListCallback mContextMenuListener = new MaterialDialog.ListCallback() {
        @Override
        public void onSelection(MaterialDialog materialDialog, View view, int selection, CharSequence charSequence) {
            switch (selection) {
                case ModelEditorContextMenu.SORT_FIELD:
                    sortByField();
                    break;
                case ModelEditorContextMenu.FIELD_REPOSITION:
                    repositionFieldDialog();
                    break;
                case ModelEditorContextMenu.FIELD_DELETE:
                    deleteFieldDialog();
                    break;
                case ModelEditorContextMenu.FIELD_RENAME:
                    renameFieldDialog();
                    break;
            }
        }
    };
}