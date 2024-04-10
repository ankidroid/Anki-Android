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

package com.ichi2.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.util.AttributeSet
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.GestureProcessor
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.dialogs.CardSideSelectionDialog
import com.ichi2.anki.dialogs.GestureSelectionDialogUtils
import com.ichi2.anki.dialogs.GestureSelectionDialogUtils.onGestureChanged
import com.ichi2.anki.dialogs.KeySelectionDialogUtils
import com.ichi2.anki.dialogs.WarningDisplay
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.reviewer.MappableBinding.*
import com.ichi2.anki.reviewer.MappableBinding.Companion.fromGesture
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.screenBuilder
import com.ichi2.anki.showThemedToast
import com.ichi2.ui.AxisPicker
import com.ichi2.ui.KeyPicker
import com.ichi2.utils.*

/**
 * A preference which allows mapping of inputs to actions (example: keys -> commands)
 *
 * This is implemented as a List, the elements allow the user to either add, or
 * remove previously mapped keys
 *
 * Future:
 * * Allow mapping gestures here
 * * Allow maps other than the reviewer
 */
class ControlPreference : ListPreference {
    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    @Suppress("unused")
    constructor(context: Context) : super(context)

    val screenBuilder: (CardSide) -> Screen
        get() = ViewerCommand.fromPreferenceKey(key).screenBuilder

    private fun refreshEntries() {
        val entryTitles: MutableList<CharSequence> = ArrayList()
        val entryIndices: MutableList<Int> = ArrayList()
        // negative indices are "add"
        entryTitles.add(context.getString(R.string.binding_add_key))
        entryIndices.add(ADD_KEY_INDEX)
        // Add a joystick/motion controller
        entryTitles.add(context.getString(R.string.binding_add_axis))
        entryIndices.add(ADD_AXIS_INDEX)
        // Put "Add gesture" option if gestures are enabled
        if (context.sharedPrefs().getBoolean(GestureProcessor.PREF_KEY, false)) {
            entryTitles.add(context.getString(R.string.binding_add_gesture))
            entryIndices.add(ADD_GESTURE_INDEX)
        }
        // 0 and above are "delete" actions for already mapped preferences
        for ((i, binding) in MappableBinding.fromPreferenceString(value).withIndex()) {
            entryTitles.add(context.getString(R.string.binding_remove_binding, binding.toDisplayString(context)))
            entryIndices.add(i)
        }
        entries = entryTitles.toTypedArray()
        entryValues = entryIndices.map { it.toString() }.toTypedArray()
    }

    override fun onClick() {
        refreshEntries()
        super.onClick()
    }

    /** The summary that appears on the preference */
    override fun getSummary(): CharSequence = MappableBinding.fromPreferenceString(value)
        .joinToString(", ") { it.toDisplayString(context) }

    /** Called when an element is selected in the ListView */
    @SuppressLint("CheckResult")
    override fun callChangeListener(newValue: Any?): Boolean {
        when (val index: Int = (newValue as String).toInt()) {
            ADD_GESTURE_INDEX -> {
                val actionName = title
                AlertDialog.Builder(context).show {
                    title(text = actionName.toString())

                    val gesturePicker = GestureSelectionDialogUtils.getGesturePicker(context)

                    positiveButton(R.string.dialog_ok) {
                        val gesture = gesturePicker.getGesture() ?: return@positiveButton
                        val mappableBinding = fromGesture(
                            gesture,
                            screenBuilder
                        )
                        if (bindingIsUsedOnAnotherCommand(mappableBinding)) {
                            showDialogToReplaceBinding(mappableBinding, context.getString(R.string.binding_replace_gesture), it)
                        } else {
                            addBinding(mappableBinding)
                            it.dismiss()
                        }
                    }
                    negativeButton(R.string.dialog_cancel) { it.dismiss() }
                    customView(view = gesturePicker)

                    gesturePicker.onGestureChanged { gesture ->
                        warnIfBindingIsUsed(fromGesture(gesture, screenBuilder), gesturePicker)
                    }
                }
            }
            ADD_KEY_INDEX -> {
                val actionName = title
                AlertDialog.Builder(context).show {
                    val keyPicker: KeyPicker = KeyPicker.inflate(context)
                    customView(view = keyPicker.rootLayout)
                    title(text = actionName.toString())

                    // When the user presses a key
                    keyPicker.setBindingChangedListener { binding ->
                        val mappableBinding = MappableBinding(
                            binding,
                            screenBuilder(CardSide.BOTH)
                        )
                        warnIfBindingIsUsed(mappableBinding, keyPicker)
                    }

                    positiveButton(R.string.dialog_ok) {
                        val binding = keyPicker.getBinding() ?: return@positiveButton
                        // Use CardSide.BOTH as placeholder just to check if binding exists
                        CardSideSelectionDialog.displayInstance(context) { side ->
                            val mappableBinding = MappableBinding(binding, screenBuilder(side))
                            if (bindingIsUsedOnAnotherCommand(mappableBinding)) {
                                showDialogToReplaceBinding(mappableBinding, context.getString(R.string.binding_replace_key), it)
                            } else {
                                addBinding(mappableBinding)
                                it.dismiss()
                            }
                        }
                    }
                    negativeButton(R.string.dialog_cancel) { it.dismiss() }

                    keyPicker.setKeycodeValidation(KeySelectionDialogUtils.disallowModifierKeyCodes())
                }
            }
            ADD_AXIS_INDEX -> displayAddAxisDialog()
            else -> {
                val bindings: MutableList<MappableBinding> = MappableBinding.fromPreferenceString(value)
                bindings.removeAt(index)
                value = bindings.toPreferenceString()
            }
        }
        // don't persist the value
        return false
    }

