/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.ui

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.appcompat.widget.AppCompatCheckBox
import com.ichi2.anki.R
import com.ichi2.utils.KotlinCleanup

/**
 * Based on https://gist.github.com/kevin-barrientos/d75a5baa13a686367d45d17aaec7f030.
 */
@KotlinCleanup("IDE-based lint")
class CheckBoxTriStates : AppCompatCheckBox {
    enum class State {
        INDETERMINATE, UNCHECKED, CHECKED
    }

    private var mState: State = State.UNCHECKED
    @KotlinCleanup("move setter function here")
    private var mCycleBackToIndeterminate = false
    private var mCycleIndeterminateToChecked = false

    /**
     * This is the listener set to the super class which is going to be invoked each
     * time the check state has changed.
     */
    private val mPrivateListener = OnCheckedChangeListener { _, _ ->
        // checkbox status is changed from unchecked to checked.
        toggle()
    }

    /**
     * Holds a reference to the listener set by a client, if any.
     */
    private var mClientListener: OnCheckedChangeListener? = null

    /**
     * This flag is needed to avoid accidentally changing the current [mState] when
     * [onRestoreInstanceState] calls [setChecked]
     * invoking our [mPrivateListener] and therefore changing the real state.
     */
    private var mRestoring = false

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    @KotlinCleanup("use ?.let")
    var state: State
        get() = mState
        set(state) {
            if (!mRestoring && mState != state) {
                mState = state
                if (mClientListener != null) {
                    mClientListener!!.onCheckedChanged(this, this.isChecked)
                }
                updateBtn()
            }
        }

    override fun toggle() {
        when (mState) {
            State.INDETERMINATE -> state = if (mCycleIndeterminateToChecked) {
                State.CHECKED
            } else {
                State.UNCHECKED
            }
            State.UNCHECKED -> state = State.CHECKED
            State.CHECKED -> state = if (mCycleBackToIndeterminate) {
                State.INDETERMINATE
            } else {
                State.UNCHECKED
            }
        }
    }

    @KotlinCleanup("Should be according to code style.")
    override fun setChecked(checked: Boolean) {
        mState = if (checked) State.CHECKED else State.UNCHECKED
    }

    override fun isChecked(): Boolean {
        return mState != State.UNCHECKED
    }

    fun setCycleBackToIndeterminate(cycleBackToIndeterminate: Boolean) {
        mCycleBackToIndeterminate = cycleBackToIndeterminate
    }

    fun setCycleIndeterminateToChecked(cycleIndeterminateToChecked: Boolean) {
        mCycleIndeterminateToChecked = cycleIndeterminateToChecked
    }

    override fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {

        // we never truly set the listener to the client implementation, instead we only hold
        // a reference to it and invoke it when needed.
        if (mPrivateListener !== listener) {
            mClientListener = listener
        }

        // always use our implementation
        super.setOnCheckedChangeListener(mPrivateListener)
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val ss = SavedState(superState)
        ss.state = mState
        ss.cycleBackToIndeterminate = mCycleBackToIndeterminate
        return ss
    }

    @KotlinCleanup("fix 'ss' variable name")
    override fun onRestoreInstanceState(state: Parcelable) {
        mRestoring = true // indicates that the ui is restoring its state
        val ss = state as SavedState
        super.onRestoreInstanceState(ss.superState)
        this.state = ss.state
        setCycleBackToIndeterminate(ss.cycleBackToIndeterminate)
        requestLayout()
        mRestoring = false
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        mCycleBackToIndeterminate = true
        if (attrs != null) {
            val a = context.theme.obtainStyledAttributes(
                attrs, R.styleable.CheckBoxTriStates, 0, 0
            )
            mCycleBackToIndeterminate = a.getBoolean(
                R.styleable.CheckBoxTriStates_cycle_back_to_indeterminate,
                mCycleBackToIndeterminate
            )
        }
        updateBtn()
        setOnCheckedChangeListener(mPrivateListener)
    }

    private fun updateBtn() {
        val btnDrawable: Int
        btnDrawable = when (mState) {
            State.UNCHECKED -> R.drawable.ic_baseline_check_box_outline_blank_24
            State.CHECKED -> R.drawable.ic_baseline_check_box_24
            else -> R.drawable.ic_baseline_indeterminate_check_box_24
        }
        setButtonDrawable(btnDrawable)
    }

    @KotlinCleanup("https://stackoverflow.com/a/69476454")
    private class SavedState : BaseSavedState {
        lateinit var state: State
        var cycleBackToIndeterminate = false

        internal constructor(superState: Parcelable?) : super(superState) {}
        private constructor(`in`: Parcel) : super(`in`) {
            state = State.values()[`in`.readInt()]
            cycleBackToIndeterminate = `in`.readInt() != 0
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeValue(state)
            out.writeInt(if (cycleBackToIndeterminate) 1 else 0)
        }

        override fun toString(): String {
            return (
                "CheckboxTriState.SavedState{" +
                    Integer.toHexString(System.identityHashCode(this)) +
                    " state=" + state +
                    " cycleBackToIndeterminate=" + cycleBackToIndeterminate + "}"
                )
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(`in`: Parcel): SavedState {
                    return SavedState(`in`)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }
}
