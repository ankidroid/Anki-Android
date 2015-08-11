package com.ichi2.anki;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
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

public class ModelEditor extends AnkiActivity{

    public final static int NEW_NOTE_TYPE = 0;
    public final static int EDIT_NOTE_TYPE = 1;

    private final static String EXTRA_CALLER = "EXTRA_CALLER";

    private final static int NORMAL_EXIT = 100001;

    //Position of the current field selected
    private int currentPos;

    private boolean isNewType;

    private ArrayAdapter<String> fieldArrayAdapter;
    private ListView fieldLabelsView;
    private ArrayList<String> fieldLabels;
    private MaterialDialog mProgressDialog;

    private Collection col;
    private JSONArray noteFields;
    private JSONObject mod;

    private ModelEditorContextMenu cMenu;

    private EditText fieldNameInput;
    private EditText addRenameEditText;


    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View mainView = getLayoutInflater().inflate(R.layout.model_editor, null);
        setContentView(mainView);
        isNewType = NEW_NOTE_TYPE == getIntent().getIntExtra(EXTRA_CALLER, NEW_NOTE_TYPE);
        startLoadingCollection();
        Toolbar toolbar = (Toolbar) mainView.findViewById(R.id.toolbar);
        fieldLabelsView = (ListView) mainView.findViewById(R.id.note_type_editor_fields);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        getSupportActionBar().setTitle(getIntent().getStringExtra("title"));
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
    public boolean onCreateOptionsMenu(Menu menu){
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.model_editor, menu);
        return true;
    }



    // ----------------------------------------------------------------------------
    // ANKI METHODS
    // ----------------------------------------------------------------------------


    @Override
    protected void onCollectionLoaded(Collection col) {
        this.col = col;
        setupLabels();
        createfieldLabels();
    }


    // ----------------------------------------------------------------------------
    // UI SETUP
    // ----------------------------------------------------------------------------


    private void createfieldLabels() {
        fieldArrayAdapter = new ArrayAdapter<String>(this, R.layout.model_editor_list_item, fieldLabels);
        fieldLabelsView.setAdapter(fieldArrayAdapter);
        fieldLabelsView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                                                   @Override
                                                   public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                                                   }
                                               }
        );

        fieldLabelsView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                cMenu = new ModelEditorContextMenu().newInstance(fieldLabels.get(position), mContextMenuListener);
                showDialogFragment(cMenu);
                currentPos = position;
                return false;
            }
        });
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

    //Removes the currently selected field
    private void deleteField(){
        if(fieldLabels.size() < 2){
            showToast(getResources().getString(R.string.toast_last_field));
        }
        else{
            ConfirmationDialog c = new ConfirmationDialog() {
                public void confirm() {
                    try {
                        col.modSchema(false);
                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DELETE_FIELD, mChangeFieldHandler,
                                new DeckTask.TaskData(new Object[]{mod, noteFields.getJSONObject(currentPos)}));
                        if (cMenu != null) {
                            cMenu.dismiss();
                            cMenu = null;
                        }
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
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
            ModelEditor.this.showDialogFragment(c);
        }
    }


    //Creates dialogue for adding field
    private void addField() {
        fieldNameInput = new EditText(this);
        fieldNameInput.setSingleLine(true);
        new MaterialDialog.Builder(this)
                .title(R.string.model_browser_add)
                .positiveText(R.string.dialog_ok)
                .customView(fieldNameInput, true)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {

                        ConfirmationDialog c = new ConfirmationDialog() {
                            public void confirm() {
                                String fieldName = fieldNameInput.getText().toString()
                                        .replaceAll("[\'\"\\n\\r\\[\\]\\(\\)]", "");

                                if (fieldName.length() == 0) {
                                    showToast(getResources().getString(R.string.toast_empty_name));
                                } else if (containsField(fieldName)) {
                                    showToast(getResources().getString(R.string.toast_duplicate_field));
                                } else {
                                    try {
                                        col.modSchema(false);
                                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ADD_FIELD, mChangeFieldHandler,
                                                new DeckTask.TaskData(new Object[]{mod, fieldName}));
                                    } catch (ConfirmModSchemaException e) {
                                        throw new RuntimeException(e);
                                    }
                                    col.getModels().update(mod);
                                    if (cMenu != null) {
                                        cMenu.dismiss();
                                        cMenu = null;
                                    }
                                    fullRefreshList();
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
                        ModelEditor.this.showDialogFragment(c);
                    }
                })
                .negativeText(R.string.dialog_cancel)
                .show();
    }


    //Also creates the dialogue, reanames based off of selected element
    private void renameField(){
        fieldNameInput = new EditText(this);
        fieldNameInput.setSingleLine(true);
        fieldNameInput.setText(fieldLabels.get(currentPos));
        fieldNameInput.setSelection(fieldNameInput.getText().length());
        new MaterialDialog.Builder(this)
                .title(R.string.rename_model)
                .positiveText(R.string.dialog_ok)
                .customView(fieldNameInput, true)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        ConfirmationDialog c = new ConfirmationDialog() {
                            public void confirm() {
                                String fieldLabel = fieldNameInput.getText().toString()
                                        .replaceAll("[\'\"\\n\\r\\[\\]\\(\\)]", "");
                                if (fieldLabel.length() == 0) {
                                    showToast(getResources().getString(R.string.toast_empty_name));
                                } else if (containsField(fieldLabel)) {
                                    showToast(getResources().getString(R.string.toast_duplicate_field));
                                } else {
                                    try {
                                        col.modSchema(false);
                                        JSONObject field = (JSONObject) noteFields.get(currentPos);
                                        col.getModels().renameField(mod, field, fieldLabel);
                                        col.getModels().save();
                                        fullRefreshList();
                                    } catch (JSONException e) {
                                        throw new RuntimeException(e);
                                    } catch (ConfirmModSchemaException e) {
                                        throw new RuntimeException(e);
                                    }
                                    if (cMenu != null) {
                                        cMenu.dismiss();
                                        cMenu = null;
                                    }
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
                        ModelEditor.this.showDialogFragment(c);

                    }
                })
                .negativeText(R.string.dialog_cancel)
                .show();
    }

    //Also creates the dialogue, reanames based off of selected element
    private void repositionField(){
        fieldNameInput = new EditText(this);
        fieldNameInput.setSingleLine(true);
        new MaterialDialog.Builder(this)
                .title(String.format(getResources().getString(R.string.model_editor_reposition), 1, fieldLabels.size()))
                .positiveText(R.string.dialog_ok)
                .customView(fieldNameInput, true)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        ConfirmationDialog c = new ConfirmationDialog() {
                            public void confirm() {
                                String newPosition = fieldNameInput.getText().toString();
                                int pos;
                                try {
                                    pos = Integer.parseInt(newPosition);
                                }
                                catch (NumberFormatException n) {
                                    if (cMenu != null) {
                                        cMenu.dismiss();
                                        cMenu = null;
                                    }
                                    return;
                                }
                                if (pos < 1 || pos > fieldLabels.size()) {
                                    showToast(getResources().getString(R.string.toast_out_of_range));
                                }
                                else {
                                    try {
                                        col.modSchema(false);
                                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REPOSITION_FIELD, mChangeFieldHandler,
                                                new DeckTask.TaskData(new Object[]{mod, noteFields.getJSONObject(currentPos), new Integer(pos-1)}));
                                    } catch (JSONException e) {
                                        throw new RuntimeException(e);
                                    } catch (ConfirmModSchemaException e) {
                                        throw new RuntimeException(e);
                                    }
                                    if (cMenu != null) {
                                        cMenu.dismiss();
                                        cMenu = null;
                                    }
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
                        ModelEditor.this.showDialogFragment(c);

                    }
                })
                .negativeText(R.string.dialog_cancel)
                .show();
    }


    //Initializes the main list of field labels
    private void setupLabels(){
        long noteTypeID = getIntent().getLongExtra("noteTypeID", 0);
        mod = col.getModels().get(noteTypeID);

        fieldLabels = new ArrayList<String>();
        try{
            noteFields = mod.getJSONArray("flds");
            for(int i = 0; i < noteFields.length(); i++){
                JSONObject o = noteFields.getJSONObject(i);
                fieldLabels.add(o.getString("name"));
            }
        }
        catch(JSONException e) {
            throw new RuntimeException(e);
        }
    }


    //Refreshes the main list display
    private void refreshList(){
        fieldArrayAdapter.notifyDataSetChanged();
    }


    //Reloads everything
    private void fullRefreshList(){
        setupLabels();
        createfieldLabels();
    }

    //checks if model contains a field with name
    private boolean containsField(String field){
        for(String s : fieldLabels){
            if(field.compareTo(s) == 0){
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

    /**
     * Displays loading bar when deleting a model loading bar is needed
     * because deleting a model also deletes all of the associated cards/notes *
     */
    private DeckTask.TaskListener mChangeFieldHandler = new DeckTask.TaskListener() {

        @Override
        public void onCancelled() {
            //This decktask can not be interrupted
            return;
        }

        @Override
        public void onPreExecute() {
            if (mProgressDialog == null) {
                mProgressDialog = StyledProgressDialog.show(ModelEditor.this, getResources().getString(R.string.model_editor_changing),
                        getResources().getString(R.string.model_editor_changing), false);
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
            return;
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_add_new_model:
                addField();
                return true;
         default:
                return super.onOptionsItemSelected(item);
        }
    }


    public void closeActivity(){
        //todo:  is there any more behaviour that is needed when closing the note editor???
        closeActivity(NORMAL_EXIT);
    }


    private void closeActivity(int reason) {
        switch(reason){
            case NORMAL_EXIT:
                finishWithAnimation(ActivityTransitionAnimation.RIGHT);
                break;
            default:
                finishWithAnimation(ActivityTransitionAnimation.RIGHT);
                break;
        }
    }


    @Override
    public void onBackPressed(){
        closeActivity();
    }


    private MaterialDialog.ListCallback mContextMenuListener = new MaterialDialog.ListCallback() {
        @Override
        public void onSelection(MaterialDialog materialDialog, View view, int selection, CharSequence charSequence) {
            switch(selection){
                case ModelEditorContextMenu.FIELD_REPOSITION:
                    repositionField();
                    break;
                case ModelEditorContextMenu.FIELD_DELETE:
                    deleteField();
                    break;
                case ModelEditorContextMenu.FIELD_RENAME:
                    renameField();
                    break;
            }
        }
    };
}