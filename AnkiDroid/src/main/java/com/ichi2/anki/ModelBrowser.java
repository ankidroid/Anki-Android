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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.dialogs.ConfirmationDialog;
import com.ichi2.anki.dialogs.ModelBrowserContextMenu;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.async.DeckTask;
import com.ichi2.async.DeckTask.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Models;
import com.ichi2.widget.WidgetStatus;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Random;

import timber.log.Timber;


public class ModelBrowser extends AnkiActivity {

    public static final int REQUEST_TEMPLATE_EDIT = 3;

    DisplayPairAdapter mModelDisplayAdapter;
    private ListView mModelListView;

    // Of the currently selected model
    private long mCurrentID;
    private int mModelListPosition;

    //Used exclusively to display model name
    private ArrayList<JSONObject> mModels;
    private ArrayList<Integer> mCardCounts;
    private ArrayList<Long> mModelIds;
    private ArrayList<DisplayPair> mModelDisplayList;

    private Collection col;
    private ActionBar mActionBar;

    //Dialogue used in renaming
    private EditText mModelNameInput;

    private ModelBrowserContextMenu mContextMenu;

    private ArrayList<String> mNewModelNames;
    private ArrayList<String> mNewModelLabels;


    // ----------------------------------------------------------------------------
    // AsyncTask methods
    // ----------------------------------------------------------------------------


    /*
     * Displays the loading bar when loading the mModels and displaying them
     * loading bar is necessary because card count per model is not cached *
     */
    private DeckTask.TaskListener mLoadingModelsHandler = new DeckTask.TaskListener() {
        @Override
        public void onCancelled() {
            hideProgressBar();
        }

        @Override
        public void onPreExecute() {
            showProgressBar();
        }

        @Override
        public void onPostExecute(TaskData result) {

            if (!result.getBoolean()) {
                throw new RuntimeException();
            }
            hideProgressBar();
            mModels = (ArrayList<JSONObject>) result.getObjArray()[0];
            mCardCounts = (ArrayList<Integer>) result.getObjArray()[1];

            fillModelList();
        }
    };

    /*
     * Displays loading bar when deleting a model loading bar is needed
     * because deleting a model also deletes all of the associated cards/notes *
     */
    private DeckTask.TaskListener mDeleteModelHandler = new DeckTask.TaskListener() {

        @Override
        public void onPreExecute() {
            showProgressBar();
        }

        @Override
        public void onPostExecute(TaskData result) {
            if (!result.getBoolean()) {
                throw new RuntimeException();
            }
            hideProgressBar();
            refreshList();
        }
    };

