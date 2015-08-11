package com.ichi2.anki;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
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


public class ModelBrowser extends NavigationDrawerActivity {

    private final static int NORMAL_EXIT = 100001;
    private final static int DB_ERROR_EXIT = 100002;

    public static final int REQUEST_ADD = 0;
    public static final int REQUEST_TEMPLATE_EDIT = 3;

    DisplayPairAdapter noteTypeArrayAdapter;
    private ListView noteTypeListView;
    private MaterialDialog mProgressDialog;

    // Of the currently selected model
    private long currentID;
    private int currentPos;

    //Used exclusively to display model name
    private ArrayList<JSONObject> models;
    private ArrayList<Integer> cardCounts;
    private ArrayList<Long> modelIds;
    private ArrayList<DisplayPair> modelDisplay;

    private Collection col;
    private ActionBar mActionBar;

    //Dialogue used in renaming
    private EditText modelNameInput;

    private Spinner addSelectionSpinner;
    private JSONObject cloneNote;
    private ArrayAdapter<String> addModelAdapter;

    private ModelBrowserContextMenu cMenu;

    ArrayList<String> modelAddName;
    ArrayList<String> addModelList;


    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View mainView = getLayoutInflater().inflate(R.layout.model_browser, null);
        setContentView(mainView);
        initNavigationDrawer(mainView);
        noteTypeListView = (ListView) findViewById(R.id.note_type_browser_list);
        mActionBar = getSupportActionBar();
        startLoadingCollection();
    }


    @Override
    public void onResume() {
        Timber.d("onResume()");
        super.onResume();
        selectNavigationItem(R.id.nav_model_browser);
    }


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
    // ANKI METHODS
    // ----------------------------------------------------------------------------


    @Override
    public void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        this.col = col;
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_COUNT_MODELS, mLoadingModelsHandler);
    }


    // ----------------------------------------------------------------------------
    // UI SETUP
    // ----------------------------------------------------------------------------


    /* Fills the main list view with model names
    handles filling the arraylists and attaching arrayadapters to main listview */
    private void fillModelList() {
        //Anonymous class for handling list item clicks
        modelDisplay = new ArrayList<DisplayPair>();
        modelIds = new ArrayList<Long>();

        for (int i = 0; i < models.size(); i++) {
            try {
                modelIds.add(models.get(i).getLong("id"));
                modelDisplay.add(new DisplayPair(models.get(i).getString("name"), cardCounts.get(i)));
            } catch (JSONException e) {
                closeActivity(DB_ERROR_EXIT);
            }
        }

        noteTypeArrayAdapter = new DisplayPairAdapter(this, modelDisplay);

        noteTypeListView.setAdapter(noteTypeArrayAdapter);

        noteTypeListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                long noteTypeID = modelIds.get(position);
                currentPos = position;
                Intent noteOpenIntent = new Intent(ModelBrowser.this, ModelEditor.class);
                noteOpenIntent.putExtra("title", modelDisplay.get(position).getName());
                noteOpenIntent.putExtra("requestCode", ModelEditor.EDIT_NOTE_TYPE);
                noteOpenIntent.putExtra("noteTypeID", noteTypeID);
                startActivityForResultWithAnimation(noteOpenIntent, ModelEditor.EDIT_NOTE_TYPE, ActivityTransitionAnimation.LEFT);
            }
        });

        noteTypeListView.setOnItemLongClickListener(new OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                String cardName = modelDisplay.get(position).getName();
                currentID = modelIds.get(position);
                currentPos = position;
                cMenu = new ModelBrowserContextMenu().newInstance(cardName, mContextMenuListener);
                showDialogFragment(cMenu);
                return true;
            }
        });
        updateSubtitleText();
    }


    /* Updates the subtitle showing the amount of models available
    ONLY CALL THIS AFTER initializing the main list */
    private void updateSubtitleText() {
        int count = modelIds.size();
        mActionBar.setSubtitle(getResources().getQuantityString(R.plurals.model_browser_types_available, count, count));
    }


    // ----------------------------------------------------------------------------
    // HELPER METHODS
    // ----------------------------------------------------------------------------


    private void dismissProgressBar() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        mProgressDialog = null;
    }


    private void renameModel() {
        try {
            modelNameInput = new EditText(this);
            modelNameInput.setSingleLine(true);
            modelNameInput.setText(models.get(currentPos).getString("name"));
            modelNameInput.setSelection(modelNameInput.getText().length());
            new MaterialDialog.Builder(this)
                    .title(R.string.rename_model)
                    .positiveText(R.string.dialog_ok)
                    .customView(modelNameInput, true)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            JSONObject model = models.get(currentPos);
                            String deckName = modelNameInput.getText().toString()
                                    .replaceAll("[\'\"\\n\\r\\[\\]\\(\\)]", "");
                            getCol().getDecks().id(deckName, true);
                            if (deckName.length() > 0) {
                                try {
                                    model.put("name", deckName);
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                                col.getModels().update(model);
                                try {
                                    models.get(currentPos).put("name", deckName);
                                    modelDisplay.set(currentPos, new DisplayPair(models.get(currentPos).getString("name"), cardCounts.get(currentPos)));
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                                refreshList();
                                if (cMenu != null) {
                                    cMenu.dismiss();
                                    cMenu = null;
                                }
                            }
                            else{
                                showToast(getResources().getString(R.string.toast_empty_name));
                                if (cMenu != null) {
                                    cMenu.dismiss();
                                    cMenu = null;
                                }
                            }
                        }
                    })
                    .negativeText(R.string.dialog_cancel)
                    .show();
        } catch (JSONException e) {
            closeActivity(DB_ERROR_EXIT);
        }
    }


    private void openTemplateEditor() {
        Intent intent = new Intent(this, CardTemplateEditor.class);
        intent.putExtra("modelId", currentID);
        startActivityForResultWithAnimation(intent, REQUEST_TEMPLATE_EDIT, ActivityTransitionAnimation.LEFT);
    }

    /* Updates the ArrayAdapters, you must update the arraylists yourself */
    private void refreshList() {
        noteTypeArrayAdapter.notifyDataSetChanged();
        updateSubtitleText();
    }

    /* Also reloads everything, takes longer than a normal refresh */
    private void fullRefresh() {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_COUNT_MODELS, mLoadingModelsHandler);
    }


    /* Creates the dialogue box to select a note type, add a name, and then clone it */
    private void addNewNote() {

        String add = getResources().getString(R.string.model_browser_add_add);
        String clone = getResources().getString(R.string.model_browser_add_clone);

        /** AnkiDroid doesn't have stdmodels class or model name localization, this could be much cleaner if implemented*/

        final String basicName = "Basic";
        final String addForwardReverseName = "Basic (and reversed card)";
        final String addForwardOptionalReverseName = "Basic (optional reversed card)";
        final String addClozeModelName = "Cloze";

        //Populates arrayadapters listing the models (includes prefixes/suffixes)
        addModelList = new ArrayList<String>();
        //Used to fetch model names
        modelAddName = new ArrayList<String>();

        addModelList.add(String.format(add, basicName));
        addModelList.add(String.format(add, addForwardReverseName));
        addModelList.add(String.format(add, addForwardOptionalReverseName));
        addModelList.add(String.format(add, addClozeModelName));

        modelAddName.add(basicName);
        modelAddName.add(addForwardReverseName);
        modelAddName.add(addForwardOptionalReverseName);
        modelAddName.add(addClozeModelName);

        for (int i = 0; i < models.size(); i++) {
            try {
                addModelList.add(String.format(clone, models.get(i).getString("name")));
                modelAddName.add(models.get(i).getString("name"));
            } catch (JSONException e) {
                closeActivity(DB_ERROR_EXIT);
            }
        }

        final Spinner addSelectionSpinner = new Spinner(this);
        addModelAdapter = new ArrayAdapter<String>(this, R.layout.dropdown_deck_item, addModelList);

        addSelectionSpinner.setAdapter(addModelAdapter);

        new MaterialDialog.Builder(this)
                .title(R.string.model_browser_add)
                .positiveText(R.string.dialog_ok)
                .customView(addSelectionSpinner, true)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        modelNameInput = new EditText(ModelBrowser.this);
                        modelNameInput.setSingleLine();

                        //Temporary workaround - Lack of stdmodels class
                        if (addSelectionSpinner.getSelectedItemPosition() < 4) {
                            modelNameInput.setText(randomizeName(modelAddName.get(addSelectionSpinner.getSelectedItemPosition())));
                        } else {
                            modelNameInput.setText(modelAddName.get(addSelectionSpinner.getSelectedItemPosition()) +
                                    " " + getResources().getString(R.string.model_clone_suffix));
                        }

                        modelNameInput.setSelection(modelNameInput.getText().length());

                        //Create textbox to name new model
                        new MaterialDialog.Builder(ModelBrowser.this)
                                .title(R.string.model_browser_add)
                                .positiveText(R.string.dialog_ok)
                                .customView(modelNameInput, true)
                                .callback(new MaterialDialog.ButtonCallback() {
                                    @Override
                                    public void onPositive(MaterialDialog dialog) {
                                        JSONObject model;

                                        String fieldName = modelNameInput.getText().toString()
                                                .replaceAll("[\'\"\\n\\r\\[\\]\\(\\)]", "");

                                        //Temporary workaround - Lack of stdmodels class, so can only handle 4 default English models
                                        //like Ankidroid but unlike desktop Anki

                                        if(fieldName.length() > 0) {
                                            switch (addSelectionSpinner.getSelectedItemPosition()) {
                                                //Basic Model
                                                case (0):
                                                    try {
                                                        model = Models.addBasicModel(col);
                                                    } catch (ConfirmModSchemaException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                    break;
                                                //Add forward reverse model
                                                case (1):
                                                    try {
                                                        model = Models.addForwardReverse(col);
                                                    } catch (ConfirmModSchemaException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                    break;
                                                //Add forward optional reverse model
                                                case (2):
                                                    try {
                                                        model = Models.addForwardOptionalReverse(col);
                                                    } catch (ConfirmModSchemaException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                    break;
                                                //Close model
                                                case (3):
                                                    try {
                                                        model = Models.addClozeModel(col);
                                                    } catch (ConfirmModSchemaException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                    break;
                                                default:
                                                    //New model
                                                    try {
                                                        //Model that is being cloned
                                                        JSONObject oldModel = new JSONObject(models.get(addSelectionSpinner.getSelectedItemPosition() - 4).toString());
                                                        JSONObject newModel = Models.addBasicModel(col);
                                                        oldModel.put("id", newModel.get("id"));

                                                        model = oldModel;
                                                    } catch (ConfirmModSchemaException e) {
                                                        throw new RuntimeException(e);
                                                    } catch (JSONException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                            }

                                            try {
                                                model.put("name", fieldName);
                                                col.getModels().update(model);
                                            } catch (JSONException e) {
                                                throw new RuntimeException(e);
                                            }
                                            fullRefresh();
                                            if (cMenu != null) {
                                                cMenu.dismiss();
                                                cMenu = null;
                                            }
                                        } else{
                                            showToast(getResources().getString(R.string.toast_empty_name));
                                            if (cMenu != null) {
                                                cMenu.dismiss();
                                                cMenu = null;
                                            }
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

    // ----------------------------------------------------------------------------
    // HANDLERS
    // ----------------------------------------------------------------------------


    public void closeActivity() {
        closeActivity(NORMAL_EXIT);
    }

    private void closeActivity(int reason) {
        DeckTask.cancelTask();
        switch (reason) {
            case NORMAL_EXIT:
                setResult(NORMAL_EXIT);
                finishWithAnimation(ActivityTransitionAnimation.RIGHT);
                break;
            case DB_ERROR_EXIT:
                setResult(DB_ERROR_EXIT);
                finishWithAnimation(ActivityTransitionAnimation.RIGHT);
                break;
            default:
                setResult(NORMAL_EXIT);
                finishWithAnimation(ActivityTransitionAnimation.RIGHT);
                break;
        }
    }


    @Override
    public void onBackPressed() {
        closeActivity();
    }


    /* Displays a confirmation box asking if you want to delete the note type and then deletes it if confirmed */
    private void deleteModel() {
        if (modelIds.size() > 1) {
            ConfirmationDialog c = new ConfirmationDialog() {
                public void confirm() {
                    try {
                        col.modSchema(false);
                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DELETE_MODEL, mDeleteModelHandler,
                                new DeckTask.TaskData(currentID));
                        models.remove(currentPos);
                        modelIds.remove(currentPos);
                        modelDisplay.remove(currentPos);
                        cardCounts.remove(currentPos);
                        refreshList();
                        if (cMenu != null) {
                            cMenu.dismiss();
                            cMenu = null;
                        }
                    } catch (ConfirmModSchemaException e) {
                        throw new RuntimeException(e);
                    }
                }
                public void cancel() {
                    if (cMenu != null) {
                        cMenu.dismiss();
                        cMenu = null;
                    }
                }
            };
            c.setArgs(getResources().getString(R.string.full_sync_confirmation));
            ModelBrowser.this.showDialogFragment(c);
        }
        // Prevent users from deleting last model
        else {
            showToast(getString(R.string.toast_last_model));
        }
    }


    /* Listens to long hold context menu for main list items */
    private MaterialDialog.ListCallback mContextMenuListener = new MaterialDialog.ListCallback() {
        @Override
        public void onSelection(MaterialDialog materialDialog, View view, int selection, CharSequence charSequence) {
            ConfirmationDialog c;
            switch (selection) {
                case ModelBrowserContextMenu.MODEL_DELETE:
                    deleteModel();
                    break;
                case ModelBrowserContextMenu.MODEL_RENAME:
                    renameModel();
                    break;
                case ModelBrowserContextMenu.MODEL_TEMPLATE:
                    openTemplateEditor();
                    break;
            }
        }
    };


    /**
     * Displays the loading bar when loading the models and displaying them
     * loading bar is necessary because card count per model is not cached *
     */
    private DeckTask.TaskListener mLoadingModelsHandler = new DeckTask.TaskListener() {
        @Override
        public void onCancelled() {
            //This decktask can not be interrupted
            return;
        }

        @Override
        public void onPreExecute() {
            if (mProgressDialog == null) {
                mProgressDialog = StyledProgressDialog.show(ModelBrowser.this, " ",
                        getResources().getString(R.string.model_editor_loading_models), false);
            }
        }

        @Override
        public void onPostExecute(TaskData result) {
            if (!result.getBoolean()) {
                closeActivity(DeckPicker.RESULT_DB_ERROR);
            }

            dismissProgressBar();

            models = (ArrayList<JSONObject>) result.getObjArray()[0];
            cardCounts = (ArrayList<Integer>) result.getObjArray()[1];

            fillModelList();
        }

        @Override
        public void onProgressUpdate(TaskData... values) {
            //This decktask does not publish updates
            return;
        }
    };


    /**
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
                mProgressDialog = StyledProgressDialog.show(ModelBrowser.this, modelDisplay.get(currentPos).getName(),
                        getResources().getString(R.string.model_editor_deletion_in_progress), false);
            }
        }

        @Override
        public void onPostExecute(TaskData result) {
            if (!result.getBoolean()) {
                closeActivity(DeckPicker.RESULT_DB_ERROR);
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

    // Generates a random alphanumeric sequence of 6 characters
    // Used to append to the end of new note types to dissuade
    // User from reusing names (which are technically not unique however)
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
                addNewNote();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    /**
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


    /**
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
