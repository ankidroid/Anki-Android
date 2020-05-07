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
import androidx.annotation.VisibleForTesting;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
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
import com.ichi2.utils.Permissions;

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

        Intent intent = this.getIntent();
        mField = getFieldFromIntent(intent);

        mNote = (IMultimediaEditableNote) intent.getSerializableExtra(EXTRA_WHOLE_NOTE);

        mFieldIndex = intent.getIntExtra(EXTRA_FIELD_INDEX, 0);

        recreateEditingUi(ChangeUIRequest.init(mField));
    }

    @VisibleForTesting
    public static IField getFieldFromIntent(Intent intent) {
        return (IField) intent.getExtras().getSerializable(EXTRA_FIELD);
    }


    private void finishCancel() {
        Timber.d("Completing activity via finishCancel()");
        Intent resultData = new Intent();
        setResult(RESULT_CANCELED, resultData);
        finishWithoutAnimation();
    }

    private boolean performPermissionRequest(IField field) {
        // Request permission to record if audio field
        if (field instanceof AudioRecordingField && !Permissions.canRecordAudio(this)) {
            Timber.d("Requesting Audio Permissions");
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.RECORD_AUDIO},
                    REQUEST_AUDIO_PERMISSION);
            return true;
        }

        // Request permission to use the camera if image field
        if (field instanceof ImageField && !Permissions.canUseCamera(this)) {
            Timber.d("Requesting Camera Permissions");
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

        //If we went through the permission check once, we don't need to do it again.
        //As we only get here a second time if we have the required permissions
        if (newUI.getRequiresPermissionCheck() && performPermissionRequest(newUI.getField())) {
            newUI.markAsPermissionRequested();
            return;
        }

        IControllerFactory controllerFactory = BasicControllerFactory.getInstance();

        IFieldController fieldController = controllerFactory.createControllerForField(newUI.getField());

        if (fieldController == null) {
            Timber.d("Field controller creation failed");
            UIRecreationHandler.onControllerCreationFailed(newUI, this);
            return;
        }

        UIRecreationHandler.onPreFieldControllerReplacement(mFieldController);

        mFieldController = fieldController;
        mField = newUI.getField();

        setupUIController(mFieldController);

        LinearLayout linearLayout = findViewById(R.id.LinearLayoutInScrollViewFieldEdit);

        linearLayout.removeAllViews();

        mFieldController.createUI(this, linearLayout);

        UIRecreationHandler.onPostUICreation(newUI, this);
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
                toTextField();
                return true;

            case R.id.multimedia_edit_field_to_image:
                Timber.i("To image button pressed");
                toImageField();
                return true;

            case R.id.multimedia_edit_field_to_audio:
                Timber.i("To audio recording button pressed");
                toAudioRecordingField();
                return true;

            case R.id.multimedia_edit_field_to_audio_clip:
                Timber.i("To audio clip button pressed");
                toAudioClipField();
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


    private void recreateEditingUIUsingCachedRequest() {
        Timber.d("recreateEditingUIUsingCachedRequest()");
        if (mCurrentChangeRequest == null) {
            cancelActivityWithAssertionFailure("mCurrentChangeRequest should be set before using cached request");
            return;
        }
        recreateEditingUi(mCurrentChangeRequest);
    }

    public void onRequestPermissionsResult (int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (mCurrentChangeRequest == null) {
            cancelActivityWithAssertionFailure("mCurrentChangeRequest should be set before requesting permissions");
            return;
        }

        Timber.d("onRequestPermissionsResult. Code: %d", requestCode);
        if (requestCode == REQUEST_AUDIO_PERMISSION && permissions.length == 1) {

            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                recreateEditingUIUsingCachedRequest();
                return;
            }

            UIUtils.showThemedToast(this,
                    getResources().getString(R.string.multimedia_editor_audio_permission_refused),
                    true);

            UIRecreationHandler.onRequiredPermissionDenied(mCurrentChangeRequest, this);

        }
        if (requestCode == REQUEST_CAMERA_PERMISSION && permissions.length == 1) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                UIUtils.showThemedToast(this,
                        getResources().getString(R.string.multimedia_editor_camera_permission_refused),
                        true);
            }

            // We check permissions to set visibility on the camera button, just recreate
            recreateEditingUIUsingCachedRequest();
        }
    }


    private void cancelActivityWithAssertionFailure(String logMessage) {
        Timber.wtf(logMessage);
        UIUtils.showThemedToast(this, getString(R.string.mutimedia_editor_assertion_failed), false);
        finishCancel();
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
        private boolean mRequiresPermissionCheck = true;

        /** Initial request when activity is created */
        public static final int ACTIVITY_LOAD = 0;
        /** A change in UI via the menu options. Cancellable */
        public static final int UI_CHANGE = 1;
        /** A change in UI via access to the activity. Not (yet) cancellable */
        public static final int EXTERNAL_FIELD_CHANGE = 2;

        private ChangeUIRequest(IField field, int state) {
            this.newField = field;
            this.state = state;
        }

        private IField getField() {
            return newField;
        }

        private static ChangeUIRequest init(IField field) {
            return new ChangeUIRequest(field, ACTIVITY_LOAD);
        }

        private static ChangeUIRequest uiChange(IField field) {
            return new ChangeUIRequest(field, UI_CHANGE);
        }

        private static ChangeUIRequest fieldChange(IField field) {
            return new ChangeUIRequest(field, EXTERNAL_FIELD_CHANGE);
        }

        private boolean getRequiresPermissionCheck() {
            return mRequiresPermissionCheck;
        }

        private void markAsPermissionRequested() {
            mRequiresPermissionCheck = false;
        }

        private int getState() {
            return state;
        }
    }

    /**
     * Class to contain logic relating to decisions made when recreating a UI.
     * Can later be converted to a non-static class to allow testing of the logic.
     * */
    private static final class UIRecreationHandler {

        /** Raised just before the field controller is replaced */
        private static void onPreFieldControllerReplacement(IFieldController previousFieldController) {
            Timber.d("onPreFieldControllerReplacement");
            //on init, we don't need to do anything
            if (previousFieldController == null) {
                return;
            }

            //Otherwise, clean up the previous screen.
            previousFieldController.onFocusLost();
        }

        /**
         * Raised when we were supplied with a field that could not generate a UI controller
         * Currently: We used a field for which we didn't know how to generate the UI
         * */
        private static void onControllerCreationFailed(ChangeUIRequest request, MultimediaEditFieldActivity activity) {
            Timber.d("onControllerCreationFailed. State: %d", request.getState());
            switch (request.getState()) {
                case ChangeUIRequest.ACTIVITY_LOAD:
                case ChangeUIRequest.EXTERNAL_FIELD_CHANGE:
                    //TODO: (Optional) change in functionality. Previously we'd be left with a menu, but no UI.
                    activity.finishCancel();
                    break;
                case ChangeUIRequest.UI_CHANGE:
                    break;
                default:
                    Timber.e("onControllerCreationFailed: Unhandled state: %s", request.getState());
                    break;
            }
        }

        private static void onPostUICreation(ChangeUIRequest request, MultimediaEditFieldActivity activity) {
            Timber.d("onPostUICreation. State: %d", request.getState());
            switch (request.getState()) {
                case ChangeUIRequest.UI_CHANGE:
                case ChangeUIRequest.EXTERNAL_FIELD_CHANGE:
                    activity.supportInvalidateOptionsMenu();
                    break;
                case ChangeUIRequest.ACTIVITY_LOAD:
                    break;
                default:
                    Timber.e("onPostUICreation: Unhandled state: %s", request.getState());
                    break;
            }
        }

        private static void onRequiredPermissionDenied(ChangeUIRequest request, MultimediaEditFieldActivity activity) {
            Timber.d("onRequiredPermissionDenied. State: %d", request.getState());
            switch (request.state) {
                case ChangeUIRequest.ACTIVITY_LOAD:
                    activity.finishCancel();
                    break;
                case ChangeUIRequest.UI_CHANGE:
                    return;
                case ChangeUIRequest.EXTERNAL_FIELD_CHANGE:
                    activity.recreateEditingUIUsingCachedRequest();
                    break;
                default:
                    Timber.e("onRequiredPermissionDenied: Unhandled state: %s", request.getState());
                    activity.finishCancel();
                    break;
            }
        }
    }

    @VisibleForTesting
    IFieldController getFieldController() {
        return mFieldController;
    }
}