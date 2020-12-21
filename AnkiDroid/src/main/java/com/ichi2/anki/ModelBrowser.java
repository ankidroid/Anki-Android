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

import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.dialogs.ConfirmationDialog;
import com.ichi2.anki.dialogs.ModelBrowserContextMenu;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskListenerWithContext;
import com.ichi2.async.TaskManager;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.StdModels;
import com.ichi2.ui.FixedEditText;
import com.ichi2.utils.Triple;
import com.ichi2.widget.WidgetStatus;

import java.util.ArrayList;
import java.util.Random;

import timber.log.Timber;

import static com.ichi2.anim.ActivityTransitionAnimation.Direction.LEFT;


public class ModelBrowser extends AnkiActivity {

    public static final int REQUEST_TEMPLATE_EDIT = 3;

    DisplayPairAdapter mModelDisplayAdapter;
    private ListView mModelListView;

    // Of the currently selected model
    private long mCurrentID;
    private int mModelListPosition;

    //Used exclusively to display model name
    private ArrayList<Model> mModels;
    private ArrayList<Integer> mCardCounts;
    private ArrayList<Long> mModelIds;
    private ArrayList<DisplayPair> mModelDisplayList;

    private Collection col;
    private ActionBar mActionBar;

    //Dialogue used in renaming
    private EditText mModelNameInput;

    private ModelBrowserContextMenu mContextMenu;

    private ArrayList<String> mNewModelNames;


    // ----------------------------------------------------------------------------
    // AsyncTask methods
    // ----------------------------------------------------------------------------


    /*
     * Displays the loading bar when loading the mModels and displaying them
     * loading bar is necessary because card count per model is not cached *
     */
    private LoadingModelsHandler loadingModelsHandler() {
        return new LoadingModelsHandler(this);
    }
    private static class LoadingModelsHandler extends TaskListenerWithContext<ModelBrowser, Void, Pair<ArrayList<Model>, ArrayList<Integer>>> {
        public LoadingModelsHandler(ModelBrowser browser) {
            super(browser);
        }

        @Override
        public void actualOnCancelled(@NonNull ModelBrowser browser) {
            browser.hideProgressBar();
        }

        @Override
        public void actualOnPreExecute(@NonNull ModelBrowser browser) {
            browser.showProgressBar();
        }

        @Override
        public void actualOnPostExecute(@NonNull ModelBrowser browser, Pair<ArrayList<Model>, ArrayList<Integer>> result) {
            if (result == null) {
                throw new RuntimeException();
            }
            browser.hideProgressBar();
            browser.mModels = result.first;
            browser.mCardCounts = result.second;

            browser.fillModelList();
        }
    }


    /*
     * Displays loading bar when deleting a model loading bar is needed
     * because deleting a model also deletes all of the associated cards/notes *
     */
    private DeleteModelHandler deleteModelHandler() {
        return new DeleteModelHandler(this);
    }
    private static class DeleteModelHandler extends TaskListenerWithContext<ModelBrowser, Void, Boolean>{
        public DeleteModelHandler(ModelBrowser browser) {
            super(browser);
        }

        @Override
        public void actualOnPreExecute(@NonNull ModelBrowser browser) {
            browser.showProgressBar();
        }

        @Override
        public void actualOnPostExecute(@NonNull ModelBrowser browser, Boolean result) {
            if (!result) {
                throw new RuntimeException();
            }
            browser.hideProgressBar();
            browser.refreshList();
        }
    }



