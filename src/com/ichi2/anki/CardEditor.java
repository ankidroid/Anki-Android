/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.KeyListener;
import android.text.style.StrikethroughSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anim.ViewAnimation;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.async.DeckTask;
import com.ichi2.filters.FilterFacade;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.StyledDialog.Builder;
import com.ichi2.themes.StyledOpenCollectionDialog;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;
import com.ichi2.widget.WidgetStatus;

import org.amr.arabic.ArabicUtilities;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

/**
 * Allows the user to edit a fact, for instance if there is a typo. A card is a presentation of a fact, and has two
 * sides: a question and an answer. Any number of fields can appear on each side. When you add a fact to Anki, cards
 * which show that fact are generated. Some models generate one card, others generate more than one.
 * 
 * @see http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Cards
 */
public class CardEditor extends Activity {

    public static final String SOURCE_LANGUAGE = "SOURCE_LANGUAGE";
    public static final String TARGET_LANGUAGE = "TARGET_LANGUAGE";
    public static final String SOURCE_TEXT = "SOURCE_TEXT";
    public static final String TARGET_TEXT = "TARGET_TEXT";
    public static final String EXTRA_CALLER = "CALLER";
    public static final String EXTRA_CARD_ID = "CARD_ID";
    public static final String EXTRA_CONTENTS = "CONTENTS";
    public static final String EXTRA_ID = "ID";

    private static final int DIALOG_DECK_SELECT = 0;
    private static final int DIALOG_MODEL_SELECT = 1;
    private static final int DIALOG_TAGS_SELECT = 2;
    private static final int DIALOG_RESET_CARD = 3;
    private static final int DIALOG_INTENT_INFORMATION = 4;

    private static final String ACTION_CREATE_FLASHCARD = "org.openintents.action.CREATE_FLASHCARD";
    private static final String ACTION_CREATE_FLASHCARD_SEND = "android.intent.action.SEND";

    private static final int MENU_LOOKUP = 0;
    private static final int MENU_RESET = 1;
    private static final int MENU_COPY_CARD = 2;
    private static final int MENU_ADD_CARD = 3;
    private static final int MENU_RESET_CARD_PROGRESS = 4;
    private static final int MENU_SAVED_INTENT = 5;

    // calling activity
    public static final int CALLER_NOCALLER = 0;

    public static final int CALLER_REVIEWER = 1;
    public static final int CALLER_STUDYOPTIONS = 2;
    public static final int CALLER_DECKPICKER = 3;

    public static final int CALLER_BIGWIDGET_EDIT = 4;
    public static final int CALLER_BIGWIDGET_ADD = 5;

    public static final int CALLER_CARDBROWSER_EDIT = 6;
    public static final int CALLER_CARDBROWSER_ADD = 7;

    public static final int CALLER_CARDEDITOR = 8;
    public static final int CALLER_CARDEDITOR_INTENT_ADD = 9;
    public static final int CALLER_INDICLASH = 10;

    public static final int REQUEST_ADD = 0;
    public static final int REQUEST_INTENT_ADD = 1;

    private static final int WAIT_TIME_UNTIL_UPDATE = 1000;

    private static boolean mChanged = false;

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;
    private Bundle mSavedInstanceState;

    private LinearLayout mFieldsLayoutContainer;

    private Button mSave;
    private Button mCancel;
    private Button mLater;
    private TextView mTagsButton;
    private TextView mModelButton;
    private TextView mDeckButton;
    private Button mSwapButton;

    private Note mEditorNote;
    private Card mCurrentEditedCard;
    private List<String> mCurrentTags;
    private long mCurrentDid;

    /* indicates if a new fact is added or a card is edited */
    private boolean mAddNote;

    private boolean mAedictIntent;

    /* indicates which activity called card editor */
    private int mCaller;

    private Collection mCol;
    private long mDeckId;

    private LinkedList<FieldEditText> mEditFields;

    private int mCardItemBackground;
    private ArrayList<HashMap<String, String>> mIntentInformation;
    private SimpleAdapter mIntentInformationAdapter;
    private StyledDialog mIntentInformationDialog;
    private StyledDialog mDeckSelectDialog;

    private String[] allTags;
    private ArrayList<String> selectedTags;
    private EditText mNewTagEditText;
    private StyledDialog mTagsDialog;

    private StyledProgressDialog mProgressDialog;
    private StyledOpenCollectionDialog mOpenCollectionDialog;

    // private String mSourceLanguage;
    // private String mTargetLanguage;
    private String[] mSourceText;
    private int mSourcePosition = 0;
    private int mTargetPosition = 1;
    private boolean mCancelled = false;

    private boolean mPrefFixArabic;

    private int mFilledFields = 0;