    /*
     * Listens to long hold context menu for main list items
     */
    private MaterialDialog.ListCallback mContextMenuListener = new MaterialDialog.ListCallback() {
        @Override
        public void onSelection(MaterialDialog materialDialog, View view, int selection, CharSequence charSequence) {
            switch (selection) {
                case ModelBrowserContextMenu.MODEL_DELETE:
                    deleteModelDialog();
                    break;
                case ModelBrowserContextMenu.MODEL_RENAME:
                    renameModelDialog();
                    break;
                case ModelBrowserContextMenu.MODEL_TEMPLATE:
                    openTemplateEditor();
                    break;
            }
        }
    };


    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.model_browser);
        mModelListView = (ListView) findViewById(R.id.note_type_browser_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        mActionBar = getSupportActionBar();
        startLoadingCollection();
    }


    @Override
    public void onResume() {
        Timber.d("onResume()");
        super.onResume();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.model_browser, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_add_new_note_type:
                addNewNoteTypeDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (!isFinishing()) {
            WidgetStatus.update(this);
            UIUtils.saveCollectionInBackground(this);
        }
    }


    // ----------------------------------------------------------------------------
    // ANKI METHODS
    // ----------------------------------------------------------------------------
    @Override
    public void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        this.col = col;
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_COUNT_MODELS, mLoadingModelsHandler);
    }



    // ----------------------------------------------------------------------------
    // HELPER METHODS
    // ----------------------------------------------------------------------------

    /*
     * Fills the main list view with model names.
     * Handles filling the ArrayLists and attaching
     * ArrayAdapters to main ListView
     */
    private void fillModelList() {
        //Anonymous class for handling list item clicks
        mModelDisplayList = new ArrayList<>();
        mModelIds = new ArrayList<>();

        for (int i = 0; i < mModels.size(); i++) {
            try {
                mModelIds.add(mModels.get(i).getLong("id"));
                mModelDisplayList.add(new DisplayPair(mModels.get(i).getString("name"), mCardCounts.get(i)));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        mModelDisplayAdapter = new DisplayPairAdapter(this, mModelDisplayList);
        mModelListView.setAdapter(mModelDisplayAdapter);

        mModelListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                long noteTypeID = mModelIds.get(position);
                mModelListPosition = position;
                Intent noteOpenIntent = new Intent(ModelBrowser.this, ModelFieldEditor.class);
                noteOpenIntent.putExtra("title", mModelDisplayList.get(position).getName());
                noteOpenIntent.putExtra("noteTypeID", noteTypeID);
                startActivityForResultWithAnimation(noteOpenIntent, 0, ActivityTransitionAnimation.LEFT);
            }
        });

        mModelListView.setOnItemLongClickListener(new OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                String cardName = mModelDisplayList.get(position).getName();
                mCurrentID = mModelIds.get(position);
                mModelListPosition = position;
                mContextMenu = ModelBrowserContextMenu.newInstance(cardName, mContextMenuListener);
                showDialogFragment(mContextMenu);
                return true;
            }
        });
        updateSubtitleText();
    }

    /*
     * Updates the subtitle showing the amount of mModels available
     * ONLY CALL THIS AFTER initializing the main list
     */
    private void updateSubtitleText() {
        int count = mModelIds.size();
        mActionBar.setSubtitle(getResources().getQuantityString(R.plurals.model_browser_types_available, count, count));
    }


    /*
     *Creates the dialogue box to select a note type, add a name, and then clone it
     */
    private void addNewNoteTypeDialog() {

        String add = getResources().getString(R.string.model_browser_add_add);
        String clone = getResources().getString(R.string.model_browser_add_clone);

        // AnkiDroid doesn't have stdmodels class or model name localization, this could be much cleaner if implemented
        final String basicName = getResources().getString(R.string.basic_model_name);
        final String addForwardReverseName = getResources().getString(R.string.forward_reverse_model_name);
        final String addForwardOptionalReverseName = getResources().getString(R.string.forward_optional_reverse_model_name);
        final String addClozeModelName = getResources().getString(R.string.cloze_model_name);

        //Populates arrayadapters listing the mModels (includes prefixes/suffixes)
        mNewModelLabels = new ArrayList<>();
        ArrayList<String> existingModelsNames = new ArrayList<>();

        //Used to fetch model names
        mNewModelNames = new ArrayList<>();
        mNewModelLabels.add(String.format(add, basicName));
        mNewModelLabels.add(String.format(add, addForwardReverseName));
        mNewModelLabels.add(String.format(add, addForwardOptionalReverseName));
        mNewModelLabels.add(String.format(add, addClozeModelName));

        mNewModelNames.add(basicName);
        mNewModelNames.add(addForwardReverseName);
        mNewModelNames.add(addForwardOptionalReverseName);
        mNewModelNames.add(addClozeModelName);

        final int numStdModels = mNewModelLabels.size();

        if (mModels != null) {
            for (JSONObject model : mModels) {
                try {
                    String name = model.getString("name");
                    mNewModelLabels.add(String.format(clone, name));
                    mNewModelNames.add(name);
                    existingModelsNames.add(name);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        final Spinner addSelectionSpinner = new Spinner(this);
        ArrayAdapter<String> mNewModelAdapter = new ArrayAdapter<>(this, R.layout.dropdown_deck_item, mNewModelLabels);

        addSelectionSpinner.setAdapter(mNewModelAdapter);

        new MaterialDialog.Builder(this)
                .title(R.string.model_browser_add)
                .positiveText(R.string.dialog_ok)
                .customView(addSelectionSpinner, true)
                .onPositive((dialog, which) -> {
                        mModelNameInput = new EditText(ModelBrowser.this);
                        mModelNameInput.setSingleLine();
                        final boolean isStdModel = addSelectionSpinner.getSelectedItemPosition() < numStdModels;
                        // Try to find a unique model name. Add "clone" if cloning, and random digits if necessary.
                        String suggestedName = mNewModelNames.get(addSelectionSpinner.getSelectedItemPosition());
                        if (!isStdModel) {
                            suggestedName += " " + getResources().getString(R.string.model_clone_suffix);
                        }

                        if (existingModelsNames.contains(suggestedName)) {
                            suggestedName = randomizeName(suggestedName);
                        }
                        //Temporary workaround - Lack of stdmodels class
                        mModelNameInput.setText(suggestedName);
                        mModelNameInput.setSelection(mModelNameInput.getText().length());

                        //Create textbox to name new model
                        new MaterialDialog.Builder(ModelBrowser.this)
                                .title(R.string.model_browser_add)
                                .positiveText(R.string.dialog_ok)
                                .customView(mModelNameInput, true)
                                .onPositive((innerDialog, innerWhich) -> {
                                        String modelName = mModelNameInput.getText().toString();
                                        addNewNoteType(modelName, addSelectionSpinner.getSelectedItemPosition());
                                    }
                                )
                                .negativeText(R.string.dialog_cancel)
                                .show();
                    }
                )
                .negativeText(R.string.dialog_cancel)
                .show();
    }

    /**
     * Add a new note type
     * @param modelName name of the new model
     * @param position position in dialog the user selected to add / clone the model type from
     */
    private void addNewNoteType(String modelName, int position) {
        //Temporary workaround - Lack of stdmodels class, so can only handle 4 default English mModels
        //like Ankidroid but unlike desktop Anki
        JSONObject model;
        try {
            if (modelName.length() > 0) {
                switch (position) {
                    //Basic Model
                    case (0):
                        model = Models.addBasicModel(col);
                        break;
                    //Add forward reverse model
                    case (1):
                        model = Models.addForwardReverse(col);
                        break;
                    //Add forward optional reverse model
                    case (2):
                        model = Models.addForwardOptionalReverse(col);
                        break;
                    //Close model
                    case (3):
                        model = Models.addClozeModel(col);
                        break;
                    default:
                        //New model
                        //Model that is being cloned
                        JSONObject oldModel = new JSONObject(mModels.get(position - 4).toString());
                        JSONObject newModel = Models.addBasicModel(col);
                        oldModel.put("id", newModel.get("id"));
                        model = oldModel;
                        break;

                }
                model.put("name", modelName);
                col.getModels().update(model);
                fullRefresh();
            } else {
                showToast(getResources().getString(R.string.toast_empty_name));
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /*
     * Displays a confirmation box asking if you want to delete the note type and then deletes it if confirmed
     */
    private void deleteModelDialog() {
        if (mModelIds.size() > 1) {
            Runnable confirm = new Runnable() {
                @Override
                public void run() {
                    try {
                        col.modSchema(false);
                        deleteModel();
                    } catch (ConfirmModSchemaException e) {
                        //This should never be reached because modSchema() didn't throw an exception
                    }
                    dismissContextMenu();
                }
            };
            Runnable cancel = new Runnable() {
                @Override
                public void run() {
                    dismissContextMenu();
                }
            };

            try {
                col.modSchema();
                ConfirmationDialog d = new ConfirmationDialog();
                d.setArgs(getResources().getString(R.string.model_delete_warning));
                d.setConfirm(confirm);
                d.setCancel(cancel);
                ModelBrowser.this.showDialogFragment(d);
            } catch (ConfirmModSchemaException e) {
                ConfirmationDialog c = new ConfirmationDialog();
                c.setArgs(getResources().getString(R.string.full_sync_confirmation));
                c.setConfirm(confirm);
                c.setCancel(cancel);
                showDialogFragment(c);
            }
        }

        // Prevent users from deleting last model
        else {
            showToast(getString(R.string.toast_last_model));
        }
    }

    /*
     * Displays a confirmation box asking if you want to rename the note type and then renames it if confirmed
     */
    private void renameModelDialog() {
        try {
            mModelNameInput = new EditText(this);
            mModelNameInput.setSingleLine(true);
            mModelNameInput.setText(mModels.get(mModelListPosition).getString("name"));
            mModelNameInput.setSelection(mModelNameInput.getText().length());
            new MaterialDialog.Builder(this)
                                .title(R.string.rename_model)
                                .positiveText(R.string.rename)
                                .negativeText(R.string.dialog_cancel)
                                .customView(mModelNameInput, true)
                                .onPositive((dialog, which) -> {
                                        JSONObject model = mModels.get(mModelListPosition);
                                        String deckName = mModelNameInput.getText().toString()
                                                .replaceAll("[\'\"\\n\\r\\[\\]\\(\\)]", "");
                                        getCol().getDecks().id(deckName, false);
                                        if (deckName.length() > 0) {
                                            try {
                                                model.put("name", deckName);
                                                col.getModels().update(model);
                                                mModels.get(mModelListPosition).put("name", deckName);
                                                mModelDisplayList.set(mModelListPosition,
                                                        new DisplayPair(mModels.get(mModelListPosition).getString("name"),
                                                                mCardCounts.get(mModelListPosition)));
                                            } catch (JSONException e) {
                                                throw new RuntimeException(e);
                                            }
                                            refreshList();
                                        } else {
                                            showToast(getResources().getString(R.string.toast_empty_name));
                                        }
                                    })
                                .show();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void dismissContextMenu() {
        if (mContextMenu != null) {
            mContextMenu.dismiss();
            mContextMenu = null;
        }
    }


    /*
     * Opens the Template Editor (Card Editor) to allow
     * the user to edit the current note's templates.
     */
    private void openTemplateEditor() {
        Intent intent = new Intent(this, CardTemplateEditor.class);
        intent.putExtra("modelId", mCurrentID);
        startActivityForResultWithAnimation(intent, REQUEST_TEMPLATE_EDIT, ActivityTransitionAnimation.LEFT);
    }

    // ----------------------------------------------------------------------------
    // HANDLERS
    // ----------------------------------------------------------------------------

    /*
     * Updates the ArrayAdapters for the main ListView.
     * ArrayLists must be manually updated.
     */
    private void refreshList() {
        mModelDisplayAdapter.notifyDataSetChanged();
        updateSubtitleText();
    }

    /*
     * Reloads everything
     */
    private void fullRefresh() {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_COUNT_MODELS, mLoadingModelsHandler);
    }

    /*
     * Deletes the currently selected model
     */
    private void deleteModel() throws ConfirmModSchemaException {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DELETE_MODEL, mDeleteModelHandler,
                new DeckTask.TaskData(mCurrentID));
        mModels.remove(mModelListPosition);
        mModelIds.remove(mModelListPosition);
        mModelDisplayList.remove(mModelListPosition);
        mCardCounts.remove(mModelListPosition);
        refreshList();
    }


    /*
     * Generates a random alphanumeric sequence of 6 characters
     * Used to append to the end of new note types to dissuade
     * User from reusing names (which are technically not unique however
     */
    private String randomizeName(String s) {
        char[] charSet = "123456789abcdefghijklmnopqrstuvqxwzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

        char[] randomString = new char[6];
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            int randomIndex = random.nextInt(charSet.length);
            randomString[i] = charSet[randomIndex];
        }

        return s + " " + new String(randomString);
    }


    private void showToast(CharSequence text) {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(this, text, duration);
        toast.show();
    }


    // ----------------------------------------------------------------------------
    // CUSTOM ADAPTERS
    // ----------------------------------------------------------------------------


    /*
     * Used so that the main ListView is able to display the number of notes using the model
     * along with the name.
     */
    public class DisplayPair {
        private String name;
        private int count;

        public DisplayPair(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }

        @Override
        public String toString() {
            return getName();
        }
    }


    /*
     * For display in the main list via an ArrayAdapter
     */
    public class DisplayPairAdapter extends ArrayAdapter<DisplayPair> {
        public DisplayPairAdapter(Context context, ArrayList<DisplayPair> items) {
            super(context, R.layout.model_browser_list_item, R.id.model_list_item_1, items);
        }

        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            DisplayPair item = getItem(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.model_browser_list_item, parent, false);
            }

            TextView tvName = (TextView) convertView.findViewById(R.id.model_list_item_1);
            TextView tvHome = (TextView) convertView.findViewById(R.id.model_list_item_2);

            int count = item.getCount();

            tvName.setText(item.getName());
            tvHome.setText(getResources().getQuantityString(R.plurals.model_browser_of_type, count, count));

            return convertView;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_TEMPLATE_EDIT) {
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_COUNT_MODELS, mLoadingModelsHandler);
        }
    }
}
