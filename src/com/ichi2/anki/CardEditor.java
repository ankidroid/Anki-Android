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
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.PopupMenu.OnMenuItemClickListener;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.dialogs.TagsDialog;
import com.ichi2.anki.dialogs.TagsDialog.TagsDialogListener;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.activity.EditFieldActivity;
import com.ichi2.anki.multimediacard.fields.AudioField;
import com.ichi2.anki.multimediacard.fields.IField;
import com.ichi2.anki.multimediacard.fields.ImageField;
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.anki.servicelayer.NoteService;
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
import com.ichi2.widget.PopupMenuWithIcons;
import com.ichi2.widget.WidgetStatus;

import org.amr.arabic.ArabicUtilities;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Allows the user to edit a fact, for instance if there is a typo. A card is a presentation of a fact, and has two
 * sides: a question and an answer. Any number of fields can appear on each side. When you add a fact to Anki, cards
 * which show that fact are generated. Some models generate one card, others generate more than one.
 * 
 * @see http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Cards
 */
public class CardEditor extends ActionBarActivity {

    public static final String SOURCE_LANGUAGE = "SOURCE_LANGUAGE";
    public static final String TARGET_LANGUAGE = "TARGET_LANGUAGE";
    public static final String SOURCE_TEXT = "SOURCE_TEXT";
    public static final String TARGET_TEXT = "TARGET_TEXT";
    public static final String EXTRA_CALLER = "CALLER";
    public static final String EXTRA_CARD_ID = "CARD_ID";
    public static final String EXTRA_CONTENTS = "CONTENTS";
    public static final String EXTRA_ID = "ID";
    public static final String EXTRA_FIELD_INDEX = "multim.card.ed.extra.field.index";
    public static final String EXTRA_FIELD = "multim.card.ed.extra.field";
    public static final String EXTRA_WHOLE_NOTE = "multim.card.ed.extra.whole.note";

    private static final int DIALOG_DECK_SELECT = 0;
    private static final int DIALOG_MODEL_SELECT = 1;
    private static final int DIALOG_TAGS_SELECT = 2;
    private static final int DIALOG_RESET_CARD = 3;
    private static final int DIALOG_INTENT_INFORMATION = 4;

    private static final String ACTION_CREATE_FLASHCARD = "org.openintents.action.CREATE_FLASHCARD";
    private static final String ACTION_CREATE_FLASHCARD_SEND = "android.intent.action.SEND";

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
    public static final int REQUEST_MULTIMEDIA_EDIT = 2;

    private boolean mChanged = false;

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

    private Note mEditorNote;
    public static Card mCurrentEditedCard;
    private List<String> mSelectedTags;
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
    private final List<Map<String, String>> mIntentInformation = new ArrayList<Map<String, String>>();
    private SimpleAdapter mIntentInformationAdapter;
    private StyledDialog mIntentInformationDialog;
    private StyledDialog mDeckSelectDialog;

    private String[] allTags;

    private StyledProgressDialog mProgressDialog;
    private StyledOpenCollectionDialog mOpenCollectionDialog;

    // private String mSourceLanguage;
    // private String mTargetLanguage;
    private String[] mSourceText;
    private boolean mCancelled = false;

    private boolean mPrefFixArabic;

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


        @Override
        public void onCancelled() {
            // TODO Auto-generated method stub
            
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
        // Try to load the collection
        mCol = AnkiDroidApp.getCol();
        if (mCol == null) {
            // Reload the collection asynchronously, let onPostExecute method call initActivity()
            reloadCollection();
            return;
        } else {
            // If collection was not null then we can safely call initActivity() directly
            initActivity(mCol);
        }
    }


    // Finish initializing the activity after the collection has been correctly loaded
    private void initActivity(Collection col) {
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        Intent intent = getIntent();
        Log.i(AnkiDroidApp.TAG, "CardEditor: caller: " + mCaller);

        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());

        if (mCaller == CALLER_INDICLASH && preferences.getBoolean("intentAdditionInstantAdd", false)) {
            // save information without showing card editor
            fetchIntentInformation(intent);
            MetaDB.saveIntentInformation(CardEditor.this, Utils.joinFields(mSourceText));
            Themes.showThemedToast(CardEditor.this, getResources().getString(R.string.app_name) + ": "
                    + getResources().getString(R.string.CardEditorLaterMessage), false);
            finish();
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

        Preferences.COMING_FROM_ADD = false;

        mAedictIntent = false;

        switch (mCaller) {
            case CALLER_NOCALLER:
                Log.i(AnkiDroidApp.TAG, "CardEditor: no caller could be identified, closing");
                finish();
                return;

            case CALLER_REVIEWER:
                mCurrentEditedCard = AbstractFlashcardViewer.getEditorCard();
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
                    contents = mSourceText[1].replaceFirst("\\[", "\u001f" + mSourceText[0] + "\u001f");
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
                @Override
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
        }

        ((LinearLayout) findViewById(R.id.CardEditorDeckButton)).setOnClickListener(new View.OnClickListener() {
            @Override
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
                showDialogFragment(DIALOG_TAGS_SELECT);
            }
        });

