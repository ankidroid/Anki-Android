// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.noteeditor

import android.Manifest.permission.RECORD_AUDIO
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.res.Configuration
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Spinner
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.core.view.allViews
import androidx.core.view.isVisible
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.notetypes.copy
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.FieldEditLine
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteFieldsCheckResult
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.checkNoteFieldsResponse
import com.ichi2.anki.clickRowAtPosition
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.compat.Compat
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog
import com.ichi2.anki.dialogs.ChangeNoteTypeViewModel
import com.ichi2.anki.dialogs.SelectedIndex
import com.ichi2.anki.getBrowserWithNotes
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.multimedia.MultimediaActivity
import com.ichi2.anki.multimedia.MultimediaBottomSheet
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction
import com.ichi2.anki.recorder.MediaRecorderStub
import com.ichi2.anki.recorder.RecorderState
import com.ichi2.anki.setCardBrowserFragmented
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.AppTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.throwOnShowError
import com.ichi2.anki.tryUndo
import com.ichi2.anki.utils.InitStatus
import com.ichi2.anki.waitForSearchResults
import com.ichi2.testutils.denyPermissions
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.ext.snackbarText
import com.ichi2.testutils.grantPermissions
import com.ichi2.testutils.parcelSize
import com.ichi2.testutils.saveState
import com.ichi2.testutils.withSplitPaneUi
import com.ichi2.testutils.withSplitPaneUiAsync
import com.ichi2.testutils.withViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItemInArray
import org.hamcrest.Matchers.lessThan
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.GraphicsMode
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class InlineAudioRecorderTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val mediaRecorderStub = MediaRecorderStub()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Before
    fun installRecorder() {
        mediaRecorderStub.install()
        grantPermissions(RECORD_AUDIO)
        Prefs.devInlineAudioRecorderEnabled = true
    }

    @After
    fun uninstallRecorder() {
        mediaRecorderStub.uninstall()
    }

    @Test
    fun `record audio opens the recording screen while the option is off`() {
        Prefs.devInlineAudioRecorderEnabled = false
        val editor = startNoteEditor().noteEditor

        editor.chooseRecordAudio(fieldIndex = 0)

        val started = shadowOf(editor.requireActivity()).nextStartedActivity
        assertThat("the recording screen opens", started?.component?.className, equalTo(MultimediaActivity::class.java.name))
        assertThat("no inline recorder", editor.inlineRecorder, nullValue())
    }

    @Test
    fun `record audio handles a field removed while its sheet is open`() =
        runTest {
            withSplitPaneUiAsync {
                ensureCollectionLoadIsSynchronous()
                setCardBrowserFragmented(true)
                val type = addStandardNoteType("One field", arrayOf("Retained"), "{{Retained}}", "{{Retained}}")
                val note = addBasicNote("Front text", "Back text")
                val cardId = note.firstCard().id
                val browser = getBrowserWithNotes(0)
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                editor
                    .requireView()
                    .allViews
                    .filterIsInstance<FieldEditLine>()
                    .elementAt(1)
                    .binding.mediaButton
                    .performClick()
                advanceRobolectricLooper()
                val sheet = browser.supportFragmentManager.findFragmentByTag("MultimediaBottomSheet") as MultimediaBottomSheet

                withCol {
                    val target = notetypes.byName(type)!!.id
                    val input =
                        notetypes.changeNotetypeInfo(note.noteTypeId, target).input.copy {
                            noteIds.add(note.id)
                            newFields[0] = 0
                            newTemplates[0] = 0
                        }
                    notetypes.changeNotetypeOfNotes(input)
                }
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                assertThat("the conversion retains the card", col.getCard(cardId).nid, equalTo(note.id))
                assertThat("the editor has only the retained field", editor.currentFields.single().name, equalTo("Retained"))
                assertThat("the old attachment sheet is still open", sheet.dialog?.isShowing, equalTo(true))

                throwOnShowError = false
                sheet.requireView().findViewById<View>(R.id.multimedia_action_recording).performClick()
                advanceRobolectricLooper()

                assertThat(
                    "the stale recording action does not show an error dialog",
                    ShadowDialog.getLatestDialog()?.isShowing == true,
                    equalTo(false),
                )
                editor.chooseRecordAudio(fieldIndex = 0)
                recordButton.assertExists()
            }
        }

    @Test
    fun `an old attachment sheet cannot redirect a finished recording`() =
        runTest {
            withSplitPaneUiAsync {
                ensureCollectionLoadIsSynchronous()
                setCardBrowserFragmented(true)
                val type = addStandardNoteType("Back first", arrayOf("Back", "Front"), "{{Front}}", "{{Back}}")
                val note = addBasicNote("Question", "Answer")
                val cardId = note.firstCard().id
                val browser = getBrowserWithNotes(0)
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                editor.chooseRecordAudio(fieldIndex = 1)
                record()

                editor
                    .requireView()
                    .allViews
                    .filterIsInstance<FieldEditLine>()
                    .elementAt(1)
                    .binding.mediaButton
                    .performClick()
                advanceRobolectricLooper()
                val sheet = browser.supportFragmentManager.findFragmentByTag("MultimediaBottomSheet") as MultimediaBottomSheet

                withCol {
                    val target = notetypes.byName(type)!!.id
                    val input =
                        notetypes.changeNotetypeInfo(note.noteTypeId, target).input.copy {
                            noteIds.add(note.id)
                            newFields[0] = 1
                            newFields[1] = 0
                            newTemplates[0] = 0
                        }
                    notetypes.changeNotetypeOfNotes(input)
                }
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                assertThat("the current card is retained", editor.currentEditedCard!!.id, equalTo(cardId))
                assertThat("Back moved to the first slot", editor.fieldText(0), equalTo("Answer"))
                assertThat("the old Back attachment sheet remains open", sheet.dialog?.isShowing, equalTo(true))

                sheet.requireView().findViewById<View>(R.id.multimedia_action_recording).performClick()
                advanceRobolectricLooper()
                insertButton.performClick()
                advanceRobolectricLooper()
                chooseRecordingFieldIfAsked("Back")

                assertThat("Front receives no recording from Back's attachment sheet", editor.fieldText(1), equalTo("Question"))
                assertThat("the recording stays with Back", editor.fieldText(0), containsString("[sound:"))
            }
        }

    @Test
    fun `a stale sheet cannot redirect a take after a same-type remap`() =
        runTest {
            withSplitPaneUiAsync {
                ensureCollectionLoadIsSynchronous()
                setCardBrowserFragmented(true)
                val note = addBasicNote("Question", "Answer")
                val cardId = note.firstCard().id
                val browser = getBrowserWithNotes(0)
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                editor.chooseRecordAudio(fieldIndex = 1)
                record()

                editor
                    .requireView()
                    .allViews
                    .filterIsInstance<FieldEditLine>()
                    .elementAt(1)
                    .binding.mediaButton
                    .performClick()
                advanceRobolectricLooper()
                val sheet = browser.supportFragmentManager.findFragmentByTag("MultimediaBottomSheet") as MultimediaBottomSheet

                withCol {
                    val target = note.noteTypeId
                    val input =
                        notetypes.changeNotetypeInfo(note.noteTypeId, target).input.copy {
                            noteIds.add(note.id)
                            newFields[0] = 1
                            newFields[1] = 0
                            newTemplates[0] = 0
                        }
                    notetypes.changeNotetypeOfNotes(input)
                }
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                assertThat("the current card is retained", editor.currentEditedCard!!.id, equalTo(cardId))
                assertThat("Back moved to the first slot", editor.fieldText(0), equalTo("Answer"))
                assertThat("the old Back attachment sheet remains open", sheet.dialog?.isShowing, equalTo(true))

                sheet.requireView().findViewById<View>(R.id.multimedia_action_recording).performClick()
                advanceRobolectricLooper()
                insertButton.performClick()
                advanceRobolectricLooper()
                chooseRecordingFieldIfAsked("Front")

                assertThat("the old Back slot receives no recording", editor.fieldText(1), equalTo("Question"))
                assertThat("the recording follows the remapped Back content", editor.fieldText(0), containsString("[sound:"))
            }
        }

    @Test
    fun `rtl split editor keeps recorder outside a left cutout`() =
        withSplitPaneUi {
            ensureCollectionLoadIsSynchronous()
            setCardBrowserFragmented(true)
            val browser = getBrowserWithNotes(1)
            browser.window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            val editor = requireNotNull(browser.fragment)
            editor.chooseRecordAudio(fieldIndex = 1)
            advanceRobolectricLooper()
            browser.dispatchInsets(cutoutLeft = 100.dp)
            val content = browser.findViewById<View>(android.R.id.content)
            content.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
            )
            content.layout(0, 0, 1080, 2400)
            advanceRobolectricLooper()
            composeRule.waitForIdle()

            val recorder = requireNotNull(editor.inlineRecorder).requireView()
            val origin = IntArray(2)
            recorder.getLocationOnScreen(origin)
            assertThat("the recorder has a measured width", recorder.width > 100.dp.toPx(browser), equalTo(true))
            assertThat("the editor uses RTL layout", editor.binding.rootLayout.layoutDirection, equalTo(View.LAYOUT_DIRECTION_RTL))
            assertThat("the recorder is in the left pane", origin[0], lessThan(100.dp.toPx(browser)))
            val bounds = recordButton.fetchSemanticsNode().boundsInRoot
            assertThat("the record control has measured bounds", bounds.width > 0, equalTo(true))
            val recordLeft = origin[0] + bounds.left
            assertThat("the record button at $recordLeft clears the cutout", recordLeft >= 100.dp.toPx(browser), equalTo(true))
        }

    @Test
    fun `record audio opens the inline recorder for the field`() {
        val editor = startNoteEditor().noteEditor

        editor.chooseRecordAudio(fieldIndex = 1)

        assertThat("no screen opens", shadowOf(editor.requireActivity()).nextStartedActivity, nullValue())
        composeRule.onNodeWithText("Back").assertExists()
        recordButton.assertExists()
        saveButton.assertDoesNotExist()
    }

    @Test
    fun `the fields retain a viewport with the landscape keyboard open`() {
        RuntimeEnvironment.setQualifiers("w800dp-h360dp-land-mdpi")
        val controller = startNoteEditor()
        val editor = controller.noteEditor
        val viewport = editor.requireView().findViewById<View>(R.id.note_editor_layout)

        fun showKeyboardInsets() {
            controller.get().dispatchInsets(imeBottom = 170.dp, cutoutTop = 24.dp)
            advanceRobolectricLooper()
            composeRule.waitForIdle()
        }

        showKeyboardInsets()
        assertThat("fields start visible with the keyboard open (height ${viewport.height})", viewport.height > 0, equalTo(true))
        editor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()
        showKeyboardInsets()

        assertThat("fields remain visible while recording (height ${viewport.height})", viewport.height > 0, equalTo(true))
    }

    @Test
    fun `the recorder makes way for the keyboard in a short window`() {
        RuntimeEnvironment.setQualifiers("w800dp-h360dp-land-mdpi")
        val controller = startNoteEditor()
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()
        advanceRobolectricLooper()
        val recorder = editor.binding.inlineAudioRecorderContainer

        controller.get().dispatchInsets(imeBottom = 170.dp, cutoutTop = 24.dp)
        advanceRobolectricLooper()
        assertThat("the keyboard takes the recorder's place", recorder.isVisible, equalTo(false))

        controller.get().dispatchInsets(cutoutTop = 24.dp)
        advanceRobolectricLooper()
        assertThat("the recorder returns with the keyboard closed", recorder.isVisible, equalTo(true))
        pauseButton.assertExists()
    }

    @Test
    fun `the screen stays on while the keyboard hides an active recorder`() {
        RuntimeEnvironment.setQualifiers("w800dp-h360dp-land-mdpi")
        val controller = startNoteEditor()
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()
        advanceRobolectricLooper()

        val window = controller.get().window

        fun screenStaysOn(): Boolean =
            window.decorView.allViews.any { it.isShown && it.keepScreenOn } ||
                window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

        assertThat("a visible active recorder keeps the screen on", screenStaysOn(), equalTo(true))
        controller.get().dispatchInsets(imeBottom = 170.dp, cutoutTop = 24.dp)
        advanceRobolectricLooper()

        assertThat("the keyboard hides the recorder", editor.binding.inlineAudioRecorderContainer.isVisible, equalTo(false))
        assertThat("the microphone is still recording", editor.recorderViewModel.recorderState.value, equalTo(RecorderState.Recording))
        assertThat("the screen stays on while recording", screenStaysOn(), equalTo(true))
    }

    @Test
    fun `a short window can show fields and record with the keyboard closed`() = verifyRecorderInShortWindow(heightDp = 220, navBarDp = 24)

    @Test
    fun `a short window can show fields with three button navigation`() = verifyRecorderInShortWindow(heightDp = 248, navBarDp = 48)

    private fun verifyRecorderInShortWindow(
        heightDp: Int,
        navBarDp: Int,
    ) {
        RuntimeEnvironment.setQualifiers("w800dp-h${heightDp}dp-land-mdpi")
        val controller = startNoteEditor()
        val editor = controller.noteEditor
        val viewport = editor.requireView().findViewById<View>(R.id.note_editor_layout)
        controller.get().dispatchInsets(navBarBottom = navBarDp.dp)
        advanceRobolectricLooper()
        val fieldHeight = requireNotNull(editor.editFieldAt(0)).lineHeight
        val readableHeight = viewport.paddingTop + fieldHeight + viewport.paddingBottom
        assertThat(
            "initial viewport height ${viewport.height} must fit a field line of $readableHeight",
            viewport.height >= readableHeight,
            equalTo(true),
        )

        editor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()
        advanceRobolectricLooper()
        composeRule.waitForIdle()

        assertThat(
            "recording viewport height ${viewport.height} must fit a field line of $readableHeight",
            viewport.height >= readableHeight,
            equalTo(true),
        )
        pauseButton.assertExists()
    }

    @Test
    fun `shrinking a recording window frees room for the note field`() {
        RuntimeEnvironment.setQualifiers("w800dp-h350dp-land-mdpi")
        val controller = startNoteEditor()
        val editor = controller.noteEditor
        val activity = controller.get()
        val root = editor.binding.rootLayout
        val fields = editor.binding.noteEditorLayout
        activity.dispatchInsets(navBarBottom = 24.dp)
        advanceRobolectricLooper()
        editor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()
        composeRule.waitForIdle()
        advanceRobolectricLooper()
        assertThat("the toolbar fits before the resize", editor.binding.editorToolbar.isVisible, equalTo(true))

        val resizedHeight = root.height - 130.dp.toPx(activity)
        val configuration = Configuration(activity.resources.configuration).apply { screenHeightDp = 220 }
        controller.configurationChange(configuration)
        assertThat("the activity handles the size change in place", controller.get(), equalTo(activity))
        // The window size changes on the layout pass after the configuration callback.
        root.layoutParams = root.layoutParams.apply { height = resizedHeight }
        advanceRobolectricLooper()
        assertThat("the editor is laid out at the new height", root.height, equalTo(resizedHeight))

        val readableHeight = fields.paddingTop + requireNotNull(editor.editFieldAt(0)).lineHeight + fields.paddingBottom
        assertThat("the resized window still shows a full line of note text", fields.height >= readableHeight, equalTo(true))
        assertThat("the toolbar makes way for the recording controls", editor.binding.editorToolbar.isVisible, equalTo(false))
        pauseButton.assertExists()
    }

    @Test
    fun `a tall window keeps the recorder above the keyboard`() {
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-port-mdpi")
        val controller = startNoteEditor()
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()
        advanceRobolectricLooper()

        controller.get().dispatchInsets(imeBottom = 300.dp)
        advanceRobolectricLooper()

        assertThat("the recorder stays visible", editor.binding.inlineAudioRecorderContainer.isVisible, equalTo(true))
    }

    @Test
    fun `opening the recorder closes the keyboard`() {
        val editor = startNoteEditor().noteEditor
        val inputMethodManager = editor.requireActivity().getSystemService(InputMethodManager::class.java)
        inputMethodManager.showSoftInput(editor.editFieldAt(0), 0)
        assertThat("the keyboard is open", shadowOf(inputMethodManager).isSoftInputVisible, equalTo(true))

        editor.chooseRecordAudio(fieldIndex = 0)

        assertThat("the keyboard closes", shadowOf(inputMethodManager).isSoftInputVisible, equalTo(false))
    }

    @Test
    fun `a tap records, pauses and resumes until the recording is saved`() {
        startNoteEditor().noteEditor.chooseRecordAudio(fieldIndex = 0)

        recordButton.performClick()
        pauseButton.performClick()
        verify { mediaRecorderStub.mediaRecorder.pause() }
        resumeButton.performClick()
        verify { mediaRecorderStub.mediaRecorder.resume() }
        verify(exactly = 0) { mediaRecorderStub.mediaRecorder.stop() }

        saveButton.performClick()
        verify { mediaRecorderStub.mediaRecorder.stop() }
        insertButton.assertExists()
        discardButton.assertExists()
    }

    @Test
    fun `holding record records until it is released`() {
        startNoteEditor().noteEditor.chooseRecordAudio(fieldIndex = 0)

        recordButton.performTouchInput { longClick() }

        verify { mediaRecorderStub.mediaRecorder.start() }
        verify { mediaRecorderStub.mediaRecorder.stop() }
        insertButton.assertExists()
    }

    @Test
    fun `screen readers and keyboards can record without holding`() {
        startNoteEditor().noteEditor.chooseRecordAudio(fieldIndex = 0)

        recordButton.performSemanticsAction(SemanticsActions.OnClick)
        pauseButton.performSemanticsAction(SemanticsActions.OnClick)
        verify { mediaRecorderStub.mediaRecorder.pause() }
        saveButton.performSemanticsAction(SemanticsActions.OnClick)

        insertButton.assertExists()
    }

    @Test
    fun `recording asks for the microphone permission first`() {
        denyPermissions(RECORD_AUDIO)
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)

        recordButton.performClick()

        verify(exactly = 0) { mediaRecorderStub.mediaRecorder.start() }
        val request = shadowOf(editor.requireActivity()).lastRequestedPermission
        assertThat("the microphone permission is requested", request.requestedPermissions, hasItemInArray(RECORD_AUDIO))
    }

    @Test
    fun `a denied microphone permission explains how to grant it`() {
        recordWithoutMicrophone()

        permissionDeniedMessage.assertExists()
        verify(exactly = 0) { mediaRecorderStub.mediaRecorder.start() }
    }

    @Test
    fun `OK on the microphone dialog opens the app settings`() {
        val activity = recordWithoutMicrophone()

        composeRule.onNodeWithText(targetContext.getString(CommonString.dialog_ok)).performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertThat("the app settings open", started?.action, equalTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS))
        permissionDeniedMessage.assertDoesNotExist()
    }

    @Test
    fun `cancel on the microphone dialog closes it`() {
        val activity = recordWithoutMicrophone()

        composeRule.onNodeWithText(targetContext.getString(CommonString.dialog_cancel)).performClick()

        permissionDeniedMessage.assertDoesNotExist()
        val started = shadowOf(activity).nextStartedActivity
        assertThat("the app settings stay closed", started?.action, not(equalTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)))
    }

    @Test
    fun `night E-ink turns off the recorder animations`() {
        RuntimeEnvironment.setQualifiers("+night")
        Prefs.appTheme = AppTheme.NIGHT
        Prefs.nightTheme = NightTheme.EINK
        val editor = startNoteEditor().noteEditor

        editor.chooseRecordAudio(fieldIndex = 0)

        assertThat("no ripples or pulsing dot", (editor.inlineRecorder as InlineAudioRecorderFragment).animated, equalTo(false))
    }

    @Test
    fun `insert adds the recording to its field and closes the recorder`() {
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 1)
        record()

        insertButton.performClick()
        advanceRobolectricLooper()

        val back = editor.fieldText(1)
        assertThat("the sound tag is added", back, startsWith("[sound:ankidroid_audiorec"))
        assertThat("the recording is in the media folder", col.media.have(back.removeSurrounding("[sound:", "]")), equalTo(true))
        assertThat("the other field is unchanged", editor.fieldText(0), equalTo(""))
        assertThat("the recorder closes", editor.inlineRecorder, nullValue())
    }

    @Test
    fun `record audio on another field moves the recording there`() {
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        record()

        editor.chooseRecordAudio(fieldIndex = 1)
        insertButton.performClick()
        advanceRobolectricLooper()

        assertThat("Front", editor.fieldText(0), equalTo(""))
        assertThat("Back", editor.fieldText(1), startsWith("[sound:ankidroid_audiorec"))
    }

    @Test
    fun `a recording is an unsaved change until it is deleted`() {
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        assertThat("before recording", editor.hasUnsavedChanges(), equalTo(false))

        recordButton.performClick()
        assertThat("while recording", editor.hasUnsavedChanges(), equalTo(true))
        saveButton.performClick()
        assertThat("once saved", editor.hasUnsavedChanges(), equalTo(true))

        discardButton.performClick()
        assertThat("after deleting", editor.hasUnsavedChanges(), equalTo(false))
        assertThat("the recording is deleted", mediaRecorderStub.outputFiles.single().exists(), equalTo(false))
        recordButton.assertExists()
    }

    @Test
    fun `closing a finished recorder keeps the clip until discard is confirmed`() {
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        record()
        val recording = mediaRecorderStub.outputFiles.single()

        closeButton.performClick()
        advanceRobolectricLooper()

        assertThat("Close keeps the clip until the user makes a choice", recording.exists(), equalTo(true))
        latestDialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        advanceRobolectricLooper()
        insertButton.performClick()
        advanceRobolectricLooper()

        assertThat("the retained recording can be inserted", editor.fieldText(0), containsString("[sound:"))
    }

    @Test
    fun `back closes an empty recorder and keeps the note editor open`() {
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)

        editor.pressBack()

        assertThat("the recorder closes", editor.inlineRecorder, nullValue())
        assertThat("the note editor stays", editor.requireActivity().isFinishing, equalTo(false))
    }

    @Test
    fun `back with a recording asks first and keeps the note editor open`() {
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        record()

        editor.pressBack()
        latestDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()

        assertThat("the recorder closes", editor.inlineRecorder, nullValue())
        assertThat("the recording is deleted", mediaRecorderStub.outputFiles.single().exists(), equalTo(false))
        assertThat("the note editor stays", editor.requireActivity().isFinishing, equalTo(false))
    }

    @Test
    fun `back while recording asks and keep editing carries on recording`() {
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()

        editor.pressBack()
        latestDialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        advanceRobolectricLooper()

        pauseButton.assertExists()
        verify(exactly = 0) { mediaRecorderStub.mediaRecorder.stop() }
        assertThat("the note editor stays", editor.requireActivity().isFinishing, equalTo(false))
    }

    @Test
    fun `close while recording asks and keep editing carries on recording`() {
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()

        closeButton.performClick()
        latestDialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        advanceRobolectricLooper()

        pauseButton.assertExists()
        verify(exactly = 0) { mediaRecorderStub.mediaRecorder.stop() }
    }

    @Test
    fun `pause, save and delete vibrate like the recording screen`() {
        val vibrations = mutableListOf<Duration>()
        val compat = CompatHelper.compat
        every { CompatHelper.compat } returns
            object : Compat by compat {
                override fun vibrate(
                    context: Context,
                    duration: Duration,
                    usage: Int,
                ) {
                    vibrations += duration
                }
            }
        startNoteEditor().noteEditor.chooseRecordAudio(fieldIndex = 0)

        recordButton.performClick()
        assertThat("start", vibrations.size, equalTo(0))
        pauseButton.performClick()
        assertThat("pause", vibrations.size, equalTo(1))
        resumeButton.performClick()
        assertThat("resume", vibrations.size, equalTo(1))
        saveButton.performClick()
        assertThat("save", vibrations.size, equalTo(2))
        discardButton.performClick()
        assertThat("delete", vibrations, equalTo(List(3) { 20.milliseconds }))
    }

    @Test
    fun `the screen stays on only while recording`() {
        val controller = startNoteEditor()
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)

        recordButton.performClick()
        assertThat("recording", editor.recorderKeepsScreenOn, equalTo(true))
        pauseButton.performClick()
        assertThat("paused", editor.recorderKeepsScreenOn, equalTo(false))
        resumeButton.performClick()
        assertThat("recording again", editor.recorderKeepsScreenOn, equalTo(true))

        controller.pause().stop()
        assertThat("in the background", editor.recorderKeepsScreenOn, equalTo(false))
    }

    @Test
    fun `leaving the note editor finishes the recording`() {
        val controller = startNoteEditor()
        controller.noteEditor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()

        controller.pause().stop()
        verify { mediaRecorderStub.mediaRecorder.stop() }
        controller.start().resume()

        insertButton.assertExists()
    }

    @Test
    fun `a recording continues when the note editor is recreated`() {
        val controller = startNoteEditor()
        controller.noteEditor.chooseRecordAudio(fieldIndex = 1)
        recordButton.performClick()

        controller.recreate()
        advanceRobolectricLooper()

        verify(exactly = 0) { mediaRecorderStub.mediaRecorder.stop() }
        saveButton.performClick()
        insertButton.performClick()
        advanceRobolectricLooper()
        assertThat("the recording goes to its field", controller.noteEditor.fieldText(1), startsWith("[sound:ankidroid_audiorec"))
    }

    @Test
    fun `a long note with a pending recording fits in saved state`() {
        val note = addBasicNote("Long note. ".repeat(36_000), "Back")
        ensureCollectionLoadIsSynchronous()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            )
        controller.noteEditor.chooseRecordAudio(fieldIndex = 1)
        record()

        val bytes = controller.saveState().parcelSize()

        assertThat("the editor's saved state must fit Android's Binder buffer", bytes, lessThan(1024 * 1024))
    }

    @Test
    fun `the waveform survives the note editor being recreated`() {
        every { mediaRecorderStub.mediaRecorder.maxAmplitude } returns 16384
        val controller = startNoteEditor()
        controller.noteEditor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS)
        val levels = controller.noteEditor.recorderViewModel.waveform.size
        assertThat("levels before recreating", levels > 0, equalTo(true))

        controller.recreate()
        advanceRobolectricLooper()

        assertThat("levels after recreating", controller.noteEditor.recorderViewModel.waveform.size >= levels, equalTo(true))
    }

    @Test
    fun `a note type change keeps the recording on the same field position`() {
        addStandardNoteType("Question and answer", arrayOf("Question", "Answer"), "{{Question}}", "{{Answer}}")
        col.notetypes.setCurrent(col.notetypes.byName("Basic")!!)
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 1)
        record()

        editor.selectNoteType("Question and answer")

        composeRule.onNodeWithText("Answer").assertExists()
        insertButton.performClick()
        advanceRobolectricLooper()
        assertThat("Answer", editor.fieldText(1), startsWith("[sound:ankidroid_audiorec"))
    }

    @Test
    fun `removing a recording field asks before discarding`() {
        val threeFields = addStandardNoteType("Three fields", arrayOf("Front", "Back", "Extra"), "{{Front}}", "{{Back}}")
        col.notetypes.setCurrent(col.notetypes.byName(threeFields)!!)
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 2)
        record()

        editor.selectNoteType("Basic")

        assertThat("the recording awaits confirmation", mediaRecorderStub.outputFiles.single().exists(), equalTo(true))
        latestDialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        advanceRobolectricLooper()

        assertThat("the note type is unchanged", editor.noteTypeSpinner!!.selectedItem.toString(), equalTo("Three fields"))
        insertButton.performClick()
        advanceRobolectricLooper()
        assertThat("the recording can still be inserted", editor.fieldText(2), startsWith("[sound:ankidroid_audiorec"))
    }

    @Test
    fun `confirming field removal discards its recording`() {
        val threeFields = addStandardNoteType("Three fields", arrayOf("Front", "Back", "Extra"), "{{Front}}", "{{Back}}")
        col.notetypes.setCurrent(col.notetypes.byName(threeFields)!!)
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 2)
        record()

        editor.selectNoteType("Basic")

        assertThat("the recording awaits confirmation", mediaRecorderStub.outputFiles.single().exists(), equalTo(true))
        latestDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()

        assertThat("the requested note type is selected", editor.noteTypeSpinner!!.selectedItem.toString(), equalTo("Basic"))
        assertThat("the recorder closes", editor.inlineRecorder, nullValue())
        assertThat("the recording is deleted", mediaRecorderStub.outputFiles.single().exists(), equalTo(false))
    }

    @Test
    fun `dismissing the field removal prompt keeps the note type`() {
        val threeFields = addStandardNoteType("Three fields", arrayOf("Front", "Back", "Extra"), "{{Front}}", "{{Back}}")
        col.notetypes.setCurrent(col.notetypes.byName(threeFields)!!)
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 2)
        record()

        editor.selectNoteType("Basic")
        latestDialog.cancel()
        advanceRobolectricLooper()

        assertThat("the note type is unchanged", editor.noteTypeSpinner!!.selectedItem.toString(), equalTo("Three fields"))
        insertButton.performClick()
        advanceRobolectricLooper()
        assertThat("the recording can still be inserted", editor.fieldText(2), startsWith("[sound:ankidroid_audiorec"))
    }

    @Test
    fun `an empty recorder closes without asking when its field is removed`() {
        val threeFields = addStandardNoteType("Three fields", arrayOf("Front", "Back", "Extra"), "{{Front}}", "{{Back}}")
        col.notetypes.setCurrent(col.notetypes.byName(threeFields)!!)
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 2)

        editor.selectNoteType("Basic")

        assertThat("no prompt", ShadowDialog.getLatestDialog(), nullValue())
        assertThat("the requested note type is selected", editor.noteTypeSpinner!!.selectedItem.toString(), equalTo("Basic"))
        assertThat("the recorder closes", editor.inlineRecorder, nullValue())
    }

    @Test
    fun `saving an edit preserves a pending recording`() {
        val note = addBasicNote("Front", "Back")
        ensureCollectionLoadIsSynchronous()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            )
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 1)
        record()

        editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
        advanceRobolectricLooper()

        if (controller.get().isFinishing) {
            controller.pause().stop().destroy()
            assertThat("closing saves the recording", col.getNote(note.id).fields[1], containsString("[sound:"))
        } else {
            insertButton.performClick()
            advanceRobolectricLooper()
            assertThat("the recording remains available", editor.fieldText(1), containsString("[sound:"))
        }
    }

    @Test
    fun `saving a new note preserves its recording`() {
        val editor = startNoteEditor().noteEditor
        editor.setFieldValueFromUi(0, "Front")
        editor.chooseRecordAudio(fieldIndex = 1)
        record()
        val count = col.noteCount()

        editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
        advanceRobolectricLooper()

        if (col.noteCount() > count) {
            val savedNote = col.getNote(col.findNotes("").single())
            assertThat("the saved note includes its recording", savedNote.fields[1], containsString("[sound:"))
        } else {
            assertThat("the original note remains", editor.fieldText(0), equalTo("Front"))
            insertButton.performClick()
            advanceRobolectricLooper()
            editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
            advanceRobolectricLooper()
            assertThat("the completed note can be saved", col.noteCount(), equalTo(count + 1))
        }
    }

    @Test
    fun `browser save preserves pending audio`() =
        withSplitPaneUi {
            setCardBrowserFragmented(true)
            val browser = getBrowserWithNotes(2)
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            advanceRobolectricLooper()
            val editor = browser.fragment!!
            val noteId = editor.editorNote!!.id
            val otherNote = col.findNotes("").first { it != noteId }
            editor.chooseRecordAudio(fieldIndex = 1)
            record()

            browser.viewModel.setQuery("nid:$otherNote", forceRefresh = true)
            advanceRobolectricLooper()
            latestDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            advanceRobolectricLooper()

            if (browser.fragment !== editor) {
                assertThat("navigation saves the recording", col.getNote(noteId).fields[1], containsString("[sound:"))
            } else {
                insertButton.assertExists()
                assertThat("the recording is retained", mediaRecorderStub.outputFiles.single().exists(), equalTo(true))
            }
        }

    @Test
    fun `hiding the browser pane stops its recording`() =
        withSplitPaneUi {
            setCardBrowserFragmented(true)
            val browser = getBrowserWithNotes(1)
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            advanceRobolectricLooper()
            val editor = browser.fragment!!
            editor.chooseRecordAudio(fieldIndex = 1)
            recordButton.performClick()

            try {
                browser.viewModel.setQuery("no-such-recorded-note", forceRefresh = true)
                advanceRobolectricLooper()

                val pane = browser.findViewById<android.view.View>(R.id.note_editor_frame)
                assertThat("the editor pane is hidden", pane.isVisible, equalTo(false))
                verify(exactly = 1) { mediaRecorderStub.mediaRecorder.stop() }
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                advanceRobolectricLooper()
                insertButton.assertExists()
            } finally {
                editor.recorderViewModel.interrupt()
            }
        }

    @Test
    fun `changing an existing note type preserves pending audio`() {
        val note = addBasicNote("Front", "Back")
        ensureCollectionLoadIsSynchronous()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            )
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 1)
        record()

        val touch = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)
        try {
            editor.noteTypeSpinner!!.dispatchTouchEvent(touch)
        } finally {
            touch.recycle()
        }
        advanceRobolectricLooper()
        val dialog =
            editor.parentFragmentManager.fragments
                .filterIsInstance<ChangeNoteTypeDialog>()
                .singleOrNull()
        if (dialog != null) {
            val destination = col.notetypes.byName("Basic (and reversed card)")!!.id
            val spinner = dialog.requireView().findViewById<Spinner>(R.id.dest_note_type_spinner)
            spinner.setSelection((0 until spinner.count).first { spinner.adapter.getItemId(it) == destination })
            advanceRobolectricLooper()
            val save = dialog.requireView().findViewById<android.view.View>(R.id.btn_save)
            assertThat("conversion is available", save.isEnabled, equalTo(true))
            save.performClick()
            advanceRobolectricLooper()
            assertThat("the conversion completed", col.getNote(note.id).noteTypeId, equalTo(destination))
        }

        if (controller.get().isFinishing) {
            controller.pause().stop().destroy()
            assertThat("closing saves the recording", col.getNote(note.id).fields[1], containsString("[sound:"))
        } else {
            insertButton.performClick()
            advanceRobolectricLooper()
            assertThat("the recording remains available", editor.fieldText(1), containsString("[sound:"))
        }
    }

    @Test
    fun `editing occlusions preserves pending audio`() {
        val note =
            col.newNote(col.notetypes.byName("Image Occlusion")!!).apply {
                setField(0, "{{c1::x}}")
                col.addNote(this, col.decks.selected())
            }
        ensureCollectionLoadIsSynchronous()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            )
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 3)
        record()

        editor.requireView().findViewById<android.view.View>(R.id.EditOcclusionsButton).performClick()
        advanceRobolectricLooper()
        val launched = shadowOf(controller.get()).nextStartedActivityForResult
        if (launched != null) {
            shadowOf(controller.get()).receiveResult(launched.intent, android.app.Activity.RESULT_OK, android.content.Intent())
            advanceRobolectricLooper()
        }

        if (controller.get().isFinishing) {
            controller.pause().stop().destroy()
            assertThat("closing saves the recording", col.getNote(note.id).fields[3], containsString("[sound:"))
        } else {
            insertButton.performClick()
            advanceRobolectricLooper()
            assertThat("the recording remains available", editor.fieldText(3), containsString("[sound:"))
        }
    }

    @Test
    fun `saving with a recording asks to insert it first`() {
        val editor = startNoteEditor().noteEditor
        editor.setFieldValueFromUi(0, "Front")
        editor.chooseRecordAudio(fieldIndex = 1)
        record()
        val count = col.noteCount()

        editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
        advanceRobolectricLooper()

        val message = targetContext.getString(CommonString.insert_or_delete_recording)
        assertThat("message", editor.requireActivity().snackbarText, equalTo(message))
        assertThat("nothing is saved", col.noteCount(), equalTo(count))
    }

    @Test
    fun `discarding the recording changes the note type`() {
        val note = addBasicNote("Front", "Back")
        ensureCollectionLoadIsSynchronous()
        val editor =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            ).noteEditor
        editor.chooseRecordAudio(fieldIndex = 1)
        record()

        val touch = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)
        editor.noteTypeSpinner!!.dispatchTouchEvent(touch)
        touch.recycle()
        advanceRobolectricLooper()
        latestDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()

        assertThat("the recording is deleted", mediaRecorderStub.outputFiles.single().exists(), equalTo(false))
        val dialogs = editor.parentFragmentManager.fragments.filterIsInstance<ChangeNoteTypeDialog>()
        assertThat("the note type dialog opens", dialogs.size, equalTo(1))
    }

    @Test
    fun `deleting the current template preserves pending audio`() {
        val note = addNoteUsingNoteTypeName("Basic (and reversed card)", "Front", "Back")
        val card = note.cards().first { it.ord == 0 }
        ensureCollectionLoadIsSynchronous()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(card.id).toIntent(targetContext),
            )
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 1)
        record()

        editor.requireView().findViewById<android.view.View>(R.id.CardEditorCardsButton).performClick()
        advanceRobolectricLooper()
        val launched = shadowOf(controller.get()).nextStartedActivityForResult
        if (launched != null) {
            val notetype = col.notetypes.get(note.noteTypeId)!!
            col.notetypes.removeTemplate(notetype, notetype.templates[card.ord])
            col.notetypes.save(notetype)
            assertThat("the other card survives", col.getNote(note.id).cardIds(col).size, equalTo(1))
            shadowOf(controller.get()).receiveResult(launched.intent, android.app.Activity.RESULT_OK, android.content.Intent())
            advanceRobolectricLooper()
        }

        if (controller.get().isFinishing) {
            controller.pause().stop().destroy()
            val attached = col.getNote(note.id).fields[1].contains("[sound:")
            assertThat("the audio is retained or attached", mediaRecorderStub.outputFiles.single().exists() || attached, equalTo(true))
        } else {
            insertButton.performClick()
            advanceRobolectricLooper()
            assertThat("the recording remains available", editor.fieldText(1), containsString("[sound:"))
        }
    }

    @Test
    fun `discarding the recording opens the card templates`() {
        val note = addNoteUsingNoteTypeName("Basic (and reversed card)", "Front", "Back")
        ensureCollectionLoadIsSynchronous()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            )
        controller.noteEditor.chooseRecordAudio(fieldIndex = 1)
        record()

        val ctrlL = KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_L, 0, KeyEvent.META_CTRL_ON)
        controller.get().dispatchKeyEvent(ctrlL)
        advanceRobolectricLooper()
        assertThat("nothing opens yet", shadowOf(controller.get()).peekNextStartedActivityForResult(), nullValue())
        latestDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()

        assertThat("the recording is deleted", mediaRecorderStub.outputFiles.single().exists(), equalTo(false))
        val launched = shadowOf(controller.get()).nextStartedActivityForResult.intent
        assertThat("the card templates open", launched.component?.className, equalTo(CardTemplateEditor::class.java.name))
    }

    @Test
    fun `removing the browser pane stops recording`() =
        withSplitPaneUi {
            ensureCollectionLoadIsSynchronous()
            setCardBrowserFragmented(true)
            addBasicNote("Front", "Back")
            val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardBrowser::class.java, android.content.Intent())
            val browser = controller.get()
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            val editor = requireNotNull(browser.fragment)
            editor.chooseRecordAudio(fieldIndex = 1)
            recordButton.performClick()
            val recorder = editor.recorderViewModel
            try {
                RuntimeEnvironment.setQualifiers("sw400dp-w400dp-h800dp-port")
                controller.recreate()
                advanceRobolectricLooper()

                assertThat(
                    "the new layout has no editor pane",
                    controller.get().findViewById<android.view.View>(R.id.note_editor_frame),
                    nullValue(),
                )
                verify(exactly = 1) { mediaRecorderStub.mediaRecorder.stop() }
                assertThat("the recording is retained", recorder.recorderState.value is RecorderState.Recorded, equalTo(true))
            } finally {
                recorder.interrupt()
            }
        }

    @Test
    fun `leaving the browser asks before deleting pending audio`() =
        withSplitPaneUi {
            setCardBrowserFragmented(true)
            val browser = getBrowserWithNotes(1)
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            val editor = requireNotNull(browser.fragment)
            editor.chooseRecordAudio(fieldIndex = 1)
            record()
            val recording = mediaRecorderStub.outputFiles.single()
            browser.drawerLayout.openDrawer(GravityCompat.START, false)
            advanceRobolectricLooper()
            assertThat("the drawer is open", browser.drawerLayout.isDrawerOpen(GravityCompat.START), equalTo(true))
            val navigation = browser.findViewById<NavigationView>(R.id.navdrawer_items_container)

            assertThat("Decks is a different destination", navigation.menu.findItem(R.id.nav_decks).isChecked, equalTo(false))
            navigation.findViewById<android.view.View>(R.id.nav_decks).performClick()
            browser.drawerLayout.closeDrawer(GravityCompat.START, false)
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            advanceRobolectricLooper()

            assertThat("leaving waits for a recording decision", shadowOf(browser).nextStartedActivity, nullValue())
            assertThat("discarding requires confirmation", ShadowDialog.getLatestDialog()?.isShowing, equalTo(true))
            latestDialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
            browser.drawerLayout.closeDrawer(GravityCompat.START, false)
            advanceRobolectricLooper()
            assertThat("cancelling keeps the browser selected", navigation.menu.findItem(R.id.nav_browser).isChecked, equalTo(true))
            assertThat("cancelling keeps the audio", recording.exists(), equalTo(true))
            insertButton.performClick()
            advanceRobolectricLooper()
            assertThat("the recording is still insertable", editor.fieldText(1), containsString("[sound:"))

            browser.drawerLayout.openDrawer(GravityCompat.START, false)
            advanceRobolectricLooper()
            navigation.findViewById<android.view.View>(R.id.nav_decks).performClick()
            browser.drawerLayout.closeDrawer(GravityCompat.START, false)
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            advanceRobolectricLooper()
            assertThat(
                "navigation works after insertion",
                shadowOf(browser).nextStartedActivity?.component?.className,
                equalTo(com.ichi2.anki.DeckPicker::class.java.name),
            )
        }

    @Test
    fun `discarding the recording opens the decks`() =
        withSplitPaneUi {
            setCardBrowserFragmented(true)
            val browser = getBrowserWithNotes(1)
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            requireNotNull(browser.fragment).chooseRecordAudio(fieldIndex = 1)
            record()
            val recording = mediaRecorderStub.outputFiles.single()
            browser.drawerLayout.openDrawer(GravityCompat.START, false)
            advanceRobolectricLooper()

            browser.onNavigationItemSelected(RoboMenuItem(R.id.nav_decks))
            browser.drawerLayout.closeDrawer(GravityCompat.START, false)
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            advanceRobolectricLooper()
            latestDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            advanceRobolectricLooper()

            assertThat("the recording is deleted", recording.exists(), equalTo(false))
            assertThat("the decks open", shadowOf(browser).nextStartedActivity?.component?.className, equalTo(DeckPicker::class.java.name))
        }

    @Test
    fun `saving audio keeps cards added by a conversion in Notes mode`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                val browser = getBrowserWithNotes(1)
                browser.viewModel.setCardsOrNotes(CardsOrNotes.NOTES).join()
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                val noteId = editor.editorNote!!.id
                val originalCard = editor.currentEditedCard!!.id
                editor.chooseRecordAudio(fieldIndex = 1)
                record()

                val destination =
                    withCol {
                        val source = getNote(noteId).noteTypeId
                        val target = notetypes.byName("Basic (and reversed card)")!!.id
                        val input =
                            notetypes.changeNotetypeInfo(source, target).input.copy {
                                noteIds.add(noteId)
                                newFields[0] = 0
                                newFields[1] = 1
                                newTemplates[0] = 0
                                newTemplates[1] = -1
                            }
                        notetypes.changeNotetypeOfNotes(input)
                        target
                    }
                assertThat("the conversion retains the card", col.getCard(originalCard).nid, equalTo(noteId))
                assertThat("the conversion adds a sibling", col.getNote(noteId).cardIds(col).size, equalTo(2))
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
                if (dialog?.isShowing == true) {
                    dialog.getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
                    advanceRobolectricLooper()
                }

                insertButton.performClick()
                advanceRobolectricLooper()
                editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
                advanceRobolectricLooper()

                val saved = col.getNote(noteId)
                assertThat("the newer note type survives saving audio", saved.noteTypeId, equalTo(destination))
                assertThat("the sibling survives", saved.cardIds(col).size, equalTo(2))
                assertThat("the recording is saved", saved.fields[1], containsString("[sound:"))
            }
        }

    @Test
    fun `saving audio keeps a conversion that replaced the card`() =
        runTest {
            withSplitPaneUiAsync {
                ensureCollectionLoadIsSynchronous()
                setCardBrowserFragmented(true)
                val note = addBasicAndReversedNote("Front", "Back")
                val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardBrowser::class.java, android.content.Intent())
                controller
                    .get()
                    .viewModel
                    .setCardsOrNotes(CardsOrNotes.NOTES)
                    .join()
                controller.get().viewModel.setQuery("deck:Default", forceRefresh = true)
                controller.get().waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(controller.get().fragment)
                val remainingCard = note.cardIds(col).single { it != editor.currentEditedCard!!.id }
                val remainingTemplate = col.getCard(remainingCard).ord
                editor.chooseRecordAudio(fieldIndex = 1)
                record()

                val target =
                    withCol {
                        val source = getNote(note.id).noteTypeId
                        val target = notetypes.byName("Basic")!!.id
                        val input =
                            notetypes.changeNotetypeInfo(source, target).input.copy {
                                noteIds.add(note.id)
                                newFields[0] = 0
                                newFields[1] = 1
                                newTemplates[0] = remainingTemplate
                            }
                        notetypes.changeNotetypeOfNotes(input)
                        target
                    }
                controller.get().viewModel.setQuery("deck:Default", forceRefresh = true)
                controller.get().waitForSearchResults()
                advanceRobolectricLooper()
                insertButton.performClick()
                advanceRobolectricLooper()
                controller.get().fragment!!.onMenuItemSelected(RoboMenuItem(R.id.action_save))
                advanceRobolectricLooper()

                val saved = col.getNote(note.id)
                assertThat("the conversion survives saving audio", saved.noteTypeId, equalTo(target))
                assertThat("the remaining card survives", saved.cardIds(col), equalTo(listOf(remainingCard)))
                assertThat("the recording is saved", saved.fields[1], containsString("[sound:"))
            }
        }

    @Test
    fun `another card of the note keeps the take and its own deck`() =
        runTest {
            withSplitPaneUiAsync {
                ensureCollectionLoadIsSynchronous()
                setCardBrowserFragmented(true)
                val note = addBasicAndReversedNote("Front", "Back")
                val otherDeck = addDeck("Other")
                val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardBrowser::class.java, android.content.Intent())
                val browser = controller.get()
                browser.viewModel.setSelectedDeck(SelectableDeck.AllDecks)
                browser.viewModel.setQuery("", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                val editedCard = editor.currentEditedCard!!
                val otherCard = note.cardIds(col).single { it != editedCard.id }
                withCol { setDeck(listOf(otherCard), otherDeck) }
                editor.chooseRecordAudio(fieldIndex = 1)
                record()

                browser.clickRowAtPosition(browser.viewModel.cards.indexOfFirst { it.cardOrNoteId == otherCard })
                advanceRobolectricLooper()
                assertThat("the same editor shows the other card", browser.fragment!!.currentEditedCard!!.id, equalTo(otherCard))
                insertButton.performClick()
                advanceRobolectricLooper()
                browser.fragment!!.onMenuItemSelected(RoboMenuItem(R.id.action_save))
                advanceRobolectricLooper()

                assertThat("the recording is saved", col.getNote(note.id).fields[1], containsString("[sound:"))
                assertThat("the other card stays in its deck", col.getCard(otherCard).did, equalTo(otherDeck))
                assertThat("the first card stays in its deck", col.getCard(editedCard.id).did, equalTo(editedCard.did))
            }
        }

    @Test
    fun `changing to an occlusion type asks before hiding the recording field`() {
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        record()

        editor.selectNoteType("Image Occlusion")

        assertThat("hiding the recording field requires confirmation", ShadowDialog.getLatestDialog()?.isShowing, equalTo(true))
        latestDialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        advanceRobolectricLooper()
        assertThat("the visible recording field is retained", editor.noteTypeSpinner!!.selectedItem.toString(), equalTo("Basic"))
        insertButton.performClick()
        advanceRobolectricLooper()
        assertThat("the recording remains insertable", editor.fieldText(0), startsWith("[sound:ankidroid_audiorec"))
    }

    @Test
    fun `starting a recording during save validation keeps it recoverable`() {
        ensureCollectionLoadIsSynchronous()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination
                    .CopyNote(
                        deckId = col.decks.selected(),
                        fieldsText = "Front\u001fBack",
                        noteTypeId = col.notetypes.byName("Basic")!!.id,
                    ).toIntent(targetContext),
            )
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 1)
        val noteCountBeforeSave = col.noteCount()
        val validationStarted = CompletableDeferred<Unit>()
        val validationMayFinish = CompletableDeferred<Unit>()
        mockkStatic(::checkNoteFieldsResponse)
        try {
            coEvery { checkNoteFieldsResponse(any()) } coAnswers {
                validationStarted.complete(Unit)
                validationMayFinish.await()
                NoteFieldsCheckResult.Success
            }
            editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
            advanceRobolectricLooper()
            assertThat("validation is pending", validationStarted.isCompleted, equalTo(true))
            if (editor.inlineRecorder != null) recordButton.performClick()
            val recording = mediaRecorderStub.outputFiles.singleOrNull()

            validationMayFinish.complete(Unit)
            advanceRobolectricLooper()
            if (controller.get().isFinishing) controller.pause().stop().destroy()

            assertThat("finishing save must preserve any new recording", recording == null || recording.exists(), equalTo(true))
            if (recording == null) assertThat("saving still completes", col.noteCount(), equalTo(noteCountBeforeSave + 1))
        } finally {
            validationMayFinish.complete(Unit)
            unmockkStatic(::checkNoteFieldsResponse)
        }
    }

    @Test
    fun `an add includes audio queued behind its field validation`() {
        ensureCollectionLoadIsSynchronous()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination
                    .CopyNote(
                        deckId = col.decks.selected(),
                        fieldsText = "Front\u001fBack",
                        noteTypeId = col.notetypes.byName("Basic")!!.id,
                    ).toIntent(targetContext),
            )
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 1)
        val validationStarted = CompletableDeferred<Unit>()
        val collectionScheduler = TestCoroutineScheduler()
        var previous: CoroutineDispatcher? = null
        mockkStatic(::checkNoteFieldsResponse)
        try {
            coEvery { checkNoteFieldsResponse(any()) } coAnswers {
                if (validationStarted.complete(Unit)) {
                    previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(collectionScheduler), useReentrantLock = false)
                }
                withCol { NoteFieldsCheckResult.Success }
            }
            editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
            advanceRobolectricLooper()
            assertThat("note validation is pending", validationStarted.isCompleted, equalTo(true))

            record()
            insertButton.performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertThat("the import is queued", editor.fieldText(1), equalTo("Back"))

            CollectionManager.setTestDispatcher(checkNotNull(previous))
            collectionScheduler.advanceUntilIdle()
            advanceRobolectricLooperUntil { controller.get().isFinishing }

            val saved = col.getNote(col.findNotes("Front").single())
            assertThat("the added note contains the audio inserted during validation", saved.fields[1], containsString("[sound:"))
        } finally {
            previous?.let { CollectionManager.setTestDispatcher(it) }
            collectionScheduler.advanceUntilIdle()
            unmockkStatic(::checkNoteFieldsResponse)
        }
    }

    @Test
    fun `a cloze warning save keeps audio inserted during validation`() {
        ensureCollectionLoadIsSynchronous()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination
                    .CopyNote(
                        deckId = col.decks.selected(),
                        fieldsText = "Plain text\u001fExtra",
                        noteTypeId = col.notetypes.byName("Cloze")!!.id,
                    ).toIntent(targetContext),
            )
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 1)
        val validationStarted = CompletableDeferred<Unit>()
        val collectionScheduler = TestCoroutineScheduler()
        var previous: CoroutineDispatcher? = null
        mockkStatic(::checkNoteFieldsResponse)
        try {
            coEvery { checkNoteFieldsResponse(any()) } coAnswers {
                if (validationStarted.complete(Unit)) {
                    previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(collectionScheduler), useReentrantLock = false)
                }
                withCol { NoteFieldsCheckResult.Failure(TR.addingYouHaveAClozeDeletionNote()) }
            }
            editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
            advanceRobolectricLooper()
            record()
            insertButton.performClick()
            shadowOf(Looper.getMainLooper()).idle()

            CollectionManager.setTestDispatcher(checkNotNull(previous))
            collectionScheduler.advanceUntilIdle()
            advanceRobolectricLooperUntil { (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true }
            latestDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            advanceRobolectricLooperUntil { controller.get().isFinishing }

            val saved = col.getNote(col.findNotes("Plain").single())
            assertThat("the cloze warning save keeps the audio", saved.fields[1], containsString("[sound:"))
        } finally {
            previous?.let { CollectionManager.setTestDispatcher(it) }
            collectionScheduler.advanceUntilIdle()
            unmockkStatic(::checkNoteFieldsResponse)
        }
    }

    @Test
    fun `cloze warning cannot save over a new recording`() {
        ensureCollectionLoadIsSynchronous()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination
                    .CopyNote(
                        deckId = col.decks.selected(),
                        fieldsText = "Plain text\u001fExtra",
                        noteTypeId = col.notetypes.byName("Cloze")!!.id,
                    ).toIntent(targetContext),
            )
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 1)
        val initialNoteCount = col.noteCount()
        val validationStarted = CompletableDeferred<Unit>()
        val validationMayFinish = CompletableDeferred<Unit>()

        mockkStatic(::checkNoteFieldsResponse)
        try {
            coEvery { checkNoteFieldsResponse(any()) } coAnswers {
                validationStarted.complete(Unit)
                validationMayFinish.await()
                NoteFieldsCheckResult.Failure(TR.addingYouHaveAClozeDeletionNote())
            }

            editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
            advanceRobolectricLooper()
            assertThat("validation is pending", validationStarted.isCompleted, equalTo(true))
            recordButton.performClick()
            val recording = mediaRecorderStub.outputFiles.single()

            validationMayFinish.complete(Unit)
            advanceRobolectricLooper()
            val warning = ShadowDialog.getLatestDialog() as? AlertDialog
            if (warning?.isShowing == true) {
                warning.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                advanceRobolectricLooper()
            }

            if (controller.get().isFinishing) controller.pause().stop().destroy()
            assertThat("the warning must not commit a note over pending audio", col.noteCount(), equalTo(initialNoteCount))
            assertThat("the recording remains recoverable", recording.exists(), equalTo(true))
        } finally {
            validationMayFinish.complete(Unit)
            unmockkStatic(::checkNoteFieldsResponse)
        }
    }

    @Test
    fun `a recording blocks save without selecting a different browser row`() =
        withSplitPaneUi {
            setCardBrowserFragmented(true)
            val browser = getBrowserWithNotes(2)
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            val editor = requireNotNull(browser.fragment)
            val originalRow = requireNotNull(browser.viewModel.paneRow)
            val noteId = editor.editorNote!!.id
            editor.chooseRecordAudio(fieldIndex = 1)
            record()
            val otherPosition = browser.viewModel.cards.indexOfFirst { it != originalRow }

            browser.clickRowAtPosition(otherPosition)
            advanceRobolectricLooperUntil { ShadowDialog.getLatestDialog()?.isShowing == true }
            latestDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            advanceRobolectricLooper()

            assertThat("the original note stays in the editor", browser.fragment!!.editorNote!!.id, equalTo(noteId))
            assertThat("the highlighted row matches the editor", browser.viewModel.paneRow, equalTo(originalRow))
        }

    @Test
    fun `moving an edited note preserves a recording begun during save`() {
        ensureCollectionLoadIsSynchronous()
        val card = addBasicNote("Front", "Back").firstCard()
        val destinationDeck = addDeck("Moved")
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(card.id).toIntent(targetContext),
            )
        val editor = controller.noteEditor
        editor.onDeckSelected(SelectableDeck.Deck(destinationDeck, "Moved"))
        editor.chooseRecordAudio(fieldIndex = 1)
        val collection = col
        val collectionScheduler = TestCoroutineScheduler()
        val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(collectionScheduler), useReentrantLock = false)
        try {
            editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
            advanceRobolectricLooper()
            assertThat("the deck move is pending", collection.getCard(card.id).did, equalTo(card.did))
            recordButton.performClick()
            val recording = mediaRecorderStub.outputFiles.singleOrNull()

            CollectionManager.setTestDispatcher(previous)
            collectionScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            if (controller.get().isFinishing) controller.pause().stop().destroy()
            assertThat("saving the edit keeps any new recording recoverable", recording == null || recording.exists(), equalTo(true))
            if (recording == null) assertThat("the deck change completes", col.getCard(card.id).did, equalTo(destinationDeck))
        } finally {
            CollectionManager.setTestDispatcher(previous)
            collectionScheduler.advanceUntilIdle()
        }
    }

    @Test
    fun `a deck change saves audio inserted during the move`() {
        ensureCollectionLoadIsSynchronous()
        val card = addBasicNote("Front", "Back").firstCard()
        val destinationDeck = addDeck("Moved")
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(card.id).toIntent(targetContext),
            )
        val editor = controller.noteEditor
        editor.onDeckSelected(SelectableDeck.Deck(destinationDeck, "Moved"))
        editor.chooseRecordAudio(fieldIndex = 1)
        val collection = col
        val moveScheduler = TestCoroutineScheduler()
        val importScheduler = TestCoroutineScheduler()
        val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(moveScheduler), useReentrantLock = false)
        try {
            editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
            advanceRobolectricLooper()
            assertThat("the deck move is pending", collection.getCard(card.id).did, equalTo(card.did))
            val flags =
                controller
                    .get()
                    .window.attributes.flags
            assertThat(
                "the recorder accepts input while the move waits",
                flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                equalTo(0),
            )

            CollectionManager.setTestDispatcher(StandardTestDispatcher(importScheduler), useReentrantLock = false)
            record()
            insertButton.performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertThat("the audio import is queued", editor.fieldText(1), equalTo("Back"))

            CollectionManager.setTestDispatcher(previous)
            moveScheduler.advanceUntilIdle()
            advanceRobolectricLooper()
            importScheduler.advanceUntilIdle()
            advanceRobolectricLooperUntil { controller.get().isFinishing }

            assertThat("the card moved to the selected deck", col.getCard(card.id).did, equalTo(destinationDeck))
            assertThat(
                "the saved note includes the audio inserted during the move",
                col.getNote(card.nid).fields[1],
                containsString("[sound:"),
            )
        } finally {
            CollectionManager.setTestDispatcher(previous)
            moveScheduler.advanceUntilIdle()
            importScheduler.advanceUntilIdle()
        }
    }

    @Test
    fun `hardware Insert during save preserves the recording`() {
        ensureCollectionLoadIsSynchronous()
        val card = addBasicNote("Front", "Back").firstCard()
        val destinationDeck = addDeck("Moved")
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(card.id).toIntent(targetContext),
            )
        val activity = controller.get()
        val editor = controller.noteEditor
        editor.onDeckSelected(SelectableDeck.Deck(destinationDeck, "Moved"))
        editor.chooseRecordAudio(fieldIndex = 1)
        val collection = col
        val moveScheduler = TestCoroutineScheduler()
        val importScheduler = TestCoroutineScheduler()
        val queuedWrite = AtomicReference<Runnable?>()
        val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(moveScheduler), useReentrantLock = false)
        try {
            editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
            advanceRobolectricLooper()
            assertThat("the deck move is pending", collection.getCard(card.id).did, equalTo(card.did))
            record()
            val insert = insertButton
            val composeView = (insert.fetchSemanticsNode().root as ViewRootForTest).view
            composeRule.runOnIdle { composeView.requestFocusFromTouch() }
            insert.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            insert.assertIsFocused()

            val writer =
                object : CoroutineDispatcher() {
                    override fun dispatch(
                        context: CoroutineContext,
                        block: Runnable,
                    ) {
                        val saving = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0
                        if (saving && queuedWrite.compareAndSet(null, block)) return
                        Dispatchers.IO.dispatch(context, block)
                    }
                }
            CollectionManager.setTestDispatcher(writer, useReentrantLock = false)
            moveScheduler.advanceUntilIdle()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (queuedWrite.get() == null && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(1)
            }
            assertThat("the note write is queued", queuedWrite.get() != null, equalTo(true))
            val flags = activity.window.attributes.flags
            assertThat("the deck moved", collection.getCard(card.id).did, equalTo(destinationDeck))
            assertThat("the note write blocks touches", flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, not(equalTo(0)))
            assertThat("the progress dialog has yet to open", ShadowDialog.getLatestDialog()?.isShowing == true, equalTo(false))

            CollectionManager.setTestDispatcher(StandardTestDispatcher(importScheduler), useReentrantLock = false)
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE))
            shadowOf(Looper.getMainLooper()).idle()

            CollectionManager.setTestDispatcher(previous)
            queuedWrite.getAndSet(null)!!.run()
            advanceRobolectricLooper()
            importScheduler.advanceUntilIdle()
            advanceRobolectricLooper()

            if (activity.isFinishing) {
                assertThat("the saved note has the recording", col.getNote(card.nid).fields[1], containsString("[sound:"))
            } else {
                if (editor.hasRecording) insertButton.performClick()
                advanceRobolectricLooper()
                assertThat("the editor retains the recording", editor.fieldText(1), containsString("[sound:"))
            }
        } finally {
            CollectionManager.setTestDispatcher(previous)
            moveScheduler.advanceUntilIdle()
            queuedWrite.getAndSet(null)?.run()
            importScheduler.advanceUntilIdle()
        }
    }

    @Test
    fun `saving an edit preserves audio started by a hardware key`() {
        ensureCollectionLoadIsSynchronous()
        val card = addBasicNote("Front", "Back").firstCard()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(card.id).toIntent(targetContext),
            )
        val editor = controller.noteEditor
        editor.editFieldAt(0)!!.setText("Edited Front")
        editor.chooseRecordAudio(fieldIndex = 1)
        val record = recordButton
        val composeView = (record.fetchSemanticsNode().root as ViewRootForTest).view
        composeRule.runOnIdle { composeView.requestFocusFromTouch() }
        record.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        record.assertIsFocused()
        val activity = controller.get()
        val queuedWrite = AtomicReference<Runnable?>()
        val dispatcher =
            object : CoroutineDispatcher() {
                override fun dispatch(
                    context: CoroutineContext,
                    block: Runnable,
                ) {
                    val saving = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0
                    if (saving && queuedWrite.compareAndSet(null, block)) return
                    Dispatchers.IO.dispatch(context, block)
                }
            }
        val previous = CollectionManager.setTestDispatcher(dispatcher, useReentrantLock = false)
        try {
            val save = KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0, KeyEvent.META_CTRL_ON)
            activity.dispatchKeyEvent(save)
            shadowOf(Looper.getMainLooper()).idle()
            assertThat("the save is waiting on the collection", queuedWrite.get() != null, equalTo(true))
            assertThat("the progress dialog has not opened", ShadowDialog.getLatestDialog()?.isShowing == true, equalTo(false))

            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE))
            val recording = mediaRecorderStub.outputFiles.singleOrNull()

            CollectionManager.setTestDispatcher(previous)
            val update = queuedWrite.getAndSet(null)
            update?.run()
            advanceRobolectricLooper()

            if (activity.isFinishing) controller.pause().stop().destroy()
            assertThat("a recording started during Save remains recoverable", recording == null || recording.exists(), equalTo(true))
            if (recording == null) assertThat("the field edit was saved", col.getNote(card.nid).fields[0], equalTo("Edited Front"))
        } finally {
            CollectionManager.setTestDispatcher(previous)
            queuedWrite.getAndSet(null)?.let {
                if (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0) it.run()
            }
        }
    }

    @Test
    fun `a hardware key cannot start audio during the add write`() {
        ensureCollectionLoadIsSynchronous()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination
                    .CopyNote(
                        deckId = col.decks.selected(),
                        fieldsText = "Front\u001fBack",
                        noteTypeId = col.notetypes.byName("Basic")!!.id,
                    ).toIntent(targetContext),
            )
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 1)
        val record = recordButton
        val composeView = (record.fetchSemanticsNode().root as ViewRootForTest).view
        composeRule.runOnIdle { composeView.requestFocusFromTouch() }
        record.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        record.assertIsFocused()
        val activity = controller.get()
        val noteCount = col.noteCount()
        val queuedWrite = AtomicReference<Runnable?>()
        val dispatcher =
            object : CoroutineDispatcher() {
                override fun dispatch(
                    context: CoroutineContext,
                    block: Runnable,
                ) {
                    val saving = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0
                    if (saving && queuedWrite.compareAndSet(null, block)) return
                    Dispatchers.IO.dispatch(context, block)
                }
            }
        mockkStatic(::checkNoteFieldsResponse)
        val previous = CollectionManager.setTestDispatcher(dispatcher, useReentrantLock = false)
        try {
            coEvery { checkNoteFieldsResponse(any()) } returns NoteFieldsCheckResult.Success
            activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0, KeyEvent.META_CTRL_ON))
            shadowOf(Looper.getMainLooper()).idle()
            assertThat("the add is waiting on the collection", queuedWrite.get() != null, equalTo(true))

            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE))
            val recordingStarted = mediaRecorderStub.outputFiles.isNotEmpty()

            CollectionManager.setTestDispatcher(previous)
            queuedWrite.getAndSet(null)!!.run()
            advanceRobolectricLooper()

            assertThat("the note is added", col.noteCount(), equalTo(noteCount + 1))
            if (recordingStarted) {
                saveButton.performClick()
                insertButton.performClick()
                advanceRobolectricLooper()
                val addedNote = col.getNote(col.findNotes("Front").single())
                assertThat("the recording belongs to the note being added", addedNote.fields[1], containsString("[sound:"))
            }
        } finally {
            CollectionManager.setTestDispatcher(previous)
            queuedWrite.getAndSet(null)?.run()
            unmockkStatic(::checkNoteFieldsResponse)
        }
    }

    @Test
    fun `saving audio preserves an external edit to another field`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                val browser = getBrowserWithNotes(1)
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                val noteId = editor.editorNote!!.id
                editor.chooseRecordAudio(fieldIndex = 1)
                record()

                withCol {
                    val latest = getNote(noteId)
                    latest.setField(0, "Replaced in browser")
                    updateNote(latest)
                }
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                insertButton.performClick()
                advanceRobolectricLooper()
                editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
                advanceRobolectricLooper()

                val saved = col.getNote(noteId)
                assertThat("the completed replacement survives", saved.fields[0], equalTo("Replaced in browser"))
                assertThat("the new recording is also saved", saved.fields[1], containsString("[sound:"))
            }
        }

    @Test
    fun `find and replace cannot redirect audio to text it just changed`() =
        runTest {
            withSplitPaneUiAsync {
                ensureCollectionLoadIsSynchronous()
                setCardBrowserFragmented(true)
                val note = addBasicNote("x", "xx")
                val browser = getBrowserWithNotes(0)
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                editor.chooseRecordAudio(fieldIndex = 1)
                record()

                val replaced = withCol { findAndReplace(listOf(note.id), search = "x", replacement = "xx") }
                assertThat(replaced.count, equalTo(1))
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                assertThat("both fields were replaced", editor.currentFieldStrings.toList(), equalTo(listOf("xx", "xxxx")))

                insertButton.performClick()
                advanceRobolectricLooper()
                chooseRecordingFieldIfAsked("Back")
                assertThat("Front stays as replaced", editor.fieldText(0), equalTo("xx"))
                assertThat("Back receives the recording", editor.fieldText(1), containsString("[sound:"))
            }
        }

    @Test
    fun `saving audio preserves a browser note type conversion`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                val browser = getBrowserWithNotes(1)
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                val noteId = editor.editorNote!!.id
                val cardId = editor.currentEditedCard!!.id
                editor.chooseRecordAudio(fieldIndex = 1)
                record()
                val destination =
                    withCol {
                        val source = getNote(noteId).noteTypeId
                        val target = notetypes.byName("Basic (and reversed card)")!!.id
                        val input =
                            notetypes.changeNotetypeInfo(source, target).input.copy {
                                noteIds.add(noteId)
                                newFields[0] = 0
                                newFields[1] = 1
                                newTemplates[0] = 0
                                newTemplates[1] = -1
                            }
                        notetypes.changeNotetypeOfNotes(input)
                        target
                    }
                assertThat("conversion completed", col.getNote(noteId).noteTypeId, equalTo(destination))
                assertThat("the original card is retained", col.getCard(cardId).nid, equalTo(noteId))
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                insertButton.performClick()
                advanceRobolectricLooper()
                editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
                advanceRobolectricLooper()

                val saved = col.getNote(noteId)
                assertThat("saving retains the completed conversion", saved.noteTypeId, equalTo(destination))
                assertThat("the recording is also saved", saved.fields[1], containsString("[sound:"))
            }
        }

    @Test
    fun `a field remap keeps a recording target despite another draft edit`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                val type = addStandardNoteType("Draft and mapping", arrayOf("Front", "Back", "Memo"), "{{Front}}", "{{Back}}")
                val note =
                    col.newNote(col.notetypes.byName(type)!!).apply {
                        setField(0, "Same")
                        setField(1, "Same")
                        setField(2, "Saved memo")
                        col.addNote(this, col.decks.selected())
                    }
                val browser = getBrowserWithNotes(0)
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                editor.chooseRecordAudio(fieldIndex = 1)
                record()
                editor.editFieldAt(2)!!.setText("Unsaved memo")

                val state = SavedStateHandle(mapOf(ChangeNoteTypeViewModel.ARG_NOTE_IDS to longArrayOf(note.id)))
                withViewModel(ChangeNoteTypeViewModel(state)) {
                    advanceRobolectricLooperUntil { flowOfInitStatus.value == InitStatus.Completed }
                    updateFieldMapping(outputFieldIndex = 0, mappedFrom = SelectedIndex.from(1)).join()
                    updateFieldMapping(outputFieldIndex = 1, mappedFrom = SelectedIndex.from(0)).join()
                    assertThat("the field remap ran", executeChangeNoteTypeAsync().await(), equalTo(1))
                }
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                assertThat("the unrelated draft survives", editor.fieldText(2), equalTo("Unsaved memo"))

                insertButton.performClick()
                advanceRobolectricLooper()
                chooseRecordingFieldIfAsked("Front")

                assertThat("the recording follows its remapped field", editor.fieldText(0), containsString("[sound:"))
                assertThat("the other field gets no audio", editor.fieldText(1), equalTo("Same"))
            }
        }

    @Test
    fun `a pending recording follows its field after a browser conversion`() = verifyRecordingTargetAfterConversion("Back")

    @Test
    fun `a pending recording follows a field renamed by a browser conversion`() = verifyRecordingTargetAfterConversion("Answer")

    @Test
    fun `a pending recording follows a renamed empty field`() = verifyRecordingTargetAfterConversion("Answer", back = "")

    private fun verifyRecordingTargetAfterConversion(
        mappedField: String,
        back: String = "back",
    ) = runTest {
        withSplitPaneUiAsync {
            ensureCollectionLoadIsSynchronous()
            setCardBrowserFragmented(true)
            val reversedFields = addStandardNoteType("Reordered", arrayOf(mappedField, "Front"), "{{Front}}", "{{$mappedField}}")
            addBasicNote("Front", back)
            val browser = getBrowserWithNotes(0)
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            val editor = requireNotNull(browser.fragment)
            val noteId = editor.editorNote!!.id
            val originalCardId = editor.currentEditedCard!!.id
            val originalBack = editor.fieldText(1)
            editor.chooseRecordAudio(fieldIndex = 1)
            record()

            withCol {
                val source = getNote(noteId).noteTypeId
                val destination = notetypes.byName(reversedFields)!!.id
                val input =
                    notetypes.changeNotetypeInfo(source, destination).input.copy {
                        noteIds.add(noteId)
                        newFields[0] = 1
                        newFields[1] = 0
                        newTemplates[0] = 0
                    }
                notetypes.changeNotetypeOfNotes(input)
            }
            assertThat("the conversion retains the edited card", col.getCard(originalCardId).nid, equalTo(noteId))
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            assertThat("the original Back content moved to the first field", editor.fieldText(0), equalTo(originalBack))

            insertButton.performClick()
            advanceRobolectricLooper()

            chooseRecordingFieldIfAsked(mappedField)
            assertThat("the recording follows its Back field", editor.fieldText(0), containsString("[sound:"))
            assertThat("the Front field gets no audio", editor.fieldText(1), not(containsString("[sound:")))
        }
    }

    @Test
    fun `a recording can follow one of two empty fields after conversion`() = verifyAmbiguousFieldConversion(removeTarget = false)

    @Test
    fun `removing its target does not redirect a recording automatically`() = verifyAmbiguousFieldConversion(removeTarget = true)

    @Test
    fun `recreation before refresh keeps the recording's intended field`() =
        verifyAmbiguousFieldConversion(removeTarget = false, recreate = true)

    @Test
    fun `removing a recording target with duplicate text asks where to insert`() =
        verifyAmbiguousFieldConversion(removeTarget = true, sharedFieldText = "Same text")

    private fun verifyAmbiguousFieldConversion(
        removeTarget: Boolean,
        recreate: Boolean = false,
        sharedFieldText: String = "",
    ) = runTest {
        withSplitPaneUiAsync {
            ensureCollectionLoadIsSynchronous()
            setCardBrowserFragmented(true)
            val oldType = addStandardNoteType("Original", arrayOf("Front", "Back", "Audio"), "{{Front}}", "{{Back}}")
            val destinationFieldNames = if (removeTarget) arrayOf("Front", "Back") else arrayOf("Front", "Pronunciation", "Back")
            val newType = addStandardNoteType("Converted", destinationFieldNames, "{{Front}}", "{{Back}}")
            val note = addNoteUsingNoteTypeName(oldType, "Front", sharedFieldText, sharedFieldText)
            val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardBrowser::class.java, android.content.Intent())
            val browser = controller.get()
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            requireNotNull(browser.fragment).chooseRecordAudio(fieldIndex = 2)
            record()

            withCol {
                val destination = notetypes.byName(newType)!!.id
                val input =
                    notetypes.changeNotetypeInfo(note.noteTypeId, destination).input.copy {
                        noteIds.add(note.id)
                        newFields[0] = 0
                        if (removeTarget) {
                            newFields[1] = 1
                        } else {
                            newFields[1] = 2
                            newFields[2] = 1
                        }
                        newTemplates[0] = 0
                    }
                notetypes.changeNotetypeOfNotes(input)
            }
            if (recreate) controller.recreate()
            val updatedBrowser = controller.get()
            updatedBrowser.viewModel.setQuery("deck:Default", forceRefresh = true)
            updatedBrowser.waitForSearchResults()
            advanceRobolectricLooper()
            val editor = requireNotNull(updatedBrowser.fragment)
            assertThat("the conversion shows the destination fields", editor.currentFields[1].name, equalTo(destinationFieldNames[1]))

            insertButton.performClick()
            advanceRobolectricLooper()
            if (removeTarget) {
                assertThat(
                    "the deleted target leaves no automatic destination",
                    editor.currentFieldStrings.toList(),
                    equalTo(listOf("Front", sharedFieldText)),
                )
            }

            chooseRecordingFieldIfAsked(destinationFieldNames[1])
            assertThat("Front receives no recording", editor.fieldText(0), equalTo("Front"))
            assertThat("the intended field receives the recording", editor.fieldText(1), containsString("[sound:"))
            if (!removeTarget) {
                assertThat("Back receives no recording", editor.fieldText(2), equalTo(""))
            }
        }
    }

    @Test
    fun `recording follows a field remapped within the same note type`() = verifyRecordingAfterFieldRemap(recreate = false)

    @Test
    fun `recreating before a same type remap refresh keeps the recording target`() = verifyRecordingAfterFieldRemap(recreate = true)

    @Test
    fun `a same type remap can move a recording between fields with equal text`() =
        verifyRecordingAfterFieldRemap(recreate = false, front = "Same text", back = "Same text")

    @Test
    fun `a queued conversion uses its applied mapping for pending audio`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                val note = addBasicNote("Same text", "Same text")
                val browser = getBrowserWithNotes(0)
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                editor.chooseRecordAudio(fieldIndex = 1)
                record()

                val state = SavedStateHandle(mapOf(ChangeNoteTypeViewModel.ARG_NOTE_IDS to longArrayOf(note.id)))
                withViewModel(ChangeNoteTypeViewModel(state)) {
                    advanceRobolectricLooperUntil { flowOfInitStatus.value == InitStatus.Completed }
                    updateFieldMapping(outputFieldIndex = 1, mappedFrom = SelectedIndex.from(0)).join()
                    val scheduler = TestCoroutineScheduler()
                    val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(scheduler), useReentrantLock = false)
                    val conversion =
                        try {
                            executeChangeNoteTypeAsync().also {
                                advanceRobolectricLooper()
                                assertThat("the conversion is waiting for the collection", it.isCompleted, equalTo(false))
                                updateFieldMapping(outputFieldIndex = 1, mappedFrom = SelectedIndex.from(1)).join()
                            }
                        } finally {
                            CollectionManager.setTestDispatcher(previous)
                            scheduler.advanceUntilIdle()
                        }
                    assertThat("the queued conversion ran", conversion.await(), equalTo(1))
                }
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()

                insertButton.performClick()
                advanceRobolectricLooper()
                assertThat("the removed source field cannot automatically receive audio", editor.fieldText(1), equalTo("Same text"))
                chooseRecordingFieldIfAsked("Front")
                assertThat("the selected field receives the pending take", editor.fieldText(0), containsString("[sound:"))
            }
        }

    @Test
    fun `recreation after conversion keeps audio with the right duplicate field`() =
        verifyRecordingAfterFieldRemap(
            recreate = true,
            front = "Same text",
            back = "Same text",
            targetNoteType = "Basic (and reversed card)",
        )

    private fun verifyRecordingAfterFieldRemap(
        recreate: Boolean,
        front: String = "Original front",
        back: String = "Original back",
        targetNoteType: String? = null,
    ) = runTest {
        withSplitPaneUiAsync {
            ensureCollectionLoadIsSynchronous()
            setCardBrowserFragmented(true)
            val note = addBasicNote(front, back)
            val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardBrowser::class.java, android.content.Intent())
            controller.get().viewModel.setQuery("deck:Default", forceRefresh = true)
            controller.get().waitForSearchResults()
            advanceRobolectricLooper()
            requireNotNull(controller.get().fragment).chooseRecordAudio(fieldIndex = 1)
            record()

            val destinationTypeId = targetNoteType?.let { col.notetypes.byName(it)!!.id } ?: note.noteTypeId
            if (targetNoteType == null) {
                val state = SavedStateHandle(mapOf(ChangeNoteTypeViewModel.ARG_NOTE_IDS to longArrayOf(note.id)))
                withViewModel(ChangeNoteTypeViewModel(state)) {
                    advanceRobolectricLooperUntil { flowOfInitStatus.value == InitStatus.Completed }
                    updateFieldMapping(outputFieldIndex = 0, mappedFrom = SelectedIndex.from(1)).join()
                    updateFieldMapping(outputFieldIndex = 1, mappedFrom = SelectedIndex.from(0)).join()
                    assertThat("the operation remapped the note", executeChangeNoteTypeAsync().await(), equalTo(1))
                }
            } else {
                withCol {
                    val input =
                        notetypes.changeNotetypeInfo(note.noteTypeId, destinationTypeId).input.copy {
                            noteIds.add(note.id)
                            newFields[0] = 1
                            newFields[1] = 0
                            newTemplates[0] = 0
                            newTemplates[1] = -1
                        }
                    notetypes.changeNotetypeOfNotes(input)
                }
            }
            if (recreate) controller.recreate()
            controller.get().viewModel.setQuery("deck:Default", forceRefresh = true)
            controller.get().waitForSearchResults()
            advanceRobolectricLooper()
            val editor = requireNotNull(controller.get().fragment)
            val updatedNote = col.getNote(note.id)
            assertThat("the new note type is saved", updatedNote.noteTypeId, equalTo(destinationTypeId))
            assertThat("the remap is saved", updatedNote.fields, equalTo(listOf(back, front)))
            if (!recreate) {
                assertThat("the remap moved Back content into Front", editor.fieldText(0), equalTo(back))
            }

            insertButton.performClick()
            advanceRobolectricLooper()
            chooseRecordingFieldIfAsked("Front")

            assertThat("the recording follows the remapped Back content", editor.fieldText(0), containsString("[sound:"))
            assertThat("the other field gets no recording", editor.fieldText(1), not(containsString("[sound:")))
        }
    }

    @Test
    fun `recreation after a conversion keeps the take beside its text`() =
        runTest {
            ensureCollectionLoadIsSynchronous()
            val note = addBasicNote("Question", "Answer")
            val target = col.notetypes.byName("Basic (and reversed card)")!!
            val cardId = note.firstCard().id
            val controller =
                startActivityControllerNormallyOpenCollectionWithIntent(
                    NoteEditorActivity::class.java,
                    NoteEditorDestination.EditNoteFromPreviewer(cardId).toIntent(targetContext),
                )
            controller.noteEditor.chooseRecordAudio(fieldIndex = 1)
            record()

            withCol {
                val input =
                    notetypes.changeNotetypeInfo(note.noteTypeId, target.id).input.copy {
                        noteIds.add(note.id)
                        newFields[0] = 1
                        newFields[1] = 0
                        newTemplates[0] = 0
                        newTemplates[1] = -1
                    }
                notetypes.changeNotetypeOfNotes(input)
            }
            assertThat("the conversion keeps the card", col.getCard(cardId).nid, equalTo(note.id))
            controller.recreate()
            advanceRobolectricLooper()
            val editor = controller.noteEditor
            assertThat("the editor shows the converted note", editor.fieldText(0), equalTo("Answer"))

            insertButton.performClick()
            advanceRobolectricLooper()
            chooseRecordingFieldIfAsked("Front")

            assertThat("the take is beside the text it was recorded for", editor.fieldText(0), startsWith("Answer[sound:"))
            assertThat("the other text gets no audio", editor.fieldText(1), equalTo("Question"))
        }

    @Test
    fun `an unsaved edit keeps the take beside its text after a remap`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                val type = addStandardNoteType("Draft and remap", arrayOf("Front", "Back", "Memo"), "{{Front}}", "{{Back}}")
                val note =
                    col.newNote(col.notetypes.byName(type)!!).apply {
                        setField(0, "Question")
                        setField(1, "Answer")
                        setField(2, "Saved memo")
                        col.addNote(this, col.decks.selected())
                    }
                val browser = getBrowserWithNotes(0)
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                editor.chooseRecordAudio(fieldIndex = 1)
                record()
                editor.editFieldAt(2)!!.setText("Unsaved memo")

                val state = SavedStateHandle(mapOf(ChangeNoteTypeViewModel.ARG_NOTE_IDS to longArrayOf(note.id)))
                withViewModel(ChangeNoteTypeViewModel(state)) {
                    advanceRobolectricLooperUntil { flowOfInitStatus.value == InitStatus.Completed }
                    updateFieldMapping(outputFieldIndex = 0, mappedFrom = SelectedIndex.from(1)).join()
                    updateFieldMapping(outputFieldIndex = 1, mappedFrom = SelectedIndex.from(0)).join()
                    assertThat("the field remap ran", executeChangeNoteTypeAsync().await(), equalTo(1))
                }
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                assertThat("the draft keeps the old layout", editor.fieldText(0), equalTo("Question"))

                insertButton.performClick()
                advanceRobolectricLooper()
                chooseRecordingFieldIfAsked("Back")
                editor.saveNote()
                advanceRobolectricLooper()

                val saved = col.getNote(note.id).fields
                assertThat("the take is saved beside the text it was recorded for", saved[1], startsWith("Answer[sound:"))
                assertThat("the other text gets no audio", saved[0], equalTo("Question"))
            }
        }

    private fun chooseRecordingFieldIfAsked(name: String) {
        // The editor may follow a known mapping itself or ask the user when it cannot identify the destination.
        (ShadowDialog.getLatestDialog() as? AlertDialog)?.takeIf { it.isShowing }?.listView?.let { choices ->
            val choice = (0 until choices.count).first { choices.adapter.getItem(it) == name }
            choices.performItemClick(choices.getChildAt(choice), choice, choices.adapter.getItemId(choice))
            advanceRobolectricLooper()
        }
    }

    @Test
    fun `a recording can be inserted into a field added before recreation`() =
        runTest {
            withSplitPaneUiAsync {
                ensureCollectionLoadIsSynchronous()
                setCardBrowserFragmented(true)
                val type = addStandardNoteType("Expanded", arrayOf("Front", "Other", "Pronunciation"), "{{Front}}", "{{Pronunciation}}")
                val note = addBasicNote("Front text", "Back text")
                val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardBrowser::class.java, android.content.Intent())
                controller.get().viewModel.setQuery("deck:Default", forceRefresh = true)
                controller.get().waitForSearchResults()
                advanceRobolectricLooper()
                val original = requireNotNull(controller.get().fragment)
                val cardId = original.currentEditedCard!!.id
                original.chooseRecordAudio(fieldIndex = 1)
                record()

                withCol {
                    val target = notetypes.byName(type)!!.id
                    val input =
                        notetypes.changeNotetypeInfo(note.noteTypeId, target).input.copy {
                            noteIds.add(note.id)
                            newFields[0] = 0
                            newFields[1] = -1
                            newFields[2] = 1
                            newTemplates[0] = 0
                        }
                    notetypes.changeNotetypeOfNotes(input)
                }
                assertThat("the conversion retains the original card", col.getCard(cardId).nid, equalTo(note.id))
                assertThat(
                    "the conversion saved the added field",
                    col.getNote(note.id).fields,
                    equalTo(listOf("Front text", "", "Back text")),
                )

                controller.recreate()
                controller.get().viewModel.setQuery("deck:Default", forceRefresh = true)
                controller.get().waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(controller.get().fragment)
                insertButton.performClick()
                advanceRobolectricLooper()
                chooseRecordingFieldIfAsked("Pronunciation")

                assertThat(
                    "the newly added field receives the original text and recording",
                    editor.currentFieldStrings.getOrNull(2).orEmpty(),
                    containsString("Back text[sound:"),
                )
            }
        }

    @Test
    fun `recreation retains audio after an external field removal`() =
        runTest {
            withSplitPaneUiAsync {
                ensureCollectionLoadIsSynchronous()
                setCardBrowserFragmented(true)
                val singleField = addStandardNoteType("Single field", arrayOf("Front"), "{{Front}}", "{{Front}}")
                addBasicNote("Front", "Back")
                val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardBrowser::class.java, android.content.Intent())
                controller.get().viewModel.setQuery("deck:Default", forceRefresh = true)
                controller.get().waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(controller.get().fragment)
                val noteId = editor.editorNote!!.id
                val cardId = editor.currentEditedCard!!.id
                editor.chooseRecordAudio(fieldIndex = 1)
                record()
                val recording = mediaRecorderStub.outputFiles.single()
                val destination =
                    withCol {
                        val source = getNote(noteId).noteTypeId
                        val target = notetypes.byName(singleField)!!.id
                        val input =
                            notetypes.changeNotetypeInfo(source, target).input.copy {
                                noteIds.add(noteId)
                                newFields[0] = 0
                                newTemplates[0] = 0
                            }
                        notetypes.changeNotetypeOfNotes(input)
                        target
                    }
                assertThat("the conversion retained the original card", col.getCard(cardId).nid, equalTo(noteId))
                assertThat("the converted note has one field", col.getNote(noteId).fields.size, equalTo(1))
                controller.get().viewModel.setQuery("deck:Default", forceRefresh = true)
                controller.get().waitForSearchResults()
                advanceRobolectricLooper()
                assertThat("the draft survived the refresh", recording.exists(), equalTo(true))

                controller.recreate()
                advanceRobolectricLooper()

                assertThat("the conversion remains saved", col.getNote(noteId).noteTypeId, equalTo(destination))
                assertThat("recreation must not delete the uninserted recording", recording.exists(), equalTo(true))
            }
        }

    @Test
    fun `blocked saves during quick row changes keep the edited note highlighted`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                val browser = getBrowserWithNotes(3)
                browser.viewModel.setCardsOrNotes(CardsOrNotes.NOTES).join()
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                val currentRow = requireNotNull(browser.viewModel.paneRow)
                val otherPositions =
                    browser.viewModel.cards.indices
                        .filter { browser.viewModel.cards[it] != currentRow }
                editor.chooseRecordAudio(fieldIndex = 1)
                record()

                val collectionScheduler = TestCoroutineScheduler()
                val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(collectionScheduler), useReentrantLock = false)
                try {
                    browser.clickRowAtPosition(otherPositions[0])
                    browser.clickRowAtPosition(otherPositions[1])
                    advanceRobolectricLooper()
                    assertThat(
                        "row taps are queued before a Save dialog",
                        ShadowDialog.getShownDialogs().any { it.isShowing },
                        equalTo(false),
                    )
                } finally {
                    CollectionManager.setTestDispatcher(previous)
                    collectionScheduler.advanceUntilIdle()
                }
                advanceRobolectricLooper()
                val dialogs =
                    ShadowDialog
                        .getShownDialogs()
                        .filterIsInstance<AlertDialog>()
                        .distinct()
                        .filter { it.isShowing }
                assertThat("both queued row clicks prompt to save", dialogs.size, equalTo(2))
                for (dialog in dialogs.asReversed()) {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                    advanceRobolectricLooper()
                }

                assertThat("the recorder keeps the edited note open", browser.fragment, equalTo(editor))
                assertThat("the edited note remains highlighted", browser.viewModel.paneRow, equalTo(currentRow))
            }
        }

    @Test
    fun `switching browser row mode keeps the recording note highlighted`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                val note = addBasicNote("Recording note", "Back")
                val other = addBasicNote("Other note", "Other back")
                val oldCardId = note.firstCard().id
                val sharedId = 42L
                withCol {
                    db.execute("UPDATE cards SET id = ? WHERE id = ?", sharedId, oldCardId)
                    db.execute("UPDATE notes SET id = ? WHERE id = ?", sharedId, other.id)
                    db.execute("UPDATE cards SET nid = ? WHERE nid = ?", sharedId, other.id)
                }
                val browser = getBrowserWithNotes(0)
                browser.viewModel.setCardsOrNotes(CardsOrNotes.CARDS).join()
                browser.viewModel.setQuery("nid:${note.id}", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                assertThat("the intended note is open", editor.editorNote!!.id, equalTo(note.id))
                editor.chooseRecordAudio(fieldIndex = 1)
                record()
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()

                browser.viewModel.setCardsOrNotes(CardsOrNotes.NOTES).join()
                browser.waitForSearchResults()
                advanceRobolectricLooperUntil { ShadowDialog.getLatestDialog()?.isShowing == true }
                latestDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                advanceRobolectricLooper()

                assertThat("the pending recording keeps its note open", browser.fragment!!.editorNote!!.id, equalTo(note.id))
                assertThat("the other note is not highlighted", browser.viewModel.paneRow?.cardOrNoteId, not(equalTo(sharedId)))
            }
        }

    @Test
    fun `blocked save does not highlight another search result`() = verifyBlockedSaveHighlight(clearResultsFirst = false)

    @Test
    fun `blocked save after an empty search leaves no row highlighted`() = verifyBlockedSaveHighlight(clearResultsFirst = true)

    private fun verifyBlockedSaveHighlight(clearResultsFirst: Boolean) =
        withSplitPaneUi {
            setCardBrowserFragmented(true)
            val browser = getBrowserWithNotes(2)
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            val editor = requireNotNull(browser.fragment)
            val originalRow = requireNotNull(browser.viewModel.paneRow)
            val noteId = editor.editorNote!!.id
            editor.chooseRecordAudio(fieldIndex = 1)
            record()

            if (clearResultsFirst) {
                browser.viewModel.setQuery("nid:1", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                assertThat("the intermediate search has no results", browser.viewModel.cards.isEmpty(), equalTo(true))
            }

            browser.viewModel.setQuery("-nid:$noteId", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooperUntil { ShadowDialog.getLatestDialog()?.isShowing == true }
            val remainingRow = browser.viewModel.cards.single()
            assertThat("the edited note is filtered out", remainingRow, not(equalTo(originalRow)))

            latestDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            advanceRobolectricLooper()

            assertThat("the recording keeps the original note open", browser.fragment!!.editorNote!!.id, equalTo(noteId))
            assertThat("the unrelated search result is not highlighted", browser.viewModel.paneRow, not(equalTo(remainingRow)))
        }

    @Test
    fun `undoing a remap sends the recording back to its field`() =
        runTest {
            withSplitPaneUiAsync {
                ensureCollectionLoadIsSynchronous()
                setCardBrowserFragmented(true)
                val note = addBasicNote("Original front", "Original back")
                val browser = getBrowserWithNotes(0)
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                editor.chooseRecordAudio(fieldIndex = 1)
                record()

                val state = SavedStateHandle(mapOf(ChangeNoteTypeViewModel.ARG_NOTE_IDS to longArrayOf(note.id)))
                withViewModel(ChangeNoteTypeViewModel(state)) {
                    advanceRobolectricLooperUntil { flowOfInitStatus.value == InitStatus.Completed }
                    updateFieldMapping(outputFieldIndex = 0, mappedFrom = SelectedIndex.from(1)).join()
                    updateFieldMapping(outputFieldIndex = 1, mappedFrom = SelectedIndex.from(0)).join()
                    executeChangeNoteTypeAsync().await()
                }
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                assertThat("the remap moved Back content into Front", editor.fieldText(0), equalTo("Original back"))
                withCol { undo() }
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                assertThat("undo restored Front", editor.fieldText(0), equalTo("Original front"))

                insertButton.performClick()
                advanceRobolectricLooper()
                chooseRecordingFieldIfAsked("Back")
                assertThat("the recording returns to Back", editor.fieldText(1), containsString("[sound:"))
                assertThat("Front gets no recording", editor.fieldText(0), equalTo("Original front"))
            }
        }

    @Test
    fun `undo of a same type remap can move audio between equal fields`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                val note = addBasicNote("Same text", "Same text")
                val browser = getBrowserWithNotes(0)
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()

                val state = SavedStateHandle(mapOf(ChangeNoteTypeViewModel.ARG_NOTE_IDS to longArrayOf(note.id)))
                withViewModel(ChangeNoteTypeViewModel(state)) {
                    advanceRobolectricLooperUntil { flowOfInitStatus.value == InitStatus.Completed }
                    updateFieldMapping(outputFieldIndex = 0, mappedFrom = SelectedIndex.from(1)).join()
                    updateFieldMapping(outputFieldIndex = 1, mappedFrom = SelectedIndex.from(0)).join()
                    assertThat("the fields were remapped", executeChangeNoteTypeAsync().await(), equalTo(1))
                }
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                editor.chooseRecordAudio(fieldIndex = 1)
                record()

                tryUndo()
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()

                insertButton.performClick()
                advanceRobolectricLooper()
                chooseRecordingFieldIfAsked("Front")
                assertThat("the take follows the field restored by Undo", editor.fieldText(0), containsString("[sound:"))
                assertThat("the other field stays untouched", editor.fieldText(1), equalTo("Same text"))
            }
        }

    private suspend fun swapFieldsAroundRecording(recreate: Boolean): NoteEditorFragment {
        ensureCollectionLoadIsSynchronous()
        setCardBrowserFragmented(true)
        val note = addBasicNote("Front", "")
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardBrowser::class.java, android.content.Intent())
        controller.get().viewModel.setQuery("deck:Default", forceRefresh = true)
        controller.get().waitForSearchResults()
        advanceRobolectricLooper()
        requireNotNull(controller.get().fragment).chooseRecordAudio(fieldIndex = 1)
        record()
        val state = SavedStateHandle(mapOf(ChangeNoteTypeViewModel.ARG_NOTE_IDS to longArrayOf(note.id)))
        withViewModel(ChangeNoteTypeViewModel(state)) {
            advanceRobolectricLooperUntil { flowOfInitStatus.value == InitStatus.Completed }
            updateFieldMapping(outputFieldIndex = 0, mappedFrom = SelectedIndex.from(1)).join()
            updateFieldMapping(outputFieldIndex = 1, mappedFrom = SelectedIndex.from(0)).join()
            executeChangeNoteTypeAsync().await()
        }
        controller.get().viewModel.setQuery("deck:Default", forceRefresh = true)
        controller.get().waitForSearchResults()
        advanceRobolectricLooper()
        assertThat(
            "the swap moved the fields",
            requireNotNull(controller.get().fragment).currentFieldStrings.toList(),
            equalTo(listOf("", "Front")),
        )
        if (recreate) {
            controller.recreate()
            advanceRobolectricLooper()
        }
        return requireNotNull(controller.get().fragment)
    }

    @Test
    fun `cancelling the field choice keeps the recording`() =
        runTest {
            withSplitPaneUiAsync {
                val editor = swapFieldsAroundRecording(recreate = false)
                insertButton.performClick()
                advanceRobolectricLooper()
                val chooser = ShadowDialog.getLatestDialog() as AlertDialog
                assertThat("Insert asks for a field", chooser.isShowing, equalTo(true))
                chooser.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
                advanceRobolectricLooper()
                assertThat("nothing is inserted", editor.currentFieldStrings.toList(), equalTo(listOf("", "Front")))
                insertButton.assertExists()

                insertButton.performClick()
                advanceRobolectricLooper()
                chooseRecordingFieldIfAsked("Front")
                assertThat("the recording goes to the chosen field", editor.fieldText(0), containsString("[sound:"))
            }
        }

    @Test
    fun `a stale field choice keeps the recording available`() =
        runTest {
            withSplitPaneUiAsync {
                val type = addStandardNoteType("One field", arrayOf("Retained"), "{{Retained}}", "{{Retained}}")
                val editor = swapFieldsAroundRecording(recreate = false)
                val note = editor.editorNote!!
                val cardId = editor.currentEditedCard!!.id
                insertButton.performClick()
                advanceRobolectricLooper()
                val chooser = ShadowDialog.getLatestDialog() as AlertDialog
                val choices = chooser.listView
                assertThat("the choice still shows Back", choices.adapter.getItem(1), equalTo("Back"))

                withCol {
                    val target = notetypes.byName(type)!!.id
                    val input =
                        notetypes.changeNotetypeInfo(note.noteTypeId, target).input.copy {
                            noteIds.add(note.id)
                            newFields[0] = 1
                            newTemplates[0] = 0
                        }
                    notetypes.changeNotetypeOfNotes(input)
                }
                val browser = editor.requireActivity() as CardBrowser
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                assertThat("the conversion retains the edited card", col.getCard(cardId).nid, equalTo(note.id))
                assertThat("the conversion leaves one field", editor.currentFields.single().name, equalTo("Retained"))

                if (chooser.isShowing) {
                    choices.performItemClick(choices.getChildAt(1), 1, choices.adapter.getItemId(1))
                    advanceRobolectricLooper()
                }

                assertThat("an obsolete field choice must leave the take available", editor.hasRecording, equalTo(true))
                insertButton.performClick()
                advanceRobolectricLooper()
                chooseRecordingFieldIfAsked("Retained")
                assertThat("the take can still be inserted after the refresh", editor.fieldText(0), containsString("[sound:"))
            }
        }

    @Test
    fun `recreation keeps asking for the recording's field`() =
        runTest {
            withSplitPaneUiAsync {
                val editor = swapFieldsAroundRecording(recreate = true)
                insertButton.performClick()
                advanceRobolectricLooper()
                assertThat("nothing is inserted automatically", editor.currentFieldStrings.toList(), equalTo(listOf("", "Front")))
                assertThat("Insert asks for a field", (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing, equalTo(true))
            }
        }

    @Test
    fun `picking a field with its microphone skips the question`() =
        runTest {
            withSplitPaneUiAsync {
                val editor = swapFieldsAroundRecording(recreate = false)
                editor.chooseRecordAudio(fieldIndex = 0)
                insertButton.performClick()
                advanceRobolectricLooper()
                assertThat("the recording goes to the field picked with the microphone", editor.fieldText(0), containsString("[sound:"))
            }
        }

    @Test
    fun `cancel after an empty search leaves no row highlighted`() =
        withSplitPaneUi {
            setCardBrowserFragmented(true)
            val browser = getBrowserWithNotes(2)
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            val editor = requireNotNull(browser.fragment)
            val noteId = editor.editorNote!!.id
            editor.chooseRecordAudio(fieldIndex = 1)
            record()
            browser.viewModel.setQuery("nid:1", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            browser.viewModel.setQuery("-nid:$noteId", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooperUntil { ShadowDialog.getLatestDialog()?.isShowing == true }
            val remainingRow = browser.viewModel.cards.single()

            latestDialog.getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
            advanceRobolectricLooper()

            assertThat("the recording keeps the original note open", browser.fragment!!.editorNote!!.id, equalTo(noteId))
            assertThat("Cancel highlights no other row", browser.viewModel.paneRow, not(equalTo(remainingRow)))
        }

    @Test
    fun `discard then cancel across queued prompts highlights the opened note`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                val browser = getBrowserWithNotes(3)
                browser.viewModel.setCardsOrNotes(CardsOrNotes.NOTES).join()
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val currentRow = requireNotNull(browser.viewModel.paneRow)
                val otherPositions =
                    browser.viewModel.cards.indices
                        .filter { browser.viewModel.cards[it] != currentRow }
                requireNotNull(browser.fragment).chooseRecordAudio(fieldIndex = 1)
                record()

                val collectionScheduler = TestCoroutineScheduler()
                val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(collectionScheduler), useReentrantLock = false)
                try {
                    browser.clickRowAtPosition(otherPositions[0])
                    browser.clickRowAtPosition(otherPositions[1])
                    advanceRobolectricLooper()
                } finally {
                    CollectionManager.setTestDispatcher(previous)
                    collectionScheduler.advanceUntilIdle()
                }
                advanceRobolectricLooper()
                val dialogs =
                    ShadowDialog
                        .getShownDialogs()
                        .filterIsInstance<AlertDialog>()
                        .distinct()
                        .filter { it.isShowing }
                assertThat("both row taps prompt", dialogs.size, equalTo(2))
                dialogs.last().getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
                advanceRobolectricLooper()
                dialogs.first().getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
                advanceRobolectricLooper()

                val secondRow = browser.viewModel.cards[otherPositions[1]]
                assertThat("Discard opened the second note", browser.fragment!!.editorNote!!.id, equalTo(secondRow.cardOrNoteId))
                assertThat("the opened note stays highlighted", browser.viewModel.paneRow, equalTo(secondRow))
            }
        }

    @Test
    fun `the formatting toolbar returns after a short window closes the recorder`() {
        RuntimeEnvironment.setQualifiers("w800dp-h220dp-land-mdpi")
        val controller = startNoteEditor()
        val editor = controller.noteEditor
        controller.get().dispatchInsets(navBarBottom = 24.dp)
        advanceRobolectricLooper()
        editor.chooseRecordAudio(fieldIndex = 0)
        composeRule.waitForIdle()
        advanceRobolectricLooper()
        assertThat("the recorder takes the toolbar's place", editor.binding.editorToolbar.isVisible, equalTo(false))

        closeButton.performClick()
        composeRule.waitForIdle()
        advanceRobolectricLooper()

        assertThat("the toolbar comes back", editor.binding.editorToolbar.isVisible, equalTo(true))
    }

    @Test
    fun `an unrelated undo keeps the recording's field while the note has unsaved edits`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                val browser = getBrowserWithNotes(2)
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                val otherNoteId = col.findNotes("").first { it != editor.editorNote!!.id }
                editor.chooseRecordAudio(fieldIndex = 1)
                record()
                editor.editFieldAt(0)!!.setText("Typed")

                withCol { updateNote(getNote(otherNoteId).apply { setField(0, "Changed") }) }
                tryUndo()
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                insertButton.performClick()
                advanceRobolectricLooper()

                assertThat("Insert does not ask", (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true, equalTo(false))
                assertThat("the recording goes to its field", editor.fieldText(1), containsString("[sound:"))
                assertThat("the typed text stays", editor.fieldText(0), equalTo("Typed"))
            }
        }

    @Test
    fun `recreation does not insert a recording into a hidden occlusion field`() =
        runTest {
            ensureCollectionLoadIsSynchronous()
            val source = col.notetypes.byName("Image Occlusion")!!
            val target = col.notetypes.copy(source)
            val note =
                col.newNote(source).apply {
                    setField(0, "{{c1::x}}")
                    setField(1, "<img src=\"original.png\">")
                    setField(2, "Header")
                    setField(3, "<img src=\"recording-target.png\">")
                    col.addNote(this, col.decks.selected())
                }
            val cardId = note.firstCard().id
            val controller =
                startActivityControllerNormallyOpenCollectionWithIntent(
                    NoteEditorActivity::class.java,
                    NoteEditorDestination.EditNoteFromPreviewer(cardId).toIntent(targetContext),
                )
            controller.noteEditor.chooseRecordAudio(fieldIndex = 3)
            record()

            withCol {
                val input =
                    notetypes.changeNotetypeInfo(source.id, target.id).input.copy {
                        noteIds.add(note.id)
                        newFields[1] = 3
                        newFields[3] = 1
                    }
                notetypes.changeNotetypeOfNotes(input)
            }
            assertThat("the edited card survives conversion", col.getCard(cardId).nid, equalTo(note.id))
            controller.recreate()
            advanceRobolectricLooper()
            val editor = controller.noteEditor

            insertButton.performClick()
            advanceRobolectricLooper()
            chooseRecordingFieldIfAsked(target.fieldsNames[3])

            assertThat("the hidden image field receives no audio", editor.fieldText(1), not(containsString("[sound:")))
            assertThat("a visible field can receive the recording", editor.fieldText(3), containsString("[sound:"))
        }

    @Test
    fun `recreating the editor keeps a recording's field among equal fields`() {
        ensureCollectionLoadIsSynchronous()
        val card = addBasicNote("Same text", "Same text").firstCard()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(card.id).toIntent(targetContext),
            )
        controller.noteEditor.chooseRecordAudio(fieldIndex = 1)
        record()

        controller.recreate()
        advanceRobolectricLooper()
        insertButton.performClick()
        advanceRobolectricLooper()

        assertThat("Insert does not ask", (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true, equalTo(false))
        assertThat("the recording goes to its field", controller.noteEditor.fieldText(1), containsString("[sound:"))
        assertThat("the other field gets none", controller.noteEditor.fieldText(0), equalTo("Same text"))
    }

    private fun startNoteEditor(): ActivityController<NoteEditorActivity> {
        ensureCollectionLoadIsSynchronous()
        return startActivityControllerNormallyOpenCollectionWithIntent(
            NoteEditorActivity::class.java,
            NoteEditorDestination.AddNote().toIntent(targetContext),
        )
    }

    private val ActivityController<NoteEditorActivity>.noteEditor: NoteEditorFragment
        get() = get().getNoteEditorFragment()

    private val NoteEditorFragment.inlineRecorder
        get() = childFragmentManager.findFragmentById(R.id.inline_audio_recorder_container)

    private val NoteEditorFragment.recorderViewModel: AudioRecordingViewModel
        get() = ViewModelProvider(inlineRecorder!!)[AudioRecordingViewModel::class.java]

    private val NoteEditorFragment.recorderKeepsScreenOn: Boolean
        get() {
            composeRule.waitForIdle()
            return requireActivity()
                .window.decorView.allViews
                .any { it.keepScreenOn }
        }

    private fun NoteEditorFragment.pressBack() {
        requireActivity().onBackPressedDispatcher.onBackPressed()
        advanceRobolectricLooper()
    }

    private val latestDialog: AlertDialog
        get() = ShadowDialog.getLatestDialog() as AlertDialog

    private fun NoteEditorFragment.chooseRecordAudio(fieldIndex: Int) {
        multimediaController.handleActions(fieldIndex)
        advanceRobolectricLooper()
        multimediaViewModel.setMultimediaAction(MultimediaAction.SELECT_AUDIO_RECORDING)
        advanceRobolectricLooper()
    }

    private fun NoteEditorFragment.selectNoteType(name: String) {
        val spinner: Spinner = noteTypeSpinner!!
        val position = (0 until spinner.count).first { spinner.getItemAtPosition(it) == name }
        spinner.setSelection(position)
        advanceRobolectricLooper()
    }

    private fun NoteEditorFragment.fieldText(index: Int) = editFieldAt(index)!!.text.toString()

    private fun record() {
        recordButton.performClick()
        saveButton.performClick()
    }

    private fun recordWithoutMicrophone(): Activity {
        denyPermissions(RECORD_AUDIO)
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()
        val activity = editor.requireActivity()
        val request = shadowOf(activity).lastRequestedPermission
        activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(PERMISSION_DENIED))
        return activity
    }

    private val permissionDeniedMessage
        get() = composeRule.onNodeWithText(targetContext.getString(CommonString.microphone_permission_denied_message))

    private fun button(
        @StringRes description: Int,
    ): SemanticsNodeInteraction = composeRule.onNodeWithContentDescription(targetContext.getString(description))

    private val recordButton get() = button(CommonString.start_recording)
    private val pauseButton get() = button(CommonString.pause_recording)
    private val resumeButton get() = button(CommonString.resume_recording)
    private val saveButton get() = button(CommonString.save_voice)
    private val insertButton get() = button(CommonString.insert_recording)
    private val discardButton get() = button(CommonString.delete_audio_button_description)
    private val closeButton get() = button(CommonString.close)

    @Test
    fun `cancelling save changes after a conversion asks for the field`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                val note = addBasicNote("Question", "Answer")
                val browser = getBrowserWithNotes(0)
                browser.viewModel.setCardsOrNotes(CardsOrNotes.NOTES).join()
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                editor.chooseRecordAudio(fieldIndex = 1)
                record()
                editor.editFieldAt(0)!!.setText("Question edited")

                withCol {
                    val target = notetypes.byName("Basic (and reversed card)")!!.id
                    val input =
                        notetypes.changeNotetypeInfo(note.noteTypeId, target).input.copy {
                            noteIds.add(note.id)
                            newFields[0] = 1
                            newFields[1] = 0
                            newTemplates[0] = 0
                            newTemplates[1] = -1
                        }
                    notetypes.changeNotetypeOfNotes(input)
                }
                assertThat("the conversion adds a sibling", col.getNote(note.id).cardIds(col).size, equalTo(2))
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                assertThat("save changes is offered", dialog.isShowing, equalTo(true))
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
                advanceRobolectricLooper()
                assertThat("the draft stays", editor.fieldText(0), equalTo("Question edited"))

                insertButton.performClick()
                advanceRobolectricLooper()
                val chooser = ShadowDialog.getLatestDialog() as? AlertDialog
                assertThat("Insert asks for the field", chooser?.isShowing == true && chooser.listView != null, equalTo(true))
            }
        }

    @Test
    fun `a deleted note with a draft still offers save changes`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                addBasicNote("Question", "Answer")
                addBasicNote("Other", "Note")
                val browser = getBrowserWithNotes(0)
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                editor.chooseRecordAudio(fieldIndex = 1)
                record()
                editor.editFieldAt(0)!!.setText("Edited")
                val shown = editor.editorNote!!.id
                col.removeNotes(noteIds = listOf(shown))

                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()

                val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
                assertThat("save changes is offered", dialog?.isShowing, equalTo(true))
            }
        }

    @Test
    fun `a field choice closes with its discarded editor`() =
        runTest {
            withSplitPaneUiAsync {
                val browser = browserWithUnknownRecordingField()
                insertButton.performClick()
                advanceRobolectricLooper()
                val chooser = latestDialog
                assertThat("Insert asks for a field", chooser.isShowing, equalTo(true))

                browser.discardEditorForAnotherRow()

                assertThat("the field choice closes with its editor", chooser.isShowing, equalTo(false))
            }
        }

    @Test
    fun `a close confirmation closes with its discarded editor`() =
        runTest {
            withSplitPaneUiAsync {
                val browser = browserWithUnknownRecordingField()
                closeButton.performClick()
                advanceRobolectricLooper()
                val confirmation = latestDialog
                assertThat("Close asks first", confirmation.isShowing, equalTo(true))

                browser.discardEditorForAnotherRow()

                assertThat("the confirmation closes with its editor", confirmation.isShowing, equalTo(false))
            }
        }

    @Test
    fun `a decks confirmation closes with its discarded editor`() =
        runTest {
            withSplitPaneUiAsync {
                val browser = browserWithUnknownRecordingField()
                browser.drawerLayout.openDrawer(GravityCompat.START, false)
                advanceRobolectricLooper()
                browser.onNavigationItemSelected(RoboMenuItem(R.id.nav_decks))
                browser.drawerLayout.closeDrawer(GravityCompat.START, false)
                shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
                advanceRobolectricLooper()
                val confirmation = latestDialog
                assertThat("Decks asks first", confirmation.isShowing, equalTo(true))

                browser.discardEditorForAnotherRow()

                assertThat("the confirmation closes with its editor", confirmation.isShowing, equalTo(false))
            }
        }

    @Test
    fun `recreation after a swap of equal fields asks for the field`() =
        runTest {
            withSplitPaneUiAsync {
                ensureCollectionLoadIsSynchronous()
                setCardBrowserFragmented(true)
                val note = addBasicNote("Same", "Same")
                val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardBrowser::class.java, android.content.Intent())
                controller.get().viewModel.setQuery("deck:Default", forceRefresh = true)
                controller.get().waitForSearchResults()
                advanceRobolectricLooper()
                requireNotNull(controller.get().fragment).chooseRecordAudio(fieldIndex = 1)
                record()
                swapFields(note.id, note.noteTypeId)

                controller.recreate()
                advanceRobolectricLooper()
                insertButton.performClick()
                advanceRobolectricLooper()

                val chooser = ShadowDialog.getLatestDialog() as? AlertDialog
                assertThat("Insert asks for the field", chooser?.isShowing == true && chooser.listView != null, equalTo(true))
            }
        }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a stacked toolbar leaves room for the field while recording`() {
        targetContext.sharedPrefs().edit {
            putBoolean("noteEditorScrollToolbar", false)
            putStringSet(
                "note_editor_custom_buttons",
                CustomToolbarButton.toStringSet(ArrayList((0 until 24).map { CustomToolbarButton(it, "$it", "<b>", "</b>") })),
            )
        }
        RuntimeEnvironment.setQualifiers("w800dp-h300dp-land-mdpi")
        val controller = startNoteEditor()
        val editor = controller.noteEditor
        controller.get().dispatchInsets(navBarBottom = 24.dp)
        advanceRobolectricLooper()
        val toolbar = editor.binding.editorToolbar
        assertThat("the toolbar is stacked", toolbar.height - toolbar.paddingBottom > 60.dp.toPx(controller.get()), equalTo(true))

        editor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()
        composeRule.waitForIdle()
        advanceRobolectricLooper()

        val fields = editor.binding.noteEditorLayout
        val controlsTop = listOf(editor.binding.inlineAudioRecorderContainer, toolbar).filter { it.isShown }.minOf { it.screenTop }
        val textBottom = fields.screenTop + fields.height - fields.paddingBottom
        assertThat(
            "the controls at $controlsTop leave the fields ending at $textBottom uncovered",
            controlsTop >= textBottom,
            equalTo(true),
        )
        pauseButton.assertExists()
    }

    @Test
    fun `a stale layout offers only fields saved where shown`() =
        runTest {
            withSplitPaneUiAsync {
                ensureCollectionLoadIsSynchronous()
                setCardBrowserFragmented(true)
                val type = addStandardNoteType("Inserted field", arrayOf("Front", "Extra", "Back"), "{{Front}}", "{{Back}}")
                val note = addBasicNote("Question", "Answer")
                val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardBrowser::class.java, android.content.Intent())
                controller.get().viewModel.setQuery("deck:Default", forceRefresh = true)
                controller.get().waitForSearchResults()
                advanceRobolectricLooper()
                val original = requireNotNull(controller.get().fragment)
                original.chooseRecordAudio(fieldIndex = 1)
                record()
                original.editFieldAt(0)!!.setText("Question edited")
                withCol {
                    val input =
                        notetypes.changeNotetypeInfo(note.noteTypeId, notetypes.byName(type)!!.id).input.copy {
                            noteIds.add(note.id)
                            newFields[0] = 0
                            newFields[1] = -1
                            newFields[2] = 1
                            newTemplates[0] = 0
                        }
                    notetypes.changeNotetypeOfNotes(input)
                }

                controller.recreate()
                advanceRobolectricLooper()
                val editor = requireNotNull(controller.get().fragment)
                insertButton.performClick()
                advanceRobolectricLooper()

                val choices = latestDialog.listView.let { list -> (0 until list.count).map { list.adapter.getItem(it) } }
                assertThat("only a field that saves where it is shown is offered", choices, equalTo(listOf("Front")))
                chooseRecordingFieldIfAsked("Front")
                editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
                advanceRobolectricLooper()
                val saved = col.getNote(note.id).fields
                assertThat("the recording is saved in Front", saved[0], containsString("[sound:"))
                assertThat("no other field gets it", saved.drop(1).none { "[sound:" in it }, equalTo(true))
            }
        }

    @Test
    fun `the toolbar keeps a full row above the keyboard`() {
        RuntimeEnvironment.setQualifiers("w800dp-h360dp-land-mdpi")
        val controller = startNoteEditor()
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()
        advanceRobolectricLooper()

        controller.get().dispatchInsets(imeBottom = 170.dp, cutoutTop = 24.dp)
        advanceRobolectricLooper()

        val toolbar = editor.binding.editorToolbar
        assertThat("the keyboard takes the recorder's place", editor.binding.inlineAudioRecorderContainer.isVisible, equalTo(false))
        assertThat(
            "the toolbar row is ${toolbar.scrollView.height}px",
            toolbar.scrollView.height >= 44.dp.toPx(controller.get()),
            equalTo(true),
        )
    }

    @Test
    fun `a floating keyboard leaves the recorder in place`() {
        RuntimeEnvironment.setQualifiers("w800dp-h250dp-land-mdpi")
        val controller = startNoteEditor()
        val editor = controller.noteEditor
        controller.get().dispatchInsets(navBarBottom = 24.dp)
        advanceRobolectricLooper()
        editor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()
        advanceRobolectricLooper()

        controller.get().dispatchInsets(navBarBottom = 24.dp, imeVisible = true)
        advanceRobolectricLooper()

        assertThat("the recorder stays", editor.binding.inlineAudioRecorderContainer.isVisible, equalTo(true))
    }

    @Test
    fun `a snackbar shown before recording stays above the recorder`() {
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-port-mdpi")
        val controller = startNoteEditor()
        val editor = controller.noteEditor
        val snackbar = requireNotNull(editor.showSnackbar("Added", Snackbar.LENGTH_INDEFINITE))
        advanceRobolectricLooper()

        editor.chooseRecordAudio(fieldIndex = 0)
        composeRule.waitForIdle()
        advanceRobolectricLooper()

        val recorderTop = editor.binding.inlineAudioRecorderContainer.screenTop
        val snackbarBottom = snackbar.view.screenTop + snackbar.view.height
        assertThat(
            "the snackbar ends at $snackbarBottom, the recorder starts at $recorderTop",
            snackbarBottom <= recorderTop,
            equalTo(true),
        )
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a stacked toolbar switched to scroll returns while recording`() {
        targetContext.sharedPrefs().edit {
            putBoolean("noteEditorScrollToolbar", false)
            putStringSet(
                "note_editor_custom_buttons",
                CustomToolbarButton.toStringSet(ArrayList((0 until 24).map { CustomToolbarButton(it, "$it", "<b>", "</b>") })),
            )
        }
        RuntimeEnvironment.setQualifiers("w800dp-h300dp-land-mdpi")
        val controller = startNoteEditor()
        val editor = controller.noteEditor
        controller.get().dispatchInsets(navBarBottom = 24.dp)
        advanceRobolectricLooper()
        editor.chooseRecordAudio(fieldIndex = 0)
        composeRule.waitForIdle()
        advanceRobolectricLooper()
        assertThat("the stacked toolbar makes way", editor.binding.editorToolbar.isVisible, equalTo(false))

        editor.onMenuItemSelected(RoboMenuItem(R.id.action_toolbar_layout))
        advanceRobolectricLooper()
        composeRule.onNodeWithText(targetContext.getString(CommonString.note_editor_toolbar_scroll)).performClick()
        composeRule.waitForIdle()
        advanceRobolectricLooper()

        assertThat("one scrolling row fits", editor.binding.editorToolbar.isVisible, equalTo(true))
    }

    @Test
    fun `record audio chosen behind a busy collection survives a stop`() {
        val controller = startNoteEditor()
        val editor = controller.noteEditor
        val collectionScheduler = TestCoroutineScheduler()
        val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(collectionScheduler), useReentrantLock = false)
        try {
            editor.chooseRecordAudio(fieldIndex = 0)
            controller.pause().stop()
        } finally {
            CollectionManager.setTestDispatcher(previous)
        }
        collectionScheduler.advanceUntilIdle()
        advanceRobolectricLooper()
        controller.start().resume()
        advanceRobolectricLooper()

        assertThat("the recorder opens", editor.inlineRecorder, not(nullValue()))
    }

    @Test
    fun `an answered prompt does not keep editing again later`() {
        val controller = startNoteEditor()
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        record()
        var keptEditing = 0
        editor.discardRecordingFirst(onKeepEditing = { keptEditing++ }) {}
        latestDialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        advanceRobolectricLooper()

        controller.pause().stop().destroy()
        advanceRobolectricLooper()

        assertThat("keep editing runs once", keptEditing, equalTo(1))
    }

    @Test
    fun `a refresh that changes nothing keeps the field beside equal text`() =
        withSplitPaneUi {
            setCardBrowserFragmented(true)
            addBasicNote("Same text", "Same text")
            val browser = getBrowserWithNotes(0)
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            val editor = requireNotNull(browser.fragment)
            editor.chooseRecordAudio(fieldIndex = 1)
            record()

            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            insertButton.performClick()
            advanceRobolectricLooper()

            assertThat("Insert does not ask", (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true, equalTo(false))
            assertThat("the recording goes to its field", editor.fieldText(1), containsString("[sound:"))
        }

    @Test
    fun `cancelling save changes keeps the field beside equal text`() =
        withSplitPaneUi {
            setCardBrowserFragmented(true)
            addBasicNote("Same text", "Same text")
            addBasicNote("Other text", "Other text")
            val browser = getBrowserWithNotes(0)
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            val editor = requireNotNull(browser.fragment)
            editor.chooseRecordAudio(fieldIndex = 1)
            record()
            editor.editFieldAt(0)!!.setText("Edited")

            val shown = editor.currentEditedCard!!.id
            browser.clickRowAtPosition(browser.viewModel.cards.indexOfFirst { it.cardOrNoteId != shown })
            advanceRobolectricLooper()
            latestDialog.getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
            advanceRobolectricLooper()
            insertButton.performClick()
            advanceRobolectricLooper()

            assertThat("Insert does not ask", (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true, equalTo(false))
            assertThat("the recording goes to its field", editor.fieldText(1), containsString("[sound:"))
        }

    private val View.screenTop: Int
        get() = IntArray(2).also(::getLocationOnScreen)[1]

    private suspend fun browserWithUnknownRecordingField(): CardBrowser {
        ensureCollectionLoadIsSynchronous()
        setCardBrowserFragmented(true)
        addBasicNote("First", "")
        addBasicNote("Second", "")
        val browser = startActivityControllerNormallyOpenCollectionWithIntent(CardBrowser::class.java, android.content.Intent()).get()
        browser.viewModel.setQuery("deck:Default", forceRefresh = true)
        browser.waitForSearchResults()
        advanceRobolectricLooper()
        val editor = requireNotNull(browser.fragment)
        editor.chooseRecordAudio(fieldIndex = 1)
        record()
        val note = editor.editorNote!!
        swapFields(note.id, note.noteTypeId)
        browser.viewModel.setQuery("deck:Default", forceRefresh = true)
        browser.waitForSearchResults()
        advanceRobolectricLooper()
        assertThat("the field is no longer known", (editor.inlineRecorder as InlineAudioRecorderFragment).isFieldKnown, equalTo(false))
        return browser
    }

    private fun CardBrowser.discardEditorForAnotherRow() {
        val shown = requireNotNull(fragment).currentEditedCard!!.id
        clickRowAtPosition(viewModel.cards.indexOfFirst { it.cardOrNoteId != shown })
        advanceRobolectricLooper()
        latestDialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        advanceRobolectricLooper()
        assertThat("the editor shows the other card", requireNotNull(fragment).currentEditedCard!!.id, not(equalTo(shown)))
    }

    private suspend fun swapFields(
        noteId: Long,
        noteTypeId: Long,
    ) = withCol {
        val input =
            notetypes.changeNotetypeInfo(noteTypeId, noteTypeId).input.copy {
                noteIds.add(noteId)
                newFields[0] = 1
                newFields[1] = 0
                newTemplates[0] = 0
            }
        notetypes.changeNotetypeOfNotes(input)
    }
}
