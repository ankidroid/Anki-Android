package com.ichi2.anki.multimediacard.activity;

import android.Manifest;
import android.app.Application;
import android.content.Intent;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.fields.IField;
import com.ichi2.anki.multimediacard.fields.IFieldController;
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote;

import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowApplication;

import androidx.test.core.app.ApplicationProvider;

import static org.mockito.Mockito.when;

public abstract class MultimediaEditFieldActivityTestBase extends RobolectricTest {

    protected void grantCameraPermission() {
        Application application = ApplicationProvider.getApplicationContext();
        ShadowApplication app = Shadows.shadowOf(application);
        app.grantPermissions(Manifest.permission.CAMERA);
    }
    
    protected IFieldController getControllerForField(IField field, IMultimediaEditableNote note, int fieldIndex) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra(MultimediaEditFieldActivity.EXTRA_FIELD_INDEX, fieldIndex);
        intent.putExtra(MultimediaEditFieldActivity.EXTRA_FIELD, field);
        intent.putExtra(MultimediaEditFieldActivity.EXTRA_WHOLE_NOTE, note);
        return getControllerForIntent(intent);
    }

    protected IMultimediaEditableNote getEmptyNote() {
        MultimediaEditableNote note = new MultimediaEditableNote();
        note.setNumFields(1);
        return note;
    }

    protected IFieldController getControllerForIntent(Intent intent) {


        ActivityController multimediaController = Robolectric.buildActivity(MultimediaEditFieldActivity.class, intent)
                .create().start().resume().visible();
        MultimediaEditFieldActivity testCardTemplatePreviewer = (MultimediaEditFieldActivity) multimediaController.get();



        return testCardTemplatePreviewer.getFieldController();
    }

    protected MultimediaEditFieldActivity setupActivityMock(IFieldController controller, MultimediaEditFieldActivity mActivity) {
        MultimediaEditFieldActivity activity = Mockito.mock(MultimediaEditFieldActivity.class);
        when(activity.getResources()).thenReturn(mActivity.getResources());
        controller.setEditingActivity(activity);
        return activity;
    }
}
