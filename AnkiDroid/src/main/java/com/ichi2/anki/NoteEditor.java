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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.PopupMenu;

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
import android.view.ViewGroup.MarginLayoutParams;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.dialogs.ConfirmationDialog;
import com.ichi2.anki.dialogs.DeckSelectionDialog;
import com.ichi2.anki.dialogs.DiscardChangesDialog;
import com.ichi2.anki.dialogs.IntegerDialog;
import com.ichi2.anki.dialogs.tags.TagsDialog;
import com.ichi2.anki.dialogs.tags.TagsDialogFactory;
import com.ichi2.anki.dialogs.tags.TagsDialogListener;
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
import com.ichi2.anki.noteeditor.FieldState;
import com.ichi2.anki.noteeditor.FieldState.FieldChangeType;
import com.ichi2.anki.noteeditor.CustomToolbarButton;
import com.ichi2.anki.noteeditor.Toolbar;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.anki.servicelayer.LanguageHintService;
import com.ichi2.anki.servicelayer.NoteService;
import com.ichi2.anki.ui.NoteTypeSpinnerUtils;
import com.ichi2.anki.widgets.DeckDropDownAdapter;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskListenerWithContext;
import com.ichi2.async.TaskManager;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Note.ClozeUtils;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.Deck;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;
import com.ichi2.anki.widgets.PopupMenuWithIcons;
import com.ichi2.utils.AdaptionUtil;
import com.ichi2.utils.HashUtil;
import com.ichi2.utils.KeyUtils;
import com.ichi2.utils.MapUtil;
import com.ichi2.utils.NoteFieldDecorator;
import com.ichi2.utils.TextViewUtil;
import com.ichi2.widget.WidgetStatus;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.text.HtmlCompat;
import timber.log.Timber;
import static com.ichi2.compat.Compat.ACTION_PROCESS_TEXT;
import static com.ichi2.compat.Compat.EXTRA_PROCESS_TEXT;

import static com.ichi2.anim.ActivityTransitionAnimation.Direction.*;
import static com.ichi2.libanki.Decks.CURRENT_DECK;
import static com.ichi2.libanki.Models.NOT_FOUND_NOTE_TYPE;

/**
 * Allows the user to edit a note, for instance if there is a typo. A card is a presentation of a note, and has two
 * sides: a question and an answer. Any number of fields can appear on each side. When you add a note to Anki, cards
 * which show that note are generated. Some models generate one card, others generate more than one.
 *
 * @see <a href="https://docs.ankiweb.net/getting-started.html#cards">the Anki Desktop manual</a>
 */
