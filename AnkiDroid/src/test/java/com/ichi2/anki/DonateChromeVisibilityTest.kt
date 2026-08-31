/*
 *  Copyright (c) 2026 AnkiDroid Contributors
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
import android.view.View
import androidx.fragment.app.commitNow
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.preferences.AboutFragment
import com.ichi2.anki.preferences.Preferences
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DonateChromeVisibilityTest : RobolectricTest() {

    @Test
    fun infoDonateButtonMatchesFlavor() {
        val intent = Intent().putExtra(Info.TYPE_EXTRA, Info.TYPE_NEW_VERSION)
        val info = startActivityNormallyOpenCollectionWithIntent(Info::class.java, intent)
        val donate = info.findViewById<View>(R.id.info_donate)
        if (BuildConfig.SHOW_DONATE_LINKS) {
            assertThat(donate.visibility, equalTo(View.VISIBLE))
        } else {
            assertThat(donate.visibility, equalTo(View.GONE))
        }
    }

    @Test
    fun aboutDonateChromeMatchesFlavor() {
        ActivityScenario.launch(Preferences::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = AboutFragment()
                activity.supportFragmentManager.commitNow {
                    add(R.id.settings_container, fragment)
                }
                val view = fragment.requireView()
                val title = view.findViewById<View>(R.id.about_donate_title)
                val description = view.findViewById<View>(R.id.about_donate_description)
                if (BuildConfig.SHOW_DONATE_LINKS) {
                    assertThat(title.visibility, not(equalTo(View.GONE)))
                    assertThat(description.visibility, not(equalTo(View.GONE)))
                } else {
                    assertThat(title.visibility, equalTo(View.GONE))
                    assertThat(description.visibility, equalTo(View.GONE))
                }
            }
        }
    }
}
