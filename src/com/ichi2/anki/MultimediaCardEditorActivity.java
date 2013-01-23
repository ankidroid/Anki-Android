package com.ichi2.anki;

import java.io.File;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import com.ichi2.anki.multimediacard.AudioView;
import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.impl.MockNoteFactory;
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote;
import com.ichi2.anki.servicelayer.NoteService;
import com.ichi2.async.DeckTask;
import com.ichi2.async.DeckTask.TaskData;
import com.ichi2.async.DeckTask.TaskListener;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;

public class MultimediaCardEditorActivity extends Activity
{
	public static final String EXTRA_CALLER = "CALLER";
	public static final int CALLER_NOCALLER = 0;
	public static final int CALLER_INDICLASH = 10;

	public static final int REQUEST_CODE_EDIT_FIELD = 1;

	public static final String EXTRA_FIELD_INDEX = "multim.card.ed.extra.field.index";
	public static final String EXTRA_FIELD = "multim.card.ed.extra.field";
	public static final String EXTRA_WHOLE_NOTE = "multim.card.ed.extra.whole.note";

	private static final String ACTION_CREATE_FLASHCARD = "org.openintents.action.CREATE_FLASHCARD";
	private static final String ACTION_CREATE_FLASHCARD_SEND = "android.intent.action.SEND";

	private LinearLayout mMainUIiLayout;

	/* Data variables below, not UI Elements */
	private Collection mCol;
	private long mCurrentDid;
	private IMultimediaEditableNote mNote;
	private Note mEditorNote;

