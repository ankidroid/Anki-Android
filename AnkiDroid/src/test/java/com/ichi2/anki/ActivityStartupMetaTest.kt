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

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.annotations.KotlinCleanup
import com.ichi2.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.compat.PackageInfoFlagsCompat
import com.ichi2.testutils.ActivityList
import com.ichi2.testutils.ActivityList.ActivityLaunchParam
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*
import java.util.stream.Collectors
import kotlin.Throws

@RunWith(AndroidJUnit4::class)
class ActivityStartupMetaTest : RobolectricTest() {
    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    @KotlinCleanup("remove throws; remove stream(), remove : String")
    fun ensureAllActivitiesAreTested() {
        // if this fails, you may need to add the missing activity to ActivityList.allActivitiesAndIntents()

        // we can't access this in a static context
        val packageInfo = targetContext.getPackageInfoCompat(targetContext.packageName, PackageInfoFlagsCompat.of(PackageManager.GET_ACTIVITIES.toLong())) ?: throw IllegalStateException("getPackageInfo failed")
        val manifestActivities = packageInfo.activities
        val testedActivityClassNames = ActivityList.allActivitiesAndIntents().stream().map { obj: ActivityLaunchParam -> obj.className }.collect(Collectors.toSet())
        val manifestActivityNames = Arrays.stream(manifestActivities)
            .map { x: ActivityInfo -> x.name }
            .filter { x: String -> x != "com.ichi2.anki.TestCardTemplatePreviewer" }
            .filter { x: String -> x != "com.ichi2.anki.AnkiCardContextMenuAction" }
            .filter { x: String -> x != "com.ichi2.anki.analytics.AnkiDroidCrashReportDialog" }
            .filter { x: String -> !x.startsWith("androidx") }
            .filter { x: String -> !x.startsWith("org.acra") }
            .filter { x: String -> !x.startsWith("leakcanary.internal") }
            .toArray()
        MatcherAssert.assertThat(testedActivityClassNames, Matchers.containsInAnyOrder(*manifestActivityNames))
    }
}
