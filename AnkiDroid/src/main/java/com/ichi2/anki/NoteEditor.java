/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
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
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.PopupMenu.OnMenuItemClickListener;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.dialogs.ConfirmationDialog;
import com.ichi2.anki.dialogs.TagsDialog;
import com.ichi2.anki.dialogs.TagsDialog.TagsDialogListener;
import com.ichi2.anki.exception.ConfirmModSchemaException;
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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Allows the user to edit a fact, for instance if there is a typo. A card is a presentation of a fact, and has two
 * sides: a question and an answer. Any number of fields can appear on each side. When you add a fact to Anki, cards
 * which show that fact are generated. Some models generate one card, others generate more than one.
 * 
 * @see <a href="http://ankisrs.net/docs/manual.html#cards">the Anki Desktop manual</a>
 */
public class NoteEditor extends AnkiActivity {

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

    //private static final int DIALOG_DECK_SELECT = 0;
    //private static final int DIALOG_MODEL_SELECT = 1;
    //private static final int DIALOG_TAGS_SELECT = 2;
    private static final int DIALOG_RESET_CARD = 3;
    private static final int DIALOG_INTENT_INFORMATION = 4;
    private static final int DIALOG_RESCHEDULE_CARD = 5;

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
    public static final int REQUEST_TEMPLATE_EDIT = 3;

    private boolean mChanged = false;
    private boolean mFieldEdited = false;
    private boolean mRescheduled = false;


    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private LinearLayout mFieldsLayoutContainer;

    private TextView mTagsButton;
    private TextView mCardsButton;
    private Spinner mNoteTypeSpinner;
    private Spinner mNoteDeckSpinner;

    private Note mEditorNote;
    public static Card mCurrentEditedCard;
    private List<String> mSelectedTags;
    private long mCurrentDid;
    private ArrayList<Long> mAllDeckIds;
    private ArrayList<Long> mAllModelIds;
    private Map<Integer, Integer> mModelChangeFieldMap;
    private Map<Integer, Integer> mModelChangeCardMap;

    /* indicates if a new fact is added or a card is edited */
    private boolean mAddNote;

    private boolean mAedictIntent;

    /* indicates which activity called Note Editor */
    private int mCaller;

    private LinkedList<FieldEditText> mEditFields;

    private int mCardItemBackground;
    private final List<Map<String, String>> mIntentInformation = new ArrayList<Map<String, String>>();
    private SimpleAdapter mIntentInformationAdapter;
    private StyledDialog mIntentInformationDialog;

    private StyledProgressDialog mProgressDialog;

    private String[] mSourceText;


    private boolean mPrefFixArabic;

    private DeckTask.TaskListener mSaveFactHandler = new DeckTask.TaskListener() {
        private boolean mCloseAfter = false;
        private Intent mIntent;


        @Override
        public void onPreExecute() {
            Resources res = getResources();
            mProgressDialog = StyledProgressDialog
                    .show(NoteEditor.this, "", res.getString(R.string.saving_facts), true);
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
                Note oldNote = mEditorNote.clone();
                setNote();
                // Respect "Remember last input when adding" field option.
                JSONArray flds;
                try {
                    flds = mEditorNote.model().getJSONArray("flds");
                    if (oldNote != null) {
                        for (int fldIdx = 0; fldIdx < flds.length(); fldIdx++) {
                            if (flds.getJSONObject(fldIdx).getBoolean("sticky")) {
                                mEditFields.get(fldIdx).setText(oldNote.getFields()[fldIdx]);
                            }
                        }
                    }
                } catch (JSONException e) {
                    throw new RuntimeException();
                }
                Themes.showThemedToast(NoteEditor.this,
                        getResources().getQuantityString(R.plurals.factadder_cards_added, count, count), true);
            } else {
                Themes.showThemedToast(NoteEditor.this, getResources().getString(R.string.factadder_saving_error), true);
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
                        Log.e(AnkiDroidApp.TAG, "Note Editor: Error on dismissing progress dialog: " + e);
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
                        Log.e(AnkiDroidApp.TAG, "Note Editor: Error on dismissing progress dialog: " + e);
                    }
                }
                if (mCloseAfter) {
                    if (mIntent != null) {
                        closeNoteEditor(mIntent);
                    } else {
                        closeNoteEditor();
                    }
                } else {
                    // Reset check for changes to fields
                    mFieldEdited = false;
                }
            } else {
                // RuntimeException occured on adding note
                closeNoteEditor(DeckPicker.RESULT_DB_ERROR);
            }
        }


        @Override
        public void onCancelled() {
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

        startLoadingCollection();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }


    // Finish initializing the activity after the collection has been correctly loaded
    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        Intent intent = getIntent();
        Log.i(AnkiDroidApp.TAG, "CardEditor: caller: " + mCaller);

        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());

        if (mCaller == CALLER_INDICLASH && preferences.getBoolean("intentAdditionInstantAdd", false)) {
            // save information without showing Note Editor
            fetchIntentInformation(intent);
            MetaDB.saveIntentInformation(NoteEditor.this, Utils.joinFields(mSourceText));
            Themes.showThemedToast(NoteEditor.this, getResources().getString(R.string.app_name) + ": "
                    + getResources().getString(R.string.CardEditorLaterMessage), false);
            finishWithoutAnimation();
            return;
        }

        registerExternalStorageListener();
        View mainView = getLayoutInflater().inflate(R.layout.note_editor, null);
        setContentView(mainView);
        Themes.setWallpaper(mainView);
        Themes.setContentStyle(mainView, Themes.CALLER_CARD_EDITOR);

        mFieldsLayoutContainer = (LinearLayout) findViewById(R.id.CardEditorEditFieldsLayout);

        mTagsButton = (TextView) findViewById(R.id.CardEditorTagText);
        mCardsButton = (TextView) findViewById(R.id.CardEditorCardsText);
        mCardsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCardTemplateEditor();
            }
        });

        Preferences.COMING_FROM_ADD = false;

        mAedictIntent = false;

        switch (mCaller) {
            case CALLER_NOCALLER:
                Log.i(AnkiDroidApp.TAG, "CardEditor: no caller could be identified, closing");
                finishWithoutAnimation();
                return;

            case CALLER_REVIEWER:
                mCurrentEditedCard = AbstractFlashcardViewer.getEditorCard();
                if (mCurrentEditedCard == null) {
                    finishWithoutAnimation();
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
                    finishWithoutAnimation();
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
                    finishWithoutAnimation();
                    return;
                }
                if (mSourceText[0].equals("Aedict Notepad") && addFromAedict(mSourceText[1])) {
                    finishWithoutAnimation();
                    return;
                }
                mAddNote = true;
                break;
        }

        // Note type Selector
        mNoteTypeSpinner = (Spinner) findViewById(R.id.note_type_spinner);    
        mAllModelIds = new ArrayList<Long>();
        final ArrayList<String> modelNames = new ArrayList<String>();
        ArrayList<JSONObject> models = getCol().getModels().all();
        Collections.sort(models, new JSONNameComparator());
        for (JSONObject m : models) {
            try {
                modelNames.add(m.getString("name"));
                mAllModelIds.add(m.getLong("id"));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        ArrayAdapter<String> noteTypeAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, modelNames);
        mNoteTypeSpinner.setAdapter(noteTypeAdapter);
        noteTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);


        // Deck Selector
        TextView deckTextView = (TextView) findViewById(R.id.CardEditorDeckText);
        // If edit mode and more than one card template distinguish between "Deck" and "Card deck"
        try {
            if (!mAddNote && mEditorNote.model().getJSONArray("tmpls").length()>1) {
                deckTextView.setText(R.string.CardEditorCardDeck);
            }
        } catch (JSONException e1) {
            throw new RuntimeException();
        }
        mNoteDeckSpinner = (Spinner) findViewById(R.id.note_deck_spinner);    
        mAllDeckIds = new ArrayList<Long>();
        final ArrayList<String> deckNames = new ArrayList<String>();

        ArrayList<JSONObject> decks = getCol().getDecks().all();
        Collections.sort(decks, new JSONNameComparator());
        for (JSONObject d : decks) {
            try {
                // add current deck and all other non-filtered decks to deck list
                long thisDid = d.getLong("id");
                long currentDid = getCol().getDecks().current().getLong("id");
                if (d.getInt("dyn") == 0 || (!mAddNote && thisDid == currentDid)) {
                    deckNames.add(d.getString("name"));
                    mAllDeckIds.add(thisDid);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        ArrayAdapter<String> noteDeckAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, deckNames);
        mNoteDeckSpinner.setAdapter(noteDeckAdapter);
        noteDeckAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mNoteDeckSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                // Log.i(AnkiDroidApp.TAG, "onItemSelected() fired on mNoteDeckSpinner with pos = "+Integer.toString(pos)); 
                mCurrentDid = mAllDeckIds.get(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do Nothing
            }
        });

        setNote(mEditorNote);
        
        // Set current note type and deck positions in spinners
        int position;
        try {
            position = mAllModelIds.indexOf(mEditorNote.model().getLong("id"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        // set selection without firing selectionChanged event
        // nb: setOnItemSelectedListener needs to occur after this
        mNoteTypeSpinner.setSelection(position, false);

        if (mAddNote) {
            mNoteTypeSpinner.setOnItemSelectedListener(new SetNoteTypeListener());
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
        } else {
            mNoteTypeSpinner.setOnItemSelectedListener(new EditNoteTypeListener());
            setTitle(R.string.cardeditor_title_edit_card);
        }


        mPrefFixArabic = preferences.getBoolean("fixArabicText", false);

        ((LinearLayout) findViewById(R.id.CardEditorTagButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTagsDialog();
            }
        });
       
        // Close collection opening dialog if needed
        dismissOpeningCollectionDialog();
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
                            Themes.showThemedToast(NoteEditor.this,
                                    getResources().getString(R.string.intent_aedict_empty), false);
                            return true;
                        }
                    } else {
                        Themes.showThemedToast(NoteEditor.this, getResources().getString(R.string.intent_aedict_empty),
                                false);
                        return true;
                    }
                    return false;
                }
            }
        }
        Themes.showThemedToast(NoteEditor.this, getResources().getString(R.string.intent_aedict_category), false);
        return true;
    }


    private void resetEditFields(String[] content) {
        for (int i = 0; i < Math.min(content.length, mEditFields.size()); i++) {
            mEditFields.get(i).setText(content[i]);
        }
    }


    private boolean hasUnsavedChanges() {
        // changed note type?
        if (!mAddNote) {
            final JSONObject newModel = getCurrentlySelectedModel();
            final JSONObject oldModel = mCurrentEditedCard.model();
            if (!newModel.equals(oldModel)) {
                return true;
            }
        }
        // changed deck?
        if (!mAddNote && mCurrentEditedCard!= null && mCurrentEditedCard.getDid() != mCurrentDid) {
            return true;
        }
        // changed fields?
        if (mFieldEdited) {
            return true;
        }
        // added tag?
        for (String t : mSelectedTags) {
            if (!mEditorNote.hasTag(t)) {
                return true;
            }
        }
        // removed tag?
        if (mEditorNote.getTags().size() > mSelectedTags.size()) {
            return true;
        }
        return false;
    }


    private void saveNote() {
        final Resources res = getResources();
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
                getCol().getModels().current().put("tags", ja);
                getCol().getModels().setChanged();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ADD_FACT, mSaveFactHandler, new DeckTask.TaskData(mEditorNote));
        } else {
            // Check whether note type has been changed
            final JSONObject newModel = getCurrentlySelectedModel();
            final JSONObject oldModel = mCurrentEditedCard.model();
            if (!newModel.equals(oldModel)) {
                if (mModelChangeCardMap.size() < mEditorNote.cards().size() || mModelChangeCardMap.containsKey(null)) {
                    // If cards will be lost via the new mapping then show a confirmation dialog before proceeding with the change
                    ConfirmationDialog dialog = new ConfirmationDialog () {
                        @Override
                        public void confirm() {
                            // Bypass the check once the user confirms
                            changeNoteTypeWithErrorHandling(oldModel, newModel);
                        }
                    };
                    dialog.setArgs(res.getString(R.string.confirm_map_cards_to_nothing));
                    showDialogFragment(dialog);                    
                } else {
                    // Otherwise go straight to changing note type
                    changeNoteTypeWithErrorHandling(oldModel, newModel);
                }
                return;
            }
            // Regular changes in note content
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
            closeNoteEditor();
        }
    }

    /**
     * Change the note type from oldModel to newModel, handling the case where a full sync will be required
     * @param oldModel
     * @param newModel
     */
    private void changeNoteTypeWithErrorHandling(final JSONObject oldModel, final JSONObject newModel) {
        Resources res = getResources();
        try {
            changeNoteType(oldModel, newModel);
        } catch (ConfirmModSchemaException e) {
            // Libanki has determined we should ask the user to confirm first
            ConfirmationDialog dialog = new ConfirmationDialog() {
                @Override
                public void confirm() {
                    // Bypass the check once the user confirms
                    getCol().modSchemaNoCheck();
                    try {
                        changeNoteType(oldModel, newModel);
                    } catch (ConfirmModSchemaException e2) {
                        // This should never be reached as we explicitly called modSchemaNoCheck()
                        throw new RuntimeException(e2);
                    }
                }
            };
            dialog.setArgs(res.getString(R.string.full_sync_confirmation));
            showDialogFragment(dialog);
        }
    }

    /**
     * Change the note type from oldModel to newModel, throwing ConfirmModSchemaException if a full sync will be required
     * @param oldModel
     * @param newModel
     * @throws ConfirmModSchemaException
     */
    private void changeNoteType(JSONObject oldModel, JSONObject newModel) throws ConfirmModSchemaException {
        final long [] nids = {mEditorNote.getId()};
        getCol().getModels().change(oldModel, nids, newModel, mModelChangeFieldMap, mModelChangeCardMap);
        // refresh the note & card objects to reflect the database changes
        mCurrentEditedCard.load();
        mEditorNote = mCurrentEditedCard.note();
        // close note editor
        closeNoteEditor();
    }


    @Override
    public void onBackPressed() {
        Log.i(AnkiDroidApp.TAG, "CardEditor - onBackPressed()");
        closeCardEditorWithCheck();
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
        getMenuInflater().inflate(R.menu.note_editor, menu);
        if (mAddNote) {
            menu.findItem(R.id.action_later).setVisible(true);
            menu.findItem(R.id.action_copy_card).setVisible(false);
        } else {
            menu.findItem(R.id.action_saved_notes).setVisible(false);
            menu.findItem(R.id.action_add_card_from_card_editor).setVisible(true);
            menu.findItem(R.id.action_reset_card_progress).setVisible(true);
            menu.findItem(R.id.action_reschedule_card).setVisible(true);
            menu.findItem(R.id.action_reset_card_progress).setVisible(true);
            // if Arabic reshaping is enabled, disable the Save button to avoid
            // saving the reshaped string to the deck
            if (mPrefFixArabic) {
                menu.findItem(R.id.action_save).setEnabled(false);
            }
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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case android.R.id.home:
                closeCardEditorWithCheck();
                return true;

            case R.id.action_save:
                saveNote();
                return true;

            case R.id.action_later:
                String content = getFieldsText();
                if (content.length() > mEditFields.size() - 1) {
                    MetaDB.saveIntentInformation(NoteEditor.this, content);
                    populateEditFields();
                    mSourceText = null;
                    Themes.showThemedToast(NoteEditor.this,
                            getResources().getString(R.string.CardEditorLaterMessage), false);
                }
                if (mCaller == CALLER_INDICLASH || mCaller == CALLER_CARDEDITOR_INTENT_ADD) {
                    closeNoteEditor();
                }
                return true;

            case R.id.action_add_card_from_card_editor:
            case R.id.action_copy_card:
                Intent intent = new Intent(NoteEditor.this, NoteEditor.class);
                intent.putExtra(EXTRA_CALLER, CALLER_CARDEDITOR);
                // intent.putExtra(EXTRA_DECKPATH, mDeckPath);
                if (item.getItemId() == R.id.action_copy_card) {
                    intent.putExtra(EXTRA_CONTENTS, getFieldsText());
                }
                startActivityForResultWithAnimation(intent, REQUEST_ADD, ActivityTransitionAnimation.LEFT);
                return true;

            case R.id.action_reset_card_progress:
                showDialog(DIALOG_RESET_CARD);
                return true;

            case R.id.action_saved_notes:
                showDialog(DIALOG_INTENT_INFORMATION);
                return true;

            case R.id.action_reschedule_card:
                showDialog(DIALOG_RESCHEDULE_CARD);
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
                        finishWithoutAnimation();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(SdCardReceiver.MEDIA_EJECT);
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }


    private void closeCardEditorWithCheck() {
        if (hasUnsavedChanges()) {
           showDiscardChangesDialog();
        } else {
            closeNoteEditor();
        }
    }


    private void showDiscardChangesDialog() {
        Dialog dialog;
        Resources res = getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(this);
        builder.setMessage(R.string.discard_unsaved_changes);
        builder.setPositiveButton(res.getString(R.string.dialog_ok),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        closeNoteEditor();
                    }
                });
        builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
        dialog = builder.create();
        dialog.show();
    }


    private void closeNoteEditor() {
        closeNoteEditor(null);
    }


    private void closeNoteEditor(Intent intent) {
        int result;
        if (mChanged) {
            result = RESULT_OK;
        } else {
            result = RESULT_CANCELED;
        }
        if (mRescheduled) {
            if (intent == null) {
                intent = new Intent();
            }
            intent.putExtra("rescheduled", true);
        }
        closeNoteEditor(result, intent);
    }


    private void closeNoteEditor(int result) {
        closeNoteEditor(result, null);
    }


    private void closeNoteEditor(int result, Intent intent) {
        if (intent != null) {
            setResult(result, intent);
        } else {
            setResult(result);
        }

        if (mCaller == CALLER_CARDEDITOR_INTENT_ADD || mCaller == CALLER_BIGWIDGET_EDIT
                || mCaller == CALLER_BIGWIDGET_ADD) {
            finishWithAnimation(ActivityTransitionAnimation.FADE);
        } else if (mCaller == CALLER_INDICLASH) {
            finishWithAnimation(ActivityTransitionAnimation.NONE);
        } else {
            finishWithAnimation(ActivityTransitionAnimation.RIGHT);
        }
    }


    private void showTagsDialog() {
        if (mSelectedTags == null) {
            mSelectedTags = new ArrayList<String>();
        }
        ArrayList<String> tags = new ArrayList<String>(getCol().getTags().all());
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
        showDialogFragment(dialog);
    }

    private void showCardTemplateEditor() {
        Intent intent = new Intent(this, CardTemplateEditor.class);
        // Pass the model ID
        try {
            intent.putExtra("modelId", getCurrentlySelectedModel().getLong("id"));
        } catch (JSONException e) {
           throw new RuntimeException(e);
        }
        // Also pass the card ID if not adding new note
        if (!mAddNote) {
            intent.putExtra("cardId", mCurrentEditedCard.getId());
        }
        startActivityForResultWithAnimation(intent, REQUEST_TEMPLATE_EDIT, ActivityTransitionAnimation.LEFT);
    }


    @Override
    protected Dialog onCreateDialog(int id) {
        StyledDialog dialog = null;
        Resources res = getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(this);

        switch (id) {
            case DIALOG_RESET_CARD:
                builder.setTitle(res.getString(R.string.reset_card_dialog_title));
                builder.setMessage(res.getString(R.string.reset_card_dialog_message));
                builder.setPositiveButton(res.getString(R.string.dialog_positive_reset), new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        getCol().getSched().forgetCards(new long[] { mCurrentEditedCard.getId() });
                        getCol().reset();
                        mRescheduled = true;
                        Themes.showThemedToast(NoteEditor.this,
                                getResources().getString(R.string.reset_card_dialog_acknowledge), true);
                    }
                });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                builder.setCancelable(true);
                dialog = builder.create();
                break;
            case DIALOG_RESCHEDULE_CARD:
                final EditText rescheduleEditText;
                rescheduleEditText = (EditText) new EditText(this);
                rescheduleEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
                rescheduleEditText.setText("0");
                rescheduleEditText.selectAll();
                builder.setTitle(res.getString(R.string.reschedule_card_dialog_title));
                builder.setMessage(res.getString(R.string.reschedule_card_dialog_message));
                builder.setPositiveButton(res.getString(R.string.dialog_ok), new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int days = Integer.parseInt(((EditText) rescheduleEditText).getText().toString());
                        getCol().getSched().reschedCards(new long[] { mCurrentEditedCard.getId() }, days, days);
                        getCol().reset();
                        mRescheduled = true;
                        Themes.showThemedToast(NoteEditor.this,
                                getResources().getString(R.string.reschedule_card_dialog_acknowledge), true);
                    }
                });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                builder.setCancelable(true);
                FrameLayout frame = new FrameLayout(this);
                frame.addView(rescheduleEditText);
                builder.setView(frame, false, true);
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
                Intent intent = new Intent(NoteEditor.this, NoteEditor.class);
                intent.putExtra(EXTRA_CALLER, CALLER_CARDEDITOR_INTENT_ADD);
                Map<String, String> map = mIntentInformation.get(position);
                intent.putExtra(EXTRA_CONTENTS, map.get("fields"));
                intent.putExtra(EXTRA_ID, map.get("id"));
                startActivityForResultWithAnimation(intent, REQUEST_INTENT_ADD, ActivityTransitionAnimation.FADE);
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
                MetaDB.resetIntentInformation(NoteEditor.this);
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
            closeNoteEditor(DeckPicker.RESULT_DB_ERROR);
        }

        switch (requestCode) {
            case REQUEST_INTENT_ADD:
                if (resultCode != RESULT_CANCELED) {
                    mChanged = true;
                    String id = data.getStringExtra(EXTRA_ID);
                    if (id != null && MetaDB.removeIntentInformation(NoteEditor.this, id)) {
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
            case REQUEST_TEMPLATE_EDIT:
                updateCards(mEditorNote.model());
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
        String[][] fields = mEditorNote.items();
        populateEditFields(fields, false);
    }

    private void populateEditFields(String[][] fields, boolean editModelMode) {
        mFieldsLayoutContainer.removeAllViews();
        mEditFields = new LinkedList<FieldEditText>();

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

            initFieldEditText(newTextbox, i, fields[i], mCustomTypeface, !editModelMode);

            TextView label = newTextbox.getLabel();
            label.setTextColor(Color.BLACK);
            label.setPadding((int) UIUtils.getDensityAdjustedValue(this, 3.4f), 0, 0, 0);
            mEditFields.add(newTextbox);

            ImageButton mediaButton = (ImageButton) editline_view.findViewById(R.id.id_media_button);
            // Make the icon change between media icon and switch field icon depending on whether editing note type
            if (editModelMode && allowFieldRemapping()) {
                // Allow remapping if originally more than two fields
                mediaButton.setBackgroundResource(R.drawable.ic_action_import_export);
                setRemapButtonListener(mediaButton, i);
            } else if (editModelMode && !allowFieldRemapping()) {
                mediaButton.setBackgroundResource(0);
            } else {
                // Use media editor button if not changing note type
                mediaButton.setBackgroundResource(R.drawable.ic_media);
                setMMButtonListener(mediaButton, i);
            }
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
                    PopupMenuWithIcons popup = new PopupMenuWithIcons(NoteEditor.this, v, true);
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


    private void setRemapButtonListener(ImageButton remapButton, final int newFieldIndex) {
        remapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show list of fields from the original note which we can map to
                PopupMenu popup = new PopupMenu(NoteEditor.this, v);
                final String [][] items = mEditorNote.items();
                for (int i = 0; i < items.length; i++) {
                    popup.getMenu().add(Menu.NONE, i, Menu.NONE, items[i][0]);
                }
                // Add "nothing" at the end of the list
                popup.getMenu().add(Menu.NONE, items.length, Menu.NONE, R.string.nothing);
                popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        // Get menu item id
                        Integer idx = item.getItemId();
                        // Retrieve any existing mappings between newFieldIndex and idx
                        Integer previousMapping = getKeyByValue(mModelChangeFieldMap, newFieldIndex);
                        Integer mappingConflict = mModelChangeFieldMap.get(idx);
                        // Update the mapping depending on any conflicts
                        if (idx == items.length && previousMapping != null) {
                            // Remove the previous mapping if None selected
                            mModelChangeFieldMap.remove(previousMapping);
                        } else if (idx < items.length && mappingConflict != null && previousMapping != null && newFieldIndex != mappingConflict) {
                            // Swap the two mappings if there was a conflict and previous mapping
                            mModelChangeFieldMap.put(previousMapping, mappingConflict);
                            mModelChangeFieldMap.put(idx, newFieldIndex);
                        } else if (idx < items.length && mappingConflict != null) {
                            // Set the conflicting field to None if no previous mapping to swap into it
                            mModelChangeFieldMap.remove(previousMapping);
                            mModelChangeFieldMap.put(idx, newFieldIndex);
                        } else if (idx < items.length) {
                            // Can simply set the new mapping if no conflicts
                            mModelChangeFieldMap.put(idx, newFieldIndex);
                        }
                        // Reload the fields                     
                        updateFieldsFromMap(getCurrentlySelectedModel());
                        return true;
                    }
                });
                popup.show();
            }
        });
    }


    private void startMultimediaFieldEditor(final int index, IMultimediaEditableNote mNote, IField field) {
        Intent editCard = new Intent(NoteEditor.this, EditFieldActivity.class);
        editCard.putExtra(EditFieldActivity.EXTRA_FIELD_INDEX, index);
        editCard.putExtra(EditFieldActivity.EXTRA_FIELD, field);
        editCard.putExtra(EditFieldActivity.EXTRA_WHOLE_NOTE, mNote);
        startActivityForResultWithoutAnimation(editCard, REQUEST_MULTIMEDIA_EDIT);
    }


    private void initFieldEditText(FieldEditText editText, final int index, String[] values, Typeface customTypeface, boolean enabled) {
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
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable arg0) {
                mFieldEdited = true;
                if (index == 0) {
                    duplicateCheck();
                }
            }


            @Override
            public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
            }


            @Override
            public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
            }
        });
        editText.setEnabled(enabled);
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
                JSONObject conf = getCol().getConf();
                JSONObject model = getCol().getModels().current();
                if (conf.optBoolean("addToCur", true)) {
                    mCurrentDid = conf.getLong("curDeck");
                    if (getCol().getDecks().isDyn(mCurrentDid)) {
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
                mEditorNote = new Note(getCol(), model);
                mEditorNote.model().put("did", mCurrentDid);
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
        updateDeckPosition();
        updateTags();
        updateCards(mEditorNote.model());
        populateEditFields();
    }


    private void updateDeckPosition() {
        int position = mAllDeckIds.indexOf(mCurrentDid);
        if (position != -1) {
            mNoteDeckSpinner.setSelection(position, false);
        } else {
            Log.e(AnkiDroidApp.TAG, "updateDeckPosition() :: mCurrentDid="+Long.toString(mCurrentDid)+", " +
            		"position = "+Integer.toString(position));
        }
    }


    private void updateTags() {
        if (mSelectedTags == null) {
            mSelectedTags = new ArrayList<String>();
        }
        mTagsButton.setText(getResources().getString(R.string.CardEditorTags,
                getCol().getTags().join(getCol().getTags().canonify(mSelectedTags)).trim().replace(" ", ", ")));
    }


    /** Update the list of card templates for current note type */
    private void updateCards(JSONObject model) {
        try {
            JSONArray tmpls = model.getJSONArray("tmpls");
            String cardsList = "";
            // Build comma separated list of card names
            for (int i = 0; i < tmpls.length(); i++) {
                String name = tmpls.getJSONObject(i).optString("name");
                // If more than one card then make currently selected card underlined
                if (!mAddNote && tmpls.length() > 1 && model == mEditorNote.model() &&
                        mCurrentEditedCard.template().optString("name").equals(name)) {
                    name = "<u>" + name + "</u>";
                }
                cardsList += name;
                if (i < tmpls.length()-1) {
                    cardsList += ", ";
                }
            }
            // Make cards list red if the number of cards is being reduced
            if (!mAddNote && tmpls.length() < mEditorNote.model().getJSONArray("tmpls").length()) {
                cardsList = "<font color='red'>" + cardsList + "</font>";
            }
            mCardsButton.setText(Html.fromHtml(getResources().getString(R.string.CardEditorCards, cardsList)));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
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

    private JSONObject getCurrentlySelectedModel() {
        return getCol().getModels().get(mAllModelIds.get(mNoteTypeSpinner.getSelectedItemPosition()));
    }

    /**
     * Convenience method for getting the corresponding key given the value in a 1-to-1 map
     * @param map
     * @param value
     * @return
     */
    private <T, E> T getKeyByValue(Map<T, E> map, E value) {
        for (Entry<T, E> entry : map.entrySet()) {
            if (value.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Update all the field EditText views based on the currently selected note type and the mModelChangeFieldMap
     */
    private void updateFieldsFromMap(JSONObject newModel) {
        // Get the field map for new model and old fields list
        String [][] oldFields = mEditorNote.items();
        Map<String, Pair<Integer, JSONObject>> fMapNew = getCol().getModels().fieldMap(newModel);
        // Build array of label/values to provide to field EditText views
        String[][] fields = new String[fMapNew.size()][2];
        for (String fname : fMapNew.keySet()) {
            // Field index of new note type
            Integer i = fMapNew.get(fname).first;
            // Add values from old note type if they exist in map, otherwise make the new field empty
            if (mModelChangeFieldMap.containsValue(i)) {
                // Get index of field from old note type given the field index of new note type
                Integer j = getKeyByValue(mModelChangeFieldMap, i);
                // Set the new field label text
                if (allowFieldRemapping()) {
                    // Show the content of old field if remapping is enabled
                    fields[i][0] = String.format(getResources().getString(R.string.field_remapping), fname, oldFields[j][0]);
                } else {
                    fields[i][0] = fname;
                }

                // Set the new field label value
                fields[i][1] = oldFields[j][1];
            } else {
                // No values from old note type exist in the mapping
                fields[i][0] = fname;
                fields[i][1] = "";
            }
        }
        populateEditFields(fields, true);
        updateCards(newModel);
    }

    /**
     *
     * @return whether or not to allow remapping of fields for current model
     */
    private boolean allowFieldRemapping() {
        // Map<String, Pair<Integer, JSONObject>> fMapNew = getCol().getModels().fieldMap(getCurrentlySelectedModel())
        return mEditorNote.items().length > 2;
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


    private class SetNoteTypeListener implements OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            // If a new column was selected then change the key used to map from mCards to the column TextView
            //Log.i(AnkiDroidApp.TAG, "onItemSelected() fired on mNoteTypeSpinner");
            long oldModelId;
            try {
                oldModelId = getCol().getModels().current().getLong("id");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            long newId = mAllModelIds.get(pos);
            if (oldModelId != newId) {
                JSONObject model = getCol().getModels().get(newId);
                getCol().getModels().setCurrent(model);
                JSONObject cdeck = getCol().getDecks().current();
                try {
                    cdeck.put("mid", newId);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                getCol().getDecks().save(cdeck);
                // Update deck
                if (!getCol().getConf().optBoolean("addToCur", true)) {
                    try {
                        mCurrentDid = model.getLong("did");
                        updateDeckPosition();
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

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            // Do Nothing
        }
    }


    private class EditNoteTypeListener implements OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            // Get the current model
            long noteModelId;
            try {
                noteModelId = mCurrentEditedCard.model().getLong("id");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            // Get new model
            JSONObject newModel = getCol().getModels().get(mAllModelIds.get(pos));            
            // Configure the interface according to whether note type is getting changed or not
            if (mAllModelIds.get(pos) != noteModelId) {
                // Initialize mapping between fields of old model -> new model
                mModelChangeFieldMap = new HashMap<Integer, Integer>();
                for (int i=0; i < mEditorNote.items().length; i++) {
                    mModelChangeFieldMap.put(i, i);
                }
                // Initialize mapping between cards new model -> old model
                mModelChangeCardMap = new HashMap<Integer, Integer>();
                try {
                    for (int i=0; i < newModel.getJSONArray("tmpls").length() ; i++) {
                        if (i < mEditorNote.cards().size()) {
                            mModelChangeCardMap.put(i, i);
                        } else {
                            mModelChangeCardMap.put(i, null);
                        }
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                // Update the field text edits based on the default mapping just assigned
                updateFieldsFromMap(newModel);
                // Don't let the user change any other values at the same time as changing note type
                mSelectedTags = mEditorNote.getTags();
                updateTags();
                ((LinearLayout) findViewById(R.id.CardEditorTagButton)).setEnabled(false);
                //((LinearLayout) findViewById(R.id.CardEditorCardsButton)).setEnabled(false);
                mNoteDeckSpinner.setEnabled(false);
                int position = mAllDeckIds.indexOf(mCurrentEditedCard.getDid());
                if (position != -1) {
                    mNoteDeckSpinner.setSelection(position, false);
                }
            } else {
                populateEditFields();
                updateCards(mCurrentEditedCard.model());
                ((LinearLayout) findViewById(R.id.CardEditorTagButton)).setEnabled(true);
                //((LinearLayout) findViewById(R.id.CardEditorCardsButton)).setEnabled(false);
                mNoteDeckSpinner.setEnabled(true);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            // Do Nothing
        }
    }
}
