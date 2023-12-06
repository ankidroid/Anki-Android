/*
 *  Copyright (c) 2023
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

import android.app.Instrumentation.ActivityMonitor
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReviewerTest : InstrumentedTest() {

    // Launch IntroductionActivity instead of DeckPicker activity because in CI
    // builds, it seems to create IntroductionActivity after the DeckPicker,
    // causing the DeckPicker activity to be destroyed. As a consequence, this
    // will throw RootViewWithoutFocusException when Espresso tries to interact
    // with an already destroyed activity. By launching IntroductionActivity, we
    // ensure that IntroductionActivity is launched first and navigate to the
    // DeckPicker -> Reviewer activities
    @get:Rule
    val activityScenarioRule = ActivityScenarioRule(IntroductionActivity::class.java)

    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    private lateinit var activityMonitor: ActivityMonitor

    @Before
    fun setup() {
        activityMonitor = InstrumentationRegistry.getInstrumentation()
            .addMonitor(Reviewer::class.java.name, null, false)
    }

    @Test
    fun testCustomSchedulerWithCustomData() {
        col.config.set(
            "cardStateCustomizer",
            """
            states.good.normal.review.easeFactor = 3.0;
            states.good.normal.review.scheduledDays = 123;
            customData.good.c += 1;
            """
        )
        val note = addNoteUsingBasicModel("foo", "bar")
        val card = note.firstCard()
        val deck = col.decks.get(note.notetype.did)!!
        card.moveToReviewQueue()
        col.backend.updateCards(
            listOf(
                card.toBackendCard().toBuilder().setCustomData("""{"c":1}""").build()
            ),
            true
        )

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id).toBackendCard()
        assertThat(cardFromDb.easeFactor, equalTo(card.factor))
        assertThat(cardFromDb.interval, equalTo(card.ivl))
        assertThat(cardFromDb.customData, equalTo("""{"c":1}"""))

        waitForReviewerActivity { clickShowAnswerAndAnswerGood() }

        cardFromDb = col.getCard(card.id).toBackendCard()
        assertThat(cardFromDb.easeFactor, equalTo(3000))
        assertThat(cardFromDb.interval, equalTo(123))
        assertThat(cardFromDb.customData, equalTo("""{"c":2}"""))
    }

    private fun closeGetStartedScreenIfExists() {
        onView(withId(R.id.get_started)).withFailureHandler { _, _ -> }.perform(click())
    }

    private fun closeBackupCollectionDialogIfExists() {
        onView(withText(R.string.button_backup_later))
            .withFailureHandler { _, _ -> }
            .perform(click())
    }

    private fun clickOnDeckWithName(deckName: String) {
        onView(withId(R.id.files)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                hasDescendant(withText(deckName)),
                click()
            )
        )
    }

    private fun clickOnStudyButtonIfExists() {
        onView(withId(R.id.studyoptions_start)).withFailureHandler { _, _ -> }.perform(click())
            .withFailureHandler { _, _ -> }
            .perform(click())
    }

    private fun reviewDeckWithName(deckName: String) {
        clickOnDeckWithName(deckName)
        // Adding cards directly to the database while in the Deck Picker screen
        // will not update the page with correct card counts. Hence, clicking
        // on the deck will bring us to the study options page where we need to
        // click on the Study button. If we have added cards to the database
        // before the Deck Picker screen has fully loaded, then we skip clicking
        // the Study button
        clickOnStudyButtonIfExists()
    }

    private fun clickShowAnswerAndAnswerGood() {
        onIdle()
        onView(withId(R.id.flashcard_layout_flip)).perform(click())
        onView(withId(R.id.flashcard_layout_ease3)).perform(click())
    }

    /**
     * Waits for the [Reviewer] activity and performs the specified [action],
     * providing the ability to call [onIdle] to wait for the system to be in
     * an idle state
     *
     * This method should be called any time a new [Reviewer] activity has been
     * created. If this is not done, then [onIdle] calls will not work because
     * the previous [Reviewer] activity has already been destroyed
     *
     * @param action The action to be performed after the [Reviewer] activity is
     * available
     *
     * @throws NullPointerException if the [Reviewer] activity is not available
     * within the specified timeout.
     */
    private fun waitForReviewerActivity(action: () -> Unit) {
        val activity =
            (activityMonitor.waitForActivityWithTimeout(TimeUnit.SECONDS.toMillis(10)) as Reviewer?)!!
        IdlingRegistry.getInstance().register(activity.customSchedulerIdlingResource)
        action()
        IdlingRegistry.getInstance().unregister(activity.customSchedulerIdlingResource)
    }
}
