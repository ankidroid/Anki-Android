/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *  Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>
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

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.preference.DialogPreference
import androidx.preference.PreferenceFragmentCompat
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.GestureSelectionDialogUtils
import com.ichi2.anki.dialogs.GestureSelectionDialogUtils.onGestureChanged
import com.ichi2.anki.dialogs.KeySelectionDialogUtils
import com.ichi2.anki.dialogs.WarningDisplay
import com.ichi2.anki.preferences.SettingsFragment
import com.ichi2.anki.preferences.allPreferences
import com.ichi2.anki.preferences.requirePreference
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.showThemedToast
import com.ichi2.ui.AxisPicker
import com.ichi2.ui.KeyPicker
import com.ichi2.utils.create
import com.ichi2.utils.customView
import com.ichi2.utils.dp
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show

/**
 * A preference which allows mapping of inputs to actions (example: keys -> commands)
 *
 * The user is allowed to either add or remove previously mapped keys
 */
open class ControlPreference :
    DialogPreference,
    DialogFragmentProvider {
    @Suppress("unused")
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    @Suppress("unused")
    constructor(context: Context) : super(context)

    open fun getMappableBindings(): List<MappableBinding> = MappableBinding.fromPreferenceString(value)

    protected open fun onKeySelected(binding: Binding): Unit = addBinding(binding)

    protected open fun onAxisSelected(binding: Binding): Unit = addBinding(binding)

    open val areGesturesEnabled: Boolean = false

    protected open fun onGestureSelected(binding: Binding) = Unit

    /** @return whether the binding is used in another action */
    open fun warnIfUsed(
        binding: Binding,
        warningDisplay: WarningDisplay?,
    ): Boolean {
        val bindingPreference = getPreferenceAssignedTo(binding) ?: return false
        if (bindingPreference == this) return false
        val actionTitle = bindingPreference.title ?: ""
        val warning = context.getString(R.string.bindings_already_bound, actionTitle)
        if (warningDisplay != null) {
            warningDisplay.setWarning(warning)
        } else {
            showThemedToast(context, warning, true)
        }
        return true
    }

    var value: String?
        get() = getPersistedString(null)
        set(value) {
            if (value != getPersistedString(null)) {
                persistString(value)
                notifyChanged()
            }
        }

    override fun getSummary(): CharSequence = getMappableBindings().joinToString(", ") { it.toDisplayString(context) }

    override fun makeDialogFragment(): DialogFragment = ControlPreferenceDialogFragment()

    fun showGesturePickerDialog() {
        AlertDialog.Builder(context).show {
            setTitle(title)
            setIcon(icon)
            val gesturePicker = GestureSelectionDialogUtils.getGesturePicker(context)
            positiveButton(R.string.dialog_ok) {
                val gesture = gesturePicker.getGesture() ?: return@positiveButton
                val binding = Binding.GestureInput(gesture)
                onGestureSelected(binding)
                it.dismiss()
            }
            negativeButton(R.string.dialog_cancel) { it.dismiss() }
            customView(view = gesturePicker)
            gesturePicker.onGestureChanged { gesture ->
                warnIfUsedOrClearWarning(Binding.GestureInput(gesture), gesturePicker)
            }
        }
    }

    fun showKeyPickerDialog() {
        AlertDialog.Builder(context).show {
            val keyPicker: KeyPicker = KeyPicker.inflate(context)
            customView(view = keyPicker.rootLayout)
            setTitle(title)
            setIcon(icon)

            // When the user presses a key
            keyPicker.setBindingChangedListener { binding ->
                warnIfUsedOrClearWarning(binding, keyPicker)
            }
            positiveButton(R.string.dialog_ok) {
                val binding = keyPicker.getBinding() ?: return@positiveButton
                onKeySelected(binding)
                it.dismiss()
            }
            negativeButton(R.string.dialog_cancel) { it.dismiss() }
            keyPicker.setKeycodeValidation(KeySelectionDialogUtils.disallowModifierKeyCodes())
        }
    }

    fun showAddAxisDialog() {
        val axisPicker =
            AxisPicker.inflate(context).apply {
                setBindingChangedListener { binding ->
                    warnIfUsedOrClearWarning(binding, warningDisplay = null)
                    onAxisSelected(binding)
                }
            }
        AlertDialog.Builder(context).show {
            customView(view = axisPicker.rootLayout)
            setTitle(title)
            setIcon(icon)
            negativeButton(R.string.dialog_cancel) { it.dismiss() }
        }
    }

    private fun warnIfUsedOrClearWarning(
        binding: Binding,
        warningDisplay: WarningDisplay?,
    ) {
        if (!warnIfUsed(binding, warningDisplay)) {
            warningDisplay?.clearWarning()
        }
    }

    fun removeMappableBinding(binding: MappableBinding) {
        val bindings = getMappableBindings().toMutableList()
        bindings.remove(binding)
        value = bindings.toPreferenceString()
    }

    private fun addBinding(binding: Binding) {
        val newBinding = MappableBinding(binding)
        getPreferenceAssignedTo(binding)?.removeMappableBinding(newBinding)
        val bindings = getMappableBindings().toMutableList()
        bindings.add(newBinding)
        value = bindings.toPreferenceString()
    }

    /**
     * Checks if any other [ControlPreference] in the `preferenceScreen`
     * has the given [binding] assigned to.
     */
    protected fun getPreferenceAssignedTo(binding: Binding): ControlPreference? {
        for (pref in preferenceManager.preferenceScreen.allPreferences()) {
            if (pref !is ControlPreference) continue
            val bindings = pref.getMappableBindings().map { it.binding }
            if (binding in bindings) {
                return pref
            }
        }
        return null
    }
}

