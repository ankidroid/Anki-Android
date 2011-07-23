/***************************************************************************************
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.Fact.Field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Allows the user to add a fact. A card is a presentation of a fact, and has two sides: a question and an answer. Any
 * number of fields can appear on each side. When you add a fact to Anki, cards which show that fact are generated. Some
 * models generate one card, others generate more than one.
 * 
 * @see http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Cards
 */
public class FactAdder extends Activity {

    private static final int DIALOG_MODEL_SELECT = 0;

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private LinearLayout mFieldsLayoutContainer;
    private HashMap<Long, Model> mModels;

    private Button mAddButton;
    private Button mCloseButton;
    private Button mModelButton;
    private Button mCardModelButton;
    private ListView mCardModelListView;
    private Button mTags;

    private AlertDialog mCardModelDialog;
    
    private Deck mDeck;
    private Long mCurrentSelectedModelId;

    private LinkedList<FieldEditText> mEditFields;
    private LinkedHashMap<Long, CardModel> mCardModels;
    
    private LinkedHashMap<Long, CardModel> mSelectedCardModels;
	private LinkedHashMap<Long, CardModel> mNewSelectedCardModels;       
	private ArrayList<Long> cardModelIds = new ArrayList<Long>();

    private Fact mNewFact;

    private String[] allTags;
    private HashSet<String> mSelectedTags;
    private String mFactTags = "";
    private EditText mNewTagEditText;
    private AlertDialog mTagsDialog;
    private AlertDialog mAddNewTag;

    private ProgressDialog mProgressDialog;


