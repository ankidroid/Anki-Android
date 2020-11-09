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

import com.ichi2.testutils.ActivityList;
import com.ichi2.testutils.ActivityList.ActivityLaunchParam;
import com.ichi2.testutils.EmptyApplication;

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

    @Test
    public void activityHandlesRestoreBackup() {
        // See: showActivityFailedScreen

        AnkiDroidApp.simulateRestoreFromBackup();
        ActivityController<? extends Activity> controller = mLauncher.build(getTargetContext()).create();

        shadowOf(getMainLooper()).idle();

        // Note: Robolectric differs from actual Android (process is not killed).
        // But we get the main idea: onCreate() doesn't throw an exception and is handled.
        // and onDestroy() is also called in the real implementation on my phone.

        assertThat("If a backup was taking place, the activity should be finishing", controller.get().isFinishing(), is(true));

        controller.destroy();

        assertThat("If a backup was taking place, the activity should be destroyed successfully", controller.get().isDestroyed(), is(true));
    }
}
