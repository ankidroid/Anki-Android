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

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.R;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.fields.AudioClipField;
import com.ichi2.anki.multimediacard.fields.AudioRecordingField;
import com.ichi2.anki.multimediacard.fields.BasicControllerFactory;
import com.ichi2.anki.multimediacard.fields.EFieldType;
import com.ichi2.anki.multimediacard.fields.IControllerFactory;
import com.ichi2.anki.multimediacard.fields.IField;
import com.ichi2.anki.multimediacard.fields.IFieldController;
import com.ichi2.anki.multimediacard.fields.ImageField;
import com.ichi2.anki.multimediacard.fields.TextField;
import com.ichi2.anki.multimediacard.utils.Permissions;

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

    private IField mField;
    private IMultimediaEditableNote mNote;
    private int mFieldIndex;

    private IFieldController mFieldController;
    /**
     * Cached copy of the current request to change a field
     * Used to access past state from OnRequestPermissionsResultCallback
     * */
    private ChangeUIRequest mCurrentChangeRequest;

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
        Toolbar toolbar = mainView.findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        mField = (IField) this.getIntent().getExtras().getSerializable(EXTRA_FIELD);

        mNote = (IMultimediaEditableNote) this.getIntent().getSerializableExtra(EXTRA_WHOLE_NOTE);

        mFieldIndex = this.getIntent().getIntExtra(EXTRA_FIELD_INDEX, 0);

        recreateEditingUi(ChangeUIRequest.init(mField));
    }


    private void finishCancel() {
        Intent resultData = new Intent();
        setResult(RESULT_CANCELED, resultData);
        finishWithoutAnimation();
    }

    private boolean performPermissionRequest(IField field) {
        // Request permission to record if audio field
        if (field instanceof AudioRecordingField && !Permissions.canRecordAudio(this)) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.RECORD_AUDIO},
                    REQUEST_AUDIO_PERMISSION);
            return true;
        }

        // Request permission to use the camera if image field
        if (field instanceof ImageField && !Permissions.canUseCamera(this)) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
            return true;
        }

        return false;
    }

    /** Sets various properties required for IFieldController to be in a valid state */
    private void setupUIController(IFieldController fieldController) {
        fieldController.setField(mField);
        fieldController.setFieldIndex(mFieldIndex);
        fieldController.setNote(mNote);
        fieldController.setEditingActivity(this);
    }

    private void recreateEditingUi(ChangeUIRequest newUI) {
        Timber.d("recreateEditingUi()");

        //Permissions are checked async, save our current state to allow continuation
        mCurrentChangeRequest = newUI;

        if (performPermissionRequest(newUI.getField())) {
            return;
        }

        IControllerFactory controllerFactory = BasicControllerFactory.getInstance();

        mFieldController = controllerFactory.createControllerForField(mField);

        if (mFieldController == null) {
            Timber.d("Field controller creation failed");
            return;
        }

        if (performPermissionRequest(mField)) {
            return;
        }

        mField = newUI.getField();

        setupUIController(mFieldController);

        LinearLayout linearLayout = findViewById(R.id.LinearLayoutInScrollViewFieldEdit);

        linearLayout.removeAllViews();

        mFieldController.createUI(this, linearLayout);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Timber.d("onCreateOptionsMenu() - mField.getType() = %s", mField.getType());
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_edit_text, menu);
        menu.findItem(R.id.multimedia_edit_field_to_text).setVisible(mField.getType() != EFieldType.TEXT);
        menu.findItem(R.id.multimedia_edit_field_to_audio).setVisible(mField.getType() != EFieldType.AUDIO_RECORDING);
        menu.findItem(R.id.multimedia_edit_field_to_audio_clip).setVisible(mField.getType() != EFieldType.AUDIO_CLIP);
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
                Timber.i("To audio recording button pressed");
                mFieldController.onFocusLost();
                toAudioRecordingField();
                supportInvalidateOptionsMenu();
                return true;

            case R.id.multimedia_edit_field_to_audio_clip:
                Timber.i("To audio clip button pressed");
                mFieldController.onFocusLost();
                toAudioClipField();
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
        } else if (mField.getType() == EFieldType.AUDIO_RECORDING) {
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

        finishWithoutAnimation();
    }


    protected void toAudioRecordingField() {
        if (mField.getType() != EFieldType.AUDIO_RECORDING) {
            ChangeUIRequest request = ChangeUIRequest.uiChange(new AudioRecordingField());
            recreateEditingUi(request);
        }
    }

    protected void toAudioClipField() {
        if (mField.getType() != EFieldType.AUDIO_CLIP) {
            ChangeUIRequest request = ChangeUIRequest.uiChange(new AudioClipField());
            recreateEditingUi(request);
        }
    }


    protected void toImageField() {
        if (mField.getType() != EFieldType.IMAGE) {
            ChangeUIRequest request = ChangeUIRequest.uiChange(new ImageField());
            recreateEditingUi(request);
        }

    }


    protected void toTextField() {
        if (mField.getType() != EFieldType.TEXT) {
            ChangeUIRequest request = ChangeUIRequest.uiChange(new TextField());
            recreateEditingUi(request);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Timber.d("onActivityResult()");
        if (mFieldController != null) {
            mFieldController.onActivityResult(requestCode, resultCode, data);
        }

        super.onActivityResult(requestCode, resultCode, data);
        supportInvalidateOptionsMenu();
    }


    public void onRequestPermissionsResult (int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (mCurrentChangeRequest == null) {
            throw new IllegalStateException("mCurrentChangeRequest should be set before using cached request");
        }
        if (requestCode == REQUEST_AUDIO_PERMISSION && permissions.length == 1) {
            // TODO:  Disable the record button / show some feedback to the user
            recreateEditingUi(mCurrentChangeRequest);
        }
        if (requestCode == REQUEST_CAMERA_PERMISSION && permissions.length == 1) {
            // We check permissions to set visibility on the camera button, just recreate
            recreateEditingUi(mCurrentChangeRequest);
        }
    }

    public void handleFieldChanged(IField newField) {
        recreateEditingUi(ChangeUIRequest.fieldChange(newField));
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mFieldController != null) {
            mFieldController.onDestroy();
        }

    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(BUNDLE_KEY_SHUT_OFF, true);
    }

    /** Intermediate class to hold state for the onRequestPermissionsResult callback */
    private final static class ChangeUIRequest {
        private final IField newField;
        private final int state;

        /** Initial request when activity is created */
        static final int ACTIVITY_LOAD = 0;
        /** A change in UI via the menu options. Cancellable */
        static final int UI_CHANGE = 1;
        /** A change in UI via access to the activity. Not (yet) cancellable */
        static final int EXTERNAL_FIELD_CHANGE = 2;

        private ChangeUIRequest(IField field, int state) {
            this.newField = field;
            this.state = state;
        }

        public IField getField() {
            return newField;
        }

        static ChangeUIRequest init(IField field) {
            return new ChangeUIRequest(field, ACTIVITY_LOAD);
        }

        static ChangeUIRequest uiChange(IField field) {
            return new ChangeUIRequest(field, UI_CHANGE);
        }

        static ChangeUIRequest fieldChange(IField field) {
            return new ChangeUIRequest(field, EXTERNAL_FIELD_CHANGE);
        }

        int getState() {
            return state;
        }
    }
}
