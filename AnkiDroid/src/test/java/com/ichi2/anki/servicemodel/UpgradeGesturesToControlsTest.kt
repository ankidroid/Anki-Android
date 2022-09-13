/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.servicemodel

import android.content.SharedPreferences
import androidx.core.content.edit
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.reviewer.Binding.Companion.keyCode
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.reviewer.MappableBinding.Screen.Reviewer
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.PreferenceUpgrade.Companion.upgradeVersionPrefKey
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.PreferenceUpgrade.UpgradeGesturesToControls
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import timber.log.Timber
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
@Ignore("flaky in ci")
class UpgradeGesturesToControlsTest(private val testData: TestData) : RobolectricTest() {
    private val changedKeys = HashSet<String>()

    private lateinit var prefs: SharedPreferences
    private lateinit var instance: UpgradeGesturesToControls

    @Before
    override fun setUp() {
        super.setUp()
        prefs = super.getPreferences()
        instance = UpgradeGesturesToControls()
        prefs.registerOnSharedPreferenceChangeListener { _, key -> run { Timber.i("added key $key"); changedKeys.add(key) } }
    }

    @Test
    fun gesture_set_no_conflicts() {
        // assume that we have a preference set, and that it has no defaults
        val command = ViewerCommand.SHOW_ANSWER
        prefs.edit { putString(testData.affectedPreferenceKey, oldCommandPreferenceStrings[command]) }

        assertTrue(prefs.contains(testData.affectedPreferenceKey))
        assertFalse(prefs.contains(testData.unaffectedPreferenceKey))
        assertThat("example command should have no defaults", MappableBinding.fromPreference(prefs, command), empty())

        upgradeAllGestures()

        assertThat(changedKeys, containsInAnyOrder(upgradeVersionPrefKey, testData.affectedPreferenceKey, command.preferenceKey))

        assertFalse(prefs.contains(testData.affectedPreferenceKey), "legacy preference removed")
        assertTrue(prefs.contains(command.preferenceKey), "new preference added")

        val fromPreference = MappableBinding.fromPreference(prefs, command)
        assertThat(fromPreference, hasSize(1))
        val binding = fromPreference.first()

        assertTrue(binding.isKey, "should be a key binding")
        assertEquals(binding, MappableBinding(keyCode(testData.keyCode), Reviewer(CardSide.BOTH)), "binding should match")
    }

    @Test
    fun if_mapped_to_non_empty_binding_then_added_to_end() {
        // common path
        // if the gesture was mapped to a command which already had bindings,
        // check it is added to the list at the end
        val command = ViewerCommand.EDIT
        prefs.edit { putString(testData.affectedPreferenceKey, oldCommandPreferenceStrings[command]) }

        assertTrue(prefs.contains(testData.affectedPreferenceKey))
        assertFalse(prefs.contains(testData.unaffectedPreferenceKey))
        assertFalse(prefs.contains(command.preferenceKey), "new preference does not exist")
        val previousCommands = MappableBinding.fromPreference(prefs, command)
        assertThat("example command should have defaults", previousCommands, not(empty()))

        upgradeAllGestures()

        assertThat(changedKeys, containsInAnyOrder(upgradeVersionPrefKey, testData.affectedPreferenceKey, command.preferenceKey))

        assertFalse(prefs.contains(testData.affectedPreferenceKey), "legacy preference removed")
        assertTrue(prefs.contains(command.preferenceKey), "new preference exists")

        val currentCommands = MappableBinding.fromPreference(prefs, command)
        assertThat("a binding was added to '${command.preferenceKey}'", currentCommands, hasSize(previousCommands.size + 1))

        // ensure that the order was not changed - the last element is not included in the zip
        previousCommands.zip(currentCommands).forEach {
            assertEquals(it.first, it.second, "bindings should not change order")
        }

        val addedBinding = currentCommands.last()

        assertTrue(addedBinding.isKey, "last should be a key binding")
        assertEquals(addedBinding, testData.binding, "last binding should match")
    }