public class NoteEditor extends AnkiActivity implements
        DeckSelectionDialog.DeckSelectionListener,
        DeckDropDownAdapter.SubtitleListener,
        TagsDialogListener {
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
    public static final String EXTRA_TAGS = "TAGS";
    public static final String EXTRA_ID = "ID";
    public static final String EXTRA_DID = "DECK_ID";
    public static final String EXTRA_TEXT_FROM_SEARCH_VIEW = "SEARCH";
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
    public static final int REQUEST_PREVIEW = 4;

    /** Whether any change are saved. E.g. multimedia, new card added, field changed and saved.*/
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

    private MediaRegistration mMediaRegistration;

    private TagsDialogFactory mTagsDialogFactory;

    private AppCompatButton mTagsButton;
    private AppCompatButton mCardsButton;
    private Spinner mNoteTypeSpinner;
    private DeckSpinnerSelection mDeckSpinnerSelection;

    // Non Null after onCollectionLoaded, but still null after construction. So essentially @NonNull but it would fail.
    private Note mEditorNote;
    @Nullable
    /* Null if adding a new card. Presently NonNull if editing an existing note - but this is subject to change */
    private Card mCurrentEditedCard;
    private ArrayList<String> mSelectedTags;
    private long mCurrentDid;
    private ArrayList<Long> mAllModelIds;
    private Map<Integer, Integer> mModelChangeFieldMap;
    private HashMap<Integer, Integer> mModelChangeCardMap;
    private ArrayList<Integer> mCustomViewIds = new ArrayList<>();

    /* indicates if a new note is added or a card is edited */
    private boolean mAddNote;
    private boolean mAedictIntent;

    /* indicates which activity called Note Editor */
    private int mCaller;

    private LinkedList<FieldEditText> mEditFields;

    private MaterialDialog mProgressDialog;

    private String[] mSourceText;

    private FieldState mFieldState = FieldState.fromEditor(this);

    private Toolbar mToolbar;

    // Use the same HTML if the same image is pasted multiple times.
    private HashMap<String, String> mPastedImageCache = new HashMap<>();

    // save field index as key and text as value when toggle sticky clicked in Field Edit Text
    private HashMap<Integer, String> mToggleStickyText = new HashMap<>();

    private final Onboarding.NoteEditor mOnboarding = new Onboarding.NoteEditor(this);

    private SaveNoteHandler saveNoteHandler() {
        return new SaveNoteHandler(this);
    }


    @Override
    public void onDeckSelected(@Nullable DeckSelectionDialog.SelectableDeck deck) {
        if (deck == null) {
            return;
        }
        mCurrentDid = deck.getDeckId();
        mDeckSpinnerSelection.initializeNoteEditorDeckSpinner(mCurrentEditedCard, mAddNote);
        mDeckSpinnerSelection.selectDeckById(deck.getDeckId(), false);
    }

    @Override
    public String getSubtitleText() {
        return "";
    }

    private enum AddClozeType {
        SAME_NUMBER,
        INCREMENT_NUMBER
    }

    private static class SaveNoteHandler extends TaskListenerWithContext<NoteEditor, Integer, Boolean> {
        private boolean mCloseAfter = false;
        private Intent mIntent;


        private SaveNoteHandler(NoteEditor noteEditor) {
            super(noteEditor);
        }


        @Override
        public void actualOnPreExecute(@NonNull NoteEditor noteEditor) {
            Resources res = noteEditor.getResources();
            noteEditor.mProgressDialog = StyledProgressDialog
                    .show(noteEditor, null, res.getString(R.string.saving_facts), false);
        }

        @Override
        public void actualOnProgressUpdate(@NonNull NoteEditor noteEditor, Integer count) {
            if (count > 0) {
                noteEditor.mChanged = true;
                noteEditor.mSourceText = null;
                noteEditor.refreshNoteData(FieldChangeType.refreshWithStickyFields(shouldReplaceNewlines()));
                UIUtils.showThemedToast(noteEditor,
                        noteEditor.getResources().getQuantityString(R.plurals.factadder_cards_added, count, count), true);
            } else {
                noteEditor.displayErrorSavingNote();
            }
            if (!noteEditor.mAddNote || noteEditor.mCaller == CALLER_CARDEDITOR || noteEditor.mAedictIntent) {
                noteEditor.mChanged = true;
                mCloseAfter = true;
            } else if (noteEditor.mCaller == CALLER_CARDEDITOR_INTENT_ADD) {
                if (count > 0) {
                    noteEditor.mChanged = true;
                }
                mCloseAfter = true;
                mIntent = new Intent();
                mIntent.putExtra(EXTRA_ID, noteEditor.getIntent().getStringExtra(EXTRA_ID));
            } else if (!noteEditor.mEditFields.isEmpty()) {
                noteEditor.mEditFields.getFirst().focusWithKeyboard();
            }
            if (!mCloseAfter && (noteEditor.mProgressDialog != null) && noteEditor.mProgressDialog.isShowing()) {
                try {
                    noteEditor.mProgressDialog.dismiss();
                }
                catch (IllegalArgumentException e) {
                    Timber.e(e, "Note Editor: Error on dismissing progress dialog");
                }
            }
        }


        @Override
        public void actualOnPostExecute(@NonNull NoteEditor noteEditor, Boolean noException) {
            if (noException) {
                if (noteEditor.mProgressDialog != null && noteEditor.mProgressDialog.isShowing()) {
                    try {
                        noteEditor.mProgressDialog.dismiss();
                    } catch (IllegalArgumentException e) {
                        Timber.e(e, "Note Editor: Error on dismissing progress dialog");
                    }
                }
                if (mCloseAfter) {
                    if (mIntent != null) {
                        noteEditor.closeNoteEditor(mIntent);
                    } else {
                        noteEditor.closeNoteEditor();
                    }
                } else {
                    // Reset check for changes to fields
                    noteEditor.mFieldEdited = false;
                    noteEditor.mTagsEdited = false;
                }
            } else {
                // RuntimeException occurred on adding note
                noteEditor.closeNoteEditor(DeckPicker.RESULT_DB_ERROR, null);
            }
        }
    }

    private void displayErrorSavingNote() {
        int errorMessageId = getAddNoteErrorResource();
        UIUtils.showThemedToast(this, getResources().getString(errorMessageId), false);
    }


    protected @StringRes int getAddNoteErrorResource() {
        //COULD_BE_BETTER: We currently don't perform edits inside this class (wat), so we only handle adds.
        if (this.isClozeType()) {
            return R.string.note_editor_no_cloze_delations;
        }

        if (TextUtils.isEmpty(getCurrentFieldText(0))) {
            return R.string.note_editor_no_first_field;
        }

        if (allFieldsHaveContent()) {
            return R.string.note_editor_no_cards_created_all_fields;
        }

        //Otherwise, display "no cards created".
        return R.string.note_editor_no_cards_created;
    }


    private boolean allFieldsHaveContent() {
        for (String s : this.getCurrentFieldStrings()) {
            if (TextUtils.isEmpty(s)) {
                return false;
            }
        }
        return true;
    }


    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return;
        }
        Timber.d("onCreate()");

        mTagsDialogFactory = new TagsDialogFactory(this).attachToActivity(this);
        mMediaRegistration =  new MediaRegistration(this);

        super.onCreate(savedInstanceState);
        mFieldState.setInstanceState(savedInstanceState);
        setContentView(R.layout.note_editor);
        Intent intent = getIntent();
        if (savedInstanceState != null) {
            mCaller = savedInstanceState.getInt("caller");
            mAddNote = savedInstanceState.getBoolean("addNote");
            mCurrentDid = savedInstanceState.getLong("did");
            mSelectedTags = savedInstanceState.getStringArrayList("tags");
            mReloadRequired = savedInstanceState.getBoolean("reloadRequired");
            mPastedImageCache = (HashMap<String, String>) savedInstanceState.getSerializable("imageCache");
            mToggleStickyText = (HashMap<Integer, String>) savedInstanceState.getSerializable("toggleSticky");
            mChanged = savedInstanceState.getBoolean("changed");
        } else {
            mCaller = intent.getIntExtra(EXTRA_CALLER, CALLER_NOCALLER);
            if (mCaller == CALLER_NOCALLER) {
                String action = intent.getAction();
                if ((ACTION_CREATE_FLASHCARD.equals(action) || ACTION_CREATE_FLASHCARD_SEND.equals(action) || ACTION_PROCESS_TEXT.equals(action))) {
                    mCaller = CALLER_CARDEDITOR_INTENT_ADD;
                }
            }
        }

        startLoadingCollection();

        mOnboarding.onCreate();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        addInstanceStateToBundle(savedInstanceState);
        super.onSaveInstanceState(savedInstanceState);
    }


    private void addInstanceStateToBundle(@NonNull Bundle savedInstanceState) {
        Timber.i("Saving instance");
        savedInstanceState.putInt("caller", mCaller);
        savedInstanceState.putBoolean("addNote", mAddNote);
        savedInstanceState.putLong("did", mCurrentDid);
        savedInstanceState.putBoolean("changed", mChanged);
        savedInstanceState.putBoolean("reloadRequired", mReloadRequired);
        savedInstanceState.putIntegerArrayList("customViewIds", mCustomViewIds);
        savedInstanceState.putSerializable("imageCache", mPastedImageCache);
        savedInstanceState.putSerializable("toggleSticky", mToggleStickyText);
        if (mSelectedTags == null) {
            mSelectedTags = new ArrayList<>(0);
        }
        savedInstanceState.putStringArrayList("tags", mSelectedTags);
    }


    @NonNull
    private Bundle getFieldsAsBundleForPreview() {
        return NoteService.getFieldsAsBundleForPreview(mEditFields, shouldReplaceNewlines());
    }

    // Finish initializing the activity after the collection has been correctly loaded
    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);

        Intent intent = getIntent();
        Timber.d("NoteEditor() onCollectionLoaded: caller: %d", mCaller);

        registerExternalStorageListener();

        View mainView = findViewById(android.R.id.content);

        mToolbar = findViewById(R.id.editor_toolbar);
        mToolbar.setFormatListener(formatter -> {
            View currentFocus = getCurrentFocus();
            if (!(currentFocus instanceof FieldEditText)) {
                return;
            }
            modifyCurrentSelection(formatter, (FieldEditText) currentFocus);
        });

        // Sets the background and icon color of toolbar respectively.
        mToolbar.setBackgroundColor(Themes.getColorFromAttr(NoteEditor.this, R.attr.toolbarBackgroundColor));
        mToolbar.setIconColor(Themes.getColorFromAttr(NoteEditor.this, R.attr.toolbarIconColor));

        enableToolbar(mainView);

        mFieldsLayoutContainer = findViewById(R.id.CardEditorEditFieldsLayout);

        mTagsButton = findViewById(R.id.CardEditorTagButton);
        mCardsButton = findViewById(R.id.CardEditorCardsButton);
        mCardsButton.setOnClickListener(v -> {
            Timber.i("NoteEditor:: Cards button pressed. Opening template editor");
            showCardTemplateEditor();
        });


        mAedictIntent = false;
        mCurrentEditedCard = null;

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
            case CALLER_CARDBROWSER_ADD:
            case CALLER_CARDEDITOR:
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
        mAllModelIds = NoteTypeSpinnerUtils.setupNoteTypeSpinner(this, mNoteTypeSpinner, col);

        // Deck Selector
        TextView deckTextView = findViewById(R.id.CardEditorDeckText);
        // If edit mode and more than one card template distinguish between "Deck" and "Card deck"
        if (!mAddNote && mEditorNote.model().getJSONArray("tmpls").length()>1) {
            deckTextView.setText(R.string.CardEditorCardDeck);
        }
        mDeckSpinnerSelection = new DeckSpinnerSelection(this, col, this.findViewById(R.id.note_deck_spinner), false, true);
        mDeckSpinnerSelection.initializeNoteEditorDeckSpinner(mCurrentEditedCard, mAddNote);

        mCurrentDid = intent.getLongExtra(EXTRA_DID, mCurrentDid);
        String getTextFromSearchView = intent.getStringExtra(EXTRA_TEXT_FROM_SEARCH_VIEW);
        setDid(mEditorNote);

        setNote(mEditorNote, FieldChangeType.onActivityCreation(shouldReplaceNewlines()));

        if (mAddNote) {
            mNoteTypeSpinner.setOnItemSelectedListener(new SetNoteTypeListener());
            setTitle(R.string.menu_add_note);
            // set information transferred by intent
            String contents = null;
            String[] tags = intent.getStringArrayExtra(EXTRA_TAGS);
            if (mSourceText != null) {
                if (mAedictIntent && (mEditFields.size() == 3) && mSourceText[1].contains("[")) {
                    contents = mSourceText[1].replaceFirst("\\[", "\u001f" + mSourceText[0] + "\u001f");
                    contents = contents.substring(0, contents.length() - 1);
                } else if (!mEditFields.isEmpty()) {
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
            if (tags != null) {
                setTags(tags);
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
        if (mAddNote) {
            Timber.i("onCollectionLoaded() Edit note activity successfully started in add card mode with node id %d", mEditorNote.getId());
        }

        // don't open keyboard if not adding note
        if (!mAddNote) {
            this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }

        //set focus to FieldEditText 'first' on startup like Anki desktop
        if (mEditFields != null && !mEditFields.isEmpty()) {
            // EXTRA_TEXT_FROM_SEARCH_VIEW takes priority over other intent inputs
            if (getTextFromSearchView != null && !getTextFromSearchView.isEmpty()) {
                mEditFields.getFirst().setText(getTextFromSearchView);
            }
            mEditFields.getFirst().requestFocus();
        }
    }

    private void modifyCurrentSelection(Toolbar.TextFormatter formatter, FieldEditText textBox) {

        // get the current text and selection locations
        int selectionStart = textBox.getSelectionStart();
        int selectionEnd = textBox.getSelectionEnd();

        // #6762 values are reversed if using a keyboard and pressing Ctrl+Shift+LeftArrow
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);

        String text = "";
        if (textBox.getText() != null) {
            text = textBox.getText().toString();
        }

        // Split the text in the places where the formatting will take place
        String beforeText = text.substring(0, start);
        String selectedText = text.substring(start, end);
        String afterText = text.substring(end);

        Toolbar.TextWrapper.StringFormat formatResult = formatter.format(selectedText);
        String newText = formatResult.result;

        // Update text field with updated text and selection
        int length = beforeText.length() + newText.length() + afterText.length();
        StringBuilder newFieldContent = new StringBuilder(length).append(beforeText).append(newText).append(afterText);
        textBox.setText(newFieldContent);

        int newStart = formatResult.start;
        int newEnd = formatResult.end;
        textBox.setSelection(start + newStart, start + newEnd);
    }


    @Override
    protected void onStop() {
        super.onStop();
        if (!isFinishing()) {
            WidgetStatus.update(this);
            UIUtils.saveCollectionInBackground();
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {

        if (mToolbar != null && mToolbar.onKeyUp(keyCode, event)) {
            return true;
        }

        switch (keyCode) {

            //some hardware keyboards swap between mobile/desktop mode...
            //when in mobile mode KEYCODE_NUMPAD_ENTER & KEYCODE_ENTER are equiv. but
            //both need to be captured for desktop keyboards
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (event.isCtrlPressed()) {
                    saveNote();
                }
                break;

            case KeyEvent.KEYCODE_D:
                //null check in case Spinner is moved into options menu in the future
                if (event.isCtrlPressed() && (mDeckSpinnerSelection.hasSpinner())) {
                    mDeckSpinnerSelection.displayDeckOverrideDialog(getCol());
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
            case KeyEvent.KEYCODE_C: {
                if (event.isCtrlPressed() && event.isShiftPressed()) {
                    insertCloze(event.isAltPressed() ? AddClozeType.SAME_NUMBER : AddClozeType.INCREMENT_NUMBER);
                    // Anki Desktop warns, but still inserts the cloze
                    if (!isClozeType()) {
                        UIUtils.showSimpleSnackbar(this, R.string.note_editor_insert_cloze_no_cloze_note_type, false);
                    }
                }
                break;
            }
            case KeyEvent.KEYCODE_P: {
                if (event.isCtrlPressed()) {
                    Timber.i("Ctrl+P: Preview Pressed");
                    performPreview();
                }
                break;
            }
            default:
                break;
        }

        // 7573: Ctrl+Shift+[Num] to select a field
        if (event.isCtrlPressed() && event.isShiftPressed() && KeyUtils.isDigit(event)) {
            int digit = KeyUtils.getDigit(event);
            // map: '0' -> 9; '1' to 0
            int indexBase10 = ((digit - 1) % 10 + 10) % 10;
            selectFieldIndex(indexBase10);
        }

        return super.onKeyUp(keyCode, event);
    }


    private void selectFieldIndex(int index) {
        Timber.i("Selecting field index %d", index);
        if (mEditFields.size() <= index || index < 0) {
            Timber.i("Index out of range: %d", index);
            return;
        }


        FieldEditText field;
        try {
            field = mEditFields.get(index);
        } catch (IndexOutOfBoundsException e) {
            Timber.w(e,"Error selecting index %d", index);
            return;
        }

        field.requestFocus();
        Timber.d("Selected field");
    }


    private void insertCloze(AddClozeType addClozeType) {
        View v = getCurrentFocus();
        if (!(v instanceof FieldEditText)) {
            return;
        }
        FieldEditText editText = (FieldEditText) v;
        convertSelectedTextToCloze(editText, addClozeType);
    }


    private void fetchIntentInformation(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }
        mSourceText = new String[2];

        if (ACTION_PROCESS_TEXT.equals(intent.getAction())) {
            String stringExtra = intent.getStringExtra(EXTRA_PROCESS_TEXT);
            Timber.d("Obtained %s from intent: %s", stringExtra, EXTRA_PROCESS_TEXT);
            mSourceText[0] = stringExtra != null ? stringExtra : "";
            mSourceText[1] = "";
        } else if (ACTION_CREATE_FLASHCARD.equals(intent.getAction())) {
            // mSourceLanguage = extras.getString(SOURCE_LANGUAGE);
            // mTargetLanguage = extras.getString(TARGET_LANGUAGE);
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

            mSourceText[0] = messages.first;
            mSourceText[1] = messages.second;
        }
    }


    private boolean addFromAedict(String extra_text) {
        String category;
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
                            return false;
                        }
                    }
                    UIUtils.showThemedToast(NoteEditor.this, getResources().getString(R.string.intent_aedict_empty),false);
                    return true;
                }
            }
        }
        UIUtils.showThemedToast(NoteEditor.this, getResources().getString(R.string.intent_aedict_category), false);
        return true;
    }


    private boolean hasUnsavedChanges() {
        if (!collectionHasLoaded()) {
            return false;
        }

        // changed note type?
        if (!mAddNote && mCurrentEditedCard != null) {
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


    private boolean collectionHasLoaded() {
        return mAllModelIds != null;
    }


    @VisibleForTesting
    void saveNote() {
        final Resources res = getResources();
        if (mSelectedTags == null) {
            mSelectedTags = new ArrayList<>(0);
        }

        saveToggleStickyMap();

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
            JSONArray tags = new JSONArray();
            for (String t : mSelectedTags) {
                tags.put(t);
            }
            getCol().getModels().current().put("tags", tags);
            getCol().getModels().setChanged();
            mReloadRequired = true;
            TaskManager.launchCollectionTask(new CollectionTask.AddNote(mEditorNote), saveNoteHandler());
            updateFieldsFromStickyText();
        } else {
            // Check whether note type has been changed
            final Model newModel = getCurrentlySelectedModel();
            final Model oldModel = (mCurrentEditedCard == null) ? null : mCurrentEditedCard.model();
            if (!newModel.equals(oldModel)) {
                mReloadRequired = true;
                if (mModelChangeCardMap.size() < mEditorNote.numberOfCards() || mModelChangeCardMap.containsValue(null)) {
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
            if (mCurrentEditedCard != null && mCurrentEditedCard.getDid() != mCurrentDid) {
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
                mChanged = true;
            }
            closeNoteEditor();
        }
    }

    /**
     * Change the note type from oldModel to newModel, handling the case where a full sync will be required
     */
    private void changeNoteTypeWithErrorHandling(final Model oldModel, final Model newModel) {
        Resources res = getResources();
        try {
            changeNoteType(oldModel, newModel);
        } catch (ConfirmModSchemaException e) {
            e.log();
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
    private void changeNoteType(Model oldModel, Model newModel) throws ConfirmModSchemaException {
        final long noteId = mEditorNote.getId();
        getCol().getModels().change(oldModel, noteId, newModel, mModelChangeFieldMap, mModelChangeCardMap);
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
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateToolbar();
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
                Editable fieldText = mEditFields.get(i).getText();
                if (fieldText != null && fieldText.length() > 0) {
                    menu.findItem(R.id.action_copy_note).setEnabled(true);
                    break;
                } else if (i == mEditFields.size() - 1) {
                    menu.findItem(R.id.action_copy_note).setEnabled(false);
                }
            }
        }

        menu.findItem(R.id.action_show_toolbar).setChecked(!shouldHideToolbar());
        menu.findItem(R.id.action_capitalize).setChecked(AnkiDroidApp.getSharedPrefs(this).getBoolean("note_editor_capitalize", true));

        return super.onCreateOptionsMenu(menu);
    }


    protected static boolean shouldReplaceNewlines() {
        return AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance()).getBoolean("noteEditorNewlineReplace", true);
    }

    protected static boolean shouldHideToolbar() {
        return !AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance()).getBoolean("noteEditorShowToolbar", true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            Timber.i("NoteEditor:: Home button pressed");
            closeCardEditorWithCheck();
            return true;
        } else if (itemId == R.id.action_preview) {
            Timber.i("NoteEditor:: Preview button pressed");
            performPreview();
            return true;
        } else if (itemId == R.id.action_save) {
            Timber.i("NoteEditor:: Save note button pressed");
            saveNote();
            return true;
        } else if (itemId == R.id.action_add_note_from_note_editor) {
            Timber.i("NoteEditor:: Add Note button pressed");
            addNewNote();
            return true;
        } else if (itemId == R.id.action_copy_note) {
            Timber.i("NoteEditor:: Copy Note button pressed");
            copyNote();
            return true;
        } else if (itemId == R.id.action_font_size) {
            Timber.i("NoteEditor:: Font Size button pressed");
            IntegerDialog repositionDialog = new IntegerDialog();
            repositionDialog.setArgs(getString(R.string.menu_font_size), getEditTextFontSize(), 2);
            repositionDialog.setCallbackRunnable(this::setFontSize);
            showDialogFragment(repositionDialog);
            return true;
        } else if (itemId == R.id.action_show_toolbar) {
            item.setChecked(!item.isChecked());
            AnkiDroidApp.getSharedPrefs(this).edit().putBoolean("noteEditorShowToolbar", item.isChecked()).apply();
            updateToolbar();
        } else if (itemId == R.id.action_capitalize) {
            Timber.i("NoteEditor:: Capitalize button pressed. New State: %b", !item.isChecked());
            item.setChecked(!item.isChecked()); // Needed for Android 9
            toggleCapitalize(item.isChecked());
            return true;
        } else if (itemId == R.id.action_scroll_toolbar) {
            item.setChecked(!item.isChecked());
            AnkiDroidApp.getSharedPrefs(this).edit().putBoolean("noteEditorScrollToolbar", item.isChecked()).apply();
            updateToolbar();
        }
        return super.onOptionsItemSelected(item);
    }


    private void toggleCapitalize(boolean value) {
        AnkiDroidApp.getSharedPrefs(this).edit().putBoolean("note_editor_capitalize", value).apply();
        for (FieldEditText f : mEditFields) {
            f.setCapitalize(value);
        }
    }


    private void setFontSize(Integer fontSizeSp) {
        if (fontSizeSp == null || fontSizeSp <= 0) {
            return;
        }
        Timber.i("Setting font size to %d", fontSizeSp);
        AnkiDroidApp.getSharedPrefs(this).edit().putInt("note_editor_font_size", fontSizeSp).apply();
        for (FieldEditText f : mEditFields) {
            f.setTextSize(fontSizeSp);
        }
    }


    private String getEditTextFontSize() {
        // Note: We're not being accurate here - the initial value isn't actually what's supplied in the layout.xml
        // So a value of 18sp in the XML won't be 18sp on the TextView, but it's close enough.
        // Values are setFontSize are whole when returned.
        float sp = TextViewUtil.getTextSizeSp(mEditFields.getFirst());
        return Integer.toString(Math.round(sp));
    }


    public void addNewNote() {
        openNewNoteEditor(intent -> { });
    }


    public void copyNote() {
        openNewNoteEditor(intent -> {
            intent.putExtra(EXTRA_CONTENTS, getFieldsText());
            if (mSelectedTags != null) {
                intent.putExtra(EXTRA_TAGS, mSelectedTags.toArray(new String[0]));
            }
        });
    }

    private void openNewNoteEditor(Consumer<Intent> intentEnricher) {
        Intent intent = new Intent(NoteEditor.this, NoteEditor.class);
        intent.putExtra(EXTRA_CALLER, CALLER_CARDEDITOR);
        intent.putExtra(EXTRA_DID, mCurrentDid);
        //mutate event with additional properties
        intentEnricher.accept(intent);
        startActivityForResultWithAnimation(intent, REQUEST_ADD, START);
    }


    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------

    @VisibleForTesting
    void performPreview() {
        Intent previewer = new Intent(NoteEditor.this, CardTemplatePreviewer.class);

        if (mCurrentEditedCard != null) {
            previewer.putExtra("ordinal", mCurrentEditedCard.getOrd());
        }
        previewer.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, TemporaryModel.saveTempModel(this, mEditorNote.model()));

        // Send the previewer all our current editing information
        Bundle noteEditorBundle = new Bundle();
        addInstanceStateToBundle(noteEditorBundle);
        noteEditorBundle.putBundle("editFields", getFieldsAsBundleForPreview());
        previewer.putExtra("noteEditorBundle", noteEditorBundle);
        startActivityForResultWithoutAnimation(previewer, REQUEST_PREVIEW);
    }


    /**
     * finish when sd card is ejected
     */
    private void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction() != null && intent.getAction().equals(SdCardReceiver.MEDIA_EJECT)) {
                        finishWithoutAnimation();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(SdCardReceiver.MEDIA_EJECT);
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }


    private void setTags(@NonNull String[] tags) {
        mSelectedTags = new ArrayList<>(Arrays.asList(tags));
        updateTags();
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
        if (intent == null) {
            intent = new Intent();
        }
        if (mReloadRequired) {
            intent.putExtra("reloadRequired", true);
        }
        if (mChanged) {
            intent.putExtra("noteChanged", true);
        }

        closeNoteEditor(result, intent);
    }


    private void closeNoteEditor(int result, @Nullable Intent intent) {
        if (intent != null) {
            setResult(result, intent);
        } else {
            setResult(result);
        }
        // ensure there are no orphans from possible edit previews
        TemporaryModel.clearTempModelFiles();
        if (mCaller == CALLER_CARDEDITOR_INTENT_ADD) {
            finishWithAnimation(NONE);
        } else {
            finishWithAnimation(END);
        }
    }

    private void showTagsDialog() {
        if (mSelectedTags == null) {
            mSelectedTags = new ArrayList<>(0);
        }
        ArrayList<String> tags = new ArrayList<>(getCol().getTags().all());
        ArrayList<String> selTags = new ArrayList<>(mSelectedTags);
        TagsDialog dialog = mTagsDialogFactory.newTagsDialog().withArguments(TagsDialog.DialogType.EDIT_TAGS, selTags, tags);
        showDialogFragment(dialog);
    }

    @Override
    public void onSelectedTags(List<String> selectedTags, List<String> indeterminateTags, int option) {
        if (!mSelectedTags.equals(selectedTags)) {
            mTagsEdited = true;
        }
        mSelectedTags = (ArrayList<String>) selectedTags;
        updateTags();
    }

    private void showCardTemplateEditor() {
        Intent intent = new Intent(this, CardTemplateEditor.class);
        // Pass the model ID
        intent.putExtra("modelId", getCurrentlySelectedModel().getLong("id"));
        Timber.d("showCardTemplateEditor() for model %s", intent.getLongExtra("modelId", NOT_FOUND_NOTE_TYPE));
        // Also pass the note id and ord if not adding new note
        if (!mAddNote && mCurrentEditedCard != null) {
            intent.putExtra("noteId", mCurrentEditedCard.note().getId());
            Timber.d("showCardTemplateEditor() with note %s", mCurrentEditedCard.note().getId());
            intent.putExtra("ordId", mCurrentEditedCard.getOrd());
            Timber.d("showCardTemplateEditor() with ord %s", mCurrentEditedCard.getOrd());
        }
        startActivityForResultWithAnimation(intent, REQUEST_TEMPLATE_EDIT, START);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Timber.d("onActivityResult() with request/result: %s/%s", requestCode, resultCode);
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == DeckPicker.RESULT_DB_ERROR) {
            closeNoteEditor(DeckPicker.RESULT_DB_ERROR, null);
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
                    if (extras == null) {
                        break;
                    }
                    int index = extras.getInt(MultimediaEditFieldActivity.EXTRA_RESULT_FIELD_INDEX);
                    IField field = (IField) extras.get(MultimediaEditFieldActivity.EXTRA_RESULT_FIELD);
                    if (field == null) {
                        break;
                    }
                    MultimediaEditableNote note = getCurrentMultimediaEditableNote(col);
                    note.setField(index, field);
                    FieldEditText fieldEditText = mEditFields.get(index);
                    // Completely replace text for text fields (because current text was passed in)
                    String formattedValue = field.getFormattedValue();
                    if (field.getType() == EFieldType.TEXT) {
                        fieldEditText.setText(formattedValue);
                    }
                    // Insert text at cursor position if the field has focus
                    else if (fieldEditText.getText() != null) {
                        insertStringInField(fieldEditText, formattedValue);
                    }
                    //DA - I think we only want to save the field here, not the note.
                    NoteService.saveMedia(col, note);
                    mChanged = true;
                }
                break;
            }
            case REQUEST_TEMPLATE_EDIT: {
                    // Model can change regardless of exit type - update ourselves and CardBrowser
                    mReloadRequired = true;
                    mEditorNote.reloadModel();
                    if (mCurrentEditedCard == null || !mEditorNote.cids().contains(mCurrentEditedCard.getId())) {
                        if (!mAddNote) {
                            /* This can occur, for example, if the
                             * card type was deleted or if the note
                             * type was changed without moving this
                             * card to another type. */
                            Timber.d("onActivityResult() template edit return - current card is gone, close note editor");
                            UIUtils.showThemedToast(this, getString(R.string.template_for_current_card_deleted), false);
                            closeNoteEditor();
                        } else {
                            Timber.d("onActivityResult() template edit return, in add mode, just re-display");
                        }
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

    /** Appends a string at the selection point, or appends to the end if not in focus */
    @VisibleForTesting
    void insertStringInField(EditText fieldEditText, String formattedValue) {
        if (fieldEditText.hasFocus()) {
            // Crashes if start > end, although this is fine for a selection via keyboard.
            int start = fieldEditText.getSelectionStart();
            int end = fieldEditText.getSelectionEnd();

            fieldEditText.getText().replace(Math.min(start, end), Math.max(start, end), formattedValue);
        }
        // Append text if the field doesn't have focus
        else {
            fieldEditText.getText().append(formattedValue);
        }
    }


    /** @param col Readonly variable to get cache dir */
    private MultimediaEditableNote getCurrentMultimediaEditableNote(Collection col) {
        MultimediaEditableNote note = NoteService.createEmptyNote(mEditorNote.model());

        String[] fields = getCurrentFieldStrings();
        NoteService.updateMultimediaNoteFromFields(col, fields, mEditorNote.getMid(), note);
        return note;
    }


    public JSONArray getCurrentFields() {
        return mEditorNote.model().getJSONArray("flds");
    }

    @CheckResult
    public String[] getCurrentFieldStrings() {
        if (mEditFields == null) {
            return new String[0];
        }
        String[] ret = new String[mEditFields.size()];
        for (int i = 0; i < mEditFields.size(); i++) {
            ret[i] = getCurrentFieldText(i);
        }
        return ret;
    }


    private void populateEditFields(FieldChangeType type, boolean editModelMode) {
        List<FieldEditLine> editLines = mFieldState.loadFieldEditLines(type);
        mFieldsLayoutContainer.removeAllViews();
        mCustomViewIds.clear();
        mEditFields = new LinkedList<>();

        // Use custom font if selected from preferences
        Typeface customTypeface = null;
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        String customFont = preferences.getString("browserEditorFont", "");
        if (!"".equals(customFont)) {
            customTypeface = AnkiFont.getTypeface(this, customFont);
        }
        ClipboardManager clipboard = ContextCompat.getSystemService(this, ClipboardManager.class);

        FieldEditLine previous = null;

        mCustomViewIds.ensureCapacity(editLines.size());
        for (int i = 0; i < editLines.size(); i++) {
            FieldEditLine edit_line_view = editLines.get(i);
            mCustomViewIds.add(edit_line_view.getId());
            FieldEditText newTextbox = edit_line_view.getEditText();
            newTextbox.setImagePasteListener(this::onImagePaste);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                if (i == 0) {
                    findViewById(R.id.note_deck_spinner).setNextFocusForwardId(newTextbox.getId());
                }
                if (previous != null) {
                    previous.getLastViewInTabOrder().setNextFocusForwardId(newTextbox.getId());
                }
            }
            previous = edit_line_view;

            edit_line_view.setEnableAnimation(animationEnabled());

            // TODO: Remove the >= M check - one callback works on API 11.
            if (CompatHelper.getSdkVersion() >= Build.VERSION_CODES.M) {
                // Use custom implementation of ActionMode.Callback customize selection and insert menus
                Field f = new Field(getFieldByIndex(i), getCol());
                ActionModeCallback actionModeCallback = new ActionModeCallback(newTextbox, f);
                edit_line_view.setActionModeCallbacks(actionModeCallback);
            }

            edit_line_view.setTypeface(customTypeface);
            edit_line_view.setHintLocale(getHintLocaleForField(edit_line_view.getName()));
            initFieldEditText(newTextbox, i, !editModelMode);
            mEditFields.add(newTextbox);
            SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(this);
            if (prefs.getInt("note_editor_font_size", -1) > 0) {
                newTextbox.setTextSize(prefs.getInt("note_editor_font_size", -1));
            }
            newTextbox.setCapitalize(prefs.getBoolean("note_editor_capitalize", true));

            ImageButton mediaButton = edit_line_view.getMediaButton();
            ImageButton toggleStickyButton = edit_line_view.getToggleSticky();
            // Load icons from attributes
            int[] icons = Themes.getResFromAttr(this, new int[] { R.attr.attachFileImage, R.attr.upDownImage, R.attr.toggleStickyImage});
            // Make the icon change between media icon and switch field icon depending on whether editing note type
            if (editModelMode && allowFieldRemapping()) {
                // Allow remapping if originally more than two fields
                mediaButton.setBackgroundResource(icons[1]);
                setRemapButtonListener(mediaButton, i);
                toggleStickyButton.setBackgroundResource(0);
            } else if (editModelMode && !allowFieldRemapping()) {
                mediaButton.setBackgroundResource(0);
                toggleStickyButton.setBackgroundResource(0);
            } else {
                // Use media editor button if not changing note type
                mediaButton.setBackgroundResource(icons[0]);
                setMMButtonListener(mediaButton, i);
                // toggle sticky button
                toggleStickyButton.setBackgroundResource(icons[2]);
                setToggleStickyButtonListener(toggleStickyButton, i);
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && previous != null) {
                previous.getLastViewInTabOrder().setNextFocusForwardId(R.id.CardEditorTagButton);
            }

            mediaButton.setContentDescription(getString(R.string.multimedia_editor_attach_mm_content, edit_line_view.getName()));
            toggleStickyButton.setContentDescription(getString(R.string.note_editor_toggle_sticky, edit_line_view.getName()));
            mFieldsLayoutContainer.addView(edit_line_view);
        }
    }

    private boolean onImagePaste(EditText editText, Uri uri) {
        String imageTag = mMediaRegistration.onImagePaste(uri);
        if (imageTag == null) {
            return false;
        }
        insertStringInField(editText, imageTag);
        return true;
    }

    private void setMMButtonListener(ImageButton mediaButton, final int index) {
        mediaButton.setOnClickListener(v -> {
            Timber.i("NoteEditor:: Multimedia button pressed for field %d", index);
            if (mEditorNote.items()[index][1].length() > 0) {
                final Collection col = CollectionHelper.getInstance().getCol(NoteEditor.this);
                // If the field already exists then we start the field editor, which figures out the type
                // automatically
                IMultimediaEditableNote note = getCurrentMultimediaEditableNote(col);
                startMultimediaFieldEditor(index, note);
            } else {
                // Otherwise we make a popup menu allowing the user to choose between audio/image/text field
                // TODO: Update the icons for dark material theme, then can set 3rd argument to true
                PopupMenuWithIcons popup = new PopupMenuWithIcons(NoteEditor.this, v, true);
                MenuInflater inflater = popup.getMenuInflater();
                inflater.inflate(R.menu.popupmenu_multimedia_options, popup.getMenu());

                popup.setOnMenuItemClickListener(item -> {

                    int itemId = item.getItemId();
                    if (itemId == R.id.menu_multimedia_audio) {
                        Timber.i("NoteEditor:: Record audio button pressed");
                        startMultimediaFieldEditorForField(index, new AudioRecordingField());
                        return true;
                    } else if (itemId == R.id.menu_multimedia_audio_clip) {
                        Timber.i("NoteEditor:: Add audio clip button pressed");
                        startMultimediaFieldEditorForField(index, new AudioClipField());
                        return true;
                    } else if (itemId == R.id.menu_multimedia_photo) {
                        Timber.i("NoteEditor:: Add image button pressed");
                        startMultimediaFieldEditorForField(index, new ImageField());
                        return true;
                    } else if (itemId == R.id.menu_multimedia_text) {
                        Timber.i("NoteEditor:: Advanced editor button pressed");
                        startAdvancedTextEditor(index);
                        return true;
                    } else if (itemId == R.id.menu_multimedia_clear_field) {
                        Timber.i("NoteEditor:: Clear field button pressed");
                        clearField(index);
                    }
                    return false;
                });
                if (AdaptionUtil.isRestrictedLearningDevice()) {
                    popup.getMenu().findItem(R.id.menu_multimedia_photo).setVisible(false);
                    popup.getMenu().findItem(R.id.menu_multimedia_text).setVisible(false);
                }
                popup.show();
            }
        });
    }

    private void setToggleStickyButtonListener(ImageButton toggleStickyButton, final int index) {
        if (mToggleStickyText.get(index) == null) {
            toggleStickyButton.getBackground().setAlpha(64);
        } else {
            toggleStickyButton.getBackground().setAlpha(255);
        }

        toggleStickyButton.setOnClickListener(v -> {
            onToggleStickyText(toggleStickyButton, index);
        });
    }

    private void onToggleStickyText(ImageButton toggleStickyButton, int index) {
        String text = mEditFields.get(index).getFieldText();
        if (mToggleStickyText.get(index) == null) {
            mToggleStickyText.put(index, text);
            toggleStickyButton.getBackground().setAlpha(255);
            Timber.d("Saved Text:: %s", mToggleStickyText.get(index));
        } else {
            mToggleStickyText.remove(index);
            toggleStickyButton.getBackground().setAlpha(64);
        }
    }

    private void saveToggleStickyMap() {
        for (Map.Entry<Integer, String> index : mToggleStickyText.entrySet()) {
            mToggleStickyText.put(index.getKey(), mEditFields.get(index.getKey()).getFieldText());
        }
    }

    private void updateFieldsFromStickyText() {
        for (Map.Entry<Integer, String> index : mToggleStickyText.entrySet()) {
            // handle fields for different note type with different size
            if (index.getKey() < mEditFields.size()) {
                mEditFields.get(index.getKey()).setText(index.getValue());
            }
        }
    }

    @VisibleForTesting
    void clearField(int index) {
        setFieldValueFromUi(index, "");
    }


    private void startMultimediaFieldEditorForField(int index, IField field) {
        Collection col = CollectionHelper.getInstance().getCol(NoteEditor.this);
        IMultimediaEditableNote note = getCurrentMultimediaEditableNote(col);
        note.setField(index, field);
        startMultimediaFieldEditor(index, note);
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
                int idx = item.getItemId();
                Timber.i("NoteEditor:: User chose to remap to old field %d", idx);
                // Retrieve any existing mappings between newFieldIndex and idx
                Integer previousMapping = MapUtil.getKeyByValue(mModelChangeFieldMap, newFieldIndex);
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


    private void startMultimediaFieldEditor(final int index, IMultimediaEditableNote note) {
        IField field = note.getField(index);
        Intent editCard = new Intent(NoteEditor.this, MultimediaEditFieldActivity.class);
        editCard.putExtra(MultimediaEditFieldActivity.EXTRA_FIELD_INDEX, index);
        editCard.putExtra(MultimediaEditFieldActivity.EXTRA_FIELD, field);
        editCard.putExtra(MultimediaEditFieldActivity.EXTRA_WHOLE_NOTE, note);
        startActivityForResultWithoutAnimation(editCard, REQUEST_MULTIMEDIA_EDIT);
    }


    private void initFieldEditText(FieldEditText editText, final int index, boolean enabled) {
        // Listen for changes in the first field so we can re-check duplicate status.
        editText.addTextChangedListener(new EditFieldTextWatcher(index));
        if (index == 0) {
            editText.setOnFocusChangeListener((v, hasFocus) -> {
                try {
                    if (hasFocus) {
                        // we only want to decorate when we lose focus
                        return;
                    }
                    String[] currentFieldStrings = getCurrentFieldStrings();
                    if (currentFieldStrings.length != 2 || currentFieldStrings[1].length() > 0) {
                        // we only decorate on 2-field cards while second field is still empty
                        return;
                    }
                    String firstField = currentFieldStrings[0];
                    String decoratedText = NoteFieldDecorator.aplicaHuevo(firstField);
                    if (!decoratedText.equals(firstField)) {
                        // we only apply the decoration if it is actually different from the first field
                        setFieldValueFromUi(1, decoratedText);
                    }
                } catch (Exception e) {
                    Timber.w(e, "Unable to decorate text field");
                }
            });
        }

        // Sets the background color of disabled EditText.
        if (!enabled) {
            editText.setBackgroundColor(Themes.getColorFromAttr(NoteEditor.this, R.attr.editTextBackgroundColor));
        }
        editText.setEnabled(enabled);
    }

    @Nullable
    private Locale getHintLocaleForField(String name) {
        JSONObject field = getFieldByName(name);
        if (field == null) {
            return null;
        }
        return LanguageHintService.getLanguageHintForField(field);
    }

    @NonNull
    private JSONObject getFieldByIndex(int index) {
        return this.getCurrentlySelectedModel().getJSONArray("flds").getJSONObject(index);
    }

    @Nullable
    private JSONObject getFieldByName(String name) {
        Pair<Integer, JSONObject> pair;
        try {
            pair = Models.fieldMap(this.getCurrentlySelectedModel()).get(name);
        } catch (Exception e) {
            Timber.w("Failed to obtain field '%s'", name);
            return null;
        }
        if (pair == null) {
            return null;
        }
        return pair.second;
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


    private void setDuplicateFieldStyles() {
        FieldEditText field = mEditFields.get(0);
        // Keep copy of current internal value for this field.
        String oldValue = mEditorNote.getFields()[0];
        // Update the field in the Note so we can run a dupe check on it.
        updateField(field);
        // 1 is empty, 2 is dupe, null is neither.
        Note.DupeOrEmpty dupeCode = mEditorNote.dupeOrEmpty();
        // Change bottom line color of text field
        if (dupeCode == Note.DupeOrEmpty.DUPE) {
            field.setDupeStyle();
        } else {
            field.setDefaultStyle();
        }
        // Put back the old value so we don't interfere with modification detection
        mEditorNote.values()[0] = oldValue;
    }


    private String getFieldsText() {
        String[] fields = new String[mEditFields.size()];
        for (int i = 0; i < mEditFields.size(); i++) {
            fields[i] = getCurrentFieldText(i);
        }
        return Utils.joinFields(fields);
    }

    /** Returns the value of the field at the given index */
    private String getCurrentFieldText(int index) {
        Editable fieldText = mEditFields.get(index).getText();
        if (fieldText == null) {
            return "";
        }
        return fieldText.toString();
    }


    private void setDid(Note note) {
        // If the target deck ID has already been set, we use that value and avoid trying to
        // determine what it should be again. An existing value means we are resuming the activity
        // where the target deck was already decided by the user.
        if (mCurrentDid != 0) {
            mDeckSpinnerSelection.selectDeckById(mCurrentDid, false);
            return;
        }
        if (note == null || mAddNote || mCurrentEditedCard == null) {
            JSONObject model = getCol().getModels().current();
            if (getCol().get_config("addToCur", true)) {
                mCurrentDid = getCol().get_config_long(CURRENT_DECK);
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
        mDeckSpinnerSelection.selectDeckById(mCurrentDid, false);
    }


    /** Refreshes the UI using the currently selected model as a template */
    private void refreshNoteData(@NonNull FieldChangeType changeType) {
        setNote(null, changeType);
    }

    /** Handles setting the current note (non-null afterwards) and rebuilding the UI based on this note */
    private void setNote(Note note, @NonNull FieldChangeType changeType) {
        if (note == null || mAddNote) {
            Model model = getCol().getModels().current();
            mEditorNote = new Note(getCol(), model);
        } else {
            mEditorNote = note;
        }
        if (mSelectedTags == null) {
            mSelectedTags = mEditorNote.getTags();
        }
        // nb: setOnItemSelectedListener and populateEditFields need to occur after this
        setNoteTypePosition();
        setDid(note);
        updateTags();
        updateCards(mEditorNote.model());
        updateToolbar();
        populateEditFields(changeType, false);
        updateFieldsFromStickyText();
    }


    private void updateToolbar() {
        if (mToolbar == null) {
            return;
        }

        View editorLayout = findViewById(R.id.note_editor_layout);
        int bottomMargin = shouldHideToolbar() ? 0 : (int) getResources().getDimension(R.dimen.note_editor_toolbar_height);
        MarginLayoutParams params = (MarginLayoutParams) editorLayout.getLayoutParams();
        params.bottomMargin = bottomMargin;
        editorLayout.setLayoutParams(params);


        if (shouldHideToolbar()) {
            mToolbar.setVisibility(View.GONE);
            return;
        } else {
            mToolbar.setVisibility(View.VISIBLE);
        }

        mToolbar.clearCustomItems();

        View clozeIcon = mToolbar.getClozeIcon();
        if (mEditorNote.model().isCloze()) {
            Toolbar.TextFormatter clozeFormatter = s -> {
                Toolbar.TextWrapper.StringFormat stringFormat = new Toolbar.TextWrapper.StringFormat();
                String prefix = "{{c" + getNextClozeIndex() + "::";
                stringFormat.result = prefix + s + "}}";
                if (s.length() == 0) {
                    stringFormat.start = prefix.length();
                    stringFormat.end = prefix.length();
                } else {
                    stringFormat.start = 0;
                    stringFormat.end = stringFormat.result.length();
                }
                return stringFormat;
            };
            clozeIcon.setOnClickListener(l -> mToolbar.onFormat(clozeFormatter));
            clozeIcon.setVisibility(View.VISIBLE);
        } else {
            clozeIcon.setVisibility(View.GONE);
        }

        ArrayList<CustomToolbarButton> buttons = getToolbarButtons();

        for (CustomToolbarButton b : buttons) {

            // 0th button shows as '1' and is Ctrl + 1
            int visualIndex = b.getIndex() + 1;
            String text = Integer.toString(visualIndex);
            Drawable bmp = mToolbar.createDrawableForString(text);

            View v = mToolbar.insertItem(0, bmp, b.toFormatter());

            // Allow Ctrl + 1...Ctrl + 0 for item 10.
            v.setTag(Integer.toString(visualIndex % 10));

            v.setOnLongClickListener(discard -> {
                suggestRemoveButton(b);
                return true;
            });
        }

        // Let the user add more buttons (always at the end).
        // Sets the add custom tag icon color.
        final Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_add_toolbar_icon, null);
        drawable.setTint(Themes.getColorFromAttr(NoteEditor.this, R.attr.toolbarIconColor));
        mToolbar.insertItem(0, drawable, this::displayAddToolbarDialog);
    }

    @NonNull
    private ArrayList<CustomToolbarButton> getToolbarButtons() {
        Set<String> set = AnkiDroidApp.getSharedPrefs(this).getStringSet("note_editor_custom_buttons", HashUtil.HashSetInit(0));
        return CustomToolbarButton.fromStringSet(set);
    }

    private void saveToolbarButtons(ArrayList<CustomToolbarButton> buttons) {
        AnkiDroidApp.getSharedPrefs(this).edit()
                .putStringSet("note_editor_custom_buttons", CustomToolbarButton.toStringSet(buttons))
                .apply();
    }

    private void addToolbarButton(String prefix, String suffix) {
        if (TextUtils.isEmpty(prefix) && TextUtils.isEmpty(suffix)) {
            return;
        }

        ArrayList<CustomToolbarButton> toolbarButtons = getToolbarButtons();

        toolbarButtons.add(new CustomToolbarButton(toolbarButtons.size(), prefix, suffix));
        saveToolbarButtons(toolbarButtons);

        updateToolbar();
    }


    private void suggestRemoveButton(CustomToolbarButton button) {
        new MaterialDialog.Builder(this)
                .title(R.string.remove_toolbar_item)
                .positiveText(R.string.dialog_positive_delete)
                .negativeText(R.string.dialog_cancel)
                .onPositive((dialog, action) -> removeButton(button))
                .show();
    }

    private void removeButton(CustomToolbarButton button) {
        ArrayList<CustomToolbarButton> toolbarButtons = getToolbarButtons();

        toolbarButtons.remove(button.getIndex());

        saveToolbarButtons(toolbarButtons);
        updateToolbar();
    }

    private void displayAddToolbarDialog() {
        new MaterialDialog.Builder(this)
                .title(R.string.add_toolbar_item)
                .customView(R.layout.note_editor_toolbar_add_custom_item, true)
                .positiveText(R.string.dialog_positive_create)
                .neutralText(R.string.help)
                .negativeText(R.string.dialog_cancel)
                .onNeutral((m, v) -> openUrl(Uri.parse(getString(R.string.link_manual_note_format_toolbar))))
                .onPositive((m, v) -> {
                    View view = m.getView();
                    EditText et =  view.findViewById(R.id.note_editor_toolbar_before);
                    EditText et2 = view.findViewById(R.id.note_editor_toolbar_after);

                    addToolbarButton(et.getText().toString(), et2.getText().toString());
                })
                .show();
    }


    private void setNoteTypePosition() {
        // Set current note type and deck positions in spinners
        int position = mAllModelIds.indexOf(mEditorNote.model().getLong("id"));
        // set selection without firing selectionChanged event
        mNoteTypeSpinner.setSelection(position, false);
    }

    private void updateTags() {
        if (mSelectedTags == null) {
            mSelectedTags = new ArrayList<>(0);
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
            // If more than one card, and we have an existing card, underline existing card
            if (!mAddNote && tmpls.length() > 1 && model == mEditorNote.model() && mCurrentEditedCard != null &&
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
        mCardsButton.setText(HtmlCompat.fromHtml(
                getResources().getString(R.string.CardEditorCards, cardsList.toString()),
                HtmlCompat.FROM_HTML_MODE_LEGACY));
    }


    private boolean updateField(FieldEditText field) {
        String fieldContent = "";
        Editable fieldText = field.getText();
        if (fieldText != null) {
            fieldContent = fieldText.toString();
        }
        String correctedFieldContent = NoteService.convertToHtmlNewline(fieldContent, shouldReplaceNewlines());
        if (!mEditorNote.values()[field.getOrd()].equals(correctedFieldContent)) {
            mEditorNote.values()[field.getOrd()] = correctedFieldContent;
            return true;
        }
        return false;
    }


    private String tagsAsString(List<String> tags) {
        return TextUtils.join(" ", tags);
    }

    private Model getCurrentlySelectedModel() {
        return getCol().getModels().get(mAllModelIds.get(mNoteTypeSpinner.getSelectedItemPosition()));
    }


    /**
     * Update all the field EditText views based on the currently selected note type and the mModelChangeFieldMap
     */
    private void updateFieldsFromMap(Model newModel) {
        FieldChangeType type = FieldChangeType.refreshWithMap(newModel, mModelChangeFieldMap, shouldReplaceNewlines());
        populateEditFields(type, true);
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

    public String[][] getFieldsFromSelectedNote() {
        return mEditorNote.items();
    }

    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------



    private class SetNoteTypeListener implements OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            // If a new column was selected then change the key used to map from mCards to the column TextView
            //Timber.i("NoteEditor:: onItemSelected() fired on mNoteTypeSpinner");
            long oldModelId = getCol().getModels().current().getLong("id");
            @NonNull Long newId = mAllModelIds.get(pos);
            Timber.i("Changing note type to '%d", newId);
            if (oldModelId != newId) {
                Model model = getCol().getModels().get(newId);
                if (model == null) {
                    Timber.w("New model %s not found, not changing note type", newId);
                    return;
                }
                getCol().getModels().setCurrent(model);
                Deck currentDeck = getCol().getDecks().current();
                currentDeck.put("mid", newId);
                getCol().getDecks().save(currentDeck);
                // Update deck
                if (!getCol().get_config("addToCur", true)) {
                    mCurrentDid = model.optLong("did", Consts.DEFAULT_DECK_ID);
                }

                refreshNoteData(FieldChangeType.changeFieldCount(shouldReplaceNewlines()));
                setDuplicateFieldStyles();
                mDeckSpinnerSelection.updateDeckPosition(mCurrentDid);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            // Do Nothing
        }
    }


    /* Uses only if mCurrentEditedCard is set, so from reviewer or card browser.*/
    private class EditNoteTypeListener implements OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            // Get the current model
            long noteModelId = mCurrentEditedCard.model().getLong("id");
            // Get new model
            Model newModel = getCol().getModels().get(mAllModelIds.get(pos));
            if (newModel == null || newModel.getJSONArray("tmpls") == null) {
                Timber.w("newModel %s not found", mAllModelIds.get(pos));
                return;
            }
            // Configure the interface according to whether note type is getting changed or not
            if (mAllModelIds.get(pos) != noteModelId) {
                // Initialize mapping between fields of old model -> new model
                int itemsLength = mEditorNote.items().length;
                mModelChangeFieldMap = HashUtil.HashMapInit(itemsLength);
                for (int i=0; i < itemsLength; i++) {
                    mModelChangeFieldMap.put(i, i);
                }
                // Initialize mapping between cards new model -> old model
                int templatesLength = newModel.getJSONArray("tmpls").length();
                mModelChangeCardMap = HashUtil.HashMapInit(templatesLength);
                for (int i = 0; i < templatesLength ; i++) {
                    if (i < mEditorNote.numberOfCards()) {
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
                mDeckSpinnerSelection.setEnabledActionBarSpinner(false);
                mDeckSpinnerSelection.updateDeckPosition(mCurrentEditedCard.getDid());
                updateFieldsFromStickyText();
            } else {
                populateEditFields(FieldChangeType.refresh(shouldReplaceNewlines()), false);
                updateCards(mCurrentEditedCard.model());
                findViewById(R.id.CardEditorTagButton).setEnabled(true);
                //((LinearLayout) findViewById(R.id.CardEditorCardsButton)).setEnabled(false);
                mDeckSpinnerSelection.setEnabledActionBarSpinner(true);
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
        private final FieldEditText mTextBox;
        private final int mClozeMenuId = View.generateViewId();
        @RequiresApi(Build.VERSION_CODES.N)
        private final int mSetLanguageId = View.generateViewId();

        private ActionModeCallback(FieldEditText textBox, Field field) {
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
            if (menu.findItem(mClozeMenuId) != null) {
                return false;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && menu.findItem(mSetLanguageId) != null) {
                return false;
            }

            int initialSize = menu.size();

            if (isClozeType()) {
                menu.add(Menu.NONE, mClozeMenuId, 0, R.string.multimedia_editor_popup_cloze);
            }

            return initialSize != menu.size();
        }

        @SuppressLint("SetTextI18n")
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == mClozeMenuId) {
                convertSelectedTextToCloze(mTextBox, AddClozeType.INCREMENT_NUMBER);
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

    private void convertSelectedTextToCloze(FieldEditText textBox, AddClozeType addClozeType) {
        int nextClozeIndex = getNextClozeIndex();
        if (addClozeType == AddClozeType.SAME_NUMBER) {
            nextClozeIndex = nextClozeIndex - 1;
        }

        String prefix = "{{c" + Math.max(1, nextClozeIndex) + "::";

        String suffix = "}}";
        modifyCurrentSelection(new Toolbar.TextWrapper(prefix, suffix), textBox);
    }

    @NonNull
    private String previewNextClozeDeletion(int start, int end, CharSequence text) {
        // TODO: Code Duplication with the above

        CharSequence selectedText = text.subSequence(start, end);
        int nextClozeIndex = getNextClozeIndex();
        nextClozeIndex = Math.max(1, nextClozeIndex);


        // Update text field with updated text and selection
        return String.format("{{c%s::%s}}", nextClozeIndex, selectedText);
    }


    private boolean hasClozeDeletions() {
        return getNextClozeIndex() > 1;
    }

    private int getNextClozeIndex() {
        // BUG: This assumes all fields are inserted as: {{cloze:Text}}
        List<String> fieldValues = new ArrayList<>(mEditFields.size());
        for (FieldEditText e : mEditFields) {
            Editable editable = e.getText();
            String fieldValue = editable == null ? "" : editable.toString();
            fieldValues.add(fieldValue);
        }
        return ClozeUtils.getNextClozeIndex(fieldValues);
    }

    private boolean isClozeType() {
        return getCurrentlySelectedModel().isCloze();
    }


    @VisibleForTesting
    void startAdvancedTextEditor(int index) {
        TextField field = new TextField();
        field.setText(getCurrentFieldText(index));
        startMultimediaFieldEditorForField(index, field);
    }

    @VisibleForTesting
    void setFieldValueFromUi(int i, String newText) {
        FieldEditText editText = mEditFields.get(i);
        editText.setText(newText);
        new EditFieldTextWatcher(i).afterTextChanged(editText.getText());
    }



    @VisibleForTesting
    long getDeckId() {
        return mCurrentDid;
    }


    @SuppressWarnings("SameParameterValue")
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    FieldEditText getFieldForTest(int index) {
        return mEditFields.get(index);
    }


    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    void setCurrentlySelectedModel(long mid) {
        int position = mAllModelIds.indexOf(mid);
        if (position == -1) {
            throw new IllegalStateException(mid + " not found");
        }
        mNoteTypeSpinner.setSelection(position);
    }

    private class EditFieldTextWatcher implements TextWatcher {
        private final int mIndex;


        public EditFieldTextWatcher(int index) {

            this.mIndex = index;
        }

        @Override
        public void afterTextChanged(Editable arg0) {
            mFieldEdited = true;
            if (mIndex == 0) {
                setDuplicateFieldStyles();
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
    }

    private static class Field {
        private final JSONObject mField;
        private final Collection mCol;


        public Field(JSONObject fieldObject, Collection collection) {
            this.mField = fieldObject;
            this.mCol = collection;
        }
    }
}
