/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

package com.ichi2.anki.multimediacard.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.multimediacard.AudioView;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.fields.IField;
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote;
import com.ichi2.anki.servicelayer.NoteService;
import com.ichi2.async.DeckTask;
import com.ichi2.async.DeckTask.TaskData;
import com.ichi2.async.DeckTask.TaskListener;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.themes.StyledDialog;
import com.ichi2.utils.BitmapUtil;
import com.ichi2.utils.ExifUtil;
import com.ichi2.utils.JSONNameComparator;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class MultimediaCardEditorActivity extends Activity {
    private static final String BUNDLE_KEY_SHUT_OFF = "key.multimedia.shut.off";
    public static final String EXTRA_CALLER = "CALLER";
    public static final int CALLER_NOCALLER = 0;
    public static final int CALLER_INDICLASH = 10;

    public static final int REQUEST_CODE_EDIT_FIELD = 1;

    public static final int RESULT_DELETED = 2; // -1, 0, 1 are taken by default
                                                // RESULT_CANCELLED, FIRST_USER,
                                                // DELETED

    public static final String EXTRA_FIELD_INDEX = "multim.card.ed.extra.field.index";
    public static final String EXTRA_FIELD = "multim.card.ed.extra.field";
    public static final String EXTRA_WHOLE_NOTE = "multim.card.ed.extra.whole.note";
    public static final String EXTRA_CARD_ID = "CARD_ID";

    // private static final String ACTION_CREATE_FLASHCARD =
    // "org.openintents.action.CREATE_FLASHCARD";
    // private static final String ACTION_CREATE_FLASHCARD_SEND = "android.intent.action.SEND";

    private static final int DIALOG_MODEL_SELECT = 1;
    private static final int DIALOG_DECK_SELECT = 2;

    private LinearLayout mEditorLayout;
    private Button mModelButton;
    private Button mDeckButton;

    /* Data variables below, not UI Elements */
    private Collection mCol;
    private long mCurrentDid;
    private IMultimediaEditableNote mNote;
    private Note mEditorNote;
    private Card mCard;

    /**
     * Indicates if editor is working with new note or one that exists already in the collection
     */
    private boolean mAddNote;
    private DisplayMetrics mMetrics;


    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            boolean b = savedInstanceState.getBoolean(BUNDLE_KEY_SHUT_OFF, false);
            if (b) {
                finishCancel();
                return;
            }
        }

        setContentView(R.layout.activity_multimedia_card_editor);

        initData();
        initUI();
        createEditorUI(mNote);
    }


    /**
     * Creates a TabBar in case action bar is not present as well as other UI Elements
     */
    private void initUI() {
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion <= android.os.Build.VERSION_CODES.GINGERBREAD_MR1) {
            LinearLayout linearLayout = (LinearLayout) findViewById(R.id.LinearLayoutForSpareMenu);
            createSpareMenu(linearLayout);
        }

        mEditorLayout = (LinearLayout) findViewById(R.id.LinearLayoutInScrollView);

        LinearLayout mToolsLayout = (LinearLayout) findViewById(R.id.LinearLayoutForButtons);

        LayoutParams pars = new LinearLayout.LayoutParams(AnkiDroidApp.getCompat().parentLayoutSize(),
                LayoutParams.WRAP_CONTENT, 1);

        Button swapButton = new Button(this);
        swapButton.setText(gtxt(R.string.multimedia_editor_activity_swap_button));
        mToolsLayout.addView(swapButton, pars);
        swapButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                swap();
            }
        });

        LinearLayout mButtonsLayout = new LinearLayout(this);
        mButtonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        mToolsLayout.addView(mButtonsLayout, pars);

        pars = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1);

        // DEPRECATED< BUT USED IN THE PROJECT.
        mModelButton = new Button(this);
        mModelButton.setOnClickListener(new OnClickListener() {
            @SuppressWarnings("deprecation")
            @Override
            public void onClick(View v) {
                showDialog(DIALOG_MODEL_SELECT);
            }
        });
        mButtonsLayout.addView(mModelButton, pars);

        mDeckButton = new Button(this);
        mDeckButton.setOnClickListener(new OnClickListener() {
            @SuppressWarnings("deprecation")
            @Override
            public void onClick(View v) {
                showDialog(DIALOG_DECK_SELECT);
            }
        });
        mButtonsLayout.addView(mDeckButton, pars);
    }


    protected void swap() {
        mNote.circularSwap();
        createEditorUI(mNote);
    }


    private void createSpareMenu(LinearLayout linearLayout) {
        LayoutParams pars = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1);

        Button saveButton = new Button(this);
        saveButton.setText(getString(R.string.dialog_positive_save));
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        linearLayout.addView(saveButton, pars);

        Button deleteButton = new Button(this);
        deleteButton.setText(getString(R.string.menu_delete_note));
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                delete();
            }
        });
        linearLayout.addView(deleteButton, pars);
    }


    /**
     * Creates the UI for editor area inside EditorLayout
     *
     * @param note
     */
    private void createEditorUI(IMultimediaEditableNote note) {
        try {
            if (mNote == null) {
                finishCancel();
                return;
            } else {
                for (int i = 0; i < mNote.getNumberOfFields(); ++i) {
                    IField f = mNote.getField(i);

                    if (f == null) {
                        finishCancel();
                        return;
                    }
                }
            }

            LinearLayout linearLayout = mEditorLayout;

            linearLayout.removeAllViews();

            for (int i = 0; i < note.getNumberOfFields(); ++i) {
                createNewViewer(linearLayout, note.getField(i), i);
            }

            mModelButton.setText(gtxt(R.string.multimedia_editor_activity_note_type) + " : "
                    + mEditorNote.model().getString("name"));
            mDeckButton.setText(gtxt(R.string.multimedia_editor_activity_deck) + " : "
                    + mCol.getDecks().get(mCurrentDid).getString("name"));
        } catch (JSONException e) {
            Log.e("Multimedia Editor", e.getMessage());
            return;
        }

    }


    private String gtxt(int id) {
        return getText(id).toString();
    }


    private void putExtrasAndStartEditActivity(final IField field, final int index, Intent i) {

        i.putExtra(EXTRA_FIELD_INDEX, index);
        i.putExtra(EXTRA_FIELD, field);
        i.putExtra(EXTRA_WHOLE_NOTE, mNote);

        startActivityForResult(i, REQUEST_CODE_EDIT_FIELD);
    }


    private void createNewViewer(LinearLayout linearLayout, final IField field, final int index) {

        final MultimediaCardEditorActivity context = this;

        switch (field.getType()) {
            case TEXT:

                // Create a text field and an edit button, opening editing for
                // the
                // text field

                TextView textView = new TextView(this);
                textView.setText(field.getText());
                linearLayout.addView(textView, AnkiDroidApp.getCompat().parentLayoutSize());

                break;

            case IMAGE:

                ImageView imgView = new ImageView(this);
                //
                // BitmapFactory.Options options = new BitmapFactory.Options();
                // options.inSampleSize = 2;
                // Bitmap bm = BitmapFactory.decodeFile(myJpgPath, options);
                // jpgView.setImageBitmap(bm);

                LinearLayout.LayoutParams p = new LayoutParams(AnkiDroidApp.getCompat().parentLayoutSize(),
                        LayoutParams.WRAP_CONTENT);

                File f = new File(field.getImagePath());

                Bitmap b = BitmapUtil.decodeFile(f, getMaxImageSize());

                int currentapiVersion = android.os.Build.VERSION.SDK_INT;
                if (currentapiVersion >= android.os.Build.VERSION_CODES.ECLAIR) {
                    b = ExifUtil.rotateFromCamera(f, b);
                }

                imgView.setImageBitmap(b);

                imgView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                imgView.setAdjustViewBounds(true);

                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(metrics);

                int height = metrics.heightPixels;
                int width = metrics.widthPixels;

                imgView.setMaxHeight((int) Math.round(height * 0.6));
                imgView.setMaxWidth((int) Math.round(width * 0.7));
                linearLayout.addView(imgView, p);

                break;
            case AUDIO:
                AudioView audioView = AudioView.createPlayerInstance(this, R.drawable.av_play, R.drawable.av_pause,
                        R.drawable.av_stop, field.getAudioPath());
                linearLayout.addView(audioView);
                break;

            default:
                Log.e("multimedia editor", "Unsupported field type found");
                break;
        }

        Button editButtonText = new Button(this);
        editButtonText.setText(gtxt(R.string.multimedia_editor_activity_edit_button));
        linearLayout.addView(editButtonText, AnkiDroidApp.getCompat().parentLayoutSize());

        editButtonText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent i = new Intent(context, EditFieldActivity.class);
                putExtrasAndStartEditActivity(field, index, i);
            }

        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_multimedia_card_editor, menu);
        return true;
    }


    // Temporary implemented
    // private IMultimediaEditableNote getMockNote()
    // {
    // return MockNoteFactory.makeNote();
    // }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.multimedia_delete_note:
                delete();
                return true;

            case R.id.multimedia_save_note:
                save();
                return true;

            case android.R.id.home:

                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_EDIT_FIELD) {
            if (resultCode == RESULT_OK) {

                IField field = (IField) data.getSerializableExtra(EditFieldActivity.EXTRA_RESULT_FIELD);
                int index = data.getIntExtra(EditFieldActivity.EXTRA_RESULT_FIELD_INDEX, -1);

                // Failed editing activity
                if (index == -1) {
                    return;
                }

                mNote.setField(index, field);
                createEditorUI(mNote);
            }

            super.onActivityResult(requestCode, resultCode, data);
        }
    }


    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_MODEL_SELECT:
                return showModelSelectDialog();

            case DIALOG_DECK_SELECT:
                return showDeckSelectDialog();

            default:
                break;

        }

        return null;
    }


    private Dialog showModelSelectDialog() {
        StyledDialog dialog = null;
        StyledDialog.Builder builder = new StyledDialog.Builder(this);

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
                Log.e("Multimedia Editor", e.getMessage());
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
                    Log.e("Multimedia Editor", e.getMessage());
                    return;
                }
                long newId = dialogIds.get(item);
                if (oldModelId != newId) {
                    changeCurrentModel(newId);
                    createEditorUI(mNote);
                }
            }
        });
        dialog = builder.create();
        return dialog;
    }


    private Dialog showDeckSelectDialog() {
        StyledDialog dialog = null;
        StyledDialog.Builder builder = new StyledDialog.Builder(this);

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
                Log.e("Multimedia Editor", e.getMessage());
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
                    if (mAddNote) {
                        try {
                            // TODO: remove from dynamic deck (see CardEditor.java in v2.1)
                            // TODO: mEditorNote.setDid(newId);
                            mEditorNote.model().put("did", newId);
                            mCol.getModels().setChanged();
                        } catch (JSONException e) {
                            Log.e("Multimedia Editor", e.getMessage());
                        }
                    }
                    mCurrentDid = newId;
                    createEditorUI(mNote);
                }
            }
        });

        dialog = builder.create();
        return dialog;
    }


    /**
     * Change current model for the Note. Changes both MultimediaNote and the mEditorNote (Note Object) and copies
     * existing values to both.
     *
     * @param newId
     */
    protected void changeCurrentModel(long newId) {
        try {
            JSONObject currentModel = mCol.getModels().get(newId);
            mCol.getModels().setCurrent(currentModel);
            JSONObject cdeck = mCol.getDecks().current();

            cdeck.put("mid", newId);

            mCol.getDecks().save(cdeck);
            int size = mNote.getNumberOfFields();
            String[] oldValues = new String[size];
            for (int i = 0; i < size; i++) {
                oldValues[i] = mNote.getField(i).getFormattedValue();
            }
            mEditorNote = new Note(mCol, currentModel);
            mEditorNote.model().put("did", mCurrentDid);

            MultimediaEditableNote newNote = NoteService.createEmptyNote(currentModel);
            for (int i = 0; i < newNote.getNumberOfFields(); i++) {
                if (i < mNote.getNumberOfFields()) {
                    newNote.setField(i, mNote.getField(i));
                }
            }
            mNote = newNote;

        } catch (JSONException e) {
            Log.e("Multimedia Editor", e.getMessage());
        }

    }


    private void delete() {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        if (mAddNote) {
                            finish();
                        } else {
                            // Do not really delete the node here, instead let
                            // the caller deal with deletion.
                            // FIXME: Since only CardBrowser calls us,
                            // CardBrowser handles deletion and Listview Update
                            setResult(RESULT_DELETED);
                            finish();
                        }
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        // Do nothing
                        break;
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(gtxt(R.string.multimedia_editor_activity_delete_question))
                .setPositiveButton(gtxt(R.string.multimedia_editor_activity_delete_confirm), dialogClickListener)
                .setNegativeButton(gtxt(R.string.multimedia_editor_activity_delete_reject), dialogClickListener).show();
    }


    /**
     * If a note has been passed, loads it. Otherwise creates a new Note It checks for a noteId, a long value with
     * EXTRA_NOTE_ID as key. If not 0, edits the note, else creates a new note
     */
    private void initData() {
        try {
            mCol = AnkiDroidApp.getCol();

            Intent callerIntent = getIntent();
            long cardId = callerIntent.getLongExtra(EXTRA_CARD_ID, 0);
            if (cardId != 0) {
                mCard = mCol.getCard(cardId);
                mCurrentDid = mCard.getDid();
                mEditorNote = mCard.note();
                mCol.getModels().setCurrent(mEditorNote.model());
                mNote = NoteService.createEmptyNote(mEditorNote.model());
                NoteService.updateMultimediaNoteFromJsonNote(mEditorNote, mNote);

                mAddNote = false;
            } else {
                mCurrentDid = mCol.getDecks().current().getLong("id");
                if (mCol.getDecks().isDyn(mCurrentDid)) {
                    mCurrentDid = 1;
                }

                mAddNote = true;

                JSONObject currentModel = mCol.getModels().current();
                mNote = NoteService.createEmptyNote(currentModel);
                if (mNote == null) {
                    Log.e("Multimedia Editor", "Cannot create a Note");
                    throw new Exception("Could not create a not");
                }

                mEditorNote = new Note(mCol, currentModel);
                mEditorNote.model().put("did", mCurrentDid);
            }
            // TODO take care of tags @see CardEditor setNote(Note)

        } catch (Exception e) {
            Log.e("Multimedia Editor", e.getMessage());

            finishCancel();

            return;
        }
    }


    private void finishCancel() {
        Intent resultData = new Intent();
        setResult(RESULT_CANCELED, resultData);
        finish();
    }


    private void save() {
        NoteService.saveMedia((MultimediaEditableNote) mNote);
        NoteService.updateJsonNoteFromMultimediaNote(mNote, mEditorNote);

        TaskListener listener = new TaskListener() {

            @Override
            public void onProgressUpdate(TaskData... values) {
                // TODO Auto-generated method stub

            }


            @Override
            public void onPreExecute() {
                // TODO Auto-generated method stub

            }


            @Override
            public void onPostExecute(TaskData result) {
                // TODO Auto-generated method stub

            }


            @Override
            public void onCancelled() {
                // TODO Auto-generated method stub
                
            }
        };

        if (mAddNote) {

            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ADD_FACT, listener, new DeckTask.TaskData(mEditorNote));
        } else {

            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_FACT, listener, new DeckTask.TaskData(mCol.getSched(),
                    mCard, false));
        }

        setResult(Activity.RESULT_OK);
        finish();
        animateRight();
    }


    private void animateRight() {
        ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.FADE);
    }


    private int getMaxImageSize() {
        DisplayMetrics metrics = getDisplayMetrics();

        int height = metrics.heightPixels;
        int width = metrics.widthPixels;

        return (int) Math.min(height * 0.4, width * 0.6);
    }


    protected DisplayMetrics getDisplayMetrics() {
        if (mMetrics == null) {
            mMetrics = new DisplayMetrics();
            this.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        }
        return mMetrics;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mEditorLayout == null) {
            return;
        }

        int n = mEditorLayout.getChildCount();
        for (int i = 0; i < n; ++i) {
            View child = mEditorLayout.getChildAt(i);

            if (child instanceof ImageView) {
                ImageView iv = (ImageView) child;
                BitmapUtil.freeImageView(iv);
            }

        }
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(BUNDLE_KEY_SHUT_OFF, true);

    }

}