    @SuppressLint("CheckResult") // noAutoDismiss
    private fun displayAddAxisDialog() {
        val actionName = title
        val axisPicker: AxisPicker = AxisPicker.inflate(context)
        val dialog = AlertDialog.Builder(context)
            .customView(view = axisPicker.rootLayout)
            .title(text = actionName.toString())
            .negativeButton(R.string.dialog_cancel) { it.dismiss() }
            .create()

        axisPicker.setBindingChangedListener { binding ->
            showToastIfBindingIsUsed(MappableBinding(binding, screenBuilder(CardSide.BOTH)))
            // Use CardSide.BOTH as placeholder just to check if binding exists
            CardSideSelectionDialog.displayInstance(context) { side ->
                val mappableBinding = MappableBinding(binding, screenBuilder(side))
                if (bindingIsUsedOnAnotherCommand(mappableBinding)) {
                    showDialogToReplaceBinding(mappableBinding, context.getString(R.string.binding_replace_key), dialog)
                } else {
                    addBinding(mappableBinding)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    /**
     * Return if another command uses
     */
    private fun bindingIsUsedOnAnotherCommand(binding: MappableBinding): Boolean {
        return getCommandWithBindingExceptThis(binding) != null
    }

    private fun warnIfBindingIsUsed(binding: MappableBinding, warningDisplay: WarningDisplay) {
        getCommandWithBindingExceptThis(binding)?.let {
            val name = context.getString(it.resourceId)
            val warning = context.getString(R.string.bindings_already_bound, name)
            warningDisplay.setWarning(warning)
        } ?: warningDisplay.clearWarning()
    }

    /** Displays a warning to the user if the provided binding couldn't be used */
    private fun showToastIfBindingIsUsed(binding: MappableBinding) {
        val bindingCommand = getCommandWithBindingExceptThis(binding)
            ?: return

        val commandName = context.getString(bindingCommand.resourceId)
        val text = context.getString(R.string.bindings_already_bound, commandName)
        showThemedToast(context, text, true)
    }

    /** @return command where the binding is mapped excluding the current command */
    private fun getCommandWithBindingExceptThis(binding: MappableBinding): ViewerCommand? {
        return MappableBinding.allMappings(context.sharedPrefs())
            // filter to the commands which have a binding matching this one except this
            .firstOrNull { x -> x.second.any { cmdBinding -> cmdBinding == binding } && x.first.preferenceKey != key }?.first
    }

    private fun addBinding(binding: MappableBinding) {
        val bindings = MappableBinding.fromPreferenceString(value)
        // by removing the binding, we ensure it's now at the start of the list
        bindings.remove(binding)
        bindings.add(0, binding)
        value = bindings.toPreferenceString()
    }

    /**
     * Remove binding from all control preferences other than this one
     */
    private fun clearBinding(binding: MappableBinding) {
        for (command in ViewerCommand.entries) {
            val commandPreference = preferenceManager.findPreference<ControlPreference>(command.preferenceKey)
                ?: continue
            val bindings = MappableBinding.fromPreferenceString(commandPreference.value)
            if (binding in bindings) {
                bindings.remove(binding)
                commandPreference.value = bindings.toPreferenceString()
            }
        }
    }

    private fun showDialogToReplaceBinding(binding: MappableBinding, title: String, parentDialog: DialogInterface) {
        val commandName = context.getString(getCommandWithBindingExceptThis(binding)!!.resourceId)

        AlertDialog.Builder(context).show {
            title(text = title)
            message(text = context.getString(R.string.bindings_already_bound, commandName))
            positiveButton(R.string.dialog_positive_replace) {
                clearBinding(binding)
                addBinding(binding)
                parentDialog.dismiss()
            }
            negativeButton(R.string.dialog_cancel)
        }
    }

    companion object {
        private const val ADD_AXIS_INDEX = -3
        private const val ADD_KEY_INDEX = -2
        private const val ADD_GESTURE_INDEX = -1
    }
}
