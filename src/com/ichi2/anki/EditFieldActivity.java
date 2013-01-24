package com.ichi2.anki;

import java.io.File;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import com.ichi2.anki.multimediacard.EFieldType;
import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.impl.AudioField;
import com.ichi2.anki.multimediacard.impl.ImageField;
import com.ichi2.anki.multimediacard.impl.TextField;

public class EditFieldActivity extends FragmentActivity
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

        if (mFieldController == null)
        {
            Log.d(AnkiDroidApp.TAG, "Field controller creation failed");
            return;
        }

        mFieldController.setField(mField);
        mFieldController.setFieldIndex(mFieldIndex);
        mFieldController.setNote(mNote);
        mFieldController.setEditingActivity(this);

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

        LayoutParams pars = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1);

        Button toTextButton = new Button(this);
        toTextButton.setText(gtxt(R.string.multimedia_editor_field_editing_text));
        toTextButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                toTextField();
            }

        });
        linearLayout.addView(toTextButton, pars);

        Button toImageButton = new Button(this);
        toImageButton.setText(gtxt(R.string.multimedia_editor_field_editing_image));
        toImageButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                toImageField();
            }

        });
        linearLayout.addView(toImageButton, pars);

        Button toAudioButton = new Button(this);
        toAudioButton.setText(gtxt(R.string.multimedia_editor_field_editing_audio));
        toAudioButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                toAudioField();
            }

        });
        linearLayout.addView(toAudioButton, pars);

        Button doneButton = new Button(this);
        doneButton.setText(gtxt(R.string.multimedia_editor_field_editing_done));
        doneButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                done();
            }

        });
        linearLayout.addView(doneButton, pars);

    }

    protected void done()
    {

        mFieldController.onDone();

        Intent resultData = new Intent();

        boolean bChangeToText = false;

        if (mField.getType() == EFieldType.IMAGE)
        {
            if (mField.getImagePath() == null)
            {
                bChangeToText = true;
            }

            if (!bChangeToText)
            {
                File f = new File(mField.getImagePath());
                if (!f.exists())
                {
                    bChangeToText = true;
                }
            }
        }
        else if (mField.getType() == EFieldType.AUDIO)
        {
            if (mField.getAudioPath() == null)
            {
                bChangeToText = true;
            }

            if (!bChangeToText)
            {
                File f = new File(mField.getAudioPath());
                if (!f.exists())
                {
                    bChangeToText = true;
                }
            }
        }

        if(bChangeToText)
        {
            mField = new TextField();
            mField.setText(" - ");
        }
        
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
        if (mField.getType() != EFieldType.IMAGE)
        {
            mField = new ImageField();
            recreateEditingUi();
        }

    }

    protected void toTextField()
    {
        if (mField.getType() != EFieldType.TEXT)
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

    public void handleFieldChanged(IField newField)
    {
        mField = newField;
        recreateEditingUi();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        mFieldController.onDestroy();

    }

    private String gtxt(int id)
    {
        return getText(id).toString();
    }
}