        mSave.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                saveNote();
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


    private void openReviewer() {
        Intent reviewer = new Intent(CardEditor.this, Previewer.class);
        startActivity(reviewer);
    }


    private void reloadCollection() {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_OPEN_COLLECTION, new DeckTask.TaskListener() {

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
                    initActivity(AnkiDroidApp.getCol());
                }
            }


            @Override
            public void onPreExecute() {
                mOpenCollectionDialog = StyledOpenCollectionDialog.show(CardEditor.this,
                        getResources().getString(R.string.open_collection), new OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface arg0) {
                                finish();
                            }
                        });
            }


            @Override
            public void onProgressUpdate(DeckTask.TaskData... values) {
            }


            @Override
            public void onCancelled() {
                // TODO Auto-generated method stub
                
            }
        }, new DeckTask.TaskData(AnkiDroidApp.getCurrentAnkiDroidDirectory() + AnkiDroidApp.COLLECTION_PATH));
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


    private void resetEditFields(String[] content) {
        for (int i = 0; i < Math.min(content.length, mEditFields.size()); i++) {
            mEditFields.get(i).setText(content[i]);
        }
    }


    private void saveNote() {
        if (mSelectedTags == null) {
            mSelectedTags = new ArrayList<String>();
        }
        // treat add new note and edit existing note independently
        if (mAddNote) {
            // load all of the fields into the note
            for (FieldEditText f : mEditFields) {
                updateField(f);
            }
            try {
                // Save deck to model
                mEditorNote.model().put("did", mCurrentDid);
                // Save tags to model
                mEditorNote.setTagsFromStr(tagsAsString(mSelectedTags));
                JSONArray ja = new JSONArray();
                for (String t : mSelectedTags) {
                    ja.put(t);
                }
                mCol.getModels().current().put("tags", ja);
                mCol.getModels().setChanged();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ADD_FACT, mSaveFactHandler, new DeckTask.TaskData(mEditorNote));
        } else {
            boolean modified = false;
            // changed did? this has to be done first as remFromDyn() involves a direct write to the database
            if (mCurrentEditedCard.getDid() != mCurrentDid) {
                // remove card from filtered deck first (if relevant)
                AnkiDroidApp.getCol().getSched().remFromDyn(new long[] { mCurrentEditedCard.getId() });
                // refresh the card object to reflect the database changes in remFromDyn()
                mCurrentEditedCard.load();
                // also reload the note object
                mEditorNote = mCurrentEditedCard.note();
                // then set the card ID to the new deck
                mCurrentEditedCard.setDid(mCurrentDid);
                modified = true;
            }
            // now load any changes to the fields from the form
            for (FieldEditText f : mEditFields) {
                modified = modified | updateField(f);
            }
            // added tag?
            for (String t : mSelectedTags) {
                modified = modified || !mEditorNote.hasTag(t);
            }
            // removed tag?
            modified = modified || mEditorNote.getTags().size() > mSelectedTags.size();
            if (modified) {
                mEditorNote.setTagsFromStr(tagsAsString(mSelectedTags));
                // set a flag so that changes to card object will be written to DB later via onActivityResult() in
                // CardBrowser
                mChanged = true;
            }
            closeCardEditor();
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
        getMenuInflater().inflate(R.menu.card_editor, menu);
        if (!mAddNote) {
            menu.findItem(R.id.action_add_card_from_card_editor).setVisible(true);
            menu.findItem(R.id.action_reset_card_progress).setVisible(true);
            menu.findItem(R.id.action_preview).setVisible(true);
        }
        if (mEditFields != null) {
            for (int i = 0; i < mEditFields.size(); i++) {
                if (mEditFields.get(i).getText().length() > 0) {
                    menu.findItem(R.id.action_copy_card).setEnabled(true);
                    break;
                } else if (i == mEditFields.size() - 1) {
                    menu.findItem(R.id.action_copy_card).setEnabled(false);
                }
            }
        }
        if (mCaller != CALLER_CARDEDITOR_INTENT_ADD) {
            updateIntentInformation();
            menu.findItem(R.id.action_saved_notes).setEnabled(!mIntentInformation.isEmpty());
        }
        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onMenuOpened(int feature, Menu menu) {
        AnkiDroidApp.getCompat().invalidateOptionsMenu(this);
        return super.onMenuOpened(feature, menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case android.R.id.home:
                closeCardEditor(AnkiDroidApp.RESULT_TO_HOME);
                return true;

            case R.id.action_preview:
                openReviewer();
                return true;

            case R.id.action_add_card_from_card_editor:
            case R.id.action_copy_card:
                Intent intent = new Intent(CardEditor.this, CardEditor.class);
                intent.putExtra(EXTRA_CALLER, CALLER_CARDEDITOR);
                // intent.putExtra(EXTRA_DECKPATH, mDeckPath);
                if (item.getItemId() == R.id.action_copy_card) {
                    intent.putExtra(EXTRA_CONTENTS, getFieldsText());
                }
                startActivityForResult(intent, REQUEST_ADD);
                ActivityTransitionAnimation.slide(CardEditor.this, ActivityTransitionAnimation.LEFT);
                return true;

            case R.id.action_reset_card_progress:
                showDialog(DIALOG_RESET_CARD);
                return true;

            case R.id.action_saved_notes:
                showDialog(DIALOG_INTENT_INFORMATION);
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }
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
            ActivityTransitionAnimation.slide(CardEditor.this, ActivityTransitionAnimation.FADE);
        } else if (mCaller == CALLER_INDICLASH) {
            ActivityTransitionAnimation.slide(CardEditor.this, ActivityTransitionAnimation.NONE);
        } else {
            ActivityTransitionAnimation.slide(CardEditor.this, ActivityTransitionAnimation.RIGHT);
        }
    }


    private DialogFragment showDialogFragment(int id) {
        DialogFragment dialogFragment = null;
        String tag = null;
        switch (id) {
            case DIALOG_TAGS_SELECT:
                if (mSelectedTags == null) {
                    mSelectedTags = new ArrayList<String>();
                }
                ArrayList<String> tags = new ArrayList<String>(mCol.getTags().all());
                ArrayList<String> selTags = new ArrayList<String>(mSelectedTags);
                TagsDialog dialog = com.ichi2.anki.dialogs.TagsDialog.newInstance(TagsDialog.TYPE_ADD_TAG, selTags,
                        tags);
                dialog.setTagsDialogListener(new TagsDialogListener() {
                    @Override
                    public void onPositive(List<String> selectedTags, int option) {
                        mSelectedTags = selectedTags;
                        updateTags();
                    }
                });
                dialogFragment = dialog;
        }
        dialogFragment.show(getSupportFragmentManager(), tag);
        return dialogFragment;
    }


    @Override
    protected Dialog onCreateDialog(int id) {
        StyledDialog dialog = null;
        Resources res = getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(this);

        switch (id) {
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
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        long newId = dialogDeckIds.get(item);
                        if (mCurrentDid != newId) {
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
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        long oldModelId;
                        try {
                            oldModelId = mCol.getModels().current().getLong("id");
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        long newId = dialogIds.get(item);
                        if (oldModelId != newId) {
                            JSONObject model = mCol.getModels().get(newId);
                            mCol.getModels().setCurrent(model);
                            JSONObject cdeck = mCol.getDecks().current();
                            try {
                                cdeck.put("mid", newId);
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            mCol.getDecks().save(cdeck);
                            // Update deck
                            if (!mCol.getConf().optBoolean("addToCur", true)) {
                                try {
                                    mCurrentDid = model.getLong("did");
                                    updateDeck();
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            // Reset edit fields
                            int size = mEditFields.size();
                            String[] oldValues = new String[size];
                            for (int i = 0; i < size; i++) {
                                oldValues[i] = mEditFields.get(i).getText().toString();
                            }
                            setNote();
                            resetEditFields(oldValues);
                            duplicateCheck();
                        }
                    }
                });
                dialog = builder.create();
                break;

            case DIALOG_RESET_CARD:
                builder.setTitle(res.getString(R.string.reset_card_dialog_title));
                builder.setMessage(res.getString(R.string.reset_card_dialog_message));
                builder.setPositiveButton(res.getString(R.string.dialog_positive_reset), new OnClickListener() {
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
                        // R.string.reset_card_dialog_acknowledge), true);
                    }
                });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                builder.setCancelable(true);
                dialog = builder.create();
                break;

            case DIALOG_INTENT_INFORMATION:
                dialog = createDialogIntentInformation(builder, res);
                break;
        }

        return dialog;
    }


    private StyledDialog createDialogIntentInformation(Builder builder, Resources res) {
        builder.setTitle(res.getString(R.string.intent_add_saved_information));
        ListView listView = new ListView(this);

        mIntentInformationAdapter = new SimpleAdapter(this, mIntentInformation, R.layout.add_intent_item, new String[] {
                "source", "target", "id" }, new int[] { R.id.source_app, R.id.card_content, R.id.id });
        listView.setAdapter(mIntentInformationAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(CardEditor.this, CardEditor.class);
                intent.putExtra(EXTRA_CALLER, CALLER_CARDEDITOR_INTENT_ADD);
                Map<String, String> map = mIntentInformation.get(position);
                intent.putExtra(EXTRA_CONTENTS, map.get("fields"));
                intent.putExtra(EXTRA_ID, map.get("id"));
                startActivityForResult(intent, REQUEST_INTENT_ADD);
                ActivityTransitionAnimation.slide(CardEditor.this, ActivityTransitionAnimation.FADE);
                mIntentInformationDialog.dismiss();
            }
        });
        mCardItemBackground = Themes.getCardBrowserBackground()[0];
        mIntentInformationAdapter.setViewBinder(new SimpleAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Object arg1, String text) {
                if (view.getId() == R.id.add_intent_item) {
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
                updateIntentInformation();
                dialog.dismiss();
            }
        });
        mIntentInformationDialog = builder.create();
        return mIntentInformationDialog;
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
                    if (id != null && MetaDB.removeIntentInformation(CardEditor.this, id)) {
                        updateIntentInformation();
                    }
                }
                if (!mIntentInformation.isEmpty()) {
                    showDialog(DIALOG_INTENT_INFORMATION);
                }
                break;
            case REQUEST_ADD:
                if (resultCode != RESULT_CANCELED) {
                    mChanged = true;
                }
                break;
            case REQUEST_MULTIMEDIA_EDIT:
                if (resultCode != RESULT_CANCELED) {
                    Bundle extras = data.getExtras();
                    int index = extras.getInt(EditFieldActivity.EXTRA_RESULT_FIELD_INDEX);
                    IField field = (IField) extras.get(EditFieldActivity.EXTRA_RESULT_FIELD);
                    IMultimediaEditableNote mNote = NoteService.createEmptyNote(mEditorNote.model());
                    NoteService.updateMultimediaNoteFromJsonNote(mEditorNote, mNote);
                    mNote.setField(index, field);
                    mEditFields.get(index).setText(field.getFormattedValue());
                    NoteService.saveMedia((MultimediaEditableNote) mNote);
                    mChanged = true;
                }
                break;
        }
    }


    /**
     * Reads the saved data from the {@link MetaDB} and updates the data of the according {@link ListView}.
     */
    private void updateIntentInformation() {
        mIntentInformation.clear();
        mIntentInformation.addAll(MetaDB.getIntentInformation(this));
        Log.d(AnkiDroidApp.TAG, "Saved data list size: " + mIntentInformation.size());
        if (mIntentInformationAdapter != null) {
            mIntentInformationAdapter.notifyDataSetChanged();
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
            View editline_view = getLayoutInflater().inflate(R.layout.card_multimedia_editline, null);
            FieldEditText newTextbox = (FieldEditText) editline_view.findViewById(R.id.id_note_editText);

            initFieldEditText(newTextbox, i, fields[i], mCustomTypeface);

            TextView label = newTextbox.getLabel();
            label.setTextColor(Color.BLACK);
            label.setPadding((int) UIUtils.getDensityAdjustedValue(this, 3.4f), 0, 0, 0);
            mEditFields.add(newTextbox);

            ImageButton mediaButton = (ImageButton) editline_view.findViewById(R.id.id_media_button);
            setMMButtonListener(mediaButton, i);

            mFieldsLayoutContainer.addView(label);
            mFieldsLayoutContainer.addView(editline_view);
        }
    }


    private void setMMButtonListener(ImageButton mediaButton, final int index) {
        mediaButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mEditorNote.items()[index][1].length() > 0) {
                    // If the field already exists then we start the field editor, which figures out the type
                    // automatically
                    IMultimediaEditableNote mNote = NoteService.createEmptyNote(mEditorNote.model());
                    NoteService.updateMultimediaNoteFromJsonNote(mEditorNote, mNote);
                    IField field = mNote.getField(index);
                    startMultimediaFieldEditor(index, mNote, field);
                } else {
                    // Otherwise we make a popup menu allowing the user to choose between audio/image/text field
                    PopupMenuWithIcons popup = new PopupMenuWithIcons(CardEditor.this, v, true);
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.popupmenu_multimedia_options, popup.getMenu());
                    popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            IMultimediaEditableNote mNote = NoteService.createEmptyNote(mEditorNote.model());
                            NoteService.updateMultimediaNoteFromJsonNote(mEditorNote, mNote);
                            IField field = mNote.getField(index);
                            switch (item.getItemId()) {
                                case R.id.menu_multimedia_audio:
                                    field = new AudioField();
                                    mNote.setField(index, field);
                                    startMultimediaFieldEditor(index, mNote, field);
                                    return true;
                                case R.id.menu_multimedia_photo:
                                    field = new ImageField();
                                    mNote.setField(index, field);
                                    startMultimediaFieldEditor(index, mNote, field);
                                    return true;
                                case R.id.menu_multimedia_text:
                                    startMultimediaFieldEditor(index, mNote, field);
                                    return true;
                                default:
                                    return false;
                            }
                        }
                    });
                    popup.show();
                }
            }
        });
    }


    private void startMultimediaFieldEditor(final int index, IMultimediaEditableNote mNote, IField field) {
        Intent editCard = new Intent(CardEditor.this, EditFieldActivity.class);
        editCard.putExtra(EXTRA_FIELD_INDEX, index);
        editCard.putExtra(EXTRA_FIELD, field);
        editCard.putExtra(EXTRA_WHOLE_NOTE, mNote);
        startActivityForResult(editCard, REQUEST_MULTIMEDIA_EDIT);
    }


    private void initFieldEditText(FieldEditText editText, int index, String[] values, Typeface customTypeface) {
        String name = values[0];
        String content = values[1];
        if (mPrefFixArabic) {
            content = ArabicUtilities.reshapeSentence(content);
        }
        editText.init(index, name, content);

        if (customTypeface != null) {
            editText.setTypeface(customTypeface);
        }

        // Listen for changes in the first field so we can re-check duplicate status.
        if (index == 0) {
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable arg0) {
                    duplicateCheck();
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


    private boolean duplicateCheck() {
        boolean isDupe;
        FieldEditText field = mEditFields.get(0);
        // Keep copy of current internal value for this field.
        String oldValue = mEditorNote.getFields()[0];
        // Update the field in the Note so we can run a dupe check on it.
        updateField(field);
        // 1 is empty, 2 is dupe, null is neither.
        Integer dupeCode = mEditorNote.dupeOrEmpty();
        if (dupeCode != null && dupeCode == 2) {
            field.setBackgroundResource(R.drawable.white_edit_text_dupe);
            isDupe = true;
        } else {
            field.setBackgroundResource(R.drawable.white_edit_text);
            isDupe = false;
        }
        // Put back the old value so we don't interfere with modification detection
        mEditorNote.values()[0] = oldValue;
        return isDupe;
    }


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
                JSONObject conf = mCol.getConf();
                JSONObject model = mCol.getModels().current();
                if (conf.optBoolean("addToCur", true)) {
                    mCurrentDid = conf.getLong("curDeck");
                    if (mCol.getDecks().isDyn(mCurrentDid)) {
                        /*
                         * If the deck in mCurrentDid is a filtered (dynamic) deck, then we can't create cards in it,
                         * and we set mCurrentDid to the Default deck. Otherwise, we keep the number that had been
                         * selected previously in the activity.
                         */
                        mCurrentDid = 1;
                    }
                } else {
                    mCurrentDid = model.getLong("did");
                }
                mEditorNote = new Note(mCol, model);
                mEditorNote.model().put("did", mCurrentDid);
                mModelButton.setText(getResources().getString(R.string.CardEditorModel, model.getString("name")));
            } else {
                mEditorNote = note;
                mCurrentDid = mCurrentEditedCard.getDid();
            }
            if (mSelectedTags == null) {
                mSelectedTags = mEditorNote.getTags();
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        updateDeck();
        updateTags();
        populateEditFields();
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
        if (mSelectedTags == null) {
            mSelectedTags = new ArrayList<String>();
        }
        mTagsButton.setText(getResources().getString(R.string.CardEditorTags,
                mCol.getTags().join(mCol.getTags().canonify(mSelectedTags)).trim().replace(" ", ", ")));
    }


    private boolean updateField(FieldEditText field) {
        String newValue = field.getText().toString().replace(FieldEditText.NEW_LINE, "<br>");
        if (!mEditorNote.values()[field.getOrd()].equals(newValue)) {
            mEditorNote.values()[field.getOrd()] = newValue;
            return true;
        }
        return false;
    }


    private String tagsAsString(List<String> tags) {
        return TextUtils.join(" ", tags);
    }

    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------

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
