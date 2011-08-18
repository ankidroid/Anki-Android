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
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.CardEditor.FieldEditText;
import com.ichi2.anki.DeckPicker.AnkiFilter;
import com.ichi2.anki.Fact.Field;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.Themes;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.io.File;
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
    private static final int DIALOG_CARD_MODEL_SELECT = 1;
    private static final int DIALOG_TAGS = 2;

    private static final String INTENT_CREATE_FLASHCARD = "org.openintents.indiclash.CREATE_FLASHCARD";
    private static final String INTENT_CREATE_FLASHCARD_SEND = "android.intent.action.SEND";

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private LinearLayout mFieldsLayoutContainer;
    private HashMap<Long, Model> mModels;

    private Button mAddButton;
    private Button mCloseButton;
    private Button mModelButton;
    private Button mSwapButton;
    private Button mCardModelButton;
    private ListView mCardModelListView;
    private Button mTags;

    private StyledDialog mCardModelDialog;
    private StyledDialog mDeckSelectDialog;
    
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
    private StyledDialog mTagsDialog;

    private ProgressDialog mProgressDialog;

    private HashMap<String, String> mFullDeckPaths;
    private String[] mDeckNames;
    private String mSourceLanguage;
    private String mTargetLanguage;
    private String mSourceText;
    private String mTargetText;
    private int mSourcePosition = 0;
    private int mTargetPosition = 1;
    private boolean mCancelled = false;

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
        		mSourceText = null;
        		mTargetText = null;
        		mSwapButton.setVisibility(View.GONE);
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

        setTitle(R.string.factadder_title);
        mFieldsLayoutContainer = (LinearLayout) findViewById(R.id.FactAdderEditFieldsLayout);
        Themes.setTextViewStyle(mFieldsLayoutContainer);

        mAddButton = (Button) findViewById(R.id.FactAdderAddButton);
        mCloseButton = (Button) findViewById(R.id.FactAdderCloseButton);
    	mSwapButton  = (Button) findViewById(R.id.FactAdderSwapButton);
        mModelButton = (Button) findViewById(R.id.FactAdderModelButton);
        mCardModelButton = (Button) findViewById(R.id.FactAdderCardModelButton);
        mTags = (Button) findViewById(R.id.FactAdderTagButton);
        mTags.setText(getResources().getString(R.string.CardEditorTags, mFactTags));

        mNewSelectedCardModels = new LinkedHashMap<Long, CardModel>();
        cardModelIds = new ArrayList<Long>();

        Intent intent = getIntent();
        String action = intent.getAction();
        if (action != null && action.equals(INTENT_CREATE_FLASHCARD)) {
        	prepareForIntentAddition();
            Bundle extras = intent.getExtras();
            mSourceLanguage = extras.getString("SOURCE_LANGUAGE");
            mTargetLanguage = extras.getString("TARGET_LANGUAGE");
            mSourceText = extras.getString("SOURCE_TEXT");
            mTargetText = extras.getString("TARGET_TEXT");
        } else if (action != null && action.equals(INTENT_CREATE_FLASHCARD_SEND)) {
        	prepareForIntentAddition();
            Bundle extras = intent.getExtras();
            mSourceText = extras.getString(Intent.EXTRA_SUBJECT);
            mTargetText = extras.getString(Intent.EXTRA_TEXT);
        } else {
            mDeck = AnkiDroidApp.deck();
        	loadContents();
        }

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
		showDialog(DIALOG_CARD_MODEL_SELECT);
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
                showDialog(DIALOG_TAGS);
                if (!mTagsDialog.isShowing()) {
                    mTagsDialog.show();
                }
            }

        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
    }


    private void loadContents() {
        mModels = Model.getModels(mDeck);
        mCurrentSelectedModelId = mDeck.getCurrentModelId();
        modelChanged();
    }


    private void prepareForIntentAddition() {
    	mSwapButton.setVisibility(View.VISIBLE);
    	mSwapButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
            	swapText(false);
            }
        });
    	initDeckSelectDialog();
    	mDeckSelectDialog.show();
    }


    private void initDeckSelectDialog() {
		int len = 0;
		File[] fileList;

		File dir = new File(PrefSettings.getSharedPrefs(getBaseContext()).getString("deckPath", AnkiDroidApp.getStorageDirectory()));
		fileList = dir.listFiles(new AnkiFilter());

		if (dir.exists() && dir.isDirectory() && fileList != null) {
			len = fileList.length;
		}

		TreeSet<String> tree = new TreeSet<String>();
		mFullDeckPaths = new HashMap<String, String>();

		if (len > 0 && fileList != null) {
			Log.i(AnkiDroidApp.TAG, "FactAdder - populateDeckDialog, number of anki files = " + len);
			for (File file : fileList) {
				String name = file.getName().replaceAll(".anki", "");
				tree.add(name);
				mFullDeckPaths.put(name, file.getAbsolutePath());
			}
		}            

		StyledDialog.Builder builder = new StyledDialog.Builder(this);
        builder.setTitle(R.string.fact_adder_select_deck);
        // Convert to Array
        mDeckNames = new String[tree.size()];
        tree.toArray(mDeckNames);

        builder.setItems(mDeckNames, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
            	loadDeck(item);
            }
        });
        mDeckSelectDialog = builder.create();
        mDeckSelectDialog.setOnCancelListener(new OnCancelListener() {

			@Override
			public void onCancel(DialogInterface arg0) {
		        mCancelled = true;
	        }
        	
        });
        mDeckSelectDialog.setOnDismissListener(new OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface arg0) {
				if (mCancelled == true) {
					finish();
				} else if (mDeck == null) {
					mDeckSelectDialog.show();
				}
			}
        });
    }


    @Override
    protected Dialog onCreateDialog(int id) {
		StyledDialog dialog = null;
		Resources res = getResources();
		StyledDialog.Builder builder = new StyledDialog.Builder(this);


        switch (id) {
        case DIALOG_MODEL_SELECT:
            ArrayList<CharSequence> dialogItems = new ArrayList<CharSequence>();
            // Use this array to know which ID is associated with each Item(name)
            final ArrayList<Long> dialogIds = new ArrayList<Long>();

            Model mModel;
            builder.setTitle(R.string.select_model);
            for (Long i : mModels.keySet()) {
                mModel = mModels.get(i);
                dialogItems.add(mModel.getName());
                dialogIds.add(i);
            }
            // Convert to Array
            String[] items = new String[dialogItems.size()];
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
                    	if ((mSourceText == null || mSourceText.isEmpty()) && (mTargetText == null || mTargetText.isEmpty())) {
                        	for (int i = 0; i < Math.min(size, mEditFields.size()) ; i++) {
                                mEditFields.get(i).setText(oldValues[i]);
                            }                    		
                    	}
                    }
                }
            });
            dialog = builder.create();
            break;

        case DIALOG_TAGS:
	        builder.setTitle(R.string.studyoptions_limit_select_tags);
	        builder.setPositiveButton(res.getString(R.string.select), new OnClickListener() {
	            @Override
	            public void onClick(DialogInterface dialog, int which) {
	                String tags = mSelectedTags.toString();
	                mFactTags = tags.substring(1, tags.length() - 1);
	                mTags.setText(getResources().getString(R.string.CardEditorTags, mFactTags));
	            }
	        });
        	builder.setNegativeButton(res.getString(R.string.cancel), null);

        	mNewTagEditText =  (EditText) new EditText(this);
        	mNewTagEditText.setHint(R.string.add_new_tag);

        	InputFilter filter = new InputFilter() { 
        	    public CharSequence filter(CharSequence source, int start, int end, 
        	        Spanned dest, int dstart, int dend) {
        	        for (int i = start; i < end; i++) { 
        	            if (!(Character.isLetterOrDigit(source.charAt(i)))) { 
        	                return "";
        	            }
        	        }
        	        return null; 
        	    } 
        	}; 
        	mNewTagEditText.setFilters(new InputFilter[]{filter});

        	ImageView mAddTextButton = new ImageView(this);
        	mAddTextButton.setImageResource(R.drawable.ic_addtag);
        	mAddTextButton.setOnClickListener(new View.OnClickListener() {
        		@Override
        		public void onClick(View v) {
    				String tag = mNewTagEditText.getText().toString();
    				if (tag.length() != 0) {
    					for (int i = 0; i < allTags.length; i++) {
    						if (allTags[i].equalsIgnoreCase(tag)) {
    							mNewTagEditText.setText("");
    							return;
    						}
    					}
                        mSelectedTags.add(tag);
                        String[] oldTags = allTags;
        	            allTags = new String[oldTags.length + 1];
        	            allTags[0] = tag;
        	            for (int j = 1; j < allTags.length; j++) {
        	                allTags[j] = oldTags[j - 1];
        	            }
        				mTagsDialog.addMultiChoiceItems(tag, true);
						mNewTagEditText.setText("");    					
        			}
        		}
        	});

            FrameLayout frame = new FrameLayout(this);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            params.rightMargin = 10;
            mAddTextButton.setLayoutParams(params);
            frame.addView(mNewTagEditText);
            frame.addView(mAddTextButton);

	        builder.setView(frame, true);
	        dialog = builder.create();
		mTagsDialog = dialog;
			break;

        case DIALOG_CARD_MODEL_SELECT:
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
        mCardModelDialog = builder.create();
        dialog = mCardModelDialog;
	break;

		}
		return dialog;
	}


    @Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		StyledDialog ad = (StyledDialog)dialog;
		switch (id) {
		case DIALOG_TAGS:
	        if (allTags == null) {
	            String[] oldTags = AnkiDroidApp.deck().allUserTags();
	            Log.i(AnkiDroidApp.TAG, "all tags: " + Arrays.toString(oldTags));            
	            allTags = new String[oldTags.length];
	            for (int i = 0; i < oldTags.length; i++) {
	                allTags[i] = oldTags[i];
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
			ad.setMultiChoiceItems(allTags, checked,
	                new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface arg0, int which) {
	                        String tag = allTags[which];
	                        if (mSelectedTags.contains(tag)) {
	                            Log.i(AnkiDroidApp.TAG, "unchecked tag: " + tag);
	                            mSelectedTags.remove(tag);
	                        } else {
	                            Log.i(AnkiDroidApp.TAG, "checked tag: " + tag);
	                            mSelectedTags.add(tag);
	                        }
						}
	                });
			break;

		case DIALOG_CARD_MODEL_SELECT:
    	mCardModels = mDeck.cardModels(mNewFact);
    	int size = mCardModels.size();
    	String dialogItems[] = new String [size];
        cardModelIds.clear();       
    	int i = 0;
        for (Long id2 : mCardModels.keySet()) {
        	dialogItems[i] = mCardModels.get(id2).getName();
        	cardModelIds.add(id2);
            i++;
        }
    	boolean[] checkedItems = new boolean[size];
        for (int j = 0; j < size; j++) {;
		checkedItems[j] = mSelectedCardModels.containsKey(cardModelIds.get(j));
        }
        mNewSelectedCardModels.clear();
        mNewSelectedCardModels.putAll(mSelectedCardModels);
		ad.setMultiChoiceItems(dialogItems, checkedItems,
                new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface arg0, int which) {
						long m = cardModelIds.get(which);
						if (mNewSelectedCardModels.containsKey(m)) {
							mNewSelectedCardModels.remove(m);
						} else {
							mNewSelectedCardModels.put(m, mCardModels.get(m));
						}
						mCardModelDialog.getButton(StyledDialog.BUTTON_POSITIVE).setEnabled(!mNewSelectedCardModels.isEmpty());
					}
                });
		ad.getButton(StyledDialog.BUTTON_POSITIVE).setEnabled(!mNewSelectedCardModels.isEmpty());
		break;
		}
	}


    private void modelChanged() {
		mNewFact = mDeck.newFact(mCurrentSelectedModelId);
		mSelectedCardModels = mDeck.activeCardModels(mNewFact);

		mModelButton.setText(getResources().getString(R.string.model) + " " + mModels.get(mCurrentSelectedModelId).getName());
		cardModelsChanged();
		populateEditFields();
		swapText(true);
    }


    private void loadDeck(int item) {
		mDeck = Deck.openDeck(mFullDeckPaths.get(mDeckNames[item]), false);
		if (mDeck == null) {
			Themes.showThemedToast(FactAdder.this, getResources().getString(R.string.fact_adder_deck_not_loaded), true);
		} else {
			setTitle(mDeckNames[item]);
			loadContents();
		}
    }


    private void swapText(boolean reset) {
		if (mEditFields.size() > mSourcePosition) {
	    	mEditFields.get(mSourcePosition).setText("");			
		}
		if (mEditFields.size() > mTargetPosition) {
	    	mEditFields.get(mTargetPosition).setText("");			
		}
		if (reset) {
			mSourcePosition = 0;
			mTargetPosition = 1;
		} else {
			mTargetPosition++;
			while (mTargetPosition == mSourcePosition || mTargetPosition >= mEditFields.size()) {
				mTargetPosition++;
				if (mTargetPosition >= mEditFields.size()) {
					mTargetPosition = 0;
					mSourcePosition++;
				}
				if (mSourcePosition >= mEditFields.size()) {
					mSourcePosition = 0;
				}
	    	}			
		}
		if (mSourceText != null) {
			mEditFields.get(mSourcePosition).setText(mSourceText);			
		}
		if (mSourceText != null) {
			mEditFields.get(mTargetPosition).setText(mTargetText);			
		}
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
            FieldEditText newTextbox = (new CardEditor()).new FieldEditText(this, f);
            TextView label = newTextbox.getLabel();
            ImageView circle = newTextbox.getCircle();
            mEditFields.add(newTextbox);
            FrameLayout frame = new FrameLayout(this);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            params.rightMargin = 10;
            circle.setLayoutParams(params);
            frame.addView(newTextbox);
            frame.addView(circle);
            mFieldsLayoutContainer.addView(label);
            mFieldsLayoutContainer.addView(frame);
        }
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
}

