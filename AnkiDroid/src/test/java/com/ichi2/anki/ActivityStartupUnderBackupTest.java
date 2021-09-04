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

package com.ichi2.anki;

import android.app.Activity;
import android.os.Bundle;

import com.canhub.cropper.CropImageActivity;
import com.ichi2.anki.multimediacard.activity.LoadPronounciationActivity;
import com.ichi2.anki.multimediacard.activity.TranslationActivity;
import com.ichi2.testutils.ActivityList;
import com.ichi2.testutils.ActivityList.ActivityLaunchParam;
import com.ichi2.testutils.EmptyApplication;
import com.ichi2.utils.ExceptionUtil;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.stream.Collectors;

import static android.os.Looper.getMainLooper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.robolectric.Shadows.shadowOf;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(application = EmptyApplication.class) // no point in Application init if we don't use it
public class ActivityStartupUnderBackupTest extends RobolectricTest {
    @Parameter
    public ActivityLaunchParam mLauncher;

    @SuppressWarnings( {"unused", "RedundantSuppression"}) // Only used for display, but needs to be defined
    @Parameter(1)
    public String mActivityName;

    @Parameters(name = "{1}")
    public static java.util.Collection<Object[]> initParameters() {
        return ActivityList.allActivitiesAndIntents().stream().map(x -> new Object[] {x, x.getSimpleName()}).collect(Collectors.toList());
    }

    @Before
    public void before() {
        notYetHandled(CropImageActivity.class.getSimpleName(), "cannot implemented - activity from canhub.cropper");
        notYetHandled(IntentHandler.class.getSimpleName(), "Not working (or implemented) - inherits from Activity");
        notYetHandled(VideoPlayer.class.getSimpleName(), "Not working (or implemented) - inherits from Activity");
        notYetHandled(LoadPronounciationActivity.class.getSimpleName(), "Not working (or implemented) - inherits from Activity");
        notYetHandled(Preferences.class.getSimpleName(), "Not working (or implemented) - inherits from AppCompatPreferenceActivity");
        notYetHandled(TranslationActivity.class.getSimpleName(), "Not working (or implemented) - inherits from FragmentActivity");
        notYetHandled(DeckOptions.class.getSimpleName(), "Not working (or implemented) - inherits from AppCompatPreferenceActivity");
        notYetHandled(FilteredDeckOptions.class.getSimpleName(), "Not working (or implemented) - inherits from AppCompatPreferenceActivity");
    }


    /**
     * Tests that each activity can handle {@link AnkiDroidApp#getInstance()} returning null
     * This happens during a backup, for details, see {@link AnkiActivity#showedActivityFailedScreen(Bundle)}
     *
     * Note: If you ran this test and it failed, please check to make sure that any new onCreate methods
     * have the following code snippet at the start:
     * <code>
     * if (showedActivityFailedScreen(savedInstanceState)) {
     *     return;
     * }
     * </code>
     */
    @Test
    public void activityHandlesRestoreBackup() {
        AnkiDroidApp.simulateRestoreFromBackup();
        ActivityController<? extends Activity> controller;
        try {
            controller = mLauncher.build(getTargetContext()).create();
        } catch (Exception npe) {
            String stackTrace = ExceptionUtil.getFullStackTrace(npe);
            Assert.fail("If you ran this test and it failed, please check to make sure that any new onCreate methods\n" +
                    "have the following code snippet at the start:\n" +
                    "if (showedActivityFailedScreen(savedInstanceState)) {\n" +
                    "  return;\n" +
                    "}\n" + stackTrace);
            // Throw is useless after failure. However, it is required to compile as the compiler do not get
            // that controller is not used below.
            throw npe;
        }
        shadowOf(getMainLooper()).idle();

        // Note: Robolectric differs from actual Android (process is not killed).
        // But we get the main idea: onCreate() doesn't throw an exception and is handled.
        // and onDestroy() is also called in the real implementation on my phone.

        assertThat("If a backup was taking place, the activity should be finishing", controller.get().isFinishing(), is(true));

        controller.destroy();

        assertThat("If a backup was taking place, the activity should be destroyed successfully", controller.get().isDestroyed(), is(true));
    }


    protected void notYetHandled(String activityName, String reason) {
        if (mLauncher.getSimpleName().equals(activityName)) {
            assumeThat(activityName + " " + reason, true, is(false));
        }
    }
}
