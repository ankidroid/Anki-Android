// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.preferences.PreferenceTestUtils.withHeaderFragment
import com.ichi2.anki.preferences.requirePreference
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.preferences.HeaderPreference
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewRemindersIntroTest : RobolectricTest() {
    @Test
    fun `intro is shown once when review reminders are opened`() {
        Prefs.shouldShowReviewRemindersIntro = true

        withScheduleRemindersFragment { fragment ->
            assertThat(
                fragment.childFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG),
                instanceOf(ReviewRemindersIntroDialog::class.java),
            )
            assertThat("the intro is only shown once", Prefs.shouldShowReviewRemindersIntro, equalTo(false))
        }

        withScheduleRemindersFragment { fragment ->
            assertThat(fragment.childFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG), nullValue())
        }
    }

    @Test
    fun `intro is not shown to users without a legacy notification`() {
        Prefs.shouldShowReviewRemindersIntro = false

        withScheduleRemindersFragment { fragment ->
            assertThat(fragment.childFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG), nullValue())
        }
    }

    @Test
    fun `review reminders header is badged until the intro is seen`() {
        Prefs.shouldShowReviewRemindersIntro = true

        withHeaderFragment { header ->
            val reviewRemindersHeader = header.requirePreference<HeaderPreference>(R.string.pref_review_reminders_screen_key)
            assertThat(reviewRemindersHeader.isBadged, equalTo(true))

            Prefs.shouldShowReviewRemindersIntro = false
            advanceRobolectricLooper()
            assertThat(reviewRemindersHeader.isBadged, equalTo(false))
        }
    }
}
