package com.ichi2.anki.multimediacard.fields;

import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivityTestBase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;


@RunWith(RobolectricTestRunner.class)
public class BasicImageFieldControllerTest extends MultimediaEditFieldActivityTestBase {

    @Test
    public void constructionWithoutDataGivesNoError() {
        grantCameraPermission();

        IFieldController controller = getControllerForField(emptyImageField(), getEmptyNote(), 0);

        assertThat(controller, instanceOf(BasicImageFieldController.class));
    }

    @Test
    public void constructionWithDataSucceeds() {
        grantCameraPermission();

        IFieldController controller = getControllerForField(imageFieldWithData(), getEmptyNote(), 0);

        assertThat(controller, instanceOf(BasicImageFieldController.class));
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
