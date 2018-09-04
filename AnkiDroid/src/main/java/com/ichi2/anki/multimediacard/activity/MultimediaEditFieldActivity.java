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

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.R;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.fields.AudioField;
import com.ichi2.anki.multimediacard.fields.BasicControllerFactory;
import com.ichi2.anki.multimediacard.fields.EFieldType;
import com.ichi2.anki.multimediacard.fields.IControllerFactory;
import com.ichi2.anki.multimediacard.fields.IField;
import com.ichi2.anki.multimediacard.fields.IFieldController;
import com.ichi2.anki.multimediacard.fields.ImageField;
import com.ichi2.anki.multimediacard.fields.TextField;

import java.io.File;

import timber.log.Timber;

public class MultimediaEditFieldActivity extends AnkiActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    public static final String EXTRA_RESULT_FIELD = "edit.field.result.field";
    public static final String EXTRA_RESULT_FIELD_INDEX = "edit.field.result.field.index";

    public static final String EXTRA_FIELD_INDEX = "multim.card.ed.extra.field.index";
    public static final String EXTRA_FIELD = "multim.card.ed.extra.field";
    public static final String EXTRA_WHOLE_NOTE = "multim.card.ed.extra.whole.note";

    private static final String BUNDLE_KEY_SHUT_OFF = "key.edit.field.shut.off";
    private static final int REQUEST_AUDIO_PERMISSION = 0;
    private static final int REQUEST_CAMERA_PERMISSION = 1;

    IField mField;
    IMultimediaEditableNote mNote;
    int mFieldIndex;

    private IFieldController mFieldController;


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

        setContentView(R.layout.multimedia_edit_field_activity);
        View mainView = findViewById(android.R.id.content);
        Toolbar toolbar = (Toolbar) mainView.findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        mField = (IField) this.getIntent().getExtras().getSerializable(EXTRA_FIELD);

        mNote = (IMultimediaEditableNote) this.getIntent().getSerializableExtra(EXTRA_WHOLE_NOTE);

        mFieldIndex = this.getIntent().getIntExtra(EXTRA_FIELD_INDEX, 0);

        recreateEditingUi();
    }


    private void finishCancel() {
        Intent resultData = new Intent();
        setResult(RESULT_CANCELED, resultData);
        finish();
    }


    private void recreateEditingUi() {

        IControllerFactory controllerFactory = BasicControllerFactory.getInstance();

        mFieldController = controllerFactory.createControllerForField(mField);

        if (mFieldController == null) {
            Timber.d("Field controller creation failed");
            return;
        }

        // Request permission to record if audio field
        if (mField instanceof AudioField && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_AUDIO_PERMISSION);
            return;
        }

        // Request permission to use the camera if image field
        if (mField instanceof ImageField && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
            return;
        }
        mFieldController.setField(mField);
        mFieldController.setFieldIndex(mFieldIndex);
        mFieldController.setNote(mNote);
        mFieldController.setEditingActivity(this);

        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.LinearLayoutInScrollViewFieldEdit);

        linearLayout.removeAllViews();

        mFieldController.createUI(this, linearLayout);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_edit_text, menu);
        menu.findItem(R.id.multimedia_edit_field_to_text).setVisible(mField.getType() != EFieldType.TEXT);
        menu.findItem(R.id.multimedia_edit_field_to_audio).setVisible(mField.getType() != EFieldType.AUDIO);
        menu.findItem(R.id.multimedia_edit_field_to_image).setVisible(mField.getType() != EFieldType.IMAGE);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.multimedia_edit_field_to_text:
                Timber.i("To text field button pressed");
                mFieldController.onFocusLost();
                toTextField();
                supportInvalidateOptionsMenu();
                return true;

            case R.id.multimedia_edit_field_to_image:
                Timber.i("To image button pressed");
                mFieldController.onFocusLost();
                toImageField();
                supportInvalidateOptionsMenu();
                return true;

            case R.id.multimedia_edit_field_to_audio:
                Timber.i("To audio button pressed");
                mFieldController.onFocusLost();
                toAudioField();
                supportInvalidateOptionsMenu();
                return true;

            case R.id.multimedia_edit_field_done:
                Timber.i("Save button pressed");
                done();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }


    private void createSpareMenu(LinearLayout linearLayout) {

        LayoutParams pars = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1);

        Button toTextButton = new Button(this);
        toTextButton.setText(gtxt(R.string.multimedia_editor_field_editing_text));
        toTextButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toTextField();
            }

        });
        linearLayout.addView(toTextButton, pars);

        Button toImageButton = new Button(this);
        toImageButton.setText(gtxt(R.string.multimedia_editor_field_editing_image));
        toImageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toImageField();
            }

        });
        linearLayout.addView(toImageButton, pars);

        Button toAudioButton = new Button(this);
        toAudioButton.setText(gtxt(R.string.multimedia_editor_field_editing_audio));
        toAudioButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toAudioField();
            }

        });
        linearLayout.addView(toAudioButton, pars);

        Button doneButton = new Button(this);
        doneButton.setText(gtxt(R.string.multimedia_editor_field_editing_done));
        doneButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                done();
            }

        });
        linearLayout.addView(doneButton, pars);

    }


    protected void done() {

        mFieldController.onDone();

        Intent resultData = new Intent();

        boolean bChangeToText = false;

        if (mField.getType() == EFieldType.IMAGE) {
            if (mField.getImagePath() == null) {
                bChangeToText = true;
            }

            if (!bChangeToText) {
                File f = new File(mField.getImagePath());
                if (!f.exists()) {
                    bChangeToText = true;
                }
            }
        } else if (mField.getType() == EFieldType.AUDIO) {
            if (mField.getAudioPath() == null) {
                bChangeToText = true;
            }

            if (!bChangeToText) {
                File f = new File(mField.getAudioPath());
                if (!f.exists()) {
                    bChangeToText = true;
                }
            }
        }

        if (bChangeToText) {
            mField = new TextField();
        }

        resultData.putExtra(EXTRA_RESULT_FIELD, mField);
        resultData.putExtra(EXTRA_RESULT_FIELD_INDEX, mFieldIndex);

        setResult(RESULT_OK, resultData);

        finish();
    }


    protected void toAudioField() {
        if (mField.getType() != EFieldType.AUDIO) {
            mField = new AudioField();
            recreateEditingUi();
        }
    }


    protected void toImageField() {
        if (mField.getType() != EFieldType.IMAGE) {
            mField = new ImageField();
            recreateEditingUi();
        }

    }


    protected void toTextField() {
        if (mField.getType() != EFieldType.TEXT) {
            mField = new TextField();
            recreateEditingUi();
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mFieldController != null) {
            mFieldController.onActivityResult(requestCode, resultCode, data);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }


    public void onRequestPermissionsResult (int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_AUDIO_PERMISSION && permissions.length == 1) {
            // TODO:  Disable the record button / show some feedback to the user
            recreateEditingUi();
        }
        if (requestCode == REQUEST_CAMERA_PERMISSION && permissions.length == 1) {
            // We check permissions to set visibility on the camera button, just recreate
            recreateEditingUi();
        }
    }

    public void handleFieldChanged(IField newField) {
        mField = newField;
        recreateEditingUi();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mFieldController != null) {
            mFieldController.onDestroy();
        }

    }


    private String gtxt(int id) {
        return getText(id).toString();
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(BUNDLE_KEY_SHUT_OFF, true);
    }

}