    private DeckTask.TaskListener mSaveFactHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
        	Resources res = getResources();
        	mProgressDialog = ProgressDialog.show(FactAdder.this, "", res.getString(R.string.saving_facts), true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            if (values[0].getBoolean()) {
                setResult(RESULT_OK);
        		mNewFact = mDeck.newFact(mCurrentSelectedModelId);
        		populateEditFields();
            } else {
            	Themes.showThemedToast(FactAdder.this, getResources().getString(R.string.factadder_saving_error), true);
            }
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	Themes.applyTheme(this);
        super.onCreate(savedInstanceState);
        
        registerExternalStorageListener();

        View mainView = getLayoutInflater().inflate(R.layout.fact_adder, null);
        setContentView(mainView);
        Themes.setWallpaper(mainView);

        mFieldsLayoutContainer = (LinearLayout) findViewById(R.id.FactAdderEditFieldsLayout);
        Themes.setTextViewStyle(mFieldsLayoutContainer);

        mAddButton = (Button) findViewById(R.id.FactAdderAddButton);
        mCloseButton = (Button) findViewById(R.id.FactAdderCloseButton);
        mModelButton = (Button) findViewById(R.id.FactAdderModelButton);
        mCardModelButton = (Button) findViewById(R.id.FactAdderCardModelButton);
        mTags = (Button) findViewById(R.id.FactAdderTagButton);
        mTags.setText(getResources().getString(R.string.CardEditorTags, mFactTags));

        mDeck = AnkiDroidApp.deck();

        mModels = Model.getModels(mDeck);
        mCurrentSelectedModelId = mDeck.getCurrentModelId();
        mNewSelectedCardModels = new LinkedHashMap<Long, CardModel>();
        cardModelIds = new ArrayList<Long>();
        modelChanged();

        mAddButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                for (FieldEditText current : mEditFields) {
                    current.updateField();
                }
                mNewFact.setTags(mFactTags);
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ADD_FACT, mSaveFactHandler, new DeckTask.TaskData(mDeck, mNewFact, mSelectedCardModels));                
            }
        });

        mModelButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                showDialog(DIALOG_MODEL_SELECT);

            }

        });

        mCardModelButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
            	recreateCardModelDialog();
            	mCardModelDialog.show();
            }

        });

        mCloseButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                closeFactAdder();
            }

        });

        allTags = null;
        mSelectedTags = new HashSet<String>();
        mTags.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                recreateTagsDialog();
                if (!mTagsDialog.isShowing()) {
                    mTagsDialog.show();
                }
            }

        });
        initDialogs();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
    }


    private void initDialogs() {
        Resources res = getResources();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View contentView = getLayoutInflater().inflate(R.layout.edittext, null);
        //contentView.setBackgroundColor(res.getColor(R.color.background));
        mNewTagEditText =  (EditText) contentView.findViewById(R.id.edit_text);
        builder.setView(contentView);
        builder.setTitle(res.getString(R.string.add_new_tag));
        builder.setPositiveButton(res.getString(R.string.add), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String tag = mNewTagEditText.getText().toString();
                if (tag.equals("")) {
                    recreateTagsDialog();
                    mTagsDialog.show();
                } else {
                    String[] oldTags = allTags;
                    mFactTags += ", " + tag;
                    Log.i(AnkiDroidApp.TAG, "all tags: " + Arrays.toString(oldTags));            
                    allTags = new String[oldTags.length + 1];
                    allTags[0] = oldTags[0]; 
                    allTags[1] = tag;
                    for (int i = 1; i < oldTags.length; i++) {
                        allTags[i + 1] = oldTags[i];
                    }
                    recreateTagsDialog();
                    mTagsDialog.show();                    
                }
            }
        });
        builder.setNegativeButton(res.getString(R.string.cancel), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                recreateTagsDialog();
                mTagsDialog.show();                    
            }
        });
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                recreateTagsDialog();
                mTagsDialog.show();
            }
        });
        mAddNewTag = builder.create();
    }


    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;

        switch (id) {
            case DIALOG_MODEL_SELECT:
                ArrayList<CharSequence> dialogItems = new ArrayList<CharSequence>();
                // Use this array to know which ID is associated with each Item(name)
                final ArrayList<Long> dialogIds = new ArrayList<Long>();

                Model mModel;

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.select_model);
                for (Long i : mModels.keySet()) {
                    mModel = mModels.get(i);
                    dialogItems.add(mModel.getName());
                    dialogIds.add(i);
                }
                // Convert to Array
                CharSequence[] items = new CharSequence[dialogItems.size()];
                dialogItems.toArray(items);

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        long oldModelId = mCurrentSelectedModelId;
                        mCurrentSelectedModelId = dialogIds.get(item);
                        if (oldModelId != mCurrentSelectedModelId) {
                            int size = mEditFields.size();
                        	String[] oldValues = new String[size];
                        	for (int i = 0; i < size; i++) {
                                oldValues[i] = mEditFields.get(i).getText().toString();
                            }
                        	modelChanged();
                        	for (int i = 0; i < Math.min(size, mEditFields.size()) ; i++) {
                                mEditFields.get(i).setText(oldValues[i]);
                            }
                        }
                    }
                });
                AlertDialog alert = builder.create();
                return alert;
            default:
                dialog = null;
        }
        return dialog;
    }


    private void modelChanged() {
		mNewFact = mDeck.newFact(mCurrentSelectedModelId);
		mSelectedCardModels = mDeck.activeCardModels(mNewFact);

		mModelButton.setText(getResources().getString(R.string.model) + " " + mModels.get(mCurrentSelectedModelId).getName());
		cardModelsChanged();
		populateEditFields();
    }


    private void cardModelsChanged() {
		String cardModelNames = ""; 	
		for (Map.Entry<Long, CardModel> entry : mSelectedCardModels.entrySet()) {
    		cardModelNames = cardModelNames + entry.getValue().getName() + ", ";
        }
    	cardModelNames = cardModelNames.substring(0, cardModelNames.length() - 2);

        if (mSelectedCardModels.size() == 1){
        	mCardModelButton.setText(getResources().getString(R.string.card) + " " + cardModelNames);        	
        } else {
        	mCardModelButton.setText(getResources().getString(R.string.cards) + " " + cardModelNames);
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Log.i(AnkiDroidApp.TAG, "CardBrowser - onBackPressed()");
            closeFactAdder();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }


    private void populateEditFields() {
        mFieldsLayoutContainer.removeAllViews();
        mEditFields = new LinkedList<FieldEditText>();
        TreeSet<Field> fields = mNewFact.getFields();
        for (Field f : fields) {
            FieldEditText newTextbox = new FieldEditText(this, f);
            TextView label = newTextbox.getLabel();
            mEditFields.add(newTextbox);

            mFieldsLayoutContainer.addView(label);
            mFieldsLayoutContainer.addView(newTextbox);
            // Generate a new EditText for each field
        }
    }

    private void recreateCardModelDialog() {
    	Resources res = getResources();
    	mCardModels = mDeck.cardModels(mNewFact);
    	int size = mCardModels.size();
    	String dialogItems[] = new String [size];
        cardModelIds.clear();       
    	int i = 0;
        for (Long id : mCardModels.keySet()) {
        	dialogItems[i] = mCardModels.get(id).getName();
        	cardModelIds.add(id);
            i++;
        }
        View contentView = getLayoutInflater().inflate(R.layout.fact_adder_card_model_list, null);
        mCardModelListView = (ListView) contentView.findViewById(R.id.card_model_list);
        mCardModelListView.setAdapter(new ArrayAdapter<String>(this, R.layout.dialog_check_item, dialogItems));
        for (int j = 0; j < size; j++) {;
        	mCardModelListView.setItemChecked(j, mSelectedCardModels.containsKey(cardModelIds.get(j)));
        }
        mNewSelectedCardModels.clear();
        mNewSelectedCardModels.putAll(mSelectedCardModels);
        mCardModelListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            	long m = cardModelIds.get(position);
            	if (((CheckedTextView)view).isChecked()) {
            		mNewSelectedCardModels.remove(m);
                } else {
                	mNewSelectedCardModels.put(m, mCardModels.get(m));
                }
           		mCardModelDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!mNewSelectedCardModels.isEmpty());
            }
        });
        mCardModelListView.setItemsCanFocus(false);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(res.getString(R.string.select_card_model));
        builder.setPositiveButton(res.getString(R.string.select), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) { 
            	mSelectedCardModels.clear();
            	mSelectedCardModels.putAll(mNewSelectedCardModels);
            	cardModelsChanged();
            }
        });
        builder.setNegativeButton(res.getString(R.string.cancel), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        builder.setView(contentView);            	
        mCardModelDialog = builder.create();
    }


    private void recreateTagsDialog() {
        Resources res = getResources();
        if (allTags == null) {
            String[] oldTags = AnkiDroidApp.deck().allUserTags();
            Log.i(AnkiDroidApp.TAG, "all tags: " + Arrays.toString(oldTags));            
            allTags = new String[oldTags.length + 1];
            allTags[0] = res.getString(R.string.add_new_tag);
            for (int i = 0; i < oldTags.length; i++) {
                allTags[i + 1] = oldTags[i];
            }
        }
        mSelectedTags.clear();
        List<String> selectedList = Arrays.asList(Utils.parseTags(mFactTags));
        int length = allTags.length;
        boolean[] checked = new boolean[length];
        for (int i = 0; i < length; i++) {
            String tag = allTags[i];
            if (selectedList.contains(tag)) {
                checked[i] = true;
                mSelectedTags.add(tag);
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.studyoptions_limit_select_tags);
        builder.setMultiChoiceItems(allTags, checked,
                new DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton, boolean isChecked) {
                        if (whichButton == 0) {
                            dialog.dismiss();
                            mNewTagEditText.setText("");
                            mAddNewTag.show();
                        } else {
                            String tag = allTags[whichButton];
                            if (!isChecked) {
                                Log.i(AnkiDroidApp.TAG, "unchecked tag: " + tag);
                                mSelectedTags.remove(tag);
                            } else {
                                Log.i(AnkiDroidApp.TAG, "checked tag: " + tag);
                                mSelectedTags.add(tag);
                            }                            
                        }
                    }
                });
        builder.setPositiveButton(res.getString(R.string.select), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String tags = mSelectedTags.toString();
                mFactTags = tags.substring(1, tags.length() - 1);
                mTags.setText(getResources().getString(R.string.CardEditorTags, mFactTags));
            }
        });
        builder.setNegativeButton(res.getString(R.string.cancel), null);
        mTagsDialog = builder.create();
    }

    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications.
     */
    private void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        finishNoStorageAvailable();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }


    private void closeFactAdder() {
    	finish();
        if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
            ActivityTransitionAnimation.slide(FactAdder.this, ActivityTransitionAnimation.RIGHT);
        }
    }


    private void finishNoStorageAvailable() {
        setResult(StudyOptions.CONTENT_NO_EXTERNAL_STORAGE);
        closeFactAdder();
    }

    // TODO: remove redundance with CardEditor.java::FieldEditText
    private class FieldEditText extends EditText {

        private Field mPairField;


        public FieldEditText(Context context, Field pairField) {
            super(context);
            mPairField = pairField;
            this.setMinimumWidth(400);
        }


        public TextView getLabel() {
            TextView label = new TextView(this.getContext());
            label.setText(mPairField.getFieldModel().getName());
            return label;
        }


        public void updateField() {
            mPairField.setValue(this.getText().toString());
        }
    }

}
