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
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ichi2.libanki.Deck;
import com.ichi2.libanki.Fact;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Utils;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.json.JSONException;
import org.json.JSONObject;

import org.amr.arabic.ArabicUtilities;

/**
 * Allows the user to edit a fact, for instance if there is a typo.
 *
 * A card is a presentation of a fact, and has two sides: a question and an answer.
 * Any number of fields can appear on each side. When you add a fact to Anki, cards
 * which show that fact are generated. Some models generate one card, others generate
 * more than one.
 *
 * @see http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Cards
 */
public class CardEditor extends Activity {

	public static final String CARD_EDITOR_ACTION = "cea";
	public static final int EDIT_REVIEWER_CARD = 0;
	public static final int EDIT_BROWSER_CARD = 1;
	public static final int ADD_CARD = 2;

    private static final int DIALOG_MODEL_SELECT = 0;

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private LinearLayout mFieldsLayoutContainer;
    private Button mSave;
    private Button mCancel;
    private Button mTags;
    private LinearLayout mModelButtons;
    private Button mModelButton;
    private Button mTemplateButton;

    private Fact mEditorFact;
    private boolean mAddFact = false;
    private Deck mDeck;

    private LinkedList<FieldEditText> mEditFields;

    private boolean mModified;

    private LinkedHashMap<Integer, Model> mModels;
    private Model mCurrentModel;
    private TreeMap<Integer, JSONObject> mTemplates;
    private TreeMap<Integer, JSONObject> mSelectedTemplates = new TreeMap<Integer, JSONObject>();

    private String[] mFields;
    private String[] allTags;
    private HashSet<String> mSelectedTags = new HashSet<String>();
    private String mFactTags;
    private EditText mNewTagEditText;
    private AlertDialog mTagsDialog;
    private AlertDialog mAddNewTag;
    
    private boolean mPrefFixArabic;

    private ProgressDialog mProgressDialog;

    private DeckTask.TaskListener mSaveFactHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
        	Resources res = getResources();
        	mProgressDialog = ProgressDialog.show(CardEditor.this, "", res.getString(R.string.saving_facts), true);
        }

        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            if (values[0].getBoolean()) {
                setResult(RESULT_OK);
//        		mNewFact = mDeck.newFact(mCurrentSelectedModelId);
//                toast with kkk
//        		populateEditFields();
            } else {
                Toast failureNotice = Toast.makeText(CardEditor.this, getResources().getString(R.string.factadder_saving_error), Toast.LENGTH_SHORT);
                failureNotice.show();
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                	mProgressDialog.dismiss();
                }
            }
        }

        @Override
        public void onPostExecute(DeckTask.TaskData result) {
        }
    };

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registerExternalStorageListener();

        setContentView(R.layout.card_editor);

        mFieldsLayoutContainer = (LinearLayout) findViewById(R.id.CardEditorEditFieldsLayout);

        mSave = (Button) findViewById(R.id.CardEditorSaveButton);
        mCancel = (Button) findViewById(R.id.CardEditorCancelButton);
        mTags = (Button) findViewById(R.id.CardEditorTagButton);
        mTags = (Button) findViewById(R.id.CardEditorTagButton);
        mModelButtons = (LinearLayout) findViewById(R.id.CardEditorSelectModelLayout);
        mModelButton = (Button) findViewById(R.id.CardEditorModelButton);
        mTemplateButton = (Button) findViewById(R.id.CardEditorTemplateButton);

        mDeck = AnkiDroidApp.deck();
        mModels = mDeck.models();
        mCurrentModel = mDeck.currentModel();
