//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.webkit.RenderProcessGoneDetail
import androidx.annotation.CheckResult
import androidx.annotation.RequiresApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.ANSWER_ORDINAL_1
import com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.ANSWER_ORDINAL_2
import com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.ANSWER_ORDINAL_3
import com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.ANSWER_ORDINAL_4
import com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.RELINQUISH_FOCUS
import com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.SHOW_ANSWER
import com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.SIGNAL_NOOP
import com.ichi2.anki.AbstractFlashcardViewer.WebViewSignalParserUtils.TYPE_FOCUS
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.reviewer.AutomaticAnswer
import com.ichi2.anki.reviewer.AutomaticAnswerAction
import com.ichi2.anki.reviewer.AutomaticAnswerSettings
import com.ichi2.anki.servicelayer.LanguageHintService
import com.ichi2.libanki.StdModels
import com.ichi2.libanki.Tuple
import com.ichi2.testutils.AnkiAssert.assertDoesNotThrow
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import java.util.*
import java.util.stream.Stream

@RequiresApi(api = Build.VERSION_CODES.O) // getImeHintLocales, toLanguageTags, onRenderProcessGone, RenderProcessGoneDetail
@RunWith(AndroidJUnit4::class)
class AbstractFlashcardViewerTest : RobolectricTest() {
    class NonAbstractFlashcardViewer : AbstractFlashcardViewer() {
        var answered: Int? = null
        private var mLastTime = 0
        override fun setTitle() {}
        override fun performReload() {
            // intentionally blank
        }

        val typedInput get() = super.typedInputText

        override fun answerCard(ease: Int) {
            super.answerCard(ease)
            answered = ease
        }

        override val elapsedRealTime: Long
            get() {
                mLastTime += AnkiDroidApp.getSharedPrefs(baseContext).getInt(DOUBLE_TAP_TIME_INTERVAL, DEFAULT_DOUBLE_TAP_TIME_INTERVAL)
                return mLastTime.toLong()
            }
        val hintLocale: String?
            get() {
                val imeHintLocales = mAnswerField!!.imeHintLocales ?: return null
                return imeHintLocales.toLanguageTags()
            }

        fun hasAutomaticAnswerQueued(): Boolean {
            return mAutomaticAnswer.timeoutHandler.hasMessages(0)
        }
    }

    /***
     * @param testcase A tuple with string and int as a testcase. The string is the input url and
     *                 the int is the actual answer.
     */
    @ParameterizedTest
    @MethodSource("testcaseProvider")
    fun getSignalFromUrlTest(testcase: Tuple<String, Int>) {
        assertEquals(testcase.first, testcase.second)
    }

    fun testcaseProvider(): Stream<Tuple<String, Int>> {
        return Stream.of(
            Tuple("signal:show_answer", SHOW_ANSWER),
            Tuple("signal:typefocus", TYPE_FOCUS),
            Tuple("signal:relinquishFocus", RELINQUISH_FOCUS),
            Tuple("signal:answer_ease1", ANSWER_ORDINAL_1),
            Tuple("signal:answer_ease2", ANSWER_ORDINAL_2),
            Tuple("signal:answer_ease3", ANSWER_ORDINAL_3),
            Tuple("signal:answer_ease4", ANSWER_ORDINAL_4),
            Tuple("signal:answer_ease0", SIGNAL_NOOP)
        )
    }

    @Test
    fun invalidEncodingDoesNotCrash() {
        // #5944 - input came in as: 'typeblurtext:%'. We've fixed the encoding, but want to make sure there's no crash
        // as JS can call this function with arbitrary data.
        val url = "typeblurtext:%"
        val viewer: NonAbstractFlashcardViewer = getViewer(true)
        assertDoesNotThrow { viewer.handleUrlFromJavascript(url) }
    }

    @Test
    fun validEncodingSetsAnswerCorrectly() {
        // 你好%
        val url = "typeblurtext:%E4%BD%A0%E5%A5%BD%25"
        val viewer: NonAbstractFlashcardViewer = getViewer(true)

        viewer.handleUrlFromJavascript(url)

        assertThat(viewer.typedInput, equalTo("你好%"))
    }

    @Test
    @Suppress("deprecation") // onActivityResult
    fun testEditingCardChangesTypedAnswer() {
        // 7363
        addNoteUsingBasicTypedModel("Hello", "World")

        val viewer: NonAbstractFlashcardViewer = getViewer(true)

        assertThat(viewer.correctTypedAnswer, equalTo("World"))

        waitForAsyncTasksToComplete()

        AbstractFlashcardViewer.editorCard = viewer.mCurrentCard

        val note = viewer.mCurrentCard!!.note()
        note.setField(1, "David")

        viewer.onActivityResult(AbstractFlashcardViewer.EDIT_CURRENT_CARD, Activity.RESULT_OK, Intent())

        waitForAsyncTasksToComplete()

        assertThat(viewer.correctTypedAnswer, equalTo("David"))
    }

