/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
import android.util.AttributeSet
import com.ichi2.anki.cardviewer.GestureProcessor
import com.ichi2.anki.dialogs.CardSideSelectionDialog
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding

class ReviewerControlPreference : ControlPreference {
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

    /** The action associated to this preference */
    private val viewerAction get() = ViewerAction.fromPreferenceKey(key)

    override val areGesturesEnabled: Boolean
        get() = sharedPreferences?.getBoolean(GestureProcessor.PREF_KEY, false) ?: false

    override fun getMappableBindings(): List<ReviewerBinding> = ReviewerBinding.fromPreferenceString(value).toList()

    override fun onKeySelected(binding: Binding) {
        selectSide { side ->
            addBinding(binding, side)
        }
    }

    override fun onGestureSelected(binding: Binding) {
        selectSide { side ->
            addBinding(binding, side)
        }
    }

    override fun onAxisSelected(binding: Binding) {
        selectSide { side ->
            addBinding(binding, side)
        }
    }

    private fun addBinding(
        binding: Binding,
        side: CardSide,
    ) {
        val bindings = ReviewerBinding.fromPreferenceString(value).toMutableList()
        // remove duplicate bindings
        bindings.firstOrNull { it.binding == binding }?.let {
            bindings.remove(it)
        }
        val newBinding = ReviewerBinding(binding, side)
        getPreferenceAssignedTo(binding)?.removeMappableBinding(newBinding)
        bindings.add(newBinding)
        value = bindings.toPreferenceString()
    }

    /**
     * If this command can be executed on a single side, execute the callback on this side.
     * Otherwise, ask the user to select one or two side(s) and execute the callback on them.
     */
    private fun selectSide(callback: (c: CardSide) -> Unit) {
        if (viewerAction == ViewerAction.SHOW_ANSWER) {
            callback(CardSide.QUESTION)
        } else {
            CardSideSelectionDialog.displayInstance(context, callback)
        }
    }
}