    private DeckTask.TaskListener mSaveFactHandler = new DeckTask.TaskListener() {
        private boolean mCloseAfter = false;
        private Intent mIntent;


        @Override
        public void onPreExecute() {
            Resources res = getResources();
            mProgressDialog = StyledProgressDialog
                    .show(CardEditor.this, "", res.getString(R.string.saving_facts), true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            int count = values[0].getInt();
            if (mCaller == CALLER_BIGWIDGET_EDIT) {
                // AnkiDroidWidgetBig.setCard(values[0].getCard());
                // AnkiDroidWidgetBig.updateWidget(AnkiDroidWidgetBig.UpdateService.VIEW_NOT_SPECIFIED);
                mChanged = true;
            } else if (count > 0) {
                mChanged = true;
                mSourceText = null;
                setNote();
                Themes.showThemedToast(CardEditor.this,
                        getResources().getQuantityString(R.plurals.factadder_cards_added, count, count), true);
            } else {
                Themes.showThemedToast(CardEditor.this, getResources().getString(R.string.factadder_saving_error), true);
            }
            if (!mAddNote || mCaller == CALLER_CARDEDITOR || mCaller == CALLER_BIGWIDGET_EDIT || mAedictIntent) {
                mChanged = true;
                mCloseAfter = true;
            } else if (mCaller == CALLER_CARDEDITOR_INTENT_ADD) {
                if (count > 0) {
                    mChanged = true;
                }
                mCloseAfter = true;
                mIntent = new Intent();
                mIntent.putExtra(EXTRA_ID, getIntent().getStringExtra(EXTRA_ID));
            } else if (!mEditFields.isEmpty()) {
                mEditFields.getFirst().requestFocus();
            }
            if (!mCloseAfter) {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    try {
                        mProgressDialog.dismiss();
                    } catch (IllegalArgumentException e) {
                        Log.e(AnkiDroidApp.TAG, "Card Editor: Error on dismissing progress dialog: " + e);
                    }
                }
            }
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (result.getBoolean()) {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    try {
                        mProgressDialog.dismiss();
                    } catch (IllegalArgumentException e) {
                        Log.e(AnkiDroidApp.TAG, "Card Editor: Error on dismissing progress dialog: " + e);
                    }
                }
                if (mCloseAfter) {
                    if (mIntent != null) {
                        closeCardEditor(mIntent);
                    } else {
                        closeCardEditor();
                    }
                }
            } else {
                // RuntimeException occured on adding note
                closeCardEditor(DeckPicker.RESULT_DB_ERROR);
            }
        }
    };


    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(AnkiDroidApp.TAG, "CardEditor: onCreate");
        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);

        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        Intent intent = getIntent();
        if (savedInstanceState != null) {
            mCaller = savedInstanceState.getInt("caller");
            mAddNote = savedInstanceState.getBoolean("addFact");
        } else {
            mCaller = intent.getIntExtra(EXTRA_CALLER, CALLER_NOCALLER);
            if (mCaller == CALLER_NOCALLER) {
                String action = intent.getAction();
                if (action != null
                        && (ACTION_CREATE_FLASHCARD.equals(action) || ACTION_CREATE_FLASHCARD_SEND.equals(action))) {
                    mCaller = CALLER_INDICLASH;
                }
            }
        }
        Log.i(AnkiDroidApp.TAG, "CardEditor: caller: " + mCaller);
        
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());

        if (mCaller == CALLER_INDICLASH && preferences.getBoolean("intentAdditionInstantAdd", false)) {
            // save information without showing card editor
        	fetchIntentInformation(intent);
            MetaDB.saveIntentInformation(CardEditor.this, Utils.joinFields(mSourceText));
            Themes.showThemedToast(CardEditor.this, getResources().getString(R.string.app_name) + ": " + getResources().getString(R.string.CardEditorLaterMessage), false);
        	finish();
        	return;
        }

        mCol = AnkiDroidApp.getCol();
        if (mCol == null) {
            reloadCollection(savedInstanceState);
            return;
        }

        registerExternalStorageListener();

        View mainView = getLayoutInflater().inflate(R.layout.card_editor, null);
        setContentView(mainView);
        Themes.setWallpaper(mainView);
        Themes.setContentStyle(mainView, Themes.CALLER_CARD_EDITOR);

        mFieldsLayoutContainer = (LinearLayout) findViewById(R.id.CardEditorEditFieldsLayout);

        mSave = (Button) findViewById(R.id.CardEditorSaveButton);
        mCancel = (Button) findViewById(R.id.CardEditorCancelButton);
        mLater = (Button) findViewById(R.id.CardEditorLaterButton);
        mDeckButton = (TextView) findViewById(R.id.CardEditorDeckText);
        mModelButton = (TextView) findViewById(R.id.CardEditorModelText);
        mTagsButton = (TextView) findViewById(R.id.CardEditorTagText);
        mSwapButton = (Button) findViewById(R.id.CardEditorSwapButton);
        mSwapButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	swapText(false);
            }
        });

        mAedictIntent = false;

        switch (mCaller) {
            case CALLER_NOCALLER:
                Log.i(AnkiDroidApp.TAG, "CardEditor: no caller could be identified, closing");
                finish();
                return;

            case CALLER_REVIEWER:
                mCurrentEditedCard = Reviewer.getEditorCard();
                if (mCurrentEditedCard == null) {
                    finish();
                    return;
                }
                mEditorNote = mCurrentEditedCard.note();
                mAddNote = false;
                break;

            case CALLER_STUDYOPTIONS:
            case CALLER_DECKPICKER:
                mAddNote = true;
                break;

            case CALLER_BIGWIDGET_EDIT:
                // Card widgetCard = AnkiDroidWidgetBig.getCard();
                // if (widgetCard == null) {
                // finish();
                // return;
                // }
                // mEditorNote = widgetCard.getFact();
                // mAddNote = false;
                break;

            case CALLER_BIGWIDGET_ADD:
                mAddNote = true;
                break;

            case CALLER_CARDBROWSER_EDIT:
                mCurrentEditedCard = CardBrowser.sCardBrowserCard;
                if (mCurrentEditedCard == null) {
                    finish();
                    return;
                }
                mEditorNote = mCurrentEditedCard.note();
                mAddNote = false;
                break;

            case CALLER_CARDBROWSER_ADD:
                mAddNote = true;
                break;

            case CALLER_CARDEDITOR:
                mAddNote = true;
                break;

            case CALLER_CARDEDITOR_INTENT_ADD:
                mAddNote = true;
                break;

            case CALLER_INDICLASH:
            	fetchIntentInformation(intent);
                if (mSourceText == null) {
                    finish();
                    return;
                }
                if (mSourceText[0].equals("Aedict Notepad") && addFromAedict(mSourceText[1])) {
                    finish();
                    return;
                }
                mAddNote = true;
                break;
        }

        setNote(mEditorNote);

        if (mAddNote) {
            setTitle(R.string.cardeditor_title_add_note);
            // set information transferred by intent
            String contents = null;
            if (mSourceText != null) {
                if (mAedictIntent && (mEditFields.size() == 3) && mSourceText[1].contains("[")) {
                    contents = mSourceText[1].replaceFirst("\\[", "\u001f");
                    contents = contents.substring(0, contents.length() - 1);
                } else {
                    mEditFields.get(0).setText(mSourceText[0]);
                    mEditFields.get(1).setText(mSourceText[1]);
                }
            } else {
                contents = intent.getStringExtra(EXTRA_CONTENTS);
            }
            if (contents != null) {
                setEditFieldTexts(contents);
            }

            LinearLayout modelButton = ((LinearLayout) findViewById(R.id.CardEditorModelButton));
            modelButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showDialog(DIALOG_MODEL_SELECT);
                }
            });
            modelButton.setVisibility(View.VISIBLE);
            mSave.setText(getResources().getString(R.string.add));
            mCancel.setText(getResources().getString(R.string.close));

            mLater.setVisibility(View.VISIBLE);
            mLater.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String content = getFieldsText();
                    if (content.length() > mEditFields.size() - 1) {
                        MetaDB.saveIntentInformation(CardEditor.this, content);
                        populateEditFields();
                        mSourceText = null;
                        Themes.showThemedToast(CardEditor.this,
                                getResources().getString(R.string.CardEditorLaterMessage), false);
                    }
                    if (mCaller == CALLER_INDICLASH || mCaller == CALLER_CARDEDITOR_INTENT_ADD) {
                        closeCardEditor();
                    }
                }
            });
        } else {
            setTitle(R.string.cardeditor_title_edit_card);
            mSwapButton.setVisibility(View.GONE);
            mSwapButton = (Button) findViewById(R.id.CardEditorLaterButton);
            mSwapButton.setVisibility(View.VISIBLE);
            mSwapButton.setText(getResources().getString(R.string.fact_adder_swap));
            mSwapButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                	swapText(false);
                }
            });
        }

        ((LinearLayout) findViewById(R.id.CardEditorDeckButton)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showDialog(DIALOG_DECK_SELECT);
            }
        });

        mPrefFixArabic = preferences.getBoolean("fixArabicText", false);
        // if Arabic reshaping is enabled, disable the Save button to avoid
        // saving the reshaped string to the deck
        if (mPrefFixArabic && !mAddNote) {
            mSave.setEnabled(false);
        }

        ((LinearLayout) findViewById(R.id.CardEditorTagButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog(DIALOG_TAGS_SELECT);
            }
        });

        mSave.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (duplicateCheck(true)) {
                    return;
                }
                boolean modified = false;
                for (FieldEditText f : mEditFields) {
                    modified = modified | f.updateField();
                }
                if (mAddNote) {
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ADD_FACT, mSaveFactHandler, new DeckTask.TaskData(
                            mEditorNote));
                } else {
                    // added tag?
                    for (String t : mCurrentTags) {
                        modified = modified || !mEditorNote.hasTag(t);
                    }
                    // removed tag?
                    modified = modified || mEditorNote.getTags().size() > mCurrentTags.size();
                    // changed did?
                    boolean changedDid = mCurrentEditedCard.getDid() != mCurrentDid;
                    modified = modified || changedDid;
                    if (modified) {
                        mEditorNote.setTags(mCurrentTags);
                        // set did for card
                        if (changedDid) {
                            mCurrentEditedCard.setDid(mCurrentDid);
                        }
                        mChanged = true;
                    }
                    closeCardEditor();
                    // if (mCaller == CALLER_BIGWIDGET_EDIT) {
                    // // DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_FACT,
                    // // mSaveFactHandler, new
                    // // DeckTask.TaskData(Reviewer.UPDATE_CARD_SHOW_QUESTION,
                    // // mDeck, AnkiDroidWidgetBig.getCard()));
                    // } else if (!mCardReset) {
                    // // Only send result to save if something was actually
                    // // changed
                    // if (mModified) {
                    // setResult(RESULT_OK);
                    // } else {
                    // setResult(RESULT_CANCELED);
                    // }
                    // closeCardEditor();
                    // }

                }
            }
        });

        mCancel.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                closeCardEditor();
            }

        });
    }


    @Override
    protected void onStop() {
        super.onStop();
        if (!isFinishing()) {
            WidgetStatus.update(this);
            UIUtils.saveCollectionInBackground();
        }
    }

    private void fetchIntentInformation(Intent intent) {
        Bundle extras = intent.getExtras();
        if (ACTION_CREATE_FLASHCARD.equals(intent.getAction())) {
            // mSourceLanguage = extras.getString(SOURCE_LANGUAGE);
            // mTargetLanguage = extras.getString(TARGET_LANGUAGE);
        	mSourceText = new String[2];
            mSourceText[0] = extras.getString(SOURCE_TEXT);
            mSourceText[1] = extras.getString(TARGET_TEXT);
        } else {
        	String first;
        	String second;
        	if (extras.getString(Intent.EXTRA_SUBJECT) != null) {
        		first = extras.getString(Intent.EXTRA_SUBJECT);
        	} else {
        		first = "";
        	}
        	if (extras.getString(Intent.EXTRA_TEXT) != null) {
        		second = extras.getString(Intent.EXTRA_TEXT);
        	} else {
        		second = "";
        	}
            Pair<String, String> messages = new Pair<String, String>(first, second);
            
            /* Filter garbage information */
            Pair<String, String> cleanMessages = new FilterFacade(getBaseContext()).filter(messages);

        	mSourceText = new String[2];
            mSourceText[0] = cleanMessages.first;
            mSourceText[1] = cleanMessages.second;
        }
    }

    private void reloadCollection(Bundle savedInstanceState) {
    	mSavedInstanceState = savedInstanceState;
        DeckTask.launchDeckTask(
                DeckTask.TASK_TYPE_OPEN_COLLECTION,
                new DeckTask.TaskListener() {

                    @Override
                    public void onPostExecute(DeckTask.TaskData result) {
                        if (mOpenCollectionDialog.isShowing()) {
                            try {
                            	mOpenCollectionDialog.dismiss();
                            } catch (Exception e) {
                                Log.e(AnkiDroidApp.TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
                            }
                        }
                        mCol = result.getCollection();
                        if (mCol == null) {
                            finish();
                        } else {
                            onCreate(mSavedInstanceState);
                        }
                    }


                    @Override
                    public void onPreExecute() {
                    	mOpenCollectionDialog = StyledOpenCollectionDialog.show(CardEditor.this, getResources().getString(R.string.open_collection), new OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface arg0) {
                                finish();
                            }
                        });
                    }


                    @Override
                    public void onProgressUpdate(DeckTask.TaskData... values) {
                    }
                },
                new DeckTask.TaskData(AnkiDroidApp.getCurrentAnkiDroidDirectory()
                        + AnkiDroidApp.COLLECTION_PATH));
    }


    private boolean addFromAedict(String extra_text) {
        String category = "";
        String[] notepad_lines = extra_text.split("\n");
        for (int i = 0; i < notepad_lines.length; i++) {
            if (notepad_lines[i].startsWith("[") && notepad_lines[i].endsWith("]")) {
                category = notepad_lines[i].substring(1, notepad_lines[i].length() - 1);
                if (category.equals("default")) {
                    if (notepad_lines.length > i + 1) {
                        String[] entry_lines = notepad_lines[i + 1].split(":");
                        if (entry_lines.length > 1) {
                            mSourceText[0] = entry_lines[1];
                            mSourceText[1] = entry_lines[0];
                            mAedictIntent = true;
                        } else {
                            Themes.showThemedToast(CardEditor.this,
                                    getResources().getString(R.string.intent_aedict_empty), false);
                            return true;
                        }
                    } else {
                        Themes.showThemedToast(CardEditor.this, getResources().getString(R.string.intent_aedict_empty),
                                false);
                        return true;
                    }
                    return false;
                }
            }
        }
        Themes.showThemedToast(CardEditor.this, getResources().getString(R.string.intent_aedict_category), false);
        return true;
    }


    private void resetEditFields (String[] content) {
    	for (int i = 0; i < Math.min(content.length, mEditFields.size()); i++) {
    		mEditFields.get(i).setText(content[i]);
    	}
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
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
    protected void onSaveInstanceState(Bundle outState) {
        // TODO
        // Log.i(AnkiDroidApp.TAG, "onSaveInstanceState: " + path);
        // outState.putString("deckFilename", path);
        outState.putBoolean("addFact", mAddNote);
        outState.putInt("caller", mCaller);
        Log.i(AnkiDroidApp.TAG, "onSaveInstanceState - Ending");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        Resources res = getResources();
        // Lookup.initialize(this, mDeck.getDeckPath());
        // item = menu.add(Menu.NONE, MENU_LOOKUP, Menu.NONE,
        // Lookup.getSearchStringTitle());
        // item.setIcon(R.drawable.ic_menu_search);
        // item.setEnabled(Lookup.isAvailable());
        // item = menu.add(Menu.NONE, MENU_RESET, Menu.NONE,
        // res.getString(R.string.card_editor_reset));
        // item.setIcon(R.drawable.ic_menu_revert);
        if (!mAddNote) {
            item = menu.add(Menu.NONE, MENU_ADD_CARD, Menu.NONE, res.getString(R.string.card_editor_add_card));
            item.setIcon(R.drawable.ic_menu_add);
        }
        item = menu.add(Menu.NONE, MENU_COPY_CARD, Menu.NONE, res.getString(R.string.card_editor_copy_card));
        item.setIcon(R.drawable.ic_menu_upload);
        if (!mAddNote) {
            item = menu.add(Menu.NONE, MENU_RESET_CARD_PROGRESS, Menu.NONE,
                    res.getString(R.string.card_editor_reset_card));
            item.setIcon(R.drawable.ic_menu_delete);
        }
        if (mCaller != CALLER_CARDEDITOR_INTENT_ADD) {
            mIntentInformation = MetaDB.getIntentInformation(this);
            item = menu.add(Menu.NONE, MENU_SAVED_INTENT, Menu.NONE,
                    res.getString(R.string.intent_add_saved_information));
            item.setIcon(R.drawable.ic_menu_archive);
        }
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // View focus = this.getWindow().getCurrentFocus();
        // menu.findItem(MENU_LOOKUP).setEnabled(
        // focus instanceof FieldEditText
        // && ((TextView) focus).getText().length() > 0
        // && Lookup.isAvailable());

        if (mEditFields == null) {
            return false;
        }
        for (int i = 0; i < mEditFields.size(); i++) {
            if (mEditFields.get(i).getText().length() > 0) {
                menu.findItem(MENU_COPY_CARD).setEnabled(true);
                break;
            } else if (i == mEditFields.size() - 1) {
                menu.findItem(MENU_COPY_CARD).setEnabled(false);
            }
        }

        if (mCaller != CALLER_CARDEDITOR_INTENT_ADD) {
            mIntentInformation = MetaDB.getIntentInformation(this);
            menu.findItem(MENU_SAVED_INTENT).setEnabled(mIntentInformation.size() > 0);
        }
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_COPY_CARD:
            case MENU_ADD_CARD:
                Intent intent = new Intent(CardEditor.this, CardEditor.class);
                intent.putExtra(EXTRA_CALLER, CALLER_CARDEDITOR);
                // intent.putExtra(EXTRA_DECKPATH, mDeckPath);
                if (item.getItemId() == MENU_COPY_CARD) {
                    intent.putExtra(EXTRA_CONTENTS, getFieldsText());
                }
                startActivityForResult(intent, REQUEST_ADD);
                if (AnkiDroidApp.SDK_VERSION > 4) {
                    ActivityTransitionAnimation.slide(CardEditor.this, ActivityTransitionAnimation.LEFT);
                }
                return true;

            case MENU_RESET:
                if (mAddNote) {
                    if (mCaller == CALLER_INDICLASH || mCaller == CALLER_CARDEDITOR_INTENT_ADD) {
                    	resetEditFields(mSourceText);
                    } else {
                        setEditFieldTexts(getIntent().getStringExtra(EXTRA_CONTENTS));
                        if (!mEditFields.isEmpty()) {
                            mEditFields.getFirst().requestFocus();
                        }
                    }
                } else {
                    populateEditFields();
                }
                return true;

            case MENU_LOOKUP:
                View focus = this.getWindow().getCurrentFocus();
                if (focus instanceof FieldEditText) {
                    FieldEditText field = (FieldEditText) focus;
                    if (!field.isSelected()) {
                        field.selectAll();
                    }
                    Lookup.lookUp(
                            field.getText().toString().substring(field.getSelectionStart(), field.getSelectionEnd()));
                }
                return true;

            case MENU_RESET_CARD_PROGRESS:
                showDialog(DIALOG_RESET_CARD);
                return true;

            case MENU_SAVED_INTENT:
                showDialog(DIALOG_INTENT_INFORMATION);
                return true;

            case android.R.id.home:
                closeCardEditor(AnkiDroidApp.RESULT_TO_HOME);
                return true;
        }
        return false;
    }


    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------

    /**
     * finish when sd card is ejected
     */
    private void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(SdCardReceiver.MEDIA_EJECT)) {
                        finish();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(SdCardReceiver.MEDIA_EJECT);
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }



    private void finishNoStorageAvailable() {
        closeCardEditor(DeckPicker.RESULT_MEDIA_EJECTED);
    }


    private void closeCardEditor() {
        closeCardEditor(null);
    }


    private void closeCardEditor(Intent intent) {
        int result;
        if (mChanged) {
            result = RESULT_OK;
        } else {
            result = RESULT_CANCELED;
        }
        closeCardEditor(result, intent);
    }


    private void closeCardEditor(int result) {
        closeCardEditor(result, null);
    }


    private void closeCardEditor(int result, Intent intent) {
        if (intent != null) {
            setResult(result, intent);
        } else {
            setResult(result);
        }
        finish();
        if (mCaller == CALLER_CARDEDITOR_INTENT_ADD || mCaller == CALLER_BIGWIDGET_EDIT
                || mCaller == CALLER_BIGWIDGET_ADD) {
            if (AnkiDroidApp.SDK_VERSION > 4) {
                ActivityTransitionAnimation.slide(CardEditor.this, ActivityTransitionAnimation.FADE);
            }
        } else if (mCaller == CALLER_INDICLASH) {
            if (AnkiDroidApp.SDK_VERSION > 4) {
                ActivityTransitionAnimation.slide(CardEditor.this, ActivityTransitionAnimation.NONE);
            }
        } else {
            if (AnkiDroidApp.SDK_VERSION > 4) {
                ActivityTransitionAnimation.slide(CardEditor.this, ActivityTransitionAnimation.RIGHT);
            }
        }
    }


    @Override
    protected Dialog onCreateDialog(int id) {
        StyledDialog dialog = null;
        Resources res = getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(this);

        switch (id) {
            case DIALOG_TAGS_SELECT:
                builder.setTitle(R.string.card_details_tags);
                builder.setPositiveButton(res.getString(R.string.select), new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mAddNote) {
                            try {
                                JSONArray ja = new JSONArray();
                                for (String t : selectedTags) {
                                    ja.put(t);
                                }
                                mCol.getModels().current().put("tags", ja);
                                mCol.getModels().setChanged();
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            mEditorNote.setTags(selectedTags);
                        }
                        mCurrentTags = selectedTags;
                        updateTags();
                    }
                });
                builder.setNegativeButton(res.getString(R.string.cancel), null);

                mNewTagEditText = (EditText) new EditText(this);
                mNewTagEditText.setHint(R.string.add_new_tag);

                InputFilter filter = new InputFilter() {
                    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart,
                            int dend) {
                        for (int i = start; i < end; i++) {
                            if (source.charAt(i) == ' ' || source.charAt(i) == ',') {
                                return "";
                            }
                        }
                        return null;
                    }
                };
                mNewTagEditText.setFilters(new InputFilter[] { filter });

                ImageView mAddTextButton = new ImageView(this);
                mAddTextButton.setImageResource(R.drawable.ic_addtag);
                mAddTextButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String tag = mNewTagEditText.getText().toString();
                        if (tag.length() != 0) {
                            if (mEditorNote.hasTag(tag)) {
                                mNewTagEditText.setText("");
                                return;
                            }
                            selectedTags.add(tag);
                            actualizeTagDialog(mTagsDialog);
                            mNewTagEditText.setText("");
                        }
                    }
                });

                FrameLayout frame = new FrameLayout(this);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                params.rightMargin = 10;
                mAddTextButton.setLayoutParams(params);
                frame.addView(mNewTagEditText);
                frame.addView(mAddTextButton);

                builder.setView(frame, false, true);
                dialog = builder.create();
                mTagsDialog = dialog;
                break;

            case DIALOG_DECK_SELECT:
                ArrayList<CharSequence> dialogDeckItems = new ArrayList<CharSequence>();
                // Use this array to know which ID is associated with each
                // Item(name)
                final ArrayList<Long> dialogDeckIds = new ArrayList<Long>();

                ArrayList<JSONObject> decks = mCol.getDecks().all();
                Collections.sort(decks, new JSONNameComparator());
                builder.setTitle(R.string.deck);
                for (JSONObject d : decks) {
                    try {
                        if (d.getInt("dyn") == 0) {
                            dialogDeckItems.add(d.getString("name"));
                            dialogDeckIds.add(d.getLong("id"));
                        }
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
                // Convert to Array
                String[] items = new String[dialogDeckItems.size()];
                dialogDeckItems.toArray(items);

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        long newId = dialogDeckIds.get(item);
                        if (mCurrentDid != newId) {
                            if (mAddNote) {
                                try {
                                    // TODO: mEditorNote.setDid(newId);
                                    mEditorNote.model().put("did", newId);
                                    mCol.getModels().setChanged();
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            mCurrentDid = newId;
                            updateDeck();
                        }
                    }
                });

                dialog = builder.create();
                mDeckSelectDialog = dialog;
                break;

            case DIALOG_MODEL_SELECT:
                ArrayList<CharSequence> dialogItems = new ArrayList<CharSequence>();
                // Use this array to know which ID is associated with each
                // Item(name)
                final ArrayList<Long> dialogIds = new ArrayList<Long>();

                ArrayList<JSONObject> models = mCol.getModels().all();
                Collections.sort(models, new JSONNameComparator());
                builder.setTitle(R.string.note_type);
                for (JSONObject m : models) {
                    try {
                        dialogItems.add(m.getString("name"));
                        dialogIds.add(m.getLong("id"));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
                // Convert to Array
                String[] items2 = new String[dialogItems.size()];
                dialogItems.toArray(items2);

                builder.setItems(items2, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        long oldModelId;
                        try {
                            oldModelId = mCol.getModels().current().getLong("id");
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        long newId = dialogIds.get(item);
                        if (oldModelId != newId) {
                            mCol.getModels().setCurrent(mCol.getModels().get(newId));
                            JSONObject cdeck = mCol.getDecks().current();
                            try {
                                cdeck.put("mid", newId);
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            mCol.getDecks().save(cdeck);
                            int size = mEditFields.size();
                            String[] oldValues = new String[size];
                            for (int i = 0; i < size; i++) {
                                oldValues[i] = mEditFields.get(i).getText().toString();
                            }
                            setNote();
                            resetEditFields(oldValues);
                            mTimerHandler.removeCallbacks(checkDuplicatesRunnable);
                            duplicateCheck(false);
                        }
                    }
                });
                dialog = builder.create();
                break;

            case DIALOG_RESET_CARD:
                builder.setTitle(res.getString(R.string.reset_card_dialog_title));
                builder.setMessage(res.getString(R.string.reset_card_dialog_message));
                builder.setPositiveButton(res.getString(R.string.yes), new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // for (long cardId :
                        // mDeck.getCardsFromFactId(mEditorNote.getId())) {
                        // mDeck.cardFromId(cardId).resetCard();
                        // }
                        // mDeck.reset();
                        // setResult(Reviewer.RESULT_EDIT_CARD_RESET);
                        // mCardReset = true;
                        // Themes.showThemedToast(CardEditor.this,
                        // getResources().getString(
                        // R.string.reset_card_dialog_confirmation), true);
                    }
                });
                builder.setNegativeButton(res.getString(R.string.no), null);
                builder.setCancelable(true);
                dialog = builder.create();
                break;

            case DIALOG_INTENT_INFORMATION:
                dialog = createDialogIntentInformation(builder, res);
        }

        return dialog;
    }


    private StyledDialog createDialogIntentInformation(Builder builder, Resources res) {
        builder.setTitle(res.getString(R.string.intent_add_saved_information));
        ListView listView = new ListView(this);

        mIntentInformationAdapter = new SimpleAdapter(this, mIntentInformation, R.layout.card_item, new String[] {
                "source", "target", "id" }, new int[] { R.id.card_sfld, R.id.card_tmpl, R.id.card_item });
        listView.setAdapter(mIntentInformationAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(CardEditor.this, CardEditor.class);
                intent.putExtra(EXTRA_CALLER, CALLER_CARDEDITOR_INTENT_ADD);
                HashMap<String, String> map = mIntentInformation.get(position);
                intent.putExtra(EXTRA_CONTENTS, map.get("fields"));
                intent.putExtra(EXTRA_ID, map.get("id"));
                startActivityForResult(intent, REQUEST_INTENT_ADD);
                if (AnkiDroidApp.SDK_VERSION > 4) {
                    ActivityTransitionAnimation.slide(CardEditor.this, ActivityTransitionAnimation.FADE);
                }
                mIntentInformationDialog.dismiss();
            }
        });
        mCardItemBackground = Themes.getCardBrowserBackground()[0];
        mIntentInformationAdapter.setViewBinder(new SimpleAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Object arg1, String text) {
                if (view.getId() == R.id.card_item) {
                    view.setBackgroundResource(mCardItemBackground);
                    return true;
                }
                return false;
            }
        });
        listView.setBackgroundColor(android.R.attr.colorBackground);
        listView.setDrawSelectorOnTop(true);
        listView.setFastScrollEnabled(true);
        Themes.setContentStyle(listView, Themes.CALLER_CARDEDITOR_INTENTDIALOG);
        builder.setView(listView, false, true);
        builder.setCancelable(true);
        builder.setPositiveButton(res.getString(R.string.intent_add_clear_all), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int arg1) {
                MetaDB.resetIntentInformation(CardEditor.this);
                mIntentInformation.clear();
                dialog.dismiss();
            }
        });
        StyledDialog dialog = builder.create();
        mIntentInformationDialog = dialog;
        return dialog;
    }


    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        StyledDialog ad = (StyledDialog) dialog;
        switch (id) {
            case DIALOG_TAGS_SELECT:
            	if (mEditorNote == null) {
            		dialog = null;
            		return;
            	}
                selectedTags = new ArrayList<String>();
                for (String s : mEditorNote.getTags()) {
                    selectedTags.add(s);
                }
                actualizeTagDialog(ad);
                break;

            case DIALOG_INTENT_INFORMATION:
                // dirty fix for dialog listview not being actualized
                mIntentInformationDialog = createDialogIntentInformation(new StyledDialog.Builder(this), getResources());
                ad = mIntentInformationDialog;
                break;
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == DeckPicker.RESULT_DB_ERROR) {
            closeCardEditor(DeckPicker.RESULT_DB_ERROR);
        }

        if (resultCode == AnkiDroidApp.RESULT_TO_HOME) {
            closeCardEditor(AnkiDroidApp.RESULT_TO_HOME);
        }
        switch (requestCode) {
            case REQUEST_INTENT_ADD:
                if (resultCode != RESULT_CANCELED) {
                    mChanged = true;
                    String id = data.getStringExtra(EXTRA_ID);
                    if (id != null) {
                        for (int i = 0; i < mIntentInformation.size(); i++) {
                            if (mIntentInformation.get(i).get("id").endsWith(id)) {
                                if (MetaDB.removeIntentInformation(CardEditor.this, id)) {
                                    mIntentInformation.remove(i);
                                    mIntentInformationAdapter.notifyDataSetChanged();
                                }
                                break;
                            }
                        }
                    }
                }
                if (mIntentInformation.size() > 0) {
                    showDialog(DIALOG_INTENT_INFORMATION);
                }
                break;
            case REQUEST_ADD:
                if (resultCode != RESULT_CANCELED) {
                    mChanged = true;
                }
                break;
        }
    }


    private void actualizeTagDialog(StyledDialog ad) {
        TreeSet<String> tags = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        for (String tag : mCol.getTags().all()) {
            tags.add(tag);
        }
        tags.addAll(selectedTags);
        int len = tags.size();
        allTags = new String[len];
        boolean[] checked = new boolean[len];
        int i = 0;
        for (String t : tags) {
            allTags[i++] = t;
            if (selectedTags.contains(t)) {
                checked[i - 1] = true;
            }
        }
        ad.setMultiChoiceItems(allTags, checked, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int which) {
                String tag = allTags[which];
                if (selectedTags.contains(tag)) {
                    Log.i(AnkiDroidApp.TAG, "unchecked tag: " + tag);
                    selectedTags.remove(tag);
                } else {
                    Log.i(AnkiDroidApp.TAG, "checked tag: " + tag);
                    selectedTags.add(tag);
                }
            }
        });
    }


    private void swapText(boolean reset) {
        int len = mEditFields.size();
        if (len < 2) {
            return;
        }
        mSourcePosition = Math.min(mSourcePosition, len - 1);
        mTargetPosition = Math.min(mTargetPosition, len - 1);

        // get source text
        FieldEditText field = mEditFields.get(mSourcePosition);
        Editable sourceText = field.getText();
        boolean sourceCutMode = field.getCutMode();
        FieldEditText.WordRow[] sourceCutString = field.getCutString();

        // get target text
        field = mEditFields.get(mTargetPosition);
        Editable targetText = field.getText();
        boolean targetCutMode = field.getCutMode();
        FieldEditText.WordRow[] targetCutString = field.getCutString();

        if (len > mSourcePosition) {
            mEditFields.get(mSourcePosition).setText("");
        }
        if (len > mTargetPosition) {
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
        if (sourceText != null) {
            mEditFields.get(mSourcePosition).setText(sourceText);
            mEditFields.get(mSourcePosition).setCutMode(sourceCutMode, sourceCutString);
        }
        if (targetText != null) {
            mEditFields.get(mTargetPosition).setText(targetText);
            mEditFields.get(mTargetPosition).setCutMode(targetCutMode, targetCutString);
        }
    }


    private void populateEditFields() {
        mFieldsLayoutContainer.removeAllViews();
        mEditFields = new LinkedList<FieldEditText>();
        String[][] fields = mEditorNote.items();

        // Use custom font if selected from preferences
        Typeface mCustomTypeface = null;
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        String customFont = preferences.getString("browserEditorFont", "");
        if (!customFont.equals("")) {
            mCustomTypeface = AnkiFont.getTypeface(this, customFont);
        }
        
        for (int i = 0; i < fields.length; i++) {
            FieldEditText newTextbox = new FieldEditText(this, i, fields[i]);

            if (mCustomTypeface != null) {
                newTextbox.setTypeface(mCustomTypeface);
            }

            TextView label = newTextbox.getLabel();
            label.setTextColor(Color.BLACK);
            label.setPadding((int) UIUtils.getDensityAdjustedValue(this, 3.4f), 0, 0, 0);
            ImageView circle = newTextbox.getCircle();
            mEditFields.add(newTextbox);
            FrameLayout frame = new FrameLayout(this);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            params.rightMargin = 10;
            circle.setLayoutParams(params);
            frame.addView(newTextbox);
            frame.addView(circle);
            mFieldsLayoutContainer.addView(label);
            mFieldsLayoutContainer.addView(frame);
        }
    }


    private void setEditFieldTexts(String contents) {
        String[] fields = null;
        int len;
        if (contents == null) {
            len = 0;
        } else {
            fields = Utils.splitFields(contents);
            len = fields.length;
        }
        for (int i = 0; i < mEditFields.size(); i++) {
            if (i < len) {
                mEditFields.get(i).setText(fields[i]);
            } else {
                mEditFields.get(i).setText("");
            }
        }
    }


    private boolean duplicateCheck(boolean checkEmptyToo) {
        FieldEditText field = mEditFields.get(0);
        if (mEditorNote.dupeOrEmpty(field.getText().toString()) > (checkEmptyToo ? 0 : 1)) {
            // TODO: theme backgrounds
            field.setBackgroundResource(R.drawable.white_edit_text_dupe);
            mSave.setEnabled(false);
            return true;
        } else {
            field.setBackgroundResource(R.drawable.white_edit_text);
            mSave.setEnabled(true);
            return false;
        }
    }

    private Handler mTimerHandler = new Handler();

    private Runnable checkDuplicatesRunnable = new Runnable() {
        public void run() {
            duplicateCheck(false);
        }
    };


    private String getFieldsText() {
        String[] fields = new String[mEditFields.size()];
        for (int i = 0; i < mEditFields.size(); i++) {
            fields[i] = mEditFields.get(i).getText().toString();
        }
        return Utils.joinFields(fields);
    }


    /** Make NOTE the current note. */
    private void setNote() {
        setNote(null);
    }


    private void setNote(Note note) {
        try {
            if (note == null) {
                mCurrentDid = mCol.getDecks().current().getLong("id");
                if (mCol.getDecks().isDyn(mCurrentDid)) {
                    mCurrentDid = 1;
                }

                JSONObject model = mCol.getModels().current();
                mEditorNote = new Note(mCol, model);
                mEditorNote.model().put("did", mCurrentDid);
                mModelButton.setText(getResources().getString(R.string.CardEditorModel,
                        model.getString("name")));
                JSONArray tags = model.getJSONArray("tags");
                for (int i = 0; i < tags.length(); i++) {
                    mEditorNote.addTag(tags.getString(i));
                }
            } else {
                mEditorNote = note;
                mCurrentDid = mCurrentEditedCard.getDid();
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        mCurrentTags = mEditorNote.getTags();
        updateDeck();
        updateTags();
        populateEditFields();
        swapText(true);
    }


    private void updateDeck() {
        try {
            mDeckButton.setText(getResources().getString(
                    mAddNote ? R.string.CardEditorNoteDeck : R.string.CardEditorCardDeck,
                    mCol.getDecks().get(mCurrentDid).getString("name")));
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private void updateTags() {
        mTagsButton.setText(getResources().getString(R.string.CardEditorTags,
                mCol.getTags().join(mCol.getTags().canonify(mCurrentTags)).trim().replace(" ", ", ")));
    }

    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------

    public class FieldEditText extends EditText {

        public final String NEW_LINE = System.getProperty("line.separator");
        public final String NL_MARK = "newLineMark";

        private WordRow mCutString[];
        private boolean mCutMode = false;
        private ImageView mCircle;
        private KeyListener mKeyListener;
        private Context mContext;

        private String mName;
        private int mOrd;


        public FieldEditText(Context context, int ord, String[] value) {
            super(context);
            mOrd = ord;
            mName = value[0];
            mContext = context;
            String content = value[1];
            if (content == null) {
                content = "";
            } else {
                content = content.replaceAll("<br(\\s*\\/*)>", NEW_LINE);
            }
            if (mPrefFixArabic) {
                this.setText(ArabicUtilities.reshapeSentence(content));
            } else {
                this.setText(content);
            }
            this.setMinimumWidth(400);
            this.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mCutMode) {
                        updateSpannables();
                    }
                }
            });
            if (ord == 0) {
                this.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void afterTextChanged(Editable arg0) {
                        mTimerHandler.removeCallbacks(checkDuplicatesRunnable);
                        mTimerHandler.postDelayed(checkDuplicatesRunnable, WAIT_TIME_UNTIL_UPDATE);
                    }


                    @Override
                    public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
                    }


                    @Override
                    public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
                    }
                });
            }
        }


        @Override
        public void onTextChanged(CharSequence text, int start, int before, int after) {
            super.onTextChanged(text, start, before, after);
            if (mCircle != null) {
                int visibility = mCircle.getVisibility();
                if (text.length() == 0) {
                    if (visibility == View.VISIBLE) {
                        mFilledFields--;
                        mCircle.setVisibility(View.GONE);
                        mCircle.setAnimation(ViewAnimation.fade(ViewAnimation.FADE_OUT, 300, 0));
                    }
                } else if (visibility == View.GONE) {
                    mFilledFields++;
                    mCircle.setVisibility(View.VISIBLE);
                    mCircle.setAnimation(ViewAnimation.fade(ViewAnimation.FADE_IN, 300, 0));
                }
            }
        }


        private void splitText(String text) {
            text = text.replace(NEW_LINE, " " + NL_MARK + " ");
            String[] cut = text.split("\\s");
            mCutString = new WordRow[cut.length];
            for (int i = 0; i < cut.length; i++) {
                mCutString[i] = new WordRow(cut[i]);
                if (mCutString[i].mWord.equals(NL_MARK)) {
                    mCutString[i].mEnabled = true;
                }
            }
        }


        public TextView getLabel() {
            TextView label = new TextView(this.getContext());
            label.setText(mName);
            return label;
        }


        public ImageView getCircle() {
            mCircle = new ImageView(this.getContext());
            mCircle.setImageResource(R.drawable.ic_circle_normal);
            mKeyListener = FieldEditText.this.getKeyListener();
            mCircle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Editable editText = FieldEditText.this.getText();
                    if (mCutMode) {
                        setCutMode(false, null);
                        updateContentAfterWordSelection(editText);
                    } else {
                        setCutMode(true, null);
                        String text = editText.toString();
                        splitText(text);
                        int pos = 0;
                        for (WordRow row : mCutString) {
                            if (row.mWord.length() == 0 || row.mWord.equals(NL_MARK)) {
                                continue;
                            }
                            row.mBegin = text.indexOf(row.mWord, pos);
                            row.mEnd = row.mBegin + row.mWord.length();
                            if (!row.mEnabled) {
                                editText.setSpan(new StrikethroughSpan(), row.mBegin, row.mEnd, 0);
                            }
                            pos = row.mEnd;
                        }
                    }
                }
            });
            if (this.getText().toString().length() > 0) {
                mFilledFields++;
                mCircle.setVisibility(View.VISIBLE);
            } else {
                mCircle.setVisibility(View.GONE);
            }
            return mCircle;
        }


        public boolean getCutMode() {
            return mCutMode;
        }


        public WordRow[] getCutString() {
            return mCutString;
        }


        public void setCutMode(boolean active, WordRow[] cutString) {
            mCutMode = active;
            if (mCutMode) {
                mCircle.setImageResource(R.drawable.ic_circle_pressed);
                InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(FieldEditText.this.getWindowToken(), 0);
                FieldEditText.this.setKeyListener(null);
                FieldEditText.this.setCursorVisible(false);
                if (cutString != null) {
                    mCutString = cutString;
                }
            } else {
                mCircle.setImageResource(R.drawable.ic_circle_normal);
                FieldEditText.this.setKeyListener(mKeyListener);
                FieldEditText.this.setCursorVisible(true);
            }
        }


        public boolean updateField() {
            if (mCutMode) {
                updateContentAfterWordSelection(FieldEditText.this.getText());
            }
            String newValue = this.getText().toString().replace(NEW_LINE, "<br>");
            ;
            if (!mEditorNote.values()[mOrd].equals(newValue)) {
                mEditorNote.values()[mOrd] = newValue;
                return true;
            }
            return false;
        }


        public void updateContentAfterWordSelection(Editable editText) {
            for (WordRow row : mCutString) {
                if (row.mEnabled && !row.mWord.equals(NL_MARK)) {
                    removeDeleted();
                    break;
                }
            }
            StrikethroughSpan[] ss = editText.getSpans(0, editText.length(), StrikethroughSpan.class);
            for (StrikethroughSpan s : ss) {
                editText.removeSpan(s);
            }
            mCutMode = false;
        }


        public void updateSpannables() {
            int cursorPosition = this.getSelectionStart();
            Editable editText = this.getText();
            for (WordRow row : mCutString) {
                if (row.mBegin <= cursorPosition && row.mEnd > cursorPosition) {
                    if (!row.mEnabled) {
                        StrikethroughSpan[] ss = this.getText().getSpans(cursorPosition, cursorPosition,
                                StrikethroughSpan.class);
                        if (ss.length != 0) {
                            editText.removeSpan(ss[0]);
                        }
                        row.mEnabled = true;
                    } else {
                        editText.setSpan(new StrikethroughSpan(), row.mBegin, row.mEnd, 0);
                        row.mEnabled = false;
                        break;
                    }
                }
            }
            this.setText(editText);
            this.setSelection(cursorPosition);
        }


        public String cleanText(String text) {
            text = text.replaceAll("\\s*(" + NL_MARK + "\\s*)+", NEW_LINE);
            text = text.replaceAll("^[,;:\\s\\)\\]" + NEW_LINE + "]*", "");
            text = text.replaceAll("[,;:\\s\\(\\[" + NEW_LINE + "]*$", "");
            return text;
        }


        public void removeDeleted() {
            if (this.getText().length() > 0) {
                StringBuilder sb = new StringBuilder();
                for (WordRow row : mCutString) {
                    if (row.mEnabled) {
                        sb.append(row.mWord);
                        sb.append(" ");
                    }
                }
                this.setText(cleanText(sb.toString()));
            }
        }

        private class WordRow {
            public String mWord;
            public int mBegin;
            public int mEnd;
            public boolean mEnabled = false;


            WordRow(String word) {
                mWord = word;
            }
        }
    }

    public class JSONNameComparator implements Comparator<JSONObject> {
        @Override
        public int compare(JSONObject lhs, JSONObject rhs) {
            String[] o1;
            String[] o2;
            try {
                o1 = lhs.getString("name").split("::");
                o2 = rhs.getString("name").split("::");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            for (int i = 0; i < Math.min(o1.length, o2.length); i++) {
                int result = o1[i].compareToIgnoreCase(o2[i]);
                if (result != 0) {
                    return result;
                }
            }
            if (o1.length < o2.length) {
                return -1;
            } else if (o1.length > o2.length) {
                return 1;
            } else {
                return 0;
            }
        }
    }
}
