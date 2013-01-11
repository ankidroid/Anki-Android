package com.ichi2.anki;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.ichi2.anki.multimediacard.EFieldType;
import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.impl.AudioField;
import com.ichi2.anki.multimediacard.impl.ImageField;
import com.ichi2.anki.multimediacard.impl.TextField;

public class EditTextFieldActivity extends FragmentActivity
{

    public static final String EXTRA_RESULT_FIELD = "edit.field.result.field";
    public static final String EXTRA_RESULT_FIELD_INDEX = "edit.field.result.field.index";

    IField mField;
    IMultimediaEditableNote mNote;
    int mFieldIndex;

    private IFieldController mFieldController;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_text);

        mField = (IField) this.getIntent().getExtras().getSerializable(MultimediaCardEditorActivity.EXTRA_FIELD);

        mNote = (IMultimediaEditableNote) this.getIntent().getSerializableExtra(
                MultimediaCardEditorActivity.EXTRA_WHOLE_NOTE);

        mFieldIndex = this.getIntent().getIntExtra(MultimediaCardEditorActivity.EXTRA_FIELD_INDEX, 0);

        recreateEditingUi();

        // Handling absence of the action bar!
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion <= android.os.Build.VERSION_CODES.GINGERBREAD_MR1)
        {
            LinearLayout linearLayout = (LinearLayout) findViewById(R.id.LinearLayoutForSpareMenuFieldEdit);
            createSpareMenu(linearLayout);
        }
    }

    private void recreateEditingUi()
    {

        IControllerFactory controllerFactory = BasicControllerFactory.getInstance();

        mFieldController = controllerFactory.createControllerForField(mField);

        if(mFieldController == null)
        {
            Log.d(AnkiDroidApp.TAG, "Field controller creation failed");
            return;
        }
        
        mFieldController.setField(mField);
        mFieldController.setFieldIndex(mFieldIndex);
        mFieldController.setNote(mNote);
        mFieldController.setFragmentActivity(this);
              

        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.LinearLayoutInScrollViewFieldEdit);

        linearLayout.removeAllViews();

        mFieldController.createUI(linearLayout);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_edit_text, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
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

    private void createSpareMenu(LinearLayout linearLayout)
    {
        Button toTextButton = new Button(this);
        toTextButton.setText("Text");
        toTextButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                toTextField();
            }

        });
        linearLayout.addView(toTextButton);

        Button toImageButton = new Button(this);
        toImageButton.setText("Image");
        toImageButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                toImageField();
            }

        });
        linearLayout.addView(toImageButton);

        Button toAudioButton = new Button(this);
        toAudioButton.setText("Audio");
        toAudioButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                toAudioField();
            }

        });
        linearLayout.addView(toAudioButton);

        Button doneButton = new Button(this);
        doneButton.setText("Done");
        doneButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                done();
            }

        });
        linearLayout.addView(doneButton);

    }

    protected void done()
    {

        mFieldController.onDone();

        Intent resultData = new Intent();

        resultData.putExtra(EXTRA_RESULT_FIELD, mField);
        resultData.putExtra(EXTRA_RESULT_FIELD_INDEX, mFieldIndex);

        setResult(RESULT_OK, resultData);

        finish();
    }

    protected void toAudioField()
    {
        if (mField.getType() != EFieldType.AUDIO)
        {
            mField = new AudioField();
            recreateEditingUi();
        }
    }

    protected void toImageField()
    {
        if(mField.getType() != EFieldType.IMAGE)
        {
            mField = new ImageField();
            recreateEditingUi();
        }

    }

    protected void toTextField()
    {
        if(mField.getType() != EFieldType.TEXT)
        {
            mField = new TextField();
            recreateEditingUi();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (mFieldController != null)
        {
            mFieldController.onActivityResult(requestCode, resultCode, data);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}
