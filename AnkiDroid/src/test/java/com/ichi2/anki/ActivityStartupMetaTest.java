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

import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.ichi2.testutils.ActivityList;
import com.ichi2.testutils.ActivityList.ActivityLaunchParam;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

@RunWith(AndroidJUnit4.class)
public class ActivityStartupMetaTest extends RobolectricTest {

    @Test
    public void ensureAllActivitiesAreTested() throws PackageManager.NameNotFoundException {
        // if this fails, you may need to add the missing activity to ActivityList.allActivitiesAndIntents()

        // we can't access this in a static context
        PackageManager pm = getTargetContext().getPackageManager();

        PackageInfo packageInfo = pm.getPackageInfo(getTargetContext().getPackageName(), PackageManager.GET_ACTIVITIES);
        ActivityInfo[] manifestActivities = packageInfo.activities;

        Set<String> testedActivityClassNames = ActivityList.allActivitiesAndIntents().stream().map(ActivityLaunchParam::getClassName).collect(Collectors.toSet());

        Object[] manifestActivityNames = Arrays.stream(manifestActivities)
                .map(x -> x.name)
                .filter(x -> !x.equals("com.ichi2.anki.TestCardTemplatePreviewer"))
                .filter(x -> !x.equals("com.ichi2.anki.AnkiCardContextMenuAction"))
                .filter(x -> !x.equals("com.ichi2.anki.analytics.AnkiDroidCrashReportDialog"))
                .filter(x -> !x.startsWith("com.yalantis"))
                .filter(x -> !x.startsWith("androidx"))
                .filter(x -> !x.startsWith("org.acra"))
                .toArray();
        assertThat(testedActivityClassNames, containsInAnyOrder(manifestActivityNames));
    }
}
