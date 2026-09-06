// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023

package com.ichi2.anki

import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.checkWithTimeout
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.closeBackupCollectionDialogIfExists
import com.ichi2.anki.testutil.closeGetStartedScreenIfExists
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.anki.utils.ext.cardStateCustomizer
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.util.UUID

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

    /** Shared on-disk collection: review a deck that contains only this test's card. */
    private var testDeckId: DeckId = 0

    override fun runBeforeEachTest() {
        super.runBeforeEachTest()

        // 17298: for an unknown reason, we were using the beta Reviewer
        // This works on my MacBook, fails in CI
        // failure is due to the card not being flipped
        // since the feature is currently in beta and unexpectedly enabled, disable it
        // TODO: remove this
        disableNewReviewer()
        // Dirty AVD prefs can leave TTS on, which shows a locale dialog over the reviewer.
        testContext.sharedPrefs().edit {
            putBoolean("tts", false)
        }
        testDeckId = col.decks.addNormalDeckWithName("ReviewerTest-${UUID.randomUUID()}").id
        col.decks.select(testDeckId)
    }

    @After
    fun tearDown() {
        if (testDeckId != 0L) {
            col.decks.remove(listOf(testDeckId))
            testDeckId = 0
        }
        col.cardStateCustomizer = ""
    }

    @Test
    fun testCustomSchedulerWithCustomData() {
        col.cardStateCustomizer =
            """
            states.good.normal.review.easeFactor = 3.0;
            states.good.normal.review.scheduledDays = 123;
            customData.good.c += 1;
            """
        val card = addCardToTestDeck()
        card.moveToReviewQueue()
        col.backend.updateCards(
            listOf(
                card
                    .toBackendCard()
                    .toBuilder()
                    .setCustomData("""{"c":1}""")
                    .build(),
            ),
            true,
        )

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(col.decks.name(testDeckId))

        var cardFromDb = col.getCard(card.id).toBackendCard()
        assertThat(cardFromDb.easeFactor, equalTo(card.factor))
        assertThat(cardFromDb.interval, equalTo(card.ivl))
        assertThat(cardFromDb.customData, equalTo("""{"c":1}"""))

        clickShowAnswerAndAnswerGood()
        waitUntil(message = { "The review of card ${card.id} was not saved" }) {
            col.getCard(card.id).reps == card.reps + 1
        }

        cardFromDb = col.getCard(card.id).toBackendCard()
        assertThat(cardFromDb.easeFactor, equalTo(3000))
        assertThat(cardFromDb.interval, equalTo(123))
        assertThat(cardFromDb.customData, equalTo("""{"c":2}"""))
    }

    @Test
    fun testCustomSchedulerWithRuntimeError() {
        // Issue 15035 - runtime errors weren't handled
        col.cardStateCustomizer = "states.this_is_not_defined.normal.review = 12;"
        addCardToTestDeck()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(col.decks.name(testDeckId))

        clickShowAnswer()

        ensureAnswerButtonsAreDisplayed()
    }

    private fun addCardToTestDeck(): Card = addNoteUsingBasicNoteType("foo", "bar").firstCard(col).update { did = testDeckId }

    private fun clickOnDeckWithName(deckName: String) {
        onView(withId(R.id.decks)).checkWithTimeout(matches(hasDescendant(withText(deckName))))
        onView(withId(R.id.decks)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                hasDescendant(withText(deckName)),
                click(),
            ),
        )
    }

    private fun clickOnStudyButtonIfExists() {
        onView(withId(R.id.studyoptions_start))
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
        clickShowAnswer()
        ensureAnswerButtonsAreDisplayed()
        onView(withId(R.id.flashcard_layout_ease3)).perform(click())
    }

    private fun clickShowAnswer() {
        onView(withId(R.id.flashcard_layout_flip)).perform(click())
    }

    private fun ensureAnswerButtonsAreDisplayed() {
        // We need to wait for the card to fully load to allow enough time for
        // the messages to be passed in and out of the WebView when evaluating
        // the custom JS scheduler code. The ease buttons are hidden until the
        // custom scheduler has finished running
        onView(withId(R.id.flashcard_layout_ease3)).checkWithTimeout(
            matches(isDisplayed()),
            100,
        )
    }

    private fun disableNewReviewer() {
        val newReviewerPrefKey = testContext.getString(R.string.new_reviewer_options_key)
        val prefs = testContext.sharedPrefs()
        val isUsingNewReviewer = prefs.getBoolean(newReviewerPrefKey, false)
        if (!isUsingNewReviewer) return

        Timber.w("unexpectedly using new reviewer: disabling it")
        prefs.edit {
            putBoolean(newReviewerPrefKey, false)
        }
    }
}