    @Test
    @Suppress("deprecation") // onActivityResult
    fun testEditingCardChangesTypedAnswerOnDisplayAnswer() {
        // 7363
        addNoteUsingBasicTypedModel("Hello", "World")

        val viewer: NonAbstractFlashcardViewer = getViewer(true)

        assertThat(viewer.correctTypedAnswer, equalTo("World"))

        viewer.displayCardAnswer()

        assertThat(viewer.cardContent, containsString("World"))

        waitForAsyncTasksToComplete()

        AbstractFlashcardViewer.editorCard = viewer.mCurrentCard

        val note = viewer.mCurrentCard!!.note()
        note.setField(1, "David")

        viewer.onActivityResult(AbstractFlashcardViewer.EDIT_CURRENT_CARD, Activity.RESULT_OK, Intent())

        waitForAsyncTasksToComplete()

        assertThat(viewer.correctTypedAnswer, equalTo("David"))
        assertThat(viewer.cardContent, not(containsString("World")))
        assertThat(viewer.cardContent, containsString("David"))
    }

    @Test
    fun testCommandPerformsAnswerCard() {
        // Regression for #8527/#8572
        // Note: Couldn't get a spy working, so overriding the method

        val viewer: NonAbstractFlashcardViewer = getViewer(true)

        assertThat("Displaying question", viewer.isDisplayingAnswer, equalTo(false))
        viewer.executeCommand(ViewerCommand.COMMAND_FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED)

        assertThat("Displaying answer", viewer.isDisplayingAnswer, equalTo(true))

        viewer.executeCommand(ViewerCommand.COMMAND_FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED)

        assertThat(viewer.answered, notNullValue())
    }

    @Test
    fun defaultLanguageIsNull() {
        assertThat(viewer.hintLocale, nullValue())
    }

    @Test
    fun typedLanguageIsSet() {
        val withLanguage = StdModels.BASIC_TYPING_MODEL.add(col, "a")
        val normal = StdModels.BASIC_TYPING_MODEL.add(col, "b")
        val typedField = 1 // BACK

        LanguageHintService.setLanguageHintForField(col.models, withLanguage, typedField, Locale("ja"))

        addNoteUsingModelName(withLanguage.getString("name"), "ichi", "ni")
        addNoteUsingModelName(normal.getString("name"), "one", "two")
        val viewer = getViewer(false)

        assertThat("A model with a language hint (japanese) should use it", viewer.hintLocale, equalTo("ja"))

        showNextCard(viewer)

        assertThat("A default model should have no preference", viewer.hintLocale, nullValue())
    }

    @Test
    fun automaticAnswerDisabledProperty() {
        val controller = getViewerController(true)
        val viewer = controller.get()
        assertThat("not disabled initially", viewer.mAutomaticAnswer.isDisabled, equalTo(false))
        controller.pause()
        assertThat("disabled after pause", viewer.mAutomaticAnswer.isDisabled, equalTo(true))
        controller.resume()
        assertThat("enabled after resume", viewer.mAutomaticAnswer.isDisabled, equalTo(false))
    }

    @Test
    fun noAutomaticAnswerAfterRenderProcessGoneAndPaused_issue9632() {
        val controller = getViewerController(true)
        val viewer = controller.get()
        viewer.mAutomaticAnswer = AutomaticAnswer(viewer, AutomaticAnswerSettings(AutomaticAnswerAction.BURY_CARD, true, 5, 5))
        viewer.executeCommand(ViewerCommand.COMMAND_SHOW_ANSWER)
        assertThat("messages after flipping card", viewer.hasAutomaticAnswerQueued(), equalTo(true))
        controller.pause()
        assertThat("disabled after pause", viewer.mAutomaticAnswer.isDisabled, equalTo(true))
        assertThat("no auto answer after pause", viewer.hasAutomaticAnswerQueued(), equalTo(false))
        viewer.mOnRenderProcessGoneDelegate.onRenderProcessGone(viewer.webView!!, mock(RenderProcessGoneDetail::class.java))
        assertThat("no auto answer after onRenderProcessGone when paused", viewer.hasAutomaticAnswerQueued(), equalTo(false))
    }

    private fun showNextCard(viewer: NonAbstractFlashcardViewer) {
        viewer.executeCommand(ViewerCommand.COMMAND_FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED)
        viewer.executeCommand(ViewerCommand.COMMAND_FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED)
    }

    @get:CheckResult
    private val viewer: NonAbstractFlashcardViewer
        get() = getViewer(true)

    @CheckResult
    private fun getViewer(addCard: Boolean): NonAbstractFlashcardViewer {
        return getViewerController(addCard).get()
    }

    @CheckResult
    private fun getViewerController(addCard: Boolean): ActivityController<NonAbstractFlashcardViewer> {
        if (addCard) {
            val n = col.newNote()
            n.setField(0, "a")
            col.addNote(n)
        }
        val multimediaController = Robolectric.buildActivity(NonAbstractFlashcardViewer::class.java, Intent())
            .create().start().resume().visible()
        saveControllerForCleanup(multimediaController)
        val viewer = multimediaController.get()
        viewer.onCollectionLoaded(col)
        viewer.loadInitialCard()
        // Without this, AbstractFlashcardViewer.mCard is still null, and RobolectricTest.tearDown executes before
        // AsyncTasks spawned by by loading the viewer finish. Is there a way to synchronize these things while under test?
        advanceRobolectricLooperWithSleep()
        advanceRobolectricLooperWithSleep()
        return multimediaController
    }
}
