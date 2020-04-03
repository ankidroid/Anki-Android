package com.ichi2.anki.multimediacard.fields;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;

import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivity;
import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivityTestBase;
import com.ichi2.testutils.AnkiAssert;
import com.ichi2.testutils.MockContentResolver;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.io.File;

import androidx.annotation.CheckResult;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;


@RunWith(RobolectricTestRunner.class)
public class BasicImageFieldControllerTest extends MultimediaEditFieldActivityTestBase {

    @Test
    public void constructionWithoutDataGivesNoError() {
        IFieldController controller = getValidControllerNoImage();
        assertThat(controller, instanceOf(BasicImageFieldController.class));
    }

    @Test
    public void constructionWithDataSucceeds() {
        grantCameraPermission();

        IFieldController controller = getControllerForField(imageFieldWithData(), getEmptyNote(), 0);

        assertThat(controller, instanceOf(BasicImageFieldController.class));
    }

    @Test
    public void nonExistingFileDoesNotDisplayPreview() {
        BasicImageFieldController controller = getValidControllerNoImage();
        assertThat(controller.isShowingPreview(), is(false));

        File f =  Mockito.mock(File.class);
        when(f.exists()).thenReturn(false);

        controller.setImagePreview(f, 100);

        assertThat("A non existing file should not display a preview",
                controller.isShowingPreview(),
                is(false));
    }

    @Test
    public void erroringFileDoesNotDisplayPreview() {
        BasicImageFieldController controller = getValidControllerNoImage();
        assertThat(controller.isShowingPreview(), is(false));

        File f =  Mockito.mock(File.class);
        when(f.exists()).thenReturn(true); //true, but it'll throw due to being a mock.

        controller.setImagePreview(f, 100);

        assertThat("A broken existing file should not display a preview",
                controller.isShowingPreview(),
                is(false));
    }


    @Test
    public void invalidImageResultDoesNotCrashController() {
        BasicImageFieldController controller = getValidControllerNoImage();
        MultimediaEditFieldActivity activity = setupActivityMock(controller, controller.mActivity);

        ContentResolver mock = MockContentResolver.returningEmptyCursor();
        when(activity.getContentResolver()).thenReturn(mock);

        //Act & Assert
        AnkiAssert.assertDoesNotThrow(() -> performImageResult(controller, new Intent()));
    }


    private void performImageResult(BasicImageFieldController controller, Intent intent) {
        controller.onActivityResult(BasicImageFieldController.ACTIVITY_SELECT_IMAGE, Activity.RESULT_OK, intent);
    }

    @CheckResult
    protected BasicImageFieldController getValidControllerNoImage() {
        grantCameraPermission();

        return (BasicImageFieldController) getControllerForField(emptyImageField(), getEmptyNote(), 0);
    }


    private static IField emptyImageField() {
        return new ImageField();
    }

    private static IField imageFieldWithData() {
        IField field = emptyImageField();
        field.setImagePath("test");
        return field;
    }
}
