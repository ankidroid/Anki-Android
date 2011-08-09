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
import android.text.Editable;
import android.text.InputType;
import android.text.method.KeyListener;
import android.text.style.StrikethroughSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anim.ViewAnimation;
import com.ichi2.anki.Fact.Field;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

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

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private LinearLayout mFieldsLayoutContainer;
    private Button mSave;
    private Button mCancel;
    private Button mTags;

    private Card mEditorCard;
    private Fact mEditorFact;

    private LinkedList<FieldEditText> mEditFields;

    private boolean mModified;

    private String[] allTags;
    private HashSet<String> mSelectedTags;
    private String mFactTags;
    private EditText mNewTagEditText;
    private AlertDialog mTagsDialog;
    private AlertDialog mAddNewTag;
    
    private boolean mPrefFixArabic;

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	Themes.applyTheme(this);
        super.onCreate(savedInstanceState);

        registerExternalStorageListener();

        View mainView = getLayoutInflater().inflate(R.layout.card_editor, null);
        setContentView(mainView);
        Themes.setWallpaper(mainView);

        mFieldsLayoutContainer = (LinearLayout) findViewById(R.id.CardEditorEditFieldsLayout);
        Themes.setTextViewStyle(mFieldsLayoutContainer);

        mSave = (Button) findViewById(R.id.CardEditorSaveButton);
        mCancel = (Button) findViewById(R.id.CardEditorCancelButton);
        mTags = (Button) findViewById(R.id.CardEditorTagButton);

        if (getIntent().getBooleanExtra("callfromcardbrowser", false)) {
            mEditorCard = CardBrowser.getEditorCard();
        } else {
            mEditorCard = Reviewer.getEditorCard();
        }

        // Card -> FactID -> FieldIDs -> FieldModels

        mEditorFact = mEditorCard.getFact();
        TreeSet<Field> fields = mEditorFact.getFields();

        mEditFields = new LinkedList<FieldEditText>();

        mModified = false;

        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        mPrefFixArabic = preferences.getBoolean("fixArabicText", false);
        // if Arabic reshaping is enabled, disable the Save button to avoid saving the reshaped string to the deck
        mSave.setEnabled(!mPrefFixArabic);

        // Generate a new EditText for each field
        Iterator<Field> iter = fields.iterator();
        while (iter.hasNext()) {
            FieldEditText newTextbox = new FieldEditText(this, iter.next());
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

        mFactTags = mEditorFact.getTags();
        mTags.setText(getResources().getString(R.string.CardEditorTags, mFactTags));
        mTags.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                recreateTagsDialog();
                if (!mTagsDialog.isShowing()) {
                    mTagsDialog.show();                    
                }
            }

        });
        allTags = null;
        mSelectedTags = new HashSet<String>();

        mSave.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
            	Iterator<FieldEditText> iter = mEditFields.iterator();
                while (iter.hasNext()) {
                    FieldEditText current = iter.next();
                    mModified |= current.updateField();
                }
                if (!mEditorFact.getTags().equals(mFactTags)) {
                    mEditorFact.setTags(mFactTags);
                    mModified = true;
                }
                // Only send result to save if something was actually changed
                if (mModified) {
                    setResult(RESULT_OK);
                } else {
                    setResult(RESULT_CANCELED);
                }
                closeCardEditor();
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


    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------

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
            ActivityTransitionAnimation.slide(CardEditor.this, ActivityTransitionAnimation.RIGHT);
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
    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------

    private class FieldEditText extends EditText {

        private Field mPairField;
        private String mCutString[];
        private boolean mCutMode = false;
        private boolean[] mEnabled;
        private ImageView mCircle;
        private KeyListener mKeyListener;

        public FieldEditText(Context context, Field pairField) {
            super(context);
            mPairField = pairField;
            if(mPrefFixArabic) {
            	this.setText(ArabicUtilities.reshapeSentence(pairField.getValue()));
            } else {
            	this.setText(pairField.getValue());
            }       	
            this.setMinimumWidth(400);
            this.setOnClickListener(new View.OnClickListener() {

            	@Override
            	public void onClick(View v) {
            		if (mCutMode) {
                		setSpannables();
            		}
            	}
            });
        }


        @Override
        public void onTextChanged(CharSequence text, int start, int before, int after) {
      		super.onTextChanged(text, start, before, after);
      		if (mCircle != null) {
      			int visibility = mCircle.getVisibility();
          		if (text.length() == 0) {
          			if (visibility == View.VISIBLE) {
              			mCircle.setVisibility(View.GONE);
              			mCircle.setAnimation(ViewAnimation.fade(ViewAnimation.FADE_OUT, 300, 0));          				
          			}
          		} else if (visibility == View.GONE) {
          			mCircle.setVisibility(View.VISIBLE);
          			mCircle.setAnimation(ViewAnimation.fade(ViewAnimation.FADE_IN, 300, 0));      			
          		}      			
      		}
      		if (before != after) {
          		mCutString = text.toString().split("[\\n\\s]");
          		mEnabled = new boolean[mCutString.length];
          		mCutMode = false;
      		}
        }


        public TextView getLabel() {
            TextView label = new TextView(this.getContext());
            label.setText(mPairField.getFieldModel().getName());
            return label;
        }


        public ImageView getCircle() {          
        	mCircle = new ImageView(this.getContext());
        	mCircle.setImageResource(R.drawable.ic_circle_normal);
        	mCircle.setOnClickListener(new View.OnClickListener() {
        		@Override
        		public void onClick(View v) {
        			ImageView view = ((ImageView)v);
        			Editable editText = FieldEditText.this.getText();
        			if (mCutMode) {
            			view.setImageResource(R.drawable.ic_circle_normal);
            			FieldEditText.this.setKeyListener(mKeyListener);
            			FieldEditText.this.setCursorVisible(true);
            			for (boolean enabled : mEnabled) {
            				if (enabled) {
            					removeDeleted();
            					break;
            				}
            			}
            			StrikethroughSpan[] ss = editText.getSpans(0, editText.length(), StrikethroughSpan.class);
            			for (StrikethroughSpan s : ss) {
            				editText.removeSpan(s);
            			}
            			mCutMode = false;
        			} else {
            			view.setImageResource(R.drawable.ic_circle_pressed);
            			InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            			imm.hideSoftInputFromWindow(FieldEditText.this.getWindowToken(), 0);
            			mKeyListener = FieldEditText.this.getKeyListener();
            			FieldEditText.this.setKeyListener(null);
            			FieldEditText.this.setCursorVisible(false);
            			mCutMode = true;
                  		mCutString = editText.toString().split(" ");
                  		mEnabled = new boolean[mCutString.length];
            			int pos = 0;
            			for (int i = 0; i < mCutString.length; i++) {
            				editText.setSpan(new StrikethroughSpan(), pos, pos + mCutString[i].length(), 0);
            				pos += mCutString[i].length() + 1;
            			}
        			}
    			}
            });
        	mCircle.setVisibility(View.VISIBLE);
            return mCircle;
        }


        public boolean updateField() {
            String newValue = this.getText().toString();
            if (!mPairField.getValue().equals(newValue)) {
                mPairField.setValue(newValue);
                return true;
            }
            return false;
        }


        public void setSpannables() {
			int cursorPosition = this.getSelectionStart();
			String text = this.getText().toString();
			int beginWord = text.lastIndexOf(" ", cursorPosition - 1) + 1;
			if (beginWord == -1) {
				beginWord = 0;
			}
			int endWord = text.indexOf(" ", cursorPosition);
			if (endWord == -1) {
				endWord = text.length();
			}
			Editable editText = this.getText();
			StrikethroughSpan[] ss = this.getText().getSpans(beginWord, endWord, StrikethroughSpan.class);
			if (ss.length != 0) {
				enableStringPart(true, cursorPosition);
				editText.removeSpan(ss[0]);
			} else {
				enableStringPart(false, cursorPosition);
				editText.setSpan(new StrikethroughSpan(), beginWord, endWord, 0);
			}
			this.setText(editText);
        }


        private void enableStringPart(boolean enable, int position) {
        	int counter = 0;
        	for (int i = 0; i < mCutString.length; i++) {
        		counter += mCutString[i].length() + 1;
        		if (counter > position) {
        			mEnabled[i] = enable;
        			break;
        		}
        	}
        }


        public void removeDeleted() {
        	if (this.getText().length() > 0) {
            	StringBuilder sb = new StringBuilder();
            	for (int i = 0; i < mCutString.length; i++) {
            		if (mEnabled[i]) {
            			sb.append(mCutString[i]);
        				sb.append(" ");
            		}
            	}
            	int last = sb.length() - 1;
            	while (sb.substring(last).equals(" ") || sb.substring(last).equals(",")) {
            		sb.deleteCharAt(last);
            		last--;
            	}
            	this.setText(sb.toString());
        	}
        }
    }
}

