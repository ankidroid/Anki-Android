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

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.AttributeSet
import android.view.KeyEvent.KEYCODE_VOLUME_DOWN
import android.view.KeyEvent.KEYCODE_VOLUME_UP
import androidx.preference.ListPreference
import androidx.preference.ListPreferenceDialogFragmentCompat
import androidx.preference.PreferenceCategory
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.dialogs.CardSideSelectionDialog
import com.ichi2.anki.dialogs.KeySelectionDialogBuilder
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import timber.log.Timber
import java.util.*
import java.util.stream.Collectors

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
    @Suppress("unused") constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    @Suppress("unused") constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    @Suppress("unused") constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    @Suppress("unused") constructor(context: Context?) : super(context)

    /**
     * Could be better: ListPreference.`value` was broken in converting to the preference support library
     * It no longer uses the preference value if not passed into the constructor
     */
    override fun getValue(): String {
        return AnkiDroidApp.getSharedPrefs(context).getString(key, "")!!
    }

    fun refreshEntries() {
        val entryTitles: MutableList<CharSequence> = ArrayList()
        val entryIndices: MutableList<Int> = ArrayList()
        // negative indices are "add"
        entryTitles.add(context.getString(R.string.binding_add_key))
        entryIndices.add(ADD_KEY_INDEX)
        // 0 and above are "delete" actions for already mapped preferences
        for ((i, binding) in MappableBinding.fromPreferenceString(value).withIndex()) {
            entryTitles.add(context.getString(R.string.binding_remove_binding, binding.toDisplayString(context)))
            entryIndices.add(i)
        }
        entries = entryTitles.toTypedArray()
        entryValues = entryIndices.map { it.toString() }.toTypedArray()
    }

    class View : ListPreferenceDialogFragmentCompat() {
        override fun onCreate(savedInstanceState: Bundle?) {
            // must be called before super
            refreshPreferenceEntities()
            super.onCreate(savedInstanceState)
        }

        private fun refreshPreferenceEntities() {
            Timber.d("refreshPreferenceEntities()")
            val pref = this.preference as ControlPreference
            pref.refreshEntries()
        }

        companion object {
            @JvmStatic
            fun newInstance(key: String): View {
                val fragment = View()
                val b = Bundle(1)
                b.putString(ARG_KEY, key)
                fragment.arguments = b
                return fragment
            }
        }
    }

    /** The summary that appears on the preference */
    override fun getSummary(): CharSequence =
        TextUtils.join(", ", MappableBinding.fromPreferenceString(value).map { it.toDisplayString(context) })

    /** Called when an element is selected in the ListView */
    override fun callChangeListener(newValue: Any?): Boolean {
        when (val index: Int = (newValue as String).toInt()) {
            ADD_KEY_INDEX -> {
                KeySelectionDialogBuilder(context)
                    .onBindingChanged { binding -> checkExistingBinding(MappableBinding(binding, MappableBinding.Screen.Reviewer(CardSide.BOTH))) }
                    // select a side, then add
                    .onBindingSubmitted { binding ->
                        CardSideSelectionDialog.displayInstance(context) { side -> addBinding(MappableBinding(binding, MappableBinding.Screen.Reviewer(side))) }
                    }
                    .disallowModifierKeys()
                    .show()
            }
            else -> {
                val bindings: MutableList<MappableBinding> = MappableBinding.fromPreferenceString(value)
                bindings.removeAt(index)
                value = bindings.toPreferenceString()
            }
        }
        // don't persist the value
        return false
    }

    /** Volume keys shouldn't be mapped until we remove the 'Volume' gestures */
    fun isVolumeKey(binding: Binding): Boolean {
        if (!binding.isKeyCode) return false
        val keycode = binding.getKeycode()
        return keycode == KEYCODE_VOLUME_DOWN || keycode == KEYCODE_VOLUME_UP
    }

    /** Displays a warning to the user if the provided binding couldn't be used */
    private fun checkExistingBinding(binding: MappableBinding) {
        val existingCommands = getExistingCommands(binding).toList()
        if (existingCommands.isEmpty()) return // no conflicts
        val commandNames = existingCommands.map { context.getString(it.resourceId) }
        val text = context.getString(R.string.bindings_already_bound, TextUtils.join(", ", commandNames))
        UIUtils.showThemedToast(context, text, true)
    }

    /** return all commands where the binding is already mapped excluding the current command */
    fun getExistingCommands(binding: MappableBinding): List<ViewerCommand> {
        // get a list of all bindings - tuple: (Command, List<MappableBinding>)
        return MappableBinding.allMappings(AnkiDroidApp.getSharedPrefs(context))
            // filter to the commands which have a binding matching this one
            .filter { x -> x.second.any { cmdBinding -> cmdBinding == binding } }
            // return the associated commands
            .map { x -> x.first }
            // except for the command that this preference refers to
            .filter { x -> x.preferenceKey != key }
    }

    private fun addBinding(binding: MappableBinding) {
        val bindings = MappableBinding.fromPreferenceString(value)
        // by removing the binding, we ensure it's now at the start of the list
        bindings.remove(binding)
        bindings.add(0, binding)
        value = bindings.toPreferenceString()
    }

    companion object {
        private const val ADD_KEY_INDEX = -1

        /** Attaches all possible [ControlPreference] elements to a given [PreferenceCategory] */
        @JvmStatic
        fun setup(cat: PreferenceCategory) {
            val commands = Arrays.stream(ViewerCommand.values()).collect(Collectors.toList())
            val context = cat.context
            commands.remove(ViewerCommand.COMMAND_NOTHING)
            for (c in commands) {
                val p = ControlPreference(context)
                p.setTitle(c.resourceId)
                p.key = c.preferenceKey
                p.setDefaultValue(c.defaultValue.toPreferenceString())
                cat.addPreference(p)
            }
        }
    }
}
