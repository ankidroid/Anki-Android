// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.view.KeyEvent
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.anki.utils.CollectionPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadKeyboardDismissalRegressionTest : InstrumentedTest() {
    private var originalCollectionPath: String? = null

    @Before
    fun setUpKeyboardCollection() {
        val preferences = testContext.sharedPrefs()
        originalCollectionPath = preferences.getString(CollectionHelper.PREF_COLLECTION_PATH, null)
        val directory =
            File(
                CollectionHelper.getAppSpecificInternalAnkiDroidDirectory(testContext),
                "keyboard-deep-review",
            ).apply { mkdirs() }
        File(directory, "androidTest").mkdirs()
        preferences.edit { putString(CollectionHelper.PREF_COLLECTION_PATH, directory.absolutePath) }
        CollectionManager.setColForTests(emptyCol)
    }

    @After
    fun restoreCollectionPath() {
        testContext.sharedPrefs().edit {
            originalCollectionPath?.let { putString(CollectionHelper.PREF_COLLECTION_PATH, it) }
                ?: remove(CollectionHelper.PREF_COLLECTION_PATH)
        }
    }

    @Test
    fun keyboardBackClearsLearnAheadInputFocus() {
        runBlocking { CollectionPreferences.setLearnAheadLimit(20.minutes) }
        checkKeyboardBack(LearnAheadSettingsFragment::class)
    }

    @Test
    fun keyboardBackCommitsTheEditedLearnAheadLimit() {
        runBlocking { CollectionPreferences.setLearnAheadLimit(20.minutes) }
        checkKeyboardBack(LearnAheadSettingsFragment::class, inputText = "45", expectedText = "45")
    }

    @Test
    fun keyboardBackEndsEditingBeforeAnotherHardwareKey() {
        runBlocking { CollectionPreferences.setLearnAheadLimit(20.minutes) }
        checkKeyboardBack(
            LearnAheadSettingsFragment::class,
            inputText = "45",
            expectedText = "45",
            keyAfterBack = KeyEvent.KEYCODE_7,
        )
    }

    @Test
    fun keyboardBackPreventsNextHardwareDigitFromBeingSavedOnExit() {
        runBlocking { CollectionPreferences.setLearnAheadLimit(20.minutes) }
        checkKeyboardBack(
            LearnAheadSettingsFragment::class,
            inputText = "45",
            expectedText = "45",
            keyAfterBack = KeyEvent.KEYCODE_7,
            verifySavedLimitOnExit = true,
        )
    }

    @Test
    fun keyboardBackPreventsLaterHardwareDigitFromBeingSavedOnBackground() {
        runBlocking { CollectionPreferences.setLearnAheadLimit(20.minutes) }
        checkKeyboardBack(
            LearnAheadSettingsFragment::class,
            inputText = "45",
            expectedText = "45",
            keyAfterBack = KeyEvent.KEYCODE_7,
            verifySavedLimitOnExit = true,
            beforeExit = { scenario ->
                scenario.moveToState(Lifecycle.State.CREATED)
                runBlocking {
                    LearnAheadSettingsViewModel.pendingSaveJob?.join()
                    assertEquals(45.minutes, CollectionPreferences.getLearnAheadLimit())
                }
            },
        )
    }

    private fun checkKeyboardBack(
        fragment: KClass<out Fragment>,
        inputText: String = "0",
        expectedText: String = "20",
        keyAfterBack: Int? = null,
        verifySavedLimitOnExit: Boolean = false,
        beforeExit: (ActivityScenario<PreferencesActivity>) -> Unit = {},
    ) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val intent = PreferencesActivity.getIntent(testContext, fragment)
        ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
            val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000)
            assertNotNull(input, "The limit input must be displayed")
            input.click()
            waitUntil(message = { "The keyboard did not open" }) { scenario.isImeVisible() }
            input.text = inputText

            device.pressBack()
            waitUntil(message = { "Back did not dismiss the keyboard" }) { !scenario.isImeVisible() }
            device.wait(Until.gone(By.clazz("android.widget.EditText").focused(true)), 2_000)

            runBlocking { assertEquals(expectedText.toInt().minutes, CollectionPreferences.getLearnAheadLimit()) }
            if (keyAfterBack != null) {
                device.pressKeyCode(KeyEvent.KEYCODE_MOVE_END)
                device.pressKeyCode(keyAfterBack)
            }
            beforeExit(scenario)
            if (verifySavedLimitOnExit) return@use

            val limitInput = device.findObject(By.clazz("android.widget.EditText"))
            assertEquals(expectedText, limitInput.text)
            assertFalse(limitInput.isFocused, "Dismissing the keyboard must also end input editing")
        }
        if (verifySavedLimitOnExit) {
            runBlocking {
                LearnAheadSettingsViewModel.pendingSaveJob?.join()
                assertEquals(expectedText.toInt().minutes, CollectionPreferences.getLearnAheadLimit())
            }
        }
    }

    private fun ActivityScenario<PreferencesActivity>.isImeVisible(): Boolean {
        var visible = false
        onActivity { activity ->
            visible = ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }
}
