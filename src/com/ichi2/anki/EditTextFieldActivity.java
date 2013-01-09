package com.ichi2.anki;

import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

public class EditTextFieldActivity extends Activity {

	public static final String EXTRA_RESULT_FIELD = "edit.field.result.field";
	public static final String EXTRA_RESULT_FIELD_INDEX = "edit.field.result.field.index";

	IField mField;
	IMultimediaEditableNote mNote;
	int mFieldIndex;

	private IFieldController mFieldController;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_edit_text);

		mField = (IField) this.getIntent().getExtras()
				.getSerializable(MultimediaCardEditorActivity.EXTRA_FIELD);
		
		mNote = (IMultimediaEditableNote) this.getIntent().getSerializableExtra(MultimediaCardEditorActivity.EXTRA_WHOLE_NOTE);
		
		mFieldIndex = this.getIntent().getIntExtra(MultimediaCardEditorActivity.EXTRA_FIELD_INDEX, 0);
		
		recreateEditingUi();
		

		// Handling absence of the action bar!
		int currentapiVersion = android.os.Build.VERSION.SDK_INT;
		if (currentapiVersion <= android.os.Build.VERSION_CODES.GINGERBREAD_MR1) {
			LinearLayout linearLayout = (LinearLayout) findViewById(R.id.LinearLayoutForSpareMenuFieldEdit);
			createSpareMenu(linearLayout);
		}

	}

	private void recreateEditingUi() {
		
		IControllerFactory controllerFactory =  BasicControllerFactory.getInstance();
		
		mFieldController = controllerFactory.createControllerForField(mField);
		
		mFieldController.setField(mField);
		mFieldController.setFieldIndex(mFieldIndex);
		mFieldController.setNote(mNote);
		
		LinearLayout linearLayout = (LinearLayout) findViewById(R.id.LinearLayoutInScrollViewFieldEdit);
		
		linearLayout.removeAllViews();
		
		mFieldController.createUI(linearLayout, this);
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_edit_text, menu);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.multimedia_edit_field_to_text:
			toTextField();
			return true;

		case R.id.multimedia_edit_field_to_image:
			toImageField();
			return true;

		case R.id.multimedia_edit_field_to_audio:
			toAudioField();
			return true;

		case R.id.multimedia_edit_field_done:
			done();
			return true;

		case android.R.id.home:

			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void createSpareMenu(LinearLayout linearLayout) {
		Button toTextButton = new Button(this);
		toTextButton.setText("Text");
		toTextButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				toTextField();
			}

		});
		linearLayout.addView(toTextButton);

		Button toImageButton = new Button(this);
		toImageButton.setText("Image");
		toImageButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				toImageField();
			}

		});
		linearLayout.addView(toImageButton);

		Button toAudioButton = new Button(this);
		toAudioButton.setText("Audio");
		toAudioButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				toAudioField();
			}

		});
		linearLayout.addView(toAudioButton);

		Button doneButton = new Button(this);
		doneButton.setText("Done");
		doneButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				done();
			}

		});
		linearLayout.addView(doneButton);

	}

	protected void done() {
		
		mFieldController.onDone();
		
		showToast("Done");
		Intent resultData = new Intent();

		resultData.putExtra(EXTRA_RESULT_FIELD, mField);
		resultData.putExtra(EXTRA_RESULT_FIELD_INDEX, mFieldIndex);

		setResult(RESULT_OK, resultData);

		finish();
	}

	protected void toAudioField() {
		showToast("To audio");
	}

	protected void toImageField() {
		showToast("To image");

	}

	protected void toTextField() {
		showToast("To text");
	}

	private void showToast(CharSequence text) {
		int duration = Toast.LENGTH_SHORT;
		Toast toast = Toast.makeText(this, text, duration);
		toast.show();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(mFieldController != null)
		{
			mFieldController.onActivityResult(requestCode, resultCode, data);
		}
		
		super.onActivityResult(requestCode, resultCode, data);
	}
	
}
