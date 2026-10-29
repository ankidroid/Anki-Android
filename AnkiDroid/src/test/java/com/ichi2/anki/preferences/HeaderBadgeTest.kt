// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.preference.PreferenceViewHolder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.preferences.PreferenceTestUtils.withHeaderFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.preferences.HeaderPreference
import com.ichi2.ui.BadgeDrawable
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.sameInstance
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeaderBadgeTest : RobolectricTest() {
    private val icon = ContextCompat.getDrawable(targetContext, R.drawable.ic_sync)

    @Test
    fun `badge wraps and restores the icon`() {
        var shouldShowBadge = true
        val preference = HeaderPreference(targetContext).apply { icon = this@HeaderBadgeTest.icon }

        preference.showBadgeWhen = { shouldShowBadge }
        assertThat(preference.isBadged, equalTo(true))
        assertThat(preference.icon, instanceOf(BadgeDrawable::class.java))
        assertThat((preference.icon as BadgeDrawable).drawable, sameInstance(icon))

        preference.refreshBadge()
        assertThat("the badge is not applied twice", (preference.icon as BadgeDrawable).drawable, sameInstance(icon))

        shouldShowBadge = false
        preference.refreshBadge()
        assertThat(preference.isBadged, equalTo(false))
        assertThat(preference.icon, sameInstance(icon))
    }

    @Test
    fun `badge is announced to screen readers`() {
        var shouldShowBadge = true
        val preference = HeaderPreference(targetContext).apply { icon = this@HeaderBadgeTest.icon }
        val itemView = LayoutInflater.from(targetContext).inflate(preference.layoutResource, null)
        val holder = PreferenceViewHolder.createInstanceForTests(itemView)

        preference.showBadgeWhen = { shouldShowBadge }
        preference.onBindViewHolder(holder)
        assertThat(
            ViewCompat.getStateDescription(holder.itemView),
            equalTo(targetContext.getString(CommonString.settings_header_badge_state)),
        )

        // views are recycled: rebinding an unbadged header must clear the description
        shouldShowBadge = false
        preference.refreshBadge()
        preference.onBindViewHolder(holder)
        assertThat(ViewCompat.getStateDescription(holder.itemView), nullValue())
    }

    @Test
    fun `badge without an icon is not announced`() {
        val preference = HeaderPreference(targetContext)
        val itemView = LayoutInflater.from(targetContext).inflate(preference.layoutResource, null)
        val holder = PreferenceViewHolder.createInstanceForTests(itemView)

        preference.showBadgeWhen = { true }
        preference.onBindViewHolder(holder)
        assertThat(ViewCompat.getStateDescription(holder.itemView), nullValue())
    }

    @Test
    fun `header badge follows its condition when a preference changes`() {
        var shouldShowBadge = true
        withHeaderFragment { header ->
            val syncHeader = header.requirePreference<HeaderPreference>(R.string.pref_sync_screen_key)
            syncHeader.showBadgeWhen = { shouldShowBadge }
            assertThat(syncHeader.isBadged, equalTo(true))

            // On large screens, the header list stays visible while the opened screen clears the condition
            shouldShowBadge = false
            Prefs.sharedPrefs.edit { putLong("headerBadgeTest", System.nanoTime()) }
            advanceRobolectricLooper()
            assertThat(syncHeader.isBadged, equalTo(false))
        }
    }
}
