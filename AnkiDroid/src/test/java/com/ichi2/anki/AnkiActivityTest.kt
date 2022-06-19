/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

import android.content.Intent
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.themes.Theme
import com.ichi2.themes.Themes
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnkiActivityTest : RobolectricTest() {
    @Test
    fun themeChangeIsValid() {
        // #12404 - fail to respond to day/night mode change
        val activity = startActivityNormallyOpenCollectionWithIntent(
            AnkiActivity::class.java,
            Intent()
        )
        assertThat(Themes.currentTheme, equalTo(Theme.LIGHT))

        val newConfig = Configuration(activity.resources.configuration)
        newConfig.uiMode = 33
        activity.resources.configuration.uiMode = 33
        activity.onConfigurationChanged(newConfig)

        assertThat(Themes.currentTheme, equalTo(Theme.BLACK))
    }
}
