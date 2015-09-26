package com.ichi2.anki;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
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
import com.ichi2.themes.StyledProgressDialog;
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
    private MaterialDialog mProgressDialog;

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

    private ArrayAdapter<String> mNewModelAdapter;

    private ModelBrowserContextMenu mContextMenu;

    private ArrayList<String> mNewModelNames;
    private ArrayList<String> mNewModelLabels;


    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    /*
     * Displays the loading bar when loading the mModels and displaying them
     * loading bar is necessary because card count per model is not cached *
     */
    private DeckTask.TaskListener mLoadingModelsHandler = new DeckTask.TaskListener() {
        @Override
        public void onCancelled() {
            //This DeckTask can not be interrupted
            return;
        }

        @Override
        public void onPreExecute() {
            if (mProgressDialog == null) {
                mProgressDialog = StyledProgressDialog.show(ModelBrowser.this, " ",
                        getResources().getString(R.string.model_browser_loading_models), false);
            }
        }

        @Override
        public void onPostExecute(TaskData result) {
            if (!result.getBoolean()) {
                throw new RuntimeException();
            }

            dismissProgressBar();

            mModels = (ArrayList<JSONObject>) result.getObjArray()[0];
            mCardCounts = (ArrayList<Integer>) result.getObjArray()[1];

            fillModelList();
        }

        @Override
        public void onProgressUpdate(TaskData... values) {
            //This decktask does not publish updates
            return;
        }
    };
    /*
     * Displays loading bar when deleting a model loading bar is needed
     * because deleting a model also deletes all of the associated cards/notes *
     */
    private DeckTask.TaskListener mDeleteModelHandler = new DeckTask.TaskListener() {

        @Override
        public void onCancelled() {
            //This decktask can not be interrupted
            return;
        }

        @Override
        public void onPreExecute() {
            if (mProgressDialog == null) {
                mProgressDialog = StyledProgressDialog.show(ModelBrowser.this, mModelDisplayList.get(mModelListPosition).getName(),
                        getResources().getString(R.string.model_browser_deletion_in_progress), false);
            }
        }

        @Override
        public void onPostExecute(TaskData result) {
            if (!result.getBoolean()) {
                throw new RuntimeException();
            }

            dismissProgressBar();
            refreshList();
        }

        @Override
        public void onProgressUpdate(TaskData... values) {
            //This decktask does not publish updates
            return;
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View mainView = getLayoutInflater().inflate(R.layout.model_browser, null);
        setContentView(mainView);
        mModelListView = (ListView) findViewById(R.id.note_type_browser_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        mActionBar = getSupportActionBar();
        startLoadingCollection();
    }


    // ----------------------------------------------------------------------------
    // ANKI METHODS
    // ----------------------------------------------------------------------------

    @Override
    public void onResume() {
        Timber.d("onResume()");
        super.onResume();
    }


    // ----------------------------------------------------------------------------
    // UI SETUP
    // ----------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.model_browser, menu);
        return true;
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
    // CONTEXT MENU DIALOGS
    // ----------------------------------------------------------------------------

    @Override
    public void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        this.col = col;
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_COUNT_MODELS, mLoadingModelsHandler);
    }

    /*
     * Fills the main list view with model names.
     * Handles filling the ArrayLists and attaching
     * ArrayAdapters to main ListView
     */
    private void fillModelList() {
        //Anonymous class for handling list item clicks
        mModelDisplayList = new ArrayList<DisplayPair>();
        mModelIds = new ArrayList<Long>();

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


    // ----------------------------------------------------------------------------
    // HELPER METHODS
    // ----------------------------------------------------------------------------

    /*
     *Creates the dialogue box to select a note type, add a name, and then clone it
     */
    private void addNewNoteDialog() {

        String add = getResources().getString(R.string.model_browser_add_add);
        String clone = getResources().getString(R.string.model_browser_add_clone);

        /** AnkiDroid doesn't have stdmodels class or model name localization, this could be much cleaner if implemented*/

        final String basicName = "Basic";
        final String addForwardReverseName = "Basic (and reversed card)";
        final String addForwardOptionalReverseName = "Basic (optional reversed card)";
        final String addClozeModelName = "Cloze";

        //Populates arrayadapters listing the mModels (includes prefixes/suffixes)
        mNewModelLabels = new ArrayList<String>();

        //Used to fetch model names
        mNewModelNames = new ArrayList<String>();
        mNewModelLabels.add(String.format(add, basicName));
        mNewModelLabels.add(String.format(add, addForwardReverseName));
        mNewModelLabels.add(String.format(add, addForwardOptionalReverseName));
        mNewModelLabels.add(String.format(add, addClozeModelName));

        mNewModelNames.add(basicName);
        mNewModelNames.add(addForwardReverseName);
        mNewModelNames.add(addForwardOptionalReverseName);
        mNewModelNames.add(addClozeModelName);

        for (JSONObject model : mModels) {
            try {
                mNewModelLabels.add(String.format(clone, model.getString("name")));
                mNewModelNames.add(model.getString("name"));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        final Spinner addSelectionSpinner = new Spinner(this);
        mNewModelAdapter = new ArrayAdapter<String>(this, R.layout.dropdown_deck_item, mNewModelLabels);

        addSelectionSpinner.setAdapter(mNewModelAdapter);

        new MaterialDialog.Builder(this)
                .title(R.string.model_browser_add)
                .positiveText(R.string.dialog_ok)
                .customView(addSelectionSpinner, true)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        mModelNameInput = new EditText(ModelBrowser.this);
                        mModelNameInput.setSingleLine();

                        //Temporary workaround - Lack of stdmodels class
                        if (addSelectionSpinner.getSelectedItemPosition() < mNewModelLabels.size()) {
                            mModelNameInput.setText(randomizeName(mNewModelNames.get(addSelectionSpinner.getSelectedItemPosition())));
                        } else {
                            mModelNameInput.setText(mNewModelNames.get(addSelectionSpinner.getSelectedItemPosition()) +
                                    " " + getResources().getString(R.string.model_clone_suffix));
                        }

                        mModelNameInput.setSelection(mModelNameInput.getText().length());

                        //Create textbox to name new model
                        new MaterialDialog.Builder(ModelBrowser.this)
                                .title(R.string.model_browser_add)
                                .positiveText(R.string.dialog_ok)
                                .customView(mModelNameInput, true)
                                .callback(new MaterialDialog.ButtonCallback() {
                                    @Override
                                    public void onPositive(MaterialDialog dialog) {
                                        JSONObject model = null;

                                        String fieldName = mModelNameInput.getText().toString()
                                                .replaceAll("[\'\"\\n\\r\\[\\]\\(\\)]", "");

                                        //Temporary workaround - Lack of stdmodels class, so can only handle 4 default English mModels
                                        //like Ankidroid but unlike desktop Anki
                                        try {
                                            if (fieldName.length() > 0) {
                                                switch (addSelectionSpinner.getSelectedItemPosition()) {
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
                                                        JSONObject oldModel = new JSONObject(mModels.get(addSelectionSpinner.getSelectedItemPosition() - 4).toString());
                                                        JSONObject newModel = Models.addBasicModel(col);
                                                        oldModel.put("id", newModel.get("id"));
                                                        model = oldModel;

                                                }

                                                model.put("name", fieldName);
                                                col.getModels().update(model);

                                                fullRefresh();

                                            } else {
                                                showToast(getResources().getString(R.string.toast_empty_name));
                                            }
                                        } catch (ConfirmModSchemaException e) {
                                            //We should never get here since we're only modifying new mModels
                                            return;
                                        } catch (JSONException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                })
                                .negativeText(R.string.dialog_cancel)
                                .show();
                    }

                })
                .negativeText(R.string.dialog_cancel)
                .show();
    }

    /*
     * Displays a confirmation box asking if you want to delete the note type and then deletes it if confirmed
     */
    private void deleteModelDialog() {
        if (mModelIds.size() > 1) {
            try {
                col.modSchema();
                deleteModel();
            } catch (ConfirmModSchemaException e) {
                ConfirmationDialog d = new ConfirmationDialog() {

                    public void confirm() {
                        ConfirmationDialog c = new ConfirmationDialog() {
                            public void confirm() {
                                try {
                                    col.modSchema(false);
                                    deleteModel();
                                } catch (ConfirmModSchemaException e) {
                                    //This should never be reached because it's inside a catch block for ConfirmModSchemaException
                                }
                                dismissContextMenu();
                            }

                            public void cancel() {
                                dismissContextMenu();
                            }
                        };
                        c.setArgs(getResources().getString(R.string.full_sync_confirmation));
                        ModelBrowser.this.showDialogFragment(c);

                    }

                    public void cancel() {
                        dismissContextMenu();
                    }
                };
                d.setArgs(getResources().getString(R.string.model_delete_warning));
                ModelBrowser.this.showDialogFragment(d);
            }
        }

        // Prevent users from deleting last model
        else {
            showToast(getString(R.string.toast_last_model));
        }
    }

    /*
     * Displays a confirmation box asking if you want to delete the note type and then deletes it if confirmed
     */
    private void renameModelDialog() {
        try {
            mModelNameInput = new EditText(this);
            mModelNameInput.setSingleLine(true);
            mModelNameInput.setText(mModels.get(mModelListPosition).getString("name"));
            mModelNameInput.setSelection(mModelNameInput.getText().length());
            new MaterialDialog.Builder(this)
                    .title(R.string.rename_model)
                    .positiveText(R.string.dialog_ok)
                    .customView(mModelNameInput, true)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            JSONObject model = mModels.get(mModelListPosition);
                            String deckName = mModelNameInput.getText().toString()
                                    .replaceAll("[\'\"\\n\\r\\[\\]\\(\\)]", "");
                            getCol().getDecks().id(deckName, true);
                            if (deckName.length() > 0) {
                                try {
                                    model.put("name", deckName);
                                    col.getModels().update(model);
                                    mModels.get(mModelListPosition).put("name", deckName);
                                    mModelDisplayList.set(mModelListPosition, new DisplayPair(mModels.get(mModelListPosition).getString("name"), mCardCounts.get(mModelListPosition)));
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                                refreshList();
                            } else {
                                showToast(getResources().getString(R.string.toast_empty_name));
                            }
                        }
                    })
                    .negativeText(R.string.dialog_cancel)
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

    private void dismissProgressBar() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        mProgressDialog = null;
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

    @Override
    public void onBackPressed() {
        finishWithAnimation(ActivityTransitionAnimation.RIGHT);
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


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_add_model_to_field:
                addNewNoteDialog();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


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
        public View getView(int position, View convertView, ViewGroup parent) {
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
}