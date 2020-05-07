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

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;

import android.util.Pair;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.dialogs.ConfirmationDialog;
import com.ichi2.anki.dialogs.DiscardChangesDialog;
import com.ichi2.anki.dialogs.TagsDialog;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivity;
import com.ichi2.anki.multimediacard.fields.AudioClipField;
import com.ichi2.anki.multimediacard.fields.AudioRecordingField;
import com.ichi2.anki.multimediacard.fields.EFieldType;
import com.ichi2.anki.multimediacard.fields.IField;
import com.ichi2.anki.multimediacard.fields.ImageField;
import com.ichi2.anki.multimediacard.fields.TextField;
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.anki.servicelayer.NoteService;
import com.ichi2.async.CollectionTask;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Note.ClozeUtils;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;
import com.ichi2.anki.widgets.PopupMenuWithIcons;
import com.ichi2.utils.AdaptionUtil;
import com.ichi2.utils.DeckComparator;
import com.ichi2.utils.NamedJSONComparator;
import com.ichi2.widget.WidgetStatus;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import timber.log.Timber;

/**
 * Allows the user to edit a note, for instance if there is a typo. A card is a presentation of a note, and has two
 * sides: a question and an answer. Any number of fields can appear on each side. When you add a note to Anki, cards
 * which show that note are generated. Some models generate one card, others generate more than one.
 *
 * @see <a href="http://ankisrs.net/docs/manual.html#cards">the Anki Desktop manual</a>
 */
@SuppressWarnings("PMD.AvoidThrowingRawExceptionTypes")
public class NoteEditor extends AnkiActivity {
    // DA 2020-04-13 - Refactoring Plans once tested:
    // * There is a difference in functionality depending on whether we are editing
    // * Extract mAddNote and mCurrentEditedCard into inner class. Gate mCurrentEditedCard on edit state.
    // * Possibly subclass
    // * Make this in memory and immutable for metadata, it's hard to reason about our state if we're modifying col
    // * Consider persistence strategy for temporary media. Saving after multimedia edit is probably too early, but
    // we don't want to risk the cache being cleared. Maybe add in functionality to remove from collection if added and
    // the action is cancelled?
    // kill sCardBrowserCard and AbstractFlashcardViewer.getEditorCard()


//    public static final String SOURCE_LANGUAGE = "SOURCE_LANGUAGE";
//    public static final String TARGET_LANGUAGE = "TARGET_LANGUAGE";
    public static final String SOURCE_TEXT = "SOURCE_TEXT";
    public static final String TARGET_TEXT = "TARGET_TEXT";
    public static final String EXTRA_CALLER = "CALLER";
    public static final String EXTRA_CARD_ID = "CARD_ID";
    public static final String EXTRA_CONTENTS = "CONTENTS";
    public static final String EXTRA_ID = "ID";

    private static final String ACTION_CREATE_FLASHCARD = "org.openintents.action.CREATE_FLASHCARD";
    private static final String ACTION_CREATE_FLASHCARD_SEND = "android.intent.action.SEND";

    // calling activity
    public static final int CALLER_NOCALLER = 0;

    public static final int CALLER_REVIEWER = 1;
    public static final int CALLER_STUDYOPTIONS = 2;
    public static final int CALLER_DECKPICKER = 3;
    public static final int CALLER_REVIEWER_ADD = 11;

    public static final int CALLER_CARDBROWSER_EDIT = 6;
    public static final int CALLER_CARDBROWSER_ADD = 7;

    public static final int CALLER_CARDEDITOR = 8;
    public static final int CALLER_CARDEDITOR_INTENT_ADD = 10;

    public static final int REQUEST_ADD = 0;
    public static final int REQUEST_MULTIMEDIA_EDIT = 2;
    public static final int REQUEST_TEMPLATE_EDIT = 3;

    private boolean mChanged = false;
    private boolean mTagsEdited = false;
    private boolean mFieldEdited = false;

    /**
     * Flag which forces the calling activity to rebuild it's definition of current card from scratch
     */
    private boolean mReloadRequired = false;


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

    /* indicates if a new note is added or a card is edited */
    private boolean mAddNote;

    private boolean mAedictIntent;

    /* indicates which activity called Note Editor */
    private int mCaller;

    private LinkedList<FieldEditText> mEditFields;

    private MaterialDialog mProgressDialog;

    private String[] mSourceText;


    // A bundle that maps field ords to the text content of that field for use in
    // restoring the Activity.
    private Bundle mSavedFields;