    /*
     * Listens to long hold context menu for main list items
     */
    private final MaterialDialog.ListCallback mContextMenuListener = (materialDialog, view, selection, charSequence) -> {
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
    };


    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return;
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.model_browser);
        mModelListView = findViewById(R.id.note_type_browser_list);
        enableToolbar();
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
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (itemId == R.id.action_add_new_note_type) {
            addNewNoteTypeDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (!isFinishing()) {
            WidgetStatus.update(this);
            UIUtils.saveCollectionInBackground();
        }
    }

    @Override
    public void onDestroy() {
        TaskManager.cancelAllTasks(CollectionTask.CountModels.class);
        super.onDestroy();
    }


    // ----------------------------------------------------------------------------
    // ANKI METHODS
    // ----------------------------------------------------------------------------
    @Override
    public void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        this.col = col;
        TaskManager.launchCollectionTask(new CollectionTask.CountModels(), loadingModelsHandler());
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
        mModelDisplayList = new ArrayList<>(mModels.size());
        mModelIds = new ArrayList<>(mModels.size());

        for (int i = 0; i < mModels.size(); i++) {
            mModelIds.add(mModels.get(i).getLong("id"));
            mModelDisplayList.add(new DisplayPair(mModels.get(i).getString("name"), mCardCounts.get(i)));
        }

        mModelDisplayAdapter = new DisplayPairAdapter(this, mModelDisplayList);
        mModelListView.setAdapter(mModelDisplayAdapter);

        mModelListView.setOnItemClickListener((parent, view, position, id) -> {
            long noteTypeID = mModelIds.get(position);
            mModelListPosition = position;
            Intent noteOpenIntent = new Intent(ModelBrowser.this, ModelFieldEditor.class);
            noteOpenIntent.putExtra("title", mModelDisplayList.get(position).getName());
            noteOpenIntent.putExtra("noteTypeID", noteTypeID);
            startActivityForResultWithAnimation(noteOpenIntent, 0, LEFT);
        });

        mModelListView.setOnItemLongClickListener((parent, view, position, id) -> {
            String cardName = mModelDisplayList.get(position).getName();
            mCurrentID = mModelIds.get(position);
            mModelListPosition = position;
            mContextMenu = ModelBrowserContextMenu.newInstance(cardName, mContextMenuListener);
            showDialogFragment(mContextMenu);
            return true;
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

        //Populates arrayadapters listing the mModels (includes prefixes/suffixes)
        int existingModelSize = (mModels == null) ? 0 : mModels.size();
        int stdModelSize = StdModels.stdModels.length;
        ArrayList<String> newModelLabels = new ArrayList<>(existingModelSize + stdModelSize);
        ArrayList<String> existingModelsNames = new ArrayList<>(existingModelSize);

        //Used to fetch model names
        mNewModelNames = new ArrayList<>(stdModelSize);
        for (StdModels StdModels: StdModels.stdModels) {
            String defaultName = StdModels.getDefaultName();
            newModelLabels.add(String.format(add, defaultName));
            mNewModelNames.add(defaultName);
        }

        final int numStdModels = newModelLabels.size();

        if (mModels != null) {
            for (Model model : mModels) {
                String name = model.getString("name");
                newModelLabels.add(String.format(clone, name));
                mNewModelNames.add(name);
                existingModelsNames.add(name);
            }
        }

        final Spinner addSelectionSpinner = new Spinner(this);
        ArrayAdapter<String> mNewModelAdapter = new ArrayAdapter<>(this, R.layout.dropdown_deck_item, newModelLabels);

        addSelectionSpinner.setAdapter(mNewModelAdapter);

        new MaterialDialog.Builder(this)
                .title(R.string.model_browser_add)
                .positiveText(R.string.dialog_ok)
                .customView(addSelectionSpinner, true)
                .onPositive((dialog, which) -> {
                        mModelNameInput = new FixedEditText(ModelBrowser.this);
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
        Model model;
        if (modelName.length() > 0) {
            int nbStdModels = StdModels.stdModels.length;
            if (position < nbStdModels) {
                model = StdModels.stdModels[position].add(col);
            } else {
                //New model
                //Model that is being cloned
                Model oldModel = mModels.get(position - nbStdModels).deepClone();
                Model newModel = StdModels.basicModel.add(col);
                oldModel.put("id", newModel.getLong("id"));
                model = oldModel;
            }
            model.put("name", modelName);
            col.getModels().update(model);
            fullRefresh();
        } else {
            showToast(getResources().getString(R.string.toast_empty_name));
        }
    }


    /*
     * Displays a confirmation box asking if you want to delete the note type and then deletes it if confirmed
     */
    private void deleteModelDialog() {
        if (mModelIds.size() > 1) {
            Runnable confirm = () -> {
                col.modSchemaNoCheck();
                deleteModel();
                dismissContextMenu();
            };
            Runnable cancel = this::dismissContextMenu;

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
        mModelNameInput = new FixedEditText(this);
        mModelNameInput.setSingleLine(true);
        mModelNameInput.setText(mModels.get(mModelListPosition).getString("name"));
        mModelNameInput.setSelection(mModelNameInput.getText().length());
        new MaterialDialog.Builder(this)
                            .title(R.string.rename_model)
                            .positiveText(R.string.rename)
                            .negativeText(R.string.dialog_cancel)
                            .customView(mModelNameInput, true)
                            .onPositive((dialog, which) -> {
                                    Model model = mModels.get(mModelListPosition);
                                    String deckName = mModelNameInput.getText().toString()
                                            // Anki desktop doesn't allow double quote characters in deck names
                                            .replaceAll("[\"\\n\\r]", "");
                                    if (deckName.length() > 0) {
                                        model.put("name", deckName);
                                        col.getModels().update(model);
                                        mModels.get(mModelListPosition).put("name", deckName);
                                        mModelDisplayList.set(mModelListPosition,
                                                new DisplayPair(mModels.get(mModelListPosition).getString("name"),
                                                        mCardCounts.get(mModelListPosition)));
                                        refreshList();
                                    } else {
                                        showToast(getResources().getString(R.string.toast_empty_name));
                                    }
                                })
                            .show();
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
        startActivityForResultWithAnimation(intent, REQUEST_TEMPLATE_EDIT, LEFT);
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
        TaskManager.launchCollectionTask(new CollectionTask.CountModels(), loadingModelsHandler());
    }

    /*
     * Deletes the currently selected model
     */
    private void deleteModel() {
        TaskManager.launchCollectionTask(new CollectionTask.DeleteModel(mCurrentID), deleteModelHandler());
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
    public static class DisplayPair {
        private final String name;
        private final int count;

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
        public @NonNull String toString() {
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

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            DisplayPair item = getItem(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.model_browser_list_item, parent, false);
            }

            TextView tvName = convertView.findViewById(R.id.model_list_item_1);
            TextView tvHome = convertView.findViewById(R.id.model_list_item_2);

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
            TaskManager.launchCollectionTask(new CollectionTask.CountModels(), loadingModelsHandler());
        }
    }
}