class ControlPreferenceDialogFragment : DialogFragment() {
    private lateinit var preference: ControlPreference

    @Suppress("DEPRECATION") // targetFragment
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val key =
            requireNotNull(requireArguments().getString(SettingsFragment.PREF_DIALOG_KEY)) {
                "ControlPreferenceDialogFragment must have a 'key' argument leading to its preference"
            }
        preference = (targetFragment as PreferenceFragmentCompat).requirePreference(key)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.control_preference, null)

        setupAddBindingDialogs(view)
        setupRemoveControlEntries(view)

        return AlertDialog.Builder(requireContext()).create {
            setTitle(preference.title)
            setIcon(preference.icon)
            customView(view, paddingTop = 16.dp.toPx(context))
            negativeButton(R.string.dialog_cancel)
        }
    }

    private fun setupAddBindingDialogs(view: View) {
        view.findViewById<View>(R.id.add_gesture).apply {
            setOnClickListener {
                preference.showGesturePickerDialog()
                dismiss()
            }
            isVisible = preference.areGesturesEnabled
        }

        view.findViewById<View>(R.id.add_key).setOnClickListener {
            preference.showKeyPickerDialog()
            dismiss()
        }

        view.findViewById<View>(R.id.add_axis).setOnClickListener {
            preference.showAddAxisDialog()
            dismiss()
        }
    }

    private fun setupRemoveControlEntries(view: View) {
        val bindings = preference.getMappableBindings().toMutableList()
        val listView = view.findViewById<ListView>(R.id.list_view)
        if (bindings.isEmpty()) {
            listView.isVisible = false
            return
        }
        val titles =
            bindings.map {
                getString(R.string.binding_remove_binding, it.toDisplayString(requireContext()))
            }
        listView.apply {
            adapter = ArrayAdapter(requireContext(), R.layout.control_preference_list_item, titles)
            setOnItemClickListener { _, _, index, _ ->
                bindings.removeAt(index)
                preference.value = bindings.toPreferenceString()
                dismiss()
            }
        }
    }
}