    private CollectionTask.TaskListener mSaveNoteHandler = new CollectionTask.TaskListener() {
        private boolean mCloseAfter = false;
        private Intent mIntent;


        @Override
        public void onPreExecute() {
            Resources res = getResources();
            mProgressDialog = StyledProgressDialog
                    .show(NoteEditor.this, "", res.getString(R.string.saving_facts), false);
        }

        @Override
        public void onProgressUpdate(CollectionTask.TaskData... values) {
            int count = values[0].getInt();
            if (count > 0) {
                mChanged = true;
                mSourceText = null;
                Note oldNote = mEditorNote.clone();
                setNote();
                // Respect "Remember last input when adding" field option.
                JSONArray flds;
                flds = mEditorNote.model().getJSONArray("flds");
                if (oldNote != null) {
                    for (int fldIdx = 0; fldIdx < flds.length(); fldIdx++) {
                        if (flds.getJSONObject(fldIdx).getBoolean("sticky")) {
                            mEditFields.get(fldIdx).setText(oldNote.getFields()[fldIdx]);
                        }
                    }
                }
                UIUtils.showThemedToast(NoteEditor.this,
                        getResources().getQuantityString(R.plurals.factadder_cards_added, count, count), true);
            } else {
                displayErrorSavingNote();
            }
            if (!mAddNote || mCaller == CALLER_CARDEDITOR || mAedictIntent) {
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
            if (!mCloseAfter && (mProgressDialog != null) && mProgressDialog.isShowing()) {
                try {
                    mProgressDialog.dismiss();
                }
                catch (IllegalArgumentException e) {
                    Timber.e(e, "Note Editor: Error on dismissing progress dialog");
                }
            }
        }


        @Override
        public void onPostExecute(CollectionTask.TaskData result) {
            if (result.getBoolean()) {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    try {
                        mProgressDialog.dismiss();
                    } catch (IllegalArgumentException e) {
                        Timber.e(e, "Note Editor: Error on dismissing progress dialog");
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
                    mTagsEdited = false;
                }
            } else {
                // RuntimeException occured on adding note
                closeNoteEditor(DeckPicker.RESULT_DB_ERROR);
            }
        }
    };

    private void displayErrorSavingNote() {
        int errorMessageId = getAddNoteErrorResource();
        UIUtils.showThemedToast(this, getResources().getString(errorMessageId), true);
    }


    protected @StringRes int getAddNoteErrorResource() {
        //COULD_BE_BETTER: We currently don't perform edits inside this class (wat), so we only handle adds.
        if (this.isClozeType()) {
            return R.string.note_editor_no_cloze_delations;
        }

        if (TextUtils.isEmpty(getCurrentFieldText(0))) {
            return R.string.note_editor_no_first_field;
        }

        //Otherwise, display "no cards created".
        return R.string.note_editor_no_cards_created;
    }


    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.d("onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_editor);
        Intent intent = getIntent();
        if (savedInstanceState != null) {
            mCaller = savedInstanceState.getInt("caller");
            mAddNote = savedInstanceState.getBoolean("addNote");
            mCurrentDid = savedInstanceState.getLong("did");
            mSelectedTags = new ArrayList<>(Arrays.asList(savedInstanceState.getStringArray("tags")));
            mSavedFields = savedInstanceState.getBundle("editFields");
        } else {
            mCaller = intent.getIntExtra(EXTRA_CALLER, CALLER_NOCALLER);
            if (mCaller == CALLER_NOCALLER) {
                String action = intent.getAction();
                if ((ACTION_CREATE_FLASHCARD.equals(action) || ACTION_CREATE_FLASHCARD_SEND.equals(action))) {
                    mCaller = CALLER_CARDEDITOR_INTENT_ADD;
                }
            }
        }

        startLoadingCollection();
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        Timber.i("Saving instance");
        savedInstanceState.putInt("caller", mCaller);
        savedInstanceState.putBoolean("addNote", mAddNote);
        savedInstanceState.putLong("did", mCurrentDid);
        if(mSelectedTags == null){
            mSelectedTags = new ArrayList<>();
        }
        savedInstanceState.putStringArray("tags", mSelectedTags.toArray(new String[0]));
        savedInstanceState.putBundle("editFields", getFieldsAsBundle());
        super.onSaveInstanceState(savedInstanceState);
    }


    private Bundle getFieldsAsBundle() {
        Bundle fields = new Bundle();
        // Save the content of all the note fields. We use the field's ord as the key to
        // easily map the fields correctly later.
        if(mEditFields == null){
            //DA - I don't believe that this is required. Needs testing
            mEditFields = new LinkedList<>();
        }
        for (FieldEditText e : mEditFields) {
            fields.putString(Integer.toString(e.getOrd()), e.getText().toString());
        }
        return fields;
    }


    // Finish initializing the activity after the collection has been correctly loaded
    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        Intent intent = getIntent();
        Timber.d("NoteEditor() onCollectionLoaded: caller: %d", mCaller);

        registerExternalStorageListener();

        View mainView = findViewById(android.R.id.content);

        Toolbar toolbar = mainView.findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        mFieldsLayoutContainer = findViewById(R.id.CardEditorEditFieldsLayout);

        mTagsButton = findViewById(R.id.CardEditorTagText);
        mCardsButton = findViewById(R.id.CardEditorCardsText);
        mCardsButton.setOnClickListener(v -> {
            Timber.i("NoteEditor:: Cards button pressed. Opening template editor");
            showCardTemplateEditor();
        });


        mAedictIntent = false;

        switch (mCaller) {
            case CALLER_NOCALLER:
                Timber.e("no caller could be identified, closing");
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
            case CALLER_REVIEWER_ADD:
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

            case CALLER_CARDEDITOR_INTENT_ADD: {
                fetchIntentInformation(intent);
                if (mSourceText == null) {
                    finishWithoutAnimation();
                    return;
                }
                if ("Aedict Notepad".equals(mSourceText[0]) && addFromAedict(mSourceText[1])) {
                    finishWithoutAnimation();
                    return;
                }
                mAddNote = true;
                break;
            }
            default:
                break;
        }

        // Note type Selector
        mNoteTypeSpinner = findViewById(R.id.note_type_spinner);
        mAllModelIds = new ArrayList<>();
        final ArrayList<String> modelNames = new ArrayList<>();
        ArrayList<JSONObject> models = getCol().getModels().all();
        Collections.sort(models, NamedJSONComparator.instance);
        for (JSONObject m : models) {
            modelNames.add(m.getString("name"));
            mAllModelIds.add(m.getLong("id"));
        }

        ArrayAdapter<String> noteTypeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modelNames);
        mNoteTypeSpinner.setAdapter(noteTypeAdapter);
        noteTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);


        // Deck Selector
        TextView deckTextView = findViewById(R.id.CardEditorDeckText);
        // If edit mode and more than one card template distinguish between "Deck" and "Card deck"
        if (!mAddNote && mEditorNote.model().getJSONArray("tmpls").length()>1) {
            deckTextView.setText(R.string.CardEditorCardDeck);
        }
        mNoteDeckSpinner = findViewById(R.id.note_deck_spinner);
        mAllDeckIds = new ArrayList<>();
        final ArrayList<String> deckNames = new ArrayList<>();

        ArrayList<JSONObject> decks = getCol().getDecks().all();
        Collections.sort(decks, DeckComparator.instance);
        for (JSONObject d : decks) {
            // add current deck and all other non-filtered decks to deck list
            long thisDid = d.getLong("id");
            if (d.getInt("dyn") == 0 || (!mAddNote && mCurrentEditedCard != null && mCurrentEditedCard.getDid() == thisDid)) {
                deckNames.add(d.getString("name"));
                mAllDeckIds.add(thisDid);
            }
        }

        ArrayAdapter<String> noteDeckAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deckNames);
        mNoteDeckSpinner.setAdapter(noteDeckAdapter);
        noteDeckAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mNoteDeckSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                // Timber.i("NoteEditor:: onItemSelected() fired on mNoteDeckSpinner with pos = "+Integer.toString(pos));
                mCurrentDid = mAllDeckIds.get(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do Nothing
            }
        });

        setDid(mEditorNote);

        setNote(mEditorNote);

        // Set current note type and deck positions in spinners
        int position;
        position = mAllModelIds.indexOf(mEditorNote.model().getLong("id"));
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
                } else if (mEditFields.size() > 0) {
                    mEditFields.get(0).setText(mSourceText[0]);
                    if (mEditFields.size() > 1) {
                        mEditFields.get(1).setText(mSourceText[1]);
                    }
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


        findViewById(R.id.CardEditorTagButton).setOnClickListener(v -> {
            Timber.i("NoteEditor:: Tags button pressed... opening tags editor");
            showTagsDialog();
        });

        if (!mAddNote && mCurrentEditedCard != null) {
            Timber.i("onCollectionLoaded() Edit note activity successfully started with card id %d", mCurrentEditedCard.getId());
        }

        //set focus to FieldEditText 'first' on startup like Anki desktop
        if (mEditFields != null && !mEditFields.isEmpty()) {
            FieldEditText first = mEditFields.getFirst();
            first.requestFocus();
        }
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
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch(keyCode) {

            //some hardware keybds swap between mobile/desktop mode...
            //when in mobile mode KEYCODE_NUMPAD_ENTER & KEYCODE_ENTER are equiv. but
            //both need to be captured for desktop keybds
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (event.isCtrlPressed()) {
                    saveNote();
                }
                break;

            case KeyEvent.KEYCODE_D:
                //null check in case Spinner is moved into options menu in the future
                if (event.isCtrlPressed() && (mNoteDeckSpinner != null)) {
                        mNoteDeckSpinner.performClick();
                }
                break;

            case KeyEvent.KEYCODE_L:
                if (event.isCtrlPressed()) {
                    showCardTemplateEditor();
                }
                break;

            case KeyEvent.KEYCODE_N:
                if (event.isCtrlPressed() && (mNoteTypeSpinner != null)) {
                        mNoteTypeSpinner.performClick();
                }
                break;

            case KeyEvent.KEYCODE_T:
                if (event.isCtrlPressed() && event.isShiftPressed()) {
                    showTagsDialog();
                }
                break;
            default:
                break;
        }

        return super.onKeyUp(keyCode, event);
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
            // Some users add cards via SEND intent from clipboard. In this case SUBJECT is empty
            if ("".equals(first)) {
                // Assume that if only one field was sent then it should be the front
                first = second;
                second = "";
            }
            Pair<String, String> messages = new Pair<>(first, second);

            mSourceText = new String[2];
            mSourceText[0] = messages.first;
            mSourceText[1] = messages.second;
        }
    }


    private boolean addFromAedict(String extra_text) {
        String category = "";
        String[] notepad_lines = extra_text.split("\n");
        for (int i = 0; i < notepad_lines.length; i++) {
            if (notepad_lines[i].startsWith("[") && notepad_lines[i].endsWith("]")) {
                category = notepad_lines[i].substring(1, notepad_lines[i].length() - 1);
                if ("default".equals(category)) {
                    if (notepad_lines.length > i + 1) {
                        String[] entry_lines = notepad_lines[i + 1].split(":");
                        if (entry_lines.length > 1) {
                            mSourceText[0] = entry_lines[1];
                            mSourceText[1] = entry_lines[0];
                            mAedictIntent = true;
                        } else {
                            UIUtils.showThemedToast(NoteEditor.this,
                                    getResources().getString(R.string.intent_aedict_empty), false);
                            return true;
                        }
                    } else {
                        UIUtils.showThemedToast(NoteEditor.this, getResources().getString(R.string.intent_aedict_empty),
                                false);
                        return true;
                    }
                    return false;
                }
            }
        }
        UIUtils.showThemedToast(NoteEditor.this, getResources().getString(R.string.intent_aedict_category), false);
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
        // changed tags?
        return mTagsEdited;
    }

    @VisibleForTesting
    void saveNote() {
        final Resources res = getResources();
        if (mSelectedTags == null) {
            mSelectedTags = new ArrayList<>();
        }
        // treat add new note and edit existing note independently
        if (mAddNote) {
            //Different from libAnki, block if there are no cloze deletions.
            //DEFECT: This does not block addition if cloze transpositions are in non-cloze fields.
            if (isClozeType() && !hasClozeDeletions()) {
                displayErrorSavingNote();
                return;
            }

            // load all of the fields into the note
            for (FieldEditText f : mEditFields) {
                updateField(f);
            }
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
            CollectionTask.launchCollectionTask(CollectionTask.TASK_TYPE_ADD_NOTE, mSaveNoteHandler, new CollectionTask.TaskData(mEditorNote));
        } else {
            // Check whether note type has been changed
            final JSONObject newModel = getCurrentlySelectedModel();
            final JSONObject oldModel = mCurrentEditedCard.model();
            if (!newModel.equals(oldModel)) {
                mReloadRequired = true;
                if (mModelChangeCardMap.size() < mEditorNote.cards().size() || mModelChangeCardMap.containsKey(null)) {
                    // If cards will be lost via the new mapping then show a confirmation dialog before proceeding with the change
                    ConfirmationDialog dialog = new ConfirmationDialog ();
                    dialog.setArgs(res.getString(R.string.confirm_map_cards_to_nothing));
                    Runnable confirm = () -> {
                        // Bypass the check once the user confirms
                        changeNoteTypeWithErrorHandling(oldModel, newModel);
                    };
                    dialog.setConfirm(confirm);
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
                mReloadRequired = true;
                // remove card from filtered deck first (if relevant)
                getCol().getSched().remFromDyn(new long[] { mCurrentEditedCard.getId() });
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
     */
    private void changeNoteTypeWithErrorHandling(final JSONObject oldModel, final JSONObject newModel) {
        Resources res = getResources();
        try {
            changeNoteType(oldModel, newModel);
        } catch (ConfirmModSchemaException e) {
            // Libanki has determined we should ask the user to confirm first
            ConfirmationDialog dialog = new ConfirmationDialog();
            dialog.setArgs(res.getString(R.string.full_sync_confirmation));
            Runnable confirm = () -> {
                // Bypass the check once the user confirms
                getCol().modSchemaNoCheck();
                try {
                    changeNoteType(oldModel, newModel);
                } catch (ConfirmModSchemaException e2) {
                    // This should never be reached as we explicitly called modSchemaNoCheck()
                    throw new RuntimeException(e2);
                }
            };
            dialog.setConfirm(confirm);
            showDialogFragment(dialog);
        }
    }

    /**
     * Change the note type from oldModel to newModel
     * @throws ConfirmModSchemaException If a full sync will be required
     */
    private void changeNoteType(JSONObject oldModel, JSONObject newModel) throws ConfirmModSchemaException {
        final long [] nids = {mEditorNote.getId()};
        getCol().getModels().change(oldModel, nids, newModel, mModelChangeFieldMap, mModelChangeCardMap);
        // refresh the note object to reflect the database changes
        mEditorNote.load();
        // close note editor
        closeNoteEditor();
    }


    @Override
    public void onBackPressed() {
        Timber.i("NoteEditor:: onBackPressed()");
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.note_editor, menu);
        if (mAddNote) {
            menu.findItem(R.id.action_copy_note).setVisible(false);
        } else {
            menu.findItem(R.id.action_add_note_from_note_editor).setVisible(true);
        }
        if (mEditFields != null) {
            for (int i = 0; i < mEditFields.size(); i++) {
                if (mEditFields.get(i).getText().length() > 0) {
                    menu.findItem(R.id.action_copy_note).setEnabled(true);
                    break;
                } else if (i == mEditFields.size() - 1) {
                    menu.findItem(R.id.action_copy_note).setEnabled(false);
                }
            }
        }
        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Timber.i("NoteEditor:: Home button pressed");
                closeCardEditorWithCheck();
                return true;

            case R.id.action_save:
                Timber.i("NoteEditor:: Save note button pressed");
                saveNote();
                return true;

            case R.id.action_add_note_from_note_editor:
            case R.id.action_copy_note: {
                Timber.i("NoteEditor:: Copy or add card button pressed");
                Intent intent = new Intent(NoteEditor.this, NoteEditor.class);
                intent.putExtra(EXTRA_CALLER, CALLER_CARDEDITOR);
                // intent.putExtra(EXTRA_DECKPATH, mDeckPath);
                if (item.getItemId() == R.id.action_copy_note) {
                    intent.putExtra(EXTRA_CONTENTS, getFieldsText());
                }
                startActivityForResultWithAnimation(intent, REQUEST_ADD, ActivityTransitionAnimation.LEFT);
                return true;
            }
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
        DiscardChangesDialog.getDefault(this)
                .onPositive((dialog, which) -> {
                    Timber.i("NoteEditor:: OK button pressed to confirm discard changes");
                    closeNoteEditor();
                })
                .build().show();
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
        if (mReloadRequired) {
            if (intent == null) {
                intent = new Intent();
            }
            intent.putExtra("reloadRequired", true);
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
        if (mCaller == CALLER_CARDEDITOR_INTENT_ADD) {
            finishWithAnimation(ActivityTransitionAnimation.NONE);
        } else {
            finishWithAnimation(ActivityTransitionAnimation.RIGHT);
        }
    }

    private void showTagsDialog() {
        if (mSelectedTags == null) {
            mSelectedTags = new ArrayList<>();
        }
        ArrayList<String> tags = new ArrayList<>(getCol().getTags().all());
        ArrayList<String> selTags = new ArrayList<>(mSelectedTags);
        TagsDialog dialog = TagsDialog.newInstance(TagsDialog.TYPE_ADD_TAG, selTags,
                tags);
        dialog.setTagsDialogListener((selectedTags, option) -> {
            if (!mSelectedTags.equals(selectedTags)) {
                mTagsEdited = true;
            }
            mSelectedTags = selectedTags;
            updateTags();
        });
        showDialogFragment(dialog);
    }

    private void showCardTemplateEditor() {
        Intent intent = new Intent(this, CardTemplateEditor.class);
        // Pass the model ID
        intent.putExtra("modelId", getCurrentlySelectedModel().getLong("id"));
        Timber.d("showCardTemplateEditor() for model %s", intent.getLongExtra("modelId", -1L));
        // Also pass the note id and ord if not adding new note
        if (!mAddNote) {
            intent.putExtra("noteId", mCurrentEditedCard.note().getId());
            Timber.d("showCardTemplateEditor() with note %s", mCurrentEditedCard.note().getId());
            intent.putExtra("ordId", mCurrentEditedCard.getOrd());
            Timber.d("showCardTemplateEditor() with ord %s", mCurrentEditedCard.getOrd());
        }
        startActivityForResultWithAnimation(intent, REQUEST_TEMPLATE_EDIT, ActivityTransitionAnimation.LEFT);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Timber.d("onActivityResult() with request/result: %s/%s", requestCode, resultCode);
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == DeckPicker.RESULT_DB_ERROR) {
            closeNoteEditor(DeckPicker.RESULT_DB_ERROR);
        }

        switch (requestCode) {
            case REQUEST_ADD: {
                if (resultCode != RESULT_CANCELED) {
                    mChanged = true;
                }
                break;
            }
            case REQUEST_MULTIMEDIA_EDIT: {
                if (resultCode != RESULT_CANCELED) {
                    Collection col = getCol();
                    Bundle extras = data.getExtras();
                    int index = extras.getInt(MultimediaEditFieldActivity.EXTRA_RESULT_FIELD_INDEX);
                    IField field = (IField) extras.get(MultimediaEditFieldActivity.EXTRA_RESULT_FIELD);
                    MultimediaEditableNote mNote = getCurrentMultimediaEditableNote(col);
                    mNote.setField(index, field);
                    FieldEditText fieldEditText = mEditFields.get(index);
                    // Completely replace text for text fields (because current text was passed in)
                    if (field.getType() == EFieldType.TEXT) {
                        fieldEditText.setText(field.getFormattedValue());
                    }
                    // Insert text at cursor position if the field has focus
                    else if (fieldEditText.hasFocus()) {
                        fieldEditText.getText().replace(fieldEditText.getSelectionStart(),
                                fieldEditText.getSelectionEnd(),
                                field.getFormattedValue());
                    }
                    // Append text if the field doesn't have focus
                    else {
                        fieldEditText.getText().append(field.getFormattedValue());
                    }
                    //DA - I think we only want to save the field here, not the note.
                    NoteService.saveMedia(col, mNote);
                    mChanged = true;
                }
                break;
            }
            case REQUEST_TEMPLATE_EDIT: {
                    // Model can change regardless of exit type - update ourselves and CardBrowser
                    mReloadRequired = true;
                    mEditorNote.reloadModel();
                    if (!mEditorNote.cards().contains(mCurrentEditedCard)) {
                        Timber.d("onActivityResult() template edit return - current card is gone");
                        UIUtils.showSimpleSnackbar(this, R.string.template_for_current_card_deleted, false);
                        closeNoteEditor();
                    } else {
                        Timber.d("onActivityResult() template edit return - current card exists");
                        // reload current card - the template ordinals are possibly different post-edit
                        mCurrentEditedCard = getCol().getCard(mCurrentEditedCard.getId());
                        updateCards(mEditorNote.model());
                    }
                break;
            }
        }
    }

    /** @param col Readonly variable to get cache dir */
    private MultimediaEditableNote getCurrentMultimediaEditableNote(Collection col) {
        MultimediaEditableNote mNote = NoteService.createEmptyNote(mEditorNote.model());

        String[] fields = getCurrentFieldStrings();
        NoteService.updateMultimediaNoteFromFields(col, fields, mEditorNote.getMid(), mNote);
        return mNote;
    }


    private String[] getCurrentFieldStrings() {
        if (mEditFields == null) {
            return new String[0];
        }
        String[] ret = new String[mEditFields.size()];
        for (int i = 0; i < mEditFields.size(); i++) {
            ret[i] = getCurrentFieldText(i);
        }
        return ret;
    }


    private void populateEditFields() {
        String[][] fields;
        // If we have a bundle of pre-populated field values, we overwrite the existing values
        // with those ones since we are resuming the activity after it was terminated early.
        if (mSavedFields != null) {
            fields = mEditorNote.items();
            for (String key : mSavedFields.keySet()) {
                int ord = Integer.parseInt(key);
                String text = mSavedFields.getString(key);
                fields[ord][1] = text;
            }
            // Clear the saved values since we've consumed them.
            mSavedFields = null;
        } else {
            fields = mEditorNote.items();
        }
        populateEditFields(fields, false);
    }

    private void populateEditFields(String[][] fields, boolean editModelMode) {
        mFieldsLayoutContainer.removeAllViews();
        mEditFields = new LinkedList<>();

        // Use custom font if selected from preferences
        Typeface mCustomTypeface = null;
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        String customFont = preferences.getString("browserEditorFont", "");
        if (!"".equals(customFont)) {
            mCustomTypeface = AnkiFont.getTypeface(this, customFont);
        }

        for (int i = 0; i < fields.length; i++) {
            View editline_view = getLayoutInflater().inflate(R.layout.card_multimedia_editline, null);
            FieldEditText newTextbox = editline_view.findViewById(R.id.id_note_editText);

            if (Build.VERSION.SDK_INT >= 23) {
                // Use custom implementation of ActionMode.Callback customize selection and insert menus
                ActionModeCallback actionModeCallback = new ActionModeCallback(newTextbox);
                newTextbox.setCustomSelectionActionModeCallback(actionModeCallback);
                newTextbox.setCustomInsertionActionModeCallback(actionModeCallback);
            }

            initFieldEditText(newTextbox, i, fields[i], mCustomTypeface, !editModelMode);

            TextView label = newTextbox.getLabel();
            label.setPadding((int) UIUtils.getDensityAdjustedValue(this, 3.4f), 0, 0, 0);
            mEditFields.add(newTextbox);

            ImageButton mediaButton = editline_view.findViewById(R.id.id_media_button);
            // Load icons from attributes
            int[] icons = Themes.getResFromAttr(this, new int[] { R.attr.attachFileImage, R.attr.upDownImage});
            // Make the icon change between media icon and switch field icon depending on whether editing note type
            if (editModelMode && allowFieldRemapping()) {
                // Allow remapping if originally more than two fields
                mediaButton.setBackgroundResource(icons[1]);
                setRemapButtonListener(mediaButton, i);
            } else if (editModelMode && !allowFieldRemapping()) {
                mediaButton.setBackgroundResource(0);
            } else {
                // Use media editor button if not changing note type
                mediaButton.setBackgroundResource(icons[0]);
                setMMButtonListener(mediaButton, i);
            }
            mediaButton.setContentDescription(getString(R.string.multimedia_editor_attach_mm_content, fields[i][0]));
            mFieldsLayoutContainer.addView(label);
            mFieldsLayoutContainer.addView(editline_view);
        }
    }


    private void setMMButtonListener(ImageButton mediaButton, final int index) {
        mediaButton.setOnClickListener(v -> {
            Timber.i("NoteEditor:: Multimedia button pressed for field %d", index);
            if (mEditorNote.items()[index][1].length() > 0) {
                final Collection col = CollectionHelper.getInstance().getCol(NoteEditor.this);
                // If the field already exists then we start the field editor, which figures out the type
                // automatically
                IMultimediaEditableNote mNote = getCurrentMultimediaEditableNote(col);
                startMultimediaFieldEditor(index, mNote);
            } else {
                // Otherwise we make a popup menu allowing the user to choose between audio/image/text field
                // TODO: Update the icons for dark material theme, then can set 3rd argument to true
                PopupMenuWithIcons popup = new PopupMenuWithIcons(NoteEditor.this, v, false);
                MenuInflater inflater = popup.getMenuInflater();
                inflater.inflate(R.menu.popupmenu_multimedia_options, popup.getMenu());
                popup.setOnMenuItemClickListener(item -> {

                    switch (item.getItemId()) {
                        case R.id.menu_multimedia_audio: {
                            Timber.i("NoteEditor:: Record audio button pressed");
                            startMultimediaFieldEditorForField(index, new AudioRecordingField());
                            return true;
                        }
                        case R.id.menu_multimedia_audio_clip: {
                            Timber.i("NoteEditor:: Add audio clip button pressed");
                            startMultimediaFieldEditorForField(index, new AudioClipField());
                            return true;
                        }
                        case R.id.menu_multimedia_photo: {
                            Timber.i("NoteEditor:: Add image button pressed");
                            startMultimediaFieldEditorForField(index, new ImageField());
                            return true;
                        }
                        case R.id.menu_multimedia_text: {
                            Timber.i("NoteEditor:: Advanced editor button pressed");
                            startAdvancedTextEditor(index);
                            return true;
                        }
                        default:
                            return false;
                    }
                });
                if (AdaptionUtil.isRestrictedLearningDevice()) {
                    popup.getMenu().findItem(R.id.menu_multimedia_photo).setVisible(false);
                    popup.getMenu().findItem(R.id.menu_multimedia_text).setVisible(false);
                }
                popup.show();
            }
        });
    }


    private void startMultimediaFieldEditorForField(int index, IField field) {
        Collection col = CollectionHelper.getInstance().getCol(NoteEditor.this);
        IMultimediaEditableNote mNote = getCurrentMultimediaEditableNote(col);
        mNote.setField(index, field);
        startMultimediaFieldEditor(index, mNote);
    }


    private void setRemapButtonListener(ImageButton remapButton, final int newFieldIndex) {
        remapButton.setOnClickListener(v -> {
            Timber.i("NoteEditor:: Remap button pressed for new field %d", newFieldIndex);
            // Show list of fields from the original note which we can map to
            PopupMenu popup = new PopupMenu(NoteEditor.this, v);
            final String[][] items = mEditorNote.items();
            for (int i = 0; i < items.length; i++) {
                popup.getMenu().add(Menu.NONE, i, Menu.NONE, items[i][0]);
            }
            // Add "nothing" at the end of the list
            popup.getMenu().add(Menu.NONE, items.length, Menu.NONE, R.string.nothing);
            popup.setOnMenuItemClickListener(item -> {
                // Get menu item id
                Integer idx = item.getItemId();
                Timber.i("NoteEditor:: User chose to remap to old field %d", idx);
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
            });
            popup.show();
        });
    }


    private void startMultimediaFieldEditor(final int index, IMultimediaEditableNote mNote) {
        IField field = mNote.getField(index);
        Intent editCard = new Intent(NoteEditor.this, MultimediaEditFieldActivity.class);
        editCard.putExtra(MultimediaEditFieldActivity.EXTRA_FIELD_INDEX, index);
        editCard.putExtra(MultimediaEditFieldActivity.EXTRA_FIELD, field);
        editCard.putExtra(MultimediaEditFieldActivity.EXTRA_WHOLE_NOTE, mNote);
        startActivityForResultWithoutAnimation(editCard, REQUEST_MULTIMEDIA_EDIT);
    }


    private void initFieldEditText(FieldEditText editText, final int index, String[] values, Typeface customTypeface, boolean enabled) {
        String name = values[0];
        String content = values[1];
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
                // do nothing
            }


            @Override
            public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
                // do nothing
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
        // Change bottom line color of text field
        if (dupeCode != null && dupeCode == 2) {
            field.setDupeStyle();
            isDupe = true;
        } else {
            field.setDefaultStyle();
            isDupe = false;
        }
        // Put back the old value so we don't interfere with modification detection
        mEditorNote.values()[0] = oldValue;
        return isDupe;
    }


    private String getFieldsText() {
        String[] fields = new String[mEditFields.size()];
        for (int i = 0; i < mEditFields.size(); i++) {
            int i1 = i;
            fields[i] = getCurrentFieldText(i1);
        }
        return Utils.joinFields(fields);
    }

    /** Returns the value of the field at the given index */
    private String getCurrentFieldText(int index) {
        return mEditFields.get(index).getText().toString();
    }


    private void setDid(Note note) {
        // If the target deck ID has already been set, we use that value and avoid trying to
        // determine what it should be again. An existing value means we are resuming the activity
        // where the target deck was already decided by the user.
        if (mCurrentDid != 0) {
            return;
        }
        if (note == null || mAddNote) {
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
        } else {
            mCurrentDid = mCurrentEditedCard.getDid();
        }
    }


    /** Make NOTE the current note. */
    private void setNote() {
        setNote(null);
    }


    private void setNote(Note note) {
        if (note == null || mAddNote) {
            JSONObject model = getCol().getModels().current();
            mEditorNote = new Note(getCol(), model);
        } else {
            mEditorNote = note;
        }
        if (mSelectedTags == null) {
            mSelectedTags = mEditorNote.getTags();
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
            Timber.e("updateDeckPosition() error :: mCurrentDid=%d, position=%d", mCurrentDid, position);
        }
    }


    private void updateTags() {
        if (mSelectedTags == null) {
            mSelectedTags = new ArrayList<>();
        }
        mTagsButton.setText(getResources().getString(R.string.CardEditorTags,
                getCol().getTags().join(getCol().getTags().canonify(mSelectedTags)).trim().replace(" ", ", ")));
    }


    /** Update the list of card templates for current note type */
    private void updateCards(JSONObject model) {
        Timber.d("updateCards()");
        JSONArray tmpls = model.getJSONArray("tmpls");
        StringBuilder cardsList = new StringBuilder();
        // Build comma separated list of card names
        Timber.d("updateCards() template count is %s", tmpls.length());
        for (int i = 0; i < tmpls.length(); i++) {
            String name = tmpls.getJSONObject(i).optString("name");
            // If more than one card then make currently selected card underlined
            if (!mAddNote && tmpls.length() > 1 && model == mEditorNote.model() &&
                mCurrentEditedCard.template().optString("name").equals(name)) {
                name = "<u>" + name + "</u>";
            }
            cardsList.append(name);
            if (i < tmpls.length()-1) {
                cardsList.append(", ");
            }
        }
        // Make cards list red if the number of cards is being reduced
        if (!mAddNote && tmpls.length() < mEditorNote.model().getJSONArray("tmpls").length()) {
            cardsList = new StringBuilder("<font color='red'>" + cardsList + "</font>");
        }
        mCardsButton.setText(CompatHelper.getCompat().fromHtml(getResources().getString(R.string.CardEditorCards, cardsList.toString())));
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



    private class SetNoteTypeListener implements OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            // If a new column was selected then change the key used to map from mCards to the column TextView
            //Timber.i("NoteEditor:: onItemSelected() fired on mNoteTypeSpinner");
            long oldModelId;
            oldModelId = getCol().getModels().current().getLong("id");
            long newId = mAllModelIds.get(pos);
            Timber.i("Changing note type to '%d", newId);
            if (oldModelId != newId) {
                JSONObject model = getCol().getModels().get(newId);
                getCol().getModels().setCurrent(model);
                JSONObject cdeck = getCol().getDecks().current();
                cdeck.put("mid", newId);
                getCol().getDecks().save(cdeck);
                // Update deck
                if (!getCol().getConf().optBoolean("addToCur", true)) {
                    mCurrentDid = model.getLong("did");
                    updateDeckPosition();
                }
                // Reset edit fields
                int size = mEditFields.size();
                String[] oldValues = new String[size];
                for (int i = 0; i < size; i++) {
                    oldValues[i] = getCurrentFieldText(i);
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
            noteModelId = mCurrentEditedCard.model().getLong("id");
            // Get new model
            JSONObject newModel = getCol().getModels().get(mAllModelIds.get(pos));            
            // Configure the interface according to whether note type is getting changed or not
            if (mAllModelIds.get(pos) != noteModelId) {
                // Initialize mapping between fields of old model -> new model
                mModelChangeFieldMap = new HashMap<>();
                for (int i=0; i < mEditorNote.items().length; i++) {
                    mModelChangeFieldMap.put(i, i);
                }
                // Initialize mapping between cards new model -> old model
                mModelChangeCardMap = new HashMap<>();
                for (int i=0; i < newModel.getJSONArray("tmpls").length() ; i++) {
                    if (i < mEditorNote.cards().size()) {
                        mModelChangeCardMap.put(i, i);
                    } else {
                        mModelChangeCardMap.put(i, null);
                    }
                }
                // Update the field text edits based on the default mapping just assigned
                updateFieldsFromMap(newModel);
                // Don't let the user change any other values at the same time as changing note type
                mSelectedTags = mEditorNote.getTags();
                updateTags();
                findViewById(R.id.CardEditorTagButton).setEnabled(false);
                //((LinearLayout) findViewById(R.id.CardEditorCardsButton)).setEnabled(false);
                mNoteDeckSpinner.setEnabled(false);
                int position = mAllDeckIds.indexOf(mCurrentEditedCard.getDid());
                if (position != -1) {
                    mNoteDeckSpinner.setSelection(position, false);
                }
            } else {
                populateEditFields();
                updateCards(mCurrentEditedCard.model());
                findViewById(R.id.CardEditorTagButton).setEnabled(true);
                //((LinearLayout) findViewById(R.id.CardEditorCardsButton)).setEnabled(false);
                mNoteDeckSpinner.setEnabled(true);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            // Do Nothing
        }
    }

    /**
     * Custom ActionMode.Callback implementation for adding and handling cloze deletion action
     * button in the text selection menu.
     */
    @TargetApi(23)
    private class ActionModeCallback implements ActionMode.Callback {
        private FieldEditText mTextBox;
        private int mMenuId = View.generateViewId();

        ActionModeCallback(FieldEditText textBox) {
            super();
            mTextBox = textBox;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            // Adding the cloze deletion floating context menu item, but only once.
            boolean itemExists = menu.findItem(mMenuId) != null;
            if (isClozeType() && !itemExists) {
                menu.add(Menu.NONE, mMenuId, 0, R.string.multimedia_editor_popup_cloze);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == mMenuId) {
                // get the current text and selection locations
                int selectionStart = mTextBox.getSelectionStart();
                int selectionEnd = mTextBox.getSelectionEnd();
                String text = mTextBox.getText().toString();

                // Split the text in the places where the cloze deletion will be inserted
                String beforeText = text.substring(0, selectionStart);
                String selectedText = text.substring(selectionStart, selectionEnd);
                String afterText = text.substring(selectionEnd);
                int nextClozeIndex = getNextClozeIndex();

                // Format the cloze deletion open bracket
                String clozeOpenBracket = "{{c" + (nextClozeIndex) + "::";

                // Update text field with updated text and selection
                mTextBox.setText(beforeText + clozeOpenBracket + selectedText + "}}" + afterText);
                int clozeOpenSize = clozeOpenBracket.length();
                mTextBox.setSelection(selectionStart+clozeOpenSize, selectionEnd+clozeOpenSize);

                mode.finish();
                return true;
            } else {
                return false;
            }
        }


        @Override
        public void onDestroyActionMode(ActionMode mode) {
            // Left empty on purpose
        }
    }

    private boolean hasClozeDeletions() {
        return getNextClozeIndex() > 1;
    }

    private int getNextClozeIndex() {
        /** BUG: This assumes all fields are inserted as: {{cloze:Text}} */
        List<String> fieldValues = new ArrayList<>(mEditFields.size());
        for (FieldEditText e : mEditFields) {
            Editable editable = e.getText();
            String fieldValue = editable == null ? "" : editable.toString();
            fieldValues.add(fieldValue);
        }
        return ClozeUtils.getNextClozeIndex(fieldValues);
    }

    private boolean isClozeType() {
        return getCurrentlySelectedModel().getInt("type") == Consts.MODEL_CLOZE;
    }


    @VisibleForTesting
    void startAdvancedTextEditor(int index) {
        TextField field = new TextField();
        field.setText(getCurrentFieldText(index));
        startMultimediaFieldEditorForField(index, field);
    }

    @VisibleForTesting
    void setFieldValueFromUi(int i, String newText) {
        mEditFields.get(i).setText(newText);
    }
}
