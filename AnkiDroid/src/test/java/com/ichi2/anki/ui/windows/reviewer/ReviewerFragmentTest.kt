// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.content.DialogInterface
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.ThemeUtils
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.fragment.app.DialogFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.TypeAnswerWebView
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.ext.cardStateCustomizer
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckConfig
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.preferences.reviewer.WhiteboardAction
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.scheduling.singleDayText
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.settings.enums.AppTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardFragment
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.RecordingInputMethodManager
import com.ichi2.testutils.ext.addNoSuggestNote
import com.ichi2.testutils.ext.createInputConnection
import com.ichi2.testutils.mockWebResourceRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowDialog
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class ReviewerFragmentTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @After
    fun resetSoftwareRendering() {
        Prefs.sharedPrefs.edit {
            remove(Prefs.key(R.string.disable_hardware_render_key))
        }
    }

    @Test
    fun `blocked launch pauses auto advance until the warning is dismissed`() =
        runTest {
            val card = addBasicNote().firstCard()
            val config = col.decks.configDictForDeckId(Consts.DEFAULT_DECK_ID)
            config.secondsToShowAnswer = 0.05
            config.jsonObject.put(DeckConfig.ANSWER_ACTION, 2) // AnswerAction.ANSWER_GOOD
            col.decks.save(config)
            editPreferences { putBoolean(targetContext.getString(R.string.pref_allow_card_external_launch_key), false) }

            withReviewer { _ ->
                viewModel.executeAction(ViewerAction.TOGGLE_AUTO_ADVANCE)
                advanceUntilIdle()
                viewModel.executeAction(ViewerAction.SHOW_ANSWER)
                viewModel.onStateMutationCallback()
                // finish showing the answer, but do not run the auto-advance timer yet
                advanceTimeBy(1.milliseconds)

                val webView = assertIs<WebView>(binding.webViewLayout.getChildAt(0))
                shadowOf(webView).webViewClient.shouldOverrideUrlLoading(
                    webView,
                    mockWebResourceRequest("market://details?id=com.example"),
                )
                advanceRobolectricLooper()
                assertEquals(1, ShadowDialog.getShownDialogs().size)

                // a card hidden behind the warning must not be graded
                advanceTimeBy(1.minutes)
                assertEquals(0, col.getCard(card.id).reps)

                // auto advance resumes after the warning is dismissed
                ShadowDialog.getLatestDialog().cancel()
                advanceRobolectricLooper()
                // grading waits for the next card render, like in the real app
                viewModel.onStateMutationCallback()
                advanceUntilIdle()
                assertEquals(1, col.getCard(card.id).reps)
            }
        }

    @Test
    fun `flipping the same card does not reopen a dismissed launch warning`() =
        runTest {
            addBasicNote()
            editPreferences { putBoolean(targetContext.getString(R.string.pref_allow_card_external_launch_key), false) }

            withReviewer {
                val webView = assertIs<WebView>(binding.webViewLayout.getChildAt(0))
                val request = mockWebResourceRequest("market://details?id=com.example")
                shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, request)
                advanceRobolectricLooper()
                ShadowDialog.getLatestDialog().cancel()
                advanceRobolectricLooper()

                viewModel.onShowAnswer()
                advanceUntilIdle()
                shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, request)
                advanceRobolectricLooper()

                assertEquals(
                    0,
                    ShadowDialog.getShownDialogs().count { it.isShowing },
                    "flipping the same card must not reset launch-warning suppression",
                )
            }
        }

    @Test
    fun `media completion does not grade a card behind the launch warning`() =
        runTest {
            val card = addBasicNote("front", "[sound:answer.webm]").firstCard()
            val config = col.decks.configDictForDeckId(card.currentDeckId())
            config.secondsToShowQuestion = 0.0
            config.secondsToShowAnswer = 0.05
            config.jsonObject.put("waitForAudio", true)
            config.jsonObject.put(DeckConfig.ANSWER_ACTION, 2)
            col.decks.save(config)
            editPreferences { putBoolean(targetContext.getString(R.string.pref_allow_card_external_launch_key), false) }

            withReviewer {
                val webView = assertIs<WebView>(binding.webViewLayout.getChildAt(0))
                viewModel.onShowAnswer()
                advanceUntilIdle()
                advanceRobolectricLooperUntil {
                    testScheduler.runCurrent()
                    shadowOf(webView).lastEvaluatedJavascript?.contains("var videos") == true
                }
                viewModel.executeAction(ViewerAction.TOGGLE_AUTO_ADVANCE)
                advanceUntilIdle()

                shadowOf(webView).webViewClient.shouldOverrideUrlLoading(
                    webView,
                    mockWebResourceRequest("intent:#Intent;action=android.intent.action.MAIN;package=com.android.settings;end"),
                )
                advanceRobolectricLooper()
                val warning = ShadowDialog.getShownDialogs().single { it.isShowing }
                val playback = viewModel.cardMediaPlayer.playAvTagsJob
                viewModel.onVideoFinished()
                playback?.join()
                advanceUntilIdle()

                assertFalse(warning.isShowing && col.getCard(card.id).reps > 0)
                warning.cancel()
                advanceRobolectricLooper()
                advanceUntilIdle()
                assertEquals(1, col.getCard(card.id).reps)
            }
        }

    @Test
    fun `dismissing a launch warning still waits for answer media before grading`() =
        runTest {
            val card = addBasicNote("front", "[sound:answer.webm]").firstCard()
            val config = col.decks.configDictForDeckId(card.currentDeckId())
            config.secondsToShowQuestion = 0.0
            config.secondsToShowAnswer = 0.05
            config.jsonObject.put("waitForAudio", true)
            config.jsonObject.put(DeckConfig.ANSWER_ACTION, 2)
            col.decks.save(config)
            editPreferences { putBoolean(targetContext.getString(R.string.pref_allow_card_external_launch_key), false) }

            withReviewer {
                val webView = assertIs<WebView>(binding.webViewLayout.getChildAt(0))
                viewModel.onShowAnswer()
                advanceUntilIdle()
                advanceRobolectricLooperUntil {
                    testScheduler.runCurrent()
                    shadowOf(webView).lastEvaluatedJavascript?.contains("var videos") == true
                }
                viewModel.executeAction(ViewerAction.TOGGLE_AUTO_ADVANCE)
                advanceUntilIdle()

                shadowOf(webView).webViewClient.shouldOverrideUrlLoading(
                    webView,
                    mockWebResourceRequest("intent:#Intent;action=android.intent.action.MAIN;package=com.android.settings;end"),
                )
                advanceRobolectricLooper()
                ShadowDialog.getShownDialogs().single { it.isShowing }.cancel()
                advanceRobolectricLooper()
                advanceUntilIdle()

                assertTrue(viewModel.cardMediaPlayer.isPlaying)
                assertEquals(0, col.getCard(card.id).reps)
                val playback = viewModel.cardMediaPlayer.playAvTagsJob
                viewModel.onVideoFinished()
                playback?.join()
                advanceUntilIdle()
                assertEquals(1, col.getCard(card.id).reps)
            }
        }

    @Test
    fun `a queued blocked launch cannot reopen a warning before its dismissal callback`() =
        runTest {
            addBasicNote()
            editPreferences { putBoolean(targetContext.getString(R.string.pref_allow_card_external_launch_key), false) }
            withReviewer {
                val webView = assertIs<WebView>(binding.webViewLayout.getChildAt(0))
                val client = shadowOf(webView).webViewClient
                val request = mockWebResourceRequest("intent:#Intent;action=android.intent.action.MAIN;package=com.android.settings;end")
                client.shouldOverrideUrlLoading(webView, request)
                ShadowDialog.getShownDialogs().single { it.isShowing }.cancel()
                advanceRobolectricLooper()
                client.shouldOverrideUrlLoading(webView, request)
                advanceRobolectricLooper()
                assertEquals(0, ShadowDialog.getShownDialogs().count { it.isShowing })
            }
        }

    @Test
    fun `pressing Cancel suppresses a navigation queued behind the button click`() =
        runTest {
            addBasicNote()
            withReviewer {
                val webView = assertIs<WebView>(binding.webViewLayout.getChildAt(0))
                val request = mockWebResourceRequest("market://details?id=com.example")
                val client = shadowOf(webView).webViewClient
                client.shouldOverrideUrlLoading(webView, request)
                val warning = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
                warning.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
                Handler(Looper.getMainLooper()).post { client.shouldOverrideUrlLoading(webView, request) }
                advanceRobolectricLooper()

                assertEquals(0, ShadowDialog.getShownDialogs().count { it.isShowing }, "Cancel should return to the card")
            }
        }

    @Test
    fun `answer rendering cannot start grading behind a launch warning`() =
        runTest {
            col.config.cardStateCustomizer = "// Use the default scheduling states."
            val card = addBasicNote().firstCard()
            val config = col.decks.configDictForDeckId(card.currentDeckId())
            config.secondsToShowQuestion = 0.0
            config.secondsToShowAnswer = 1.0
            config.jsonObject.put("waitForAudio", false)
            config.jsonObject.put(DeckConfig.ANSWER_ACTION, 2)
            col.decks.save(config)
            withReviewer {
                val webView = assertIs<WebView>(binding.webViewLayout.getChildAt(0))
                viewModel.executeAction(ViewerAction.TOGGLE_AUTO_ADVANCE)
                testScheduler.runCurrent()
                // The pending answer waits for the WebView to return its custom scheduling states.
                viewModel.onShowAnswer()
                testScheduler.runCurrent()
                assertFalse(viewModel.showingAnswer.value)
                shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, mockWebResourceRequest("market://details?id=com.example"))
                val warning = ShadowDialog.getShownDialogs().single { it.isShowing }
                viewModel.onStateMutationCallback()
                advanceUntilIdle()
                assertEquals(0, col.getCard(card.id).reps, "rendering the answer must preserve the modal pause")
                warning.cancel()
                advanceRobolectricLooper()
                advanceUntilIdle()
                assertEquals(1, col.getCard(card.id).reps)
            }
        }

    @Test
    fun `a replayed answer cannot leave an older timer grading behind the warning`() =
        runTest {
            val card = addBasicNote("front", "[sound:answer.webm]").firstCard()
            val config = col.decks.configDictForDeckId(card.currentDeckId())
            config.secondsToShowQuestion = 0.0
            config.secondsToShowAnswer = 2.0
            config.jsonObject.put("waitForAudio", true)
            config.jsonObject.put(DeckConfig.ANSWER_ACTION, 2)
            col.decks.save(config)
            withReviewer {
                val webView = assertIs<WebView>(binding.webViewLayout.getChildAt(0))

                suspend fun finishAnswerPlayback() {
                    advanceRobolectricLooperUntil {
                        testScheduler.runCurrent()
                        shadowOf(webView).lastEvaluatedJavascript?.contains("var videos") == true
                    }
                    val playback = assertNotNull(viewModel.cardMediaPlayer.playAvTagsJob)
                    viewModel.onVideoFinished()
                    advanceRobolectricLooperUntil {
                        testScheduler.runCurrent()
                        playback.isCompleted
                    }
                }

                viewModel.onShowAnswer()
                advanceUntilIdle()
                viewModel.executeAction(ViewerAction.TOGGLE_AUTO_ADVANCE)
                testScheduler.runCurrent()
                finishAnswerPlayback()
                webView.evaluateJavascript("void 0", null)
                viewModel.executeAction(ViewerAction.PLAY_MEDIA)
                finishAnswerPlayback()
                assertEquals(0, col.getCard(card.id).reps)

                shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, mockWebResourceRequest("market://details?id=com.example"))
                val warning = ShadowDialog.getShownDialogs().single { it.isShowing }
                advanceUntilIdle()
                assertEquals(0, col.getCard(card.id).reps, "dismissing all pending timers must protect the hidden answer")
                warning.cancel()
                advanceRobolectricLooper()
                advanceUntilIdle()
                assertEquals(1, col.getCard(card.id).reps)
            }
        }

    @Test
    fun `recreating the study screen preserves a dismissed warning on the same card`() =
        runTest {
            val card = addBasicNote().firstCard()
            withReviewer { controller ->
                val request = mockWebResourceRequest("market://details?id=com.example")
                val initial = assertIs<WebView>(binding.webViewLayout.getChildAt(0))
                shadowOf(initial).webViewClient.shouldOverrideUrlLoading(initial, request)
                ShadowDialog.getShownDialogs().single { it.isShowing }.cancel()
                advanceRobolectricLooper()

                controller.recreate()
                val recreated = assertIs<ReviewerFragment>(controller.get().fragment)
                recreated.viewModel.onPageFinished(true)
                advanceUntilIdle()
                assertEquals(card.id, recreated.viewModel.getCardId())
                val replacement = assertIs<WebView>(recreated.binding.webViewLayout.getChildAt(0))
                shadowOf(replacement).webViewClient.shouldOverrideUrlLoading(replacement, request)
                advanceRobolectricLooper()
                assertEquals(0, ShadowDialog.getShownDialogs().count { it.isShowing }, "the same card was already dismissed")
            }
        }

    @Test
    fun `dismissing a warning during a timebox break cannot bury the reviewed card`() =
        runTest {
            val card = addBasicNote("first", "answer").firstCard()
            addBasicNote("second", "answer")
            col.config.set("timeLim", 60)
            val config = col.decks.configDictForDeckId(card.currentDeckId())
            config.secondsToShowQuestion = 0.0
            config.secondsToShowAnswer = 1.0
            config.jsonObject.put("waitForAudio", false)
            config.jsonObject.put(DeckConfig.ANSWER_ACTION, 0) // Bury card
            col.decks.save(config)

            withReviewer {
                viewModel.onShowAnswer()
                advanceUntilIdle()
                viewModel.executeAction(ViewerAction.TOGGLE_AUTO_ADVANCE)
                testScheduler.runCurrent()
                assertIs<MockTime>(TimeManager.time).addM(2)
                viewModel.answerCard(Rating.GOOD)
                advanceUntilIdle()
                val breakDialog = ShadowDialog.getShownDialogs().single { it.isShowing }
                assertEquals(1, col.getCard(card.id).reps)
                val queueAfterAnswer = col.getCard(card.id).queue

                val webView = assertIs<WebView>(binding.webViewLayout.getChildAt(0))
                shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, mockWebResourceRequest("market://details?id=com.example"))
                ShadowDialog.getShownDialogs().single { it.isShowing && it !== breakDialog }.cancel()
                advanceRobolectricLooper()
                advanceUntilIdle()

                assertTrue(breakDialog.isShowing)
                assertEquals(queueAfterAnswer, col.getCard(card.id).queue, "the break still owns the pause")
            }
        }

    @Test
    fun `closing the warning before answer autoplay starts still waits for audio`() =
        runTest {
            val card = addBasicNote("front", "[sound:answer.webm]").firstCard()
            val config = col.decks.configDictForDeckId(card.currentDeckId())
            config.secondsToShowQuestion = 0.0
            config.secondsToShowAnswer = 1.0
            config.jsonObject.put("waitForAudio", true)
            config.jsonObject.put(DeckConfig.ANSWER_ACTION, 2)
            col.decks.save(config)

            withReviewer {
                val rendering = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val collector =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        viewModel.eval.collect { script ->
                            if (script.startsWith("_showQuestion(")) {
                                rendering.complete(Unit)
                                release.await()
                            }
                        }
                    }
                try {
                    viewModel.onPageFinished(true)
                    rendering.await()
                    viewModel.executeAction(ViewerAction.TOGGLE_AUTO_ADVANCE)
                    testScheduler.runCurrent()
                    viewModel.onShowAnswer()
                    testScheduler.runCurrent()
                    assertTrue(viewModel.showingAnswer.value)
                    assertFalse(viewModel.cardMediaPlayer.isPlaying)
                    val webView = assertIs<WebView>(binding.webViewLayout.getChildAt(0))
                    shadowOf(
                        webView,
                    ).webViewClient.shouldOverrideUrlLoading(webView, mockWebResourceRequest("market://details?id=com.example"))
                    ShadowDialog.getShownDialogs().single { it.isShowing }.cancel()
                    advanceRobolectricLooper()
                    testScheduler.runCurrent()
                    release.complete(Unit)
                    advanceRobolectricLooperUntil {
                        testScheduler.runCurrent()
                        shadowOf(webView).lastEvaluatedJavascript?.contains("var videos") == true
                    }
                    advanceTimeBy(1500)
                    testScheduler.runCurrent()
                    assertEquals(0, col.getCard(card.id).reps, "answer playback is still in progress")
                    val playback = assertNotNull(viewModel.cardMediaPlayer.playAvTagsJob)
                    viewModel.onVideoFinished()
                    playback.join()
                    advanceUntilIdle()
                    assertEquals(1, col.getCard(card.id).reps)
                } finally {
                    release.complete(Unit)
                    collector.cancel()
                }
            }
        }

    @Test
    fun `an automatic grade waiting on the collection respects an open warning`() =
        runTest {
            val card = addBasicNote().firstCard()
            val config = col.decks.configDictForDeckId(card.currentDeckId())
            config.secondsToShowQuestion = 0.0
            config.secondsToShowAnswer = 1.0
            config.jsonObject.put("waitForAudio", false)
            config.jsonObject.put(DeckConfig.ANSWER_ACTION, 2)
            col.decks.save(config)
            withReviewer {
                viewModel.onShowAnswer()
                advanceUntilIdle()
                viewModel.executeAction(ViewerAction.TOGGLE_AUTO_ADVANCE)
                testScheduler.runCurrent()
                val pending = ArrayDeque<Runnable>()
                val delayed =
                    object : kotlinx.coroutines.CoroutineDispatcher() {
                        override fun dispatch(
                            context: kotlin.coroutines.CoroutineContext,
                            block: Runnable,
                        ) {
                            pending.addLast(block)
                        }
                    }
                val previous =
                    com.ichi2.anki.CollectionManager
                        .setTestDispatcher(delayed, useReentrantLock = false)
                try {
                    advanceTimeBy(1100)
                    testScheduler.runCurrent()
                    assertTrue(pending.isNotEmpty(), "the automatic action must be waiting on the collection")
                    val webView = assertIs<WebView>(binding.webViewLayout.getChildAt(0))
                    shadowOf(
                        webView,
                    ).webViewClient.shouldOverrideUrlLoading(webView, mockWebResourceRequest("market://details?id=com.example"))
                    val warning = ShadowDialog.getShownDialogs().single { it.isShowing }
                    com.ichi2.anki.CollectionManager
                        .setTestDispatcher(previous)
                    while (pending.isNotEmpty()) pending.removeFirst().run()
                    advanceUntilIdle()
                    assertTrue(warning.isShowing)
                    assertEquals(0, col.getCard(card.id).reps, "a database-bound grade must respect the warning")
                } finally {
                    com.ichi2.anki.CollectionManager
                        .setTestDispatcher(previous)
                    while (pending.isNotEmpty()) pending.removeFirst().run()
                }
            }
        }

    @Test
    fun `an automatic grade already waiting for a reviewer action respects an open warning`() =
        runTest {
            val card = addBasicNote().firstCard()
            val config = col.decks.configDictForDeckId(card.currentDeckId())
            config.secondsToShowQuestion = 0.0
            config.secondsToShowAnswer = 1.0
            config.jsonObject.put("waitForAudio", false)
            config.jsonObject.put(DeckConfig.ANSWER_ACTION, 2)
            col.decks.save(config)
            withReviewer {
                viewModel.onShowAnswer()
                advanceUntilIdle()
                viewModel.executeAction(ViewerAction.TOGGLE_AUTO_ADVANCE)
                testScheduler.runCurrent()
                val entered = CompletableDeferred<Unit>()
                val ready = CompletableDeferred<Unit>()
                val collector =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        viewModel.eval.collect { script ->
                            if (script == "ankidroid.userAction(1);") {
                                entered.complete(Unit)
                                ready.await()
                            }
                        }
                    }
                try {
                    viewModel.executeAction(ViewerAction.USER_ACTION_1)
                    entered.await()
                    viewModel.executeAction(ViewerAction.USER_ACTION_1)
                    advanceTimeBy(1100)
                    testScheduler.runCurrent()
                    assertEquals(0, col.getCard(card.id).reps)
                    val webView = assertIs<WebView>(binding.webViewLayout.getChildAt(0))
                    shadowOf(
                        webView,
                    ).webViewClient.shouldOverrideUrlLoading(webView, mockWebResourceRequest("market://details?id=com.example"))
                    val warning = ShadowDialog.getShownDialogs().single { it.isShowing }

                    ready.complete(Unit)
                    advanceUntilIdle()
                    assertTrue(warning.isShowing)
                    assertEquals(0, col.getCard(card.id).reps, "the queued grade must honor the warning")
                    warning.cancel()
                    advanceRobolectricLooper()
                    advanceUntilIdle()
                    assertEquals(1, col.getCard(card.id).reps)
                } finally {
                    ready.complete(Unit)
                    collector.cancel()
                }
            }
        }

    @Test
    fun `recreated WebView retains keyboard support`() =
        runTest {
            addBasicWithTypingNote("front", "back")
            withReviewer {
                val layout = binding.webViewLayout
                val original = assertIs<TypeAnswerWebView>(layout.getChildAt(0))
                layout.onRenderProcessGone(original)
                val replacement = assertIs<TypeAnswerWebView>(layout.getChildAt(0))
                assertNotSame(original, replacement)
                assertNotNull(shadowOf(replacement).getJavascriptInterface("AnkiDroidKeyboard"))
            }
        }

    @Test
    fun `software rendering is applied to the study screen when enabled`() =
        runTest {
            addBasicNote()
            Prefs.useSoftwareRendering = true

            withReviewer {
                assertRenderingLayerType(View.LAYER_TYPE_SOFTWARE)
            }
        }

    @Test
    fun `software rendering is reapplied after the WebView is recreated`() =
        runTest {
            addBasicNote()
            Prefs.useSoftwareRendering = true

            withReviewer {
                val webViewLayout = binding.webViewLayout
                val originalWebView = assertIs<WebView>(webViewLayout.getChildAt(0))
                webViewLayout.onRenderProcessGone(originalWebView)

                assertNotSame(originalWebView, webViewLayout.getChildAt(0))
                assertRenderingLayerType(View.LAYER_TYPE_SOFTWARE)
            }
        }

    @Test
    fun `card WebView uses the theme background before the card loads`() =
        runTest {
            addBasicNote()
            RuntimeEnvironment.setQualifiers("+night")
            PrefsRepository(targetContext).apply {
                appTheme = AppTheme.NIGHT
                nightTheme = NightTheme.DARK
            }

            withReviewer {
                val background = ThemeUtils.getThemeAttrColor(requireContext(), android.R.attr.colorBackground)
                val webViewLayout = binding.webViewLayout
                assertEquals(background, shadowOf(assertIs<WebView>(webViewLayout.getChildAt(0))).backgroundColor, "card WebView")

                webViewLayout.onRenderProcessGone(assertIs<WebView>(webViewLayout.getChildAt(0)))
                assertEquals(background, shadowOf(assertIs<WebView>(webViewLayout.getChildAt(0))).backgroundColor, "recreated WebView")
            }
        }

    @Test
    fun `default rendering is preserved when software rendering is disabled`() =
        runTest {
            addBasicNote()
            Prefs.useSoftwareRendering = false

            withReviewer {
                assertRenderingLayerType(View.LAYER_TYPE_NONE)
            }
        }

    @Test
    fun `default rendering is preserved when the rendering preference is unset`() =
        runTest {
            addBasicNote()
            Prefs.sharedPrefs.edit {
                remove(Prefs.key(R.string.disable_hardware_render_key))
            }

            withReviewer {
                assertRenderingLayerType(View.LAYER_TYPE_NONE)
            }
        }

    @Test
    fun `nosuggest supports Done`() =
        runTest {
            targetContext.sharedPrefs().edit { putBoolean("useInputTag", false) }
            col.addNoSuggestNote()
            withReviewer {
                val field = binding.typeAnswerEditText
                assertTrue(field.onCheckIsTextEditor())
                assertNotEquals(InputType.TYPE_NULL, field.inputType)
                val info = EditorInfo()
                val connection = field.createInputConnection(info)
                assertEquals(InputType.TYPE_NULL, info.inputType)

                connection.performEditorAction(EditorInfo.IME_ACTION_DONE)
                advanceUntilIdle()
                assertTrue(viewModel.showingAnswer.value)
            }
        }

    @Test
    @Config(shadows = [RecordingInputMethodManager::class])
    fun `nosuggest resets for the next typing card`() =
        runTest {
            targetContext.sharedPrefs().edit { putBoolean("useInputTag", false) }
            val noSuggestCard = col.addNoSuggestNote().firstCard()
            val normalCard = addBasicWithTypingNote("Normal question", "Answer").firstCard()
            withReviewer {
                val field = binding.typeAnswerEditText
                val originalInputType = field.inputType
                val inputMethodManager = Shadow.extract<RecordingInputMethodManager>(field.context.getSystemService<InputMethodManager>())
                assertEquals(noSuggestCard.id, viewModel.getCardId())
                assertTrue(field.onCheckIsTextEditor())
                assertNotEquals(InputType.TYPE_NULL, field.inputType)
                val noSuggestInfo = EditorInfo()
                field.createInputConnection(noSuggestInfo)
                assertEquals(InputType.TYPE_NULL, noSuggestInfo.inputType)

                viewModel.onShowAnswer()
                advanceUntilIdle()
                assertTrue(viewModel.showingAnswer.value)

                inputMethodManager.restartedViews.clear()
                viewModel.answerCard(Rating.EASY)
                advanceUntilIdle()
                assertEquals(normalCard.id, viewModel.getCardId())
                assertContains(inputMethodManager.restartedViews, field)
                assertTrue(field.onCheckIsTextEditor())
                assertEquals(originalInputType, field.inputType)
                val normalInfo = EditorInfo()
                field.createInputConnection(normalInfo)
                assertEquals(originalInputType, normalInfo.inputType)
            }
        }

    @Test
    fun `shaking does not stack set due date dialogs`() = assertShakeDoesNotStackDialogs(ViewerAction.RESCHEDULE_NOTE)

    @Test
    fun `shaking does not stack edit tags dialogs`() = assertShakeDoesNotStackDialogs(ViewerAction.TAG)

    @Test
    fun `repeated set due date actions preserve the open dialog`() =
        runTest {
            val cardId = addBasicNote().firstCard().id
            withReviewer {
                viewModel.executeAction(ViewerAction.RESCHEDULE_NOTE)
                advanceUntilIdle()
                advanceRobolectricLooper()
                val dialog = assertIs<SetDueDateDialog>(currentDialog)
                dialog.singleDayText.setText("12")
                val backStackCount = parentFragmentManager.backStackEntryCount

                repeat(3) {
                    viewModel.executeAction(ViewerAction.RESCHEDULE_NOTE)
                }
                advanceUntilIdle()
                advanceRobolectricLooper()

                assertSame(dialog, currentDialog)
                assertEquals(backStackCount, parentFragmentManager.backStackEntryCount)
                assertEquals("12", dialog.singleDayText.text.toString())
                assertEquals(listOf(cardId), dialog.cardIds)
            }
        }

    @Test
    fun `repeated edit tags actions preserve unconfirmed selections`() =
        runTest {
            addBasicNote()
            withReviewer {
                val dialog = openEditTags(this)
                dialog.addTag("unconfirmed")
                advanceUntilIdle()
                val tags = dialog.viewModel.tags.await()
                assertTrue(tags.isChecked("unconfirmed"))
                val backStackCount = parentFragmentManager.backStackEntryCount

                repeat(3) {
                    viewModel.executeAction(ViewerAction.TAG)
                }
                advanceUntilIdle()
                advanceRobolectricLooper()

                assertSame(dialog, currentDialog)
                assertEquals(backStackCount, parentFragmentManager.backStackEntryCount)
                assertTrue(tags.isChecked("unconfirmed"))
            }
        }

    @Test
    fun `edit tags can reopen after dismissal without unconfirmed selections`() =
        runTest {
            addBasicNote()
            withReviewer {
                val dialog = openEditTags(this)
                dialog.addTag("unconfirmed")
                advanceUntilIdle()
                val tags = dialog.viewModel.tags.await()
                assertTrue(tags.isChecked("unconfirmed"))

                dialog.dismiss()
                advanceRobolectricLooper()
                assertNull(currentDialog)

                val reopenedDialog = openEditTags(this)
                assertNotSame(dialog, reopenedDialog)
                assertTrue(reopenedDialog.requireDialog().isShowing)
                val reopenedTags = reopenedDialog.viewModel.tags.await()
                assertFalse(reopenedTags.isChecked("unconfirmed"))
            }
        }

    @Test
    fun `shake reaches the study screen while the whiteboard is hidden`() =
        runTest {
            val cardId = addBasicNote().firstCard().id
            bindToStudyScreenAndWhiteboard(ReviewerBinding.fromGesture(Gesture.SHAKE))
            withReviewer {
                hideWhiteboard(this)
                hearShake()
                advanceUntilIdle()
                advanceRobolectricLooper()
                assertEquals(listOf(cardId), assertIs<SetDueDateDialog>(currentDialog).cardIds)
            }
        }

    @Test
    fun `key press reaches the study screen while the whiteboard is hidden`() =
        runTest {
            val cardId = addBasicNote().firstCard().id
            bindToStudyScreenAndWhiteboard(ReviewerBinding(Binding.KeyCode(KeyEvent.KEYCODE_F5), CardSide.BOTH))
            withReviewer {
                hideWhiteboard(this)
                dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F5))
                advanceUntilIdle()
                advanceRobolectricLooper()
                assertEquals(listOf(cardId), assertIs<SetDueDateDialog>(currentDialog).cardIds)
            }
        }

    private fun bindToStudyScreenAndWhiteboard(binding: ReviewerBinding) {
        val bindings = listOf(binding).toPreferenceString()
        targetContext.sharedPrefs().edit {
            putString(ViewerAction.RESCHEDULE_NOTE.preferenceKey, bindings)
            putString(WhiteboardAction.CLEAR.preferenceKey, bindings)
        }
        StudyScreenRepository().isWhiteboardEnabled = true
    }

    private fun TestScope.hideWhiteboard(reviewer: ReviewerFragment) {
        advanceRobolectricLooper()
        val whiteboard =
            assertIs<WhiteboardFragment>(reviewer.childFragmentManager.findFragmentByTag(WhiteboardFragment::class.jvmName))
        reviewer.viewModel.whiteboardEnabledFlow.value = false
        advanceUntilIdle()
        advanceRobolectricLooper()
        assertTrue(whiteboard.isHidden)
    }

    private fun ReviewerFragment.assertRenderingLayerType(expected: Int) {
        assertEquals(expected, requireView().layerType, "study screen root")
        assertEquals(expected, binding.backButton.layerType, "toolbar button")
        assertEquals(expected, binding.answerArea.layerType, "answer buttons")
        assertEquals(expected, assertIs<WebView>(binding.webViewLayout.getChildAt(0)).layerType, "card WebView")
    }

    private suspend fun TestScope.openEditTags(reviewer: ReviewerFragment): TagsDialog {
        reviewer.viewModel.executeAction(ViewerAction.TAG)
        advanceUntilIdle()
        advanceRobolectricLooper()
        return assertIs<TagsDialog>(reviewer.currentDialog)
    }

    private fun assertShakeDoesNotStackDialogs(action: ViewerAction) =
        runTest {
            addBasicNote()
            targetContext.sharedPrefs().edit {
                putString(action.preferenceKey, listOf(ReviewerBinding.fromGesture(Gesture.SHAKE)).toPreferenceString())
            }

            withReviewer { controller ->
                fun shake() {
                    hearShake()
                    advanceUntilIdle()
                    advanceRobolectricLooper()
                }

                shake()
                val dialog = assertNotNull(currentDialog)
                assertTrue(dialog.requireDialog().isShowing)
                val backStackCount = parentFragmentManager.backStackEntryCount

                // Android transfers window focus to the dialog while the reviewer remains resumed.
                controller.windowFocusChanged(false)
                repeat(3) { shake() }
                assertSame(dialog, currentDialog)
                assertEquals(backStackCount, parentFragmentManager.backStackEntryCount)

                dialog.dismiss()
                advanceRobolectricLooper()
                assertNull(currentDialog)
                controller.windowFocusChanged(true)

                shake()
                val reopenedDialog = assertNotNull(currentDialog)
                assertNotSame(dialog, reopenedDialog)
                assertTrue(reopenedDialog.requireDialog().isShowing)
            }
        }

    private val ReviewerFragment.currentDialog: DialogFragment?
        get() = parentFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as DialogFragment?

    private suspend fun TestScope.withReviewer(block: suspend ReviewerFragment.(ActivityController<CardViewerActivity>) -> Unit) {
        val intent = ReviewerFragment.getIntent(targetContext)
        Robolectric.buildActivity(CardViewerActivity::class.java, intent).use { controller ->
            controller.setup().windowFocusChanged(true)
            advanceUntilIdle()
            val reviewer = controller.get().fragment as ReviewerFragment
            reviewer.viewModel.onPageFinished(false)
            advanceUntilIdle()
            reviewer.block(controller)
        }
    }
}
