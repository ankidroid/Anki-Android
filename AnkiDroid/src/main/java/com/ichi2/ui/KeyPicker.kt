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

package com.ichi2.ui

import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import com.ichi2.anki.R
import com.ichi2.anki.reviewer.Binding
import timber.log.Timber

typealias KeyCode = Int

/**
 * Square dialog which allows a user to select a [Binding] for a key press
 * This does not yet support bluetooth headsets.
 */
class KeyPicker(context: Context) : FixedTextView(context) {
    private var mBinding: Binding? = null
    private var mOnBindingChangedListener: ((Binding) -> Unit)? = null
    private var mIsValidKeyCode: ((KeyCode) -> Boolean)? = null

    fun getBinding(): Binding? = mBinding

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return true

        // When accepting a keypress, we only want to find the keycode, not the unicode character.
        val isValidKeyCode = mIsValidKeyCode
        val maybeBinding = Binding.key(event).stream().filter { x -> x.isKeyCode && (isValidKeyCode == null || isValidKeyCode(x.getKeycode()!!)) }.findFirst()
        if (!maybeBinding.isPresent) {
            return true
        }

        val newBinding = maybeBinding.get()
        Timber.d("Changed key to '%s'", newBinding)
        mBinding = newBinding
        text = newBinding.toDisplayString(context)
        mOnBindingChangedListener?.invoke(newBinding)
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // enforce height == width
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }

    fun setBindingChangedListener(listener: (Binding) -> Unit) {
        mOnBindingChangedListener = listener
    }

    fun setKeycodeValidation(validation: (KeyCode) -> Boolean) {
        mIsValidKeyCode = validation
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        text = context.getString(R.string.key_picker_default_press_key)
        textSize = 24f
        textAlignment = TEXT_ALIGNMENT_CENTER
        gravity = Gravity.CENTER
        requestFocus()
    }
}