//        mNewSelectedCardModels = new LinkedHashMap<Long, CardModel>();
//        cardModelIds = new ArrayList<Long>();

        switch(getIntent().getIntExtra(CARD_EDITOR_ACTION, ADD_CARD)) {
        case EDIT_REVIEWER_CARD:
        	mEditorFact = Reviewer.getEditorCard().getFact();
        	break;
        case EDIT_BROWSER_CARD:
        	mEditorFact = CardBrowser.getEditorCard().getFact();
        	break;
        case ADD_CARD:
        	mAddFact = true;
        	mEditorFact = new Fact(mDeck, mCurrentModel);
        	mModelButtons.setVisibility(View.VISIBLE);
        	Resources res = getResources();
        	mSave.setText(res.getString(R.string.add));
        	mCancel.setText(res.getString(R.string.close));
            mModelButton.setOnClickListener(new View.OnClickListener() {

                public void onClick(View v) {
                    showDialog(DIALOG_MODEL_SELECT);
                }

            });
            mTemplateButton.setOnClickListener(new View.OnClickListener() {

                public void onClick(View v) {
                	templatesDialog().show();
                }

            });
        	break;
        }

        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        mPrefFixArabic = preferences.getBoolean("fixArabicText", false);
        // if Arabic reshaping is enabled, disable the Save button to avoid saving the reshaped string to the deck
        mSave.setEnabled(!mPrefFixArabic);

        initializeFactVariables();
        mTags.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                recreateTagsDialog();
                if (!mTagsDialog.isShowing()) {
                    mTagsDialog.show();                    
                }
            }

        });

        mSave.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
            	for (FieldEditText f : mEditFields) {
                    mModified |= f.updateField();            		
            	}
                if (!mEditorFact.stringTags().equals(mFactTags)) {
                    mEditorFact.setTags(mFactTags);
                    mModified = true;
                }
                // Only send result to save if something was actually changed
                if (mModified) {
                	if (mAddFact) {
                		AnkiDroidApp.deck().addFact(mEditorFact);
                	} else {
                    	mEditorFact.flush(); 
                        setResult(RESULT_OK);
                        finish();
                	}
                } else {
                	setResult(RESULT_CANCELED);
                }
            }
        });

        mCancel.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                closeCardEditor();
            }
        });
        initDialogs();
        modelChanged();
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Log.i(AnkiDroidApp.TAG, "CardEditor - onBackPressed()");
            closeCardEditor();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
    }


    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;

        switch (id) {
            case DIALOG_MODEL_SELECT:
                ArrayList<CharSequence> dialogItems = new ArrayList<CharSequence>();
                // Use this array to know which ID is associated with each Item(name)
                final ArrayList<Integer> dialogIds = new ArrayList<Integer>();

                Model mModel;

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.select_model);
                for (int i : mModels.keySet()) {
                    mModel = mModels.get(i);
                    dialogItems.add(mModel.getName());
                    dialogIds.add(i);
                }
                // Convert to Array
                CharSequence[] items = new CharSequence[dialogItems.size()];
                dialogItems.toArray(items);

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        int oldModelId = mCurrentModel.getId();
                        mCurrentModel = mDeck.getModel(dialogIds.get(item));
                        if (oldModelId != dialogIds.get(item)) {
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

    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------

    private void initializeFactVariables() {
        mFields = mEditorFact.getFields();
        mEditFields = new LinkedList<FieldEditText>();
        mModified = false;
        mFactTags = mEditorFact.stringTags();
        mTags.setText(getResources().getString(R.string.CardEditorTags, mFactTags));    	
    }

    
    private void modelChanged() {
    	if (mAddFact) {
    		mEditorFact = new Fact(mDeck, mCurrentModel);
    		initializeFactVariables();
    		mTemplates = mCurrentModel.getTemplates();
    		mSelectedTemplates.clear();
    		for (Entry<Integer, JSONObject> entry : mTemplates.entrySet()) {
        		try {
        			if (entry.getValue().getString("actv").toLowerCase().equals("true")) {
            			mSelectedTemplates.put(entry.getKey(), entry.getValue());    				
        			}
    			} catch (JSONException e) {
    				throw new RuntimeException(e);
    			}
            }
    		mModelButton.setText(getResources().getString(R.string.model) + " " + mModels.get(mCurrentModel.getId()).getName());
    		cardModelsChanged();    		
    	}
		populateEditFields();
    }


    private void cardModelsChanged() {
		String cardModelNames = "";
		for (Entry<Integer, JSONObject> entry : mSelectedTemplates.entrySet()) {
    		try {
				cardModelNames = cardModelNames + entry.getValue().getString("name") + ", ";
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
        }
		if (cardModelNames.length() > 2) {
	    	cardModelNames = cardModelNames.substring(0, cardModelNames.length() - 2);			
		}
        if (mSelectedTemplates.size() == 1){
        	mTemplateButton.setText(getResources().getString(R.string.card) + " " + cardModelNames);        	
        } else {
        	mTemplateButton.setText(getResources().getString(R.string.cards) + " " + cardModelNames);
        }
    }


    private void populateEditFields() {
        // Generate a new EditText for each field
    	mFieldsLayoutContainer.removeAllViews();
        for (int i = 0; i < mFields.length; i++) {
            FieldEditText newTextbox = new FieldEditText(this, mEditorFact, i);
            TextView label = newTextbox.getLabel();
            mEditFields.add(newTextbox);

            mFieldsLayoutContainer.addView(label);
            mFieldsLayoutContainer.addView(newTextbox);
        }
    }


    private Dialog templatesDialog() {
    	Resources res = getResources();
    	mTemplates = mCurrentModel.getTemplates();
    	
        int length = mTemplates.size();
        boolean[] checked = new boolean[length];
        String[] templates = new String[length];
        try {
        	for (int i = 0; i < length; i++) {
            	JSONObject template = mTemplates.get(i);
                templates[i] = template.getString("name");
				if (mSelectedTemplates.containsKey(i)) {
				    checked[i] = true;
				}
            }
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.select_card_model);
        builder.setMultiChoiceItems(templates, checked,
                new DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton, boolean isChecked) {
                        if (!isChecked) {
                            mSelectedTemplates.remove(whichButton);
                        } else {
                            mSelectedTemplates.put(whichButton, mTemplates.get(whichButton));
                        }
                    }
                });
        builder.setPositiveButton(res.getString(R.string.select), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            	cardModelsChanged();
            }
        });
        builder.setNegativeButton(res.getString(R.string.cancel), null);
        return builder.create();
    }


    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications.
     */
    public void registerExternalStorageListener() {
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


    private void finishNoStorageAvailable() {
        setResult(StudyOptions.CONTENT_NO_EXTERNAL_STORAGE);
        finish();
    }


    private void closeCardEditor() {
        finish();
        if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
            MyAnimation.slide(CardEditor.this, MyAnimation.RIGHT);
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


    private void recreateTagsDialog() {
        Resources res = getResources();
        if (allTags == null) {
            String[] oldTags = AnkiDroidApp.deck().tagList();
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
    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------

    private class FieldEditText extends EditText {

        private Fact mFact;
        private int mFieldOrd;


        public FieldEditText(Context context, Fact fact, int fieldOrd) {
            super(context);
            mFact = fact;
            mFieldOrd = fieldOrd;
            String content = mFact.getFields()[fieldOrd];
            if (content == null) {
            	content = "";
            }
            if(mPrefFixArabic) {
				this.setText(ArabicUtilities.reshapeSentence(content));
            } else {
				this.setText(content);
            }
        }


        public TextView getLabel() {
            TextView label = new TextView(this.getContext());
            label.setText(mFact.fieldName(mFieldOrd));
            return label;
        }


        public boolean updateField() {
            String newValue = this.getText().toString();
            if (!mFact.getFields()[mFieldOrd].equals(newValue)) {
            	mFact.getFields()[mFieldOrd] = newValue;
                return true;
            }
            return false;
        }
    }

}