	/**
	 * Indicates if editor is working with new note or one that exists already
	 * in the collection
	 */
	private boolean mAddNote;

	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_multimedia_card_editor);

		_initData();
		_initActionBar();

		mMainUIiLayout = (LinearLayout) findViewById(R.id.LinearLayoutInScrollView);

		recreateUi(mNote);
	}

	/**
	 * Creates a TabBar in case action bar is not present
	 */
	private void _initActionBar()
	{
		int currentapiVersion = android.os.Build.VERSION.SDK_INT;
		if (currentapiVersion <= android.os.Build.VERSION_CODES.GINGERBREAD_MR1)
		{
			LinearLayout linearLayout = (LinearLayout) findViewById(R.id.LinearLayoutForSpareMenu);
			createSpareMenu(linearLayout);
		}
	}

    private void createSpareMenu(LinearLayout linearLayout)
    {
        LayoutParams pars = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1);

        Button saveButton = new Button(this);
        saveButton.setText(getString(R.string.CardEditorSaveButton));
        saveButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                save();
            }
        });
        linearLayout.addView(saveButton, pars);

        Button deleteButton = new Button(this);
        deleteButton.setText(getString(R.string.menu_delete_note));
        deleteButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                delete();
            }
        });
        linearLayout.addView(deleteButton, pars);
    }

	private void recreateUi(IMultimediaEditableNote note)
	{

		LinearLayout linearLayout = mMainUIiLayout;

		linearLayout.removeAllViews();

		for (int i = 0; i < note.getNumberOfFields(); ++i)
		{
			createNewViewer(linearLayout, note.getField(i), i);
		}

	}

	private void putExtrasAndStartEditActivity(final IField field, final int index, Intent i)
	{

		i.putExtra(EXTRA_FIELD_INDEX, index);
		i.putExtra(EXTRA_FIELD, field);
		i.putExtra(EXTRA_WHOLE_NOTE, mNote);

		startActivityForResult(i, REQUEST_CODE_EDIT_FIELD);
	}

	private void createNewViewer(LinearLayout linearLayout, final IField field, final int index)
	{

		final MultimediaCardEditorActivity context = this;

		switch (field.getType())
		{
			case TEXT:

				// Create a text field and an edit button, opening editing for
				// the
				// text field

				TextView textView = new TextView(this);
				textView.setText(field.getText());
				linearLayout.addView(textView, LinearLayout.LayoutParams.MATCH_PARENT);

				break;

			case IMAGE:

				ImageView imgView = new ImageView(this);
				//
				// BitmapFactory.Options options = new BitmapFactory.Options();
				// options.inSampleSize = 2;
				// Bitmap bm = BitmapFactory.decodeFile(myJpgPath, options);
				// jpgView.setImageBitmap(bm);

				imgView.setImageURI(Uri.fromFile(new File(field.getImagePath())));
				linearLayout.addView(imgView, LinearLayout.LayoutParams.MATCH_PARENT);

				break;
			case AUDIO:
				AudioView audioView = AudioView.createPlayerInstance(this, R.drawable.av_play, R.drawable.av_pause,
						R.drawable.av_stop, field.getAudioPath());
				linearLayout.addView(audioView);
				break;

			default:
				TextView unsupp = new TextView(this);
				unsupp.setText("Unsupported field type");
				unsupp.setEnabled(false);
				linearLayout.addView(unsupp);
				break;
		}

		Button editButtonText = new Button(this);
		editButtonText.setText("Edit");
		linearLayout.addView(editButtonText, LinearLayout.LayoutParams.MATCH_PARENT);

		editButtonText.setOnClickListener(new View.OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				Intent i = new Intent(context, EditFieldActivity.class);
				putExtrasAndStartEditActivity(field, index, i);
			}

		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.activity_multimedia_card_editor, menu);
		return true;
	}

	// Temporary implemented
	private IMultimediaEditableNote getMockNote()
	{
		return MockNoteFactory.makeNote();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
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
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{

		if (requestCode == REQUEST_CODE_EDIT_FIELD)
		{

			if (resultCode == RESULT_OK)
			{

				IField field = (IField) data.getSerializableExtra(EditFieldActivity.EXTRA_RESULT_FIELD);
				int index = data.getIntExtra(EditFieldActivity.EXTRA_RESULT_FIELD_INDEX, -1);

				// Failed editing activity
				if (index == -1)
				{
					return;
				}

				mNote.setField(index, field);
				recreateUi(mNote);
			}

			super.onActivityResult(requestCode, resultCode, data);
		}

	}

	private void delete()
	{
		CharSequence text = "Delete clicked!";
		showToast(text);
	}

	private void showToast(CharSequence text)
	{
		int duration = Toast.LENGTH_SHORT;
		Toast toast = Toast.makeText(this, text, duration);
		toast.show();
	}

	/**
	 * If a note has been passed, loads it. Otherwise creates a new Note
	 * 
	 * Current does not support editing of note, just creating new notes
	 * according to some note type
	 */
	private void _initData()
	{
		// Intent intent = getIntent();
		// mCaller = intent.getIntExtra(EXTRA_CALLER, CALLER_NOCALLER);
		// if (mCaller == CALLER_NOCALLER)
		// {
		// String action = intent.getAction();
		// if (action != null
		// && (ACTION_CREATE_FLASHCARD.equals(action) ||
		// ACTION_CREATE_FLASHCARD_SEND.equals(action)))
		// {
		// mCaller = CALLER_INDICLASH;
		// }
		// }

		// Log.i(AnkiDroidApp.TAG, "CardEditor: caller: " + mCaller);
		try
		{
			mCol = AnkiDroidApp.getCol();

			mCurrentDid = mCol.getDecks().current().getLong("id");
			if (mCol.getDecks().isDyn(mCurrentDid))
			{
				mCurrentDid = 1;
			}

			mAddNote = true;

			JSONObject currentModel = mCol.getModels().current();
			mNote = NoteService.createEmptyNote(currentModel);
			if (mNote == null)
			{
				throw new RuntimeException("Cannot create a Note");
			}

			mEditorNote = new Note(mCol, currentModel);
			mEditorNote.model().put("did", mCurrentDid);
			// TODO take care of tags @see CardEditor setNote(Note)
		}
		catch (JSONException e)
		{
			throw new RuntimeException(e);
		}
	}

	private void save()
	{
		NoteService.saveMedia((MultimediaEditableNote) mNote);
		NoteService.updateJsonNoteFromMultimediaNote(mNote, mEditorNote);
		TaskListener listener = new TaskListener()
		{

			@Override
			public void onProgressUpdate(TaskData... values)
			{
				// TODO Auto-generated method stub

			}

			@Override
			public void onPreExecute()
			{
				// TODO Auto-generated method stub

			}

			@Override
			public void onPostExecute(TaskData result)
			{
				// TODO Auto-generated method stub

			}
		};
		DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ADD_FACT, listener, new DeckTask.TaskData(mEditorNote));
		// TODO Check what the listener does here
	}
}
