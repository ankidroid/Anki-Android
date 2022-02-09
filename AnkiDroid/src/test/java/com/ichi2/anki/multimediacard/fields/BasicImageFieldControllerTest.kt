/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.multimediacard.fields

import android.app.Activity
import android.content.Intent
import androidx.annotation.CheckResult
import com.ichi2.anki.R
import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivityTestBase
import com.ichi2.testutils.AnkiAssert
import com.ichi2.testutils.MockContentResolver
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast
import java.io.File

@RunWith(RobolectricTestRunner::class)
open class BasicImageFieldControllerTest : MultimediaEditFieldActivityTestBase() {
    @Test
    fun constructionWithoutDataGivesNoError() {
        val controller: IFieldController = validControllerNoImage
        assertThat(controller, instanceOf(BasicImageFieldController::class.java))
    }

    @Test
    fun constructionWithDataSucceeds() {
        grantCameraPermission()
        val controller = getControllerForField(imageFieldWithData(), emptyNote, 0)
        assertThat(controller, instanceOf(BasicImageFieldController::class.java))
    }

    @Test
    fun nonExistingFileDoesNotDisplayPreview() {
        val controller = validControllerNoImage
        assertThat(controller.isShowingPreview, `is`(false))
        val f = mock(File::class.java)
        `when`(f.exists()).thenReturn(false)
        controller.setImagePreview(f, 100)
        assertThat(
            "A non existing file should not display a preview",
            controller.isShowingPreview,
            `is`(false)
        )
    }

    @Test
    fun erroringFileDoesNotDisplayPreview() {
        val controller = validControllerNoImage
        assertThat(controller.isShowingPreview, `is`(false))
        val f = mock(File::class.java)
        `when`(f.exists()).thenReturn(true) // true, but it'll throw due to being a mock.
        controller.setImagePreview(f, 100)
        assertThat(
            "A broken existing file should not display a preview",
            controller.isShowingPreview,
            `is`(false)
        )
    }

    @Test
    fun fileSelectedOnSVG() {
        val controller = validControllerNoImage
        val f = File("test.svg")
        controller.setImagePreview(f, 100)
        assertThat(
            "A SVG image file can't be previewed", ShadowToast.getTextOfLatestToast(),
            equalTo(getResourceString(R.string.multimedia_editor_svg_preview))
        )
        assertThat("A SVG image file can't be previewed", controller.isShowingPreview, `is`(false))
    }

    @Test
    fun invalidImageResultDoesNotCrashController() {
        val controller = validControllerNoImage
        val activity = setupActivityMock(controller, controller.mActivity)
        val mock = MockContentResolver.returningEmptyCursor()
        `when`(activity.contentResolver).thenReturn(mock)

        // Act & Assert
        AnkiAssert.assertDoesNotThrow { performImageResult(controller, Intent()) }
    }

    private fun performImageResult(controller: BasicImageFieldController, intent: Intent) {
        controller.onActivityResult(BasicImageFieldController.ACTIVITY_SELECT_IMAGE, Activity.RESULT_OK, intent)
    }

    @get:CheckResult
    protected val validControllerNoImage: BasicImageFieldController
        get() {
            grantCameraPermission()
            return getControllerForField(emptyImageField(), emptyNote, 0) as BasicImageFieldController
        }

    private fun imageFieldWithData(): IField {
        val field = emptyImageField()
        field.imagePath = targetContext.externalCacheDir.toString() + "/temp-photos/test"
        return field
    }

    companion object {
        private fun emptyImageField(): IField {
            return ImageField()
        }
    }
}
