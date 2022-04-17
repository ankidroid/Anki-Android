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
package com.ichi2.anki

import android.app.Activity
import android.os.Looper.getMainLooper
import com.canhub.cropper.CropImageActivity
import com.ichi2.anki.multimediacard.activity.LoadPronunciationActivity
import com.ichi2.anki.multimediacard.activity.TranslationActivity
import com.ichi2.testutils.ActivityList
import com.ichi2.testutils.ActivityList.ActivityLaunchParam
import com.ichi2.testutils.EmptyApplication
import com.ichi2.utils.ExceptionUtil.getFullStackTrace
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.util.stream.Collectors

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = EmptyApplication::class) // no point in Application init if we don't use it
@KotlinCleanup("IDE lint")
@KotlinCleanup("See if we can combine Parameter and JvmField")
@KotlinCleanup("`is` -> equalTo")
class ActivityStartupUnderBackupTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField
    var mLauncher: ActivityLaunchParam? = null

    // Only used for display, but needs to be defined
    @ParameterizedRobolectricTestRunner.Parameter(1)
    @JvmField
    var mActivityName: String? = null
    @Before
    fun before() {
        notYetHandled(CropImageActivity::class.java.simpleName, "cannot implemented - activity from canhub.cropper")
        notYetHandled(IntentHandler::class.java.simpleName, "Not working (or implemented) - inherits from Activity")
        notYetHandled(VideoPlayer::class.java.simpleName, "Not working (or implemented) - inherits from Activity")
        notYetHandled(LoadPronunciationActivity::class.java.simpleName, "Not working (or implemented) - inherits from Activity")
        notYetHandled(Preferences::class.java.simpleName, "Not working (or implemented) - inherits from AppCompatPreferenceActivity")
        notYetHandled(TranslationActivity::class.java.simpleName, "Not working (or implemented) - inherits from FragmentActivity")
        notYetHandled(DeckOptions::class.java.simpleName, "Not working (or implemented) - inherits from AppCompatPreferenceActivity")
        notYetHandled(FilteredDeckOptions::class.java.simpleName, "Not working (or implemented) - inherits from AppCompatPreferenceActivity")
    }

    /**
     * Tests that each activity can handle [AnkiDroidApp.getInstance] returning null
     * This happens during a backup, for details, see [AnkiActivity.showedActivityFailedScreen]
     *
     * Note: If you ran this test and it failed, please check to make sure that any new onCreate methods
     * have the following code snippet at the start:
     * `
     * if (showedActivityFailedScreen(savedInstanceState)) {
     * return;
     * }
     ` *
     */
    @Test
    fun activityHandlesRestoreBackup() {
        AnkiDroidApp.simulateRestoreFromBackup()
        val controller: ActivityController<out Activity?>
        controller = try {
            mLauncher!!.build(targetContext).create()
        } catch (npe: Exception) {
            val stackTrace = getFullStackTrace(npe)
            Assert.fail(
                """If you ran this test and it failed, please check to make sure that any new onCreate methods
have the following code snippet at the start:
if (showedActivityFailedScreen(savedInstanceState)) {
  return;
}
$stackTrace"""
            )
            throw npe
        }
        shadowOf(getMainLooper()).idle()

        // Note: Robolectric differs from actual Android (process is not killed).
        // But we get the main idea: onCreate() doesn't throw an exception and is handled.
        // and onDestroy() is also called in the real implementation on my phone.
        assertThat("If a backup was taking place, the activity should be finishing", controller.get()!!.isFinishing, `is`(true))
        controller.destroy()
        assertThat("If a backup was taking place, the activity should be destroyed successfully", controller.get()!!.isDestroyed, `is`(true))
    }

    protected fun notYetHandled(activityName: String, reason: String) {
        if (mLauncher!!.simpleName == activityName) {
            assumeThat("$activityName $reason", true, `is`(false))
        }
    }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        @JvmStatic
        fun initParameters(): Collection<Array<Any>> {
            return ActivityList.allActivitiesAndIntents().stream().map { x: ActivityLaunchParam -> arrayOf(x, x.simpleName) }.collect(Collectors.toList())
        }
    }
}
