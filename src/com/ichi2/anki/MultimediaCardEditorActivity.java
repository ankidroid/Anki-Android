package com.ichi2.anki;

import java.io.File;

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
import android.widget.TextView;

import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.impl.MockNoteFactory;

public class MultimediaCardEditorActivity extends Activity {

	private static final int REQUEST_CODE_EDIT_FIELD = 1;
	static final String EXTRA_FIELD_INDEX = "multim.card.ed.extra.field.index";
	static final String EXTRA_FIELD = "multim.card.ed.extra.field";

	IMultimediaEditableNote mNote;

	private IMultimediaEditableNote getNotePrivate() {
		if (mNote == null) {
			mNote = loadNote();
		}

		return mNote;
	}

	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_multimedia_card_editor);

		LinearLayout linearLayout = (LinearLayout) findViewById(R.id.LinearLayoutInScrollView);
		IMultimediaEditableNote note = getNotePrivate();

		createUI(linearLayout, note);

	}

	private void createUI(LinearLayout linearLayout,
			IMultimediaEditableNote note) {

		linearLayout.removeAllViews();

		for (int i = 0; i < note.getNumberOfFields(); ++i) {
			createNewViewer(linearLayout, note.getField(i), i);
		}

	}

	private void createNewViewer(LinearLayout linearLayout, final IField field,
			final int index) {

		final MultimediaCardEditorActivity context = this;

		switch (field.getType()) {
		case TEXT:

			//Create a text field and an edit button, opening editing for the 
			//text field
			
			TextView textView = new TextView(this);
			textView.setText(field.getText());
			linearLayout.addView(textView,
					LinearLayout.LayoutParams.MATCH_PARENT);

			Button editButtonText = new Button(this);
			editButtonText.setText("Edit");
			linearLayout.addView(editButtonText,
					LinearLayout.LayoutParams.MATCH_PARENT);

			editButtonText.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					Intent i = new Intent(context, EditTextFieldActivity.class);
					i.putExtra(EXTRA_FIELD_INDEX, index);
					i.putExtra(EXTRA_FIELD, field);
					startActivityForResult(i, REQUEST_CODE_EDIT_FIELD);
				}

			});

			break;

		case IMAGE:
			
			ImageView imgView = new ImageView(this);
//			
//			 BitmapFactory.Options options = new BitmapFactory.Options();
//			  options.inSampleSize = 2;
//			  Bitmap bm = BitmapFactory.decodeFile(myJpgPath, options);
//			  jpgView.setImageBitmap(bm); 
			
			imgView.setImageURI(Uri.fromFile(new File(field.getImagePath())));
		    linearLayout.addView(imgView, LinearLayout.LayoutParams.MATCH_PARENT);
		    
		    Button editButtonImage = new Button(this);
			editButtonImage.setText("Edit");
			linearLayout.addView(editButtonImage,
					LinearLayout.LayoutParams.MATCH_PARENT);

			editButtonImage.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					Intent i = new Intent(context, EditImageActivity.class);
					i.putExtra(EXTRA_FIELD_INDEX, index);
					i.putExtra(EXTRA_FIELD, field);
					startActivityForResult(i, REQUEST_CODE_EDIT_FIELD);
				}

			});		    
			
			
			break;
			
		default:
			TextView unsupp = new TextView(this);
			unsupp.setText("Unsupported field type");
			unsupp.setEnabled(false);
			linearLayout.addView(unsupp);
			break;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_multimedia_card_editor, menu);
		return true;
	}

	// Loads from extras or whatever else
	private IMultimediaEditableNote loadNote() {
		return getMockNote();
	}

	// Temporary implemented
	private IMultimediaEditableNote getMockNote() {
		return MockNoteFactory.makeNote();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:

			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		if (requestCode == REQUEST_CODE_EDIT_FIELD) {

			if (resultCode == RESULT_OK) {
				
				IField field = (IField) data.getSerializableExtra(EXTRA_FIELD);
				int index = data.getIntExtra(EXTRA_FIELD_INDEX, -1);
				
				//Failed editing activity
				if(index == -1)
				{
					return;
				}

				getNotePrivate().setField(index, field);

			}

			super.onActivityResult(requestCode, resultCode, data);
		}

	}

}