    @Test
    fun if_gesture_already_exists_then_do_not_modify_list() {
        // the gestures shouldn't already be a keybind (as we've just introduced the feature)
        // but if it is, then we want to ignore it in the upgrade.

        val command = ViewerCommand.EDIT
        command.addBinding(prefs, testData.binding)

        prefs.edit { putString(testData.affectedPreferenceKey, oldCommandPreferenceStrings[command]) }

        assertTrue(prefs.contains(testData.affectedPreferenceKey))
        assertFalse(prefs.contains(testData.unaffectedPreferenceKey))
        assertTrue(prefs.contains(command.preferenceKey), "new preference exists")
        val previousCommands = MappableBinding.fromPreference(prefs, command)
        assertThat("example command should have defaults", previousCommands, hasSize(2))
        assertEquals(previousCommands.first(), testData.binding)

        upgradeAllGestures()

        assertThat("Binding gestures should not be changed", changedKeys, contains(upgradeVersionPrefKey, testData.affectedPreferenceKey))

        assertFalse(prefs.contains(testData.affectedPreferenceKey), "legacy preference removed")
        assertTrue(prefs.contains(command.preferenceKey), "new preference still exists")
    }

    @Test
    fun invalid_preference_value_results_in_old_null_value_and_no_new_value() {
        prefs.edit { putString(testData.affectedPreferenceKey, "bananas") }

        upgradeAllGestures()

        assertThat("Binding gestures should not be changed", changedKeys, contains(upgradeVersionPrefKey, testData.affectedPreferenceKey))

        assertFalse(prefs.contains(testData.affectedPreferenceKey), "legacy preference removed")
    }

    @Test
    fun invalid_command_value_results_in_old_null_value_and_no_new_value() {
        // a valid int, but not a valid command
        prefs.edit { putString(testData.affectedPreferenceKey, "-1") }

        upgradeAllGestures()

        assertThat("Binding gestures should not be changed", changedKeys, containsInAnyOrder(upgradeVersionPrefKey, testData.affectedPreferenceKey))

        assertFalse(prefs.contains(testData.affectedPreferenceKey), "legacy preference removed")
    }

    private fun upgradeAllGestures() {
        changedKeys.clear()
        instance.performUpgrade(prefs)
    }

    companion object {
        private const val KEYCODE_VOLUME_UP = 24
        private const val KEYCODE_VOLUME_DOWN = 25
        private const val PREF_KEY_VOLUME_UP = "gestureVolumeUp"
        private const val PREF_KEY_VOLUME_DOWN = "gestureVolumeDown"

        val oldCommandPreferenceStrings: HashMap<ViewerCommand, String> = hashMapOf(*UpgradeGesturesToControls().oldCommandValues.map { Pair(it.value, it.key.toString()) }.toTypedArray())

        private val volume_up_binding = MappableBinding(keyCode(KEYCODE_VOLUME_UP), Reviewer(CardSide.BOTH))
        private val volume_down_binding = MappableBinding(keyCode(KEYCODE_VOLUME_DOWN), Reviewer(CardSide.BOTH))

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: isValid({0})={1}")
        fun data(): Iterable<Array<Any>> {
            // pref key, keyCode, opposite key
            return arrayListOf<Array<Any>>(
                arrayOf(TestData(PREF_KEY_VOLUME_UP, KEYCODE_VOLUME_UP, PREF_KEY_VOLUME_DOWN, volume_up_binding)),
                arrayOf(TestData(PREF_KEY_VOLUME_DOWN, KEYCODE_VOLUME_DOWN, PREF_KEY_VOLUME_UP, volume_down_binding)),
            ).toList()
        }
        data class TestData(val affectedPreferenceKey: String, val keyCode: Int, val unaffectedPreferenceKey: String, val binding: MappableBinding)
    }
}
