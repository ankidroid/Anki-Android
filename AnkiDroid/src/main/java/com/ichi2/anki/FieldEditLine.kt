/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.ClassLoaderCreator
import android.util.AttributeSet
import android.util.SparseArray
import android.view.AbsSavedState
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.ichi2.anki.UIUtils.getDensityAdjustedValue
import com.ichi2.ui.AnimationUtil.collapseView
import com.ichi2.ui.AnimationUtil.expandView
import com.ichi2.utils.KotlinCleanup
import java.util.*

@KotlinCleanup("Fix IDE lint issues")
@KotlinCleanup("see which class properties could be maybe lateinit")
class FieldEditLine : FrameLayout {
    var editText: FieldEditText? = null
        private set
    private var mLabel: TextView? = null
    var toggleSticky: ImageButton? = null
        private set
    var mediaButton: ImageButton? = null
        private set
    private var mExpandButton: ImageButton? = null
    private var mName: String? = null
    private var mExpansionState: ExpansionState? = null
    private var mEnableAnimation = true

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init()
    }

    private fun init() {
        LayoutInflater.from(context).inflate(R.layout.card_multimedia_editline, this, true)
        editText = findViewById(R.id.id_note_editText)
        mLabel = findViewById(R.id.id_label)
        toggleSticky = findViewById(R.id.id_toggle_sticky_button)
        mediaButton = findViewById(R.id.id_media_button)
        val constraintLayout: ConstraintLayout = findViewById(R.id.constraint_layout)
        mExpandButton = findViewById(R.id.id_expand_button)
        // 7433 -
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            editText!!.setId(ViewCompat.generateViewId())
            toggleSticky!!.setId(ViewCompat.generateViewId())
            mediaButton!!.setId(ViewCompat.generateViewId())
            mExpandButton!!.setId(ViewCompat.generateViewId())
            editText!!.setNextFocusForwardId(toggleSticky!!.getId())
            toggleSticky!!.setNextFocusForwardId(mediaButton!!.getId())
            mediaButton!!.setNextFocusForwardId(mExpandButton!!.getId())
            val constraintSet = ConstraintSet()
            @KotlinCleanup("use a scope function")
            constraintSet.clone(constraintLayout)
            constraintSet.connect(toggleSticky!!.getId(), ConstraintSet.END, mediaButton!!.getId(), ConstraintSet.START)
            constraintSet.connect(mediaButton!!.getId(), ConstraintSet.END, mExpandButton!!.getId(), ConstraintSet.START)
            constraintSet.applyTo(constraintLayout)
        }
        mExpansionState = ExpansionState.EXPANDED
        setExpanderBackgroundImage()
        @KotlinCleanup("use a lambda for OnClickListener")
        mExpandButton!!.setOnClickListener(OnClickListener { _: View? -> toggleExpansionState() })
        editText!!.init()
        mLabel!!.setPadding(getDensityAdjustedValue(context, 3.4f).toInt(), 0, 0, 0)
    }

    private fun toggleExpansionState() {
        when (mExpansionState) {
            ExpansionState.EXPANDED -> {
                collapseView(editText!!, mEnableAnimation)
                mExpansionState = ExpansionState.COLLAPSED
            }
            ExpansionState.COLLAPSED -> {
                expandView(editText!!, mEnableAnimation)
                mExpansionState = ExpansionState.EXPANDED
            }
            else -> {}
        }
        setExpanderBackgroundImage()
    }

    @KotlinCleanup("if mExpansionState can be lateinit remove the else clause")
    private fun setExpanderBackgroundImage() {
        when (mExpansionState) {
            ExpansionState.COLLAPSED -> mExpandButton!!.background = getBackgroundImage(R.drawable.ic_expand_more_black_24dp_xml)
            ExpansionState.EXPANDED -> mExpandButton!!.background = getBackgroundImage(R.drawable.ic_expand_less_black_24dp)
            else -> {
                // ignored, see method @KotlinCleanup
            }
        }
    }

    private fun getBackgroundImage(@DrawableRes idRes: Int): Drawable? {
        return VectorDrawableCompat.create(this.resources, idRes, context.theme)
    }

    fun setActionModeCallbacks(callback: ActionMode.Callback?) {
        editText!!.customSelectionActionModeCallback = callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            editText!!.customInsertionActionModeCallback = callback
        }
    }

    fun setTypeface(typeface: Typeface?) {
        if (typeface != null) {
            editText!!.typeface = typeface
        }
    }

    fun setHintLocale(hintLocale: Locale?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && hintLocale != null) {
            editText!!.setHintLocale(hintLocale)
        }
    }

    fun setContent(content: String?, replaceNewline: Boolean) {
        editText!!.setContent(content, replaceNewline)
    }

    fun setOrd(i: Int) {
        editText!!.ord = i
    }

    fun setEnableAnimation(value: Boolean) {
        mEnableAnimation = value
    }

    var name: String?
        get() = mName
        set(name) {
            mName = name
            editText!!.contentDescription = name
            mLabel!!.text = name
        }

    val lastViewInTabOrder: View?
        get() = mExpandButton

    fun loadState(state: AbsSavedState) {
        onRestoreInstanceState(state)
    }

    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>) {
        dispatchFreezeSelfOnly(container)
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>) {
        dispatchThawSelfOnly(container)
    }

    @Suppress("RedundantNullableReturnType") // keeps the platform defined nullability
    public override fun onSaveInstanceState(): Parcelable? {
        val state = super.onSaveInstanceState()
        val savedState = SavedState(state)
        @KotlinCleanup("use a scope function")
        savedState.mChildrenStates = SparseArray()
        savedState.mEditTextId = editText!!.id
        savedState.mToggleStickyId = toggleSticky!!.id
        savedState.mMediaButtonId = mediaButton!!.id
        savedState.mExpandButtonId = mExpandButton!!.id
        for (i in 0 until childCount) {
            getChildAt(i).saveHierarchyState(savedState.mChildrenStates)
        }
        savedState.mExpansionState = mExpansionState
        return savedState
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        val ss = state
        val editTextId = editText!!.id
        val toggleStickyId = toggleSticky!!.id
        val mediaButtonId = mediaButton!!.id
        val expandButtonId = mExpandButton!!.id
        editText!!.id = ss.mEditTextId
        toggleSticky!!.id = ss.mToggleStickyId
        mediaButton!!.id = ss.mMediaButtonId
        mExpandButton!!.id = ss.mExpandButtonId
        super.onRestoreInstanceState(ss.superState)
        for (i in 0 until childCount) {
            getChildAt(i).restoreHierarchyState(ss.mChildrenStates)
        }
        editText!!.id = editTextId
        toggleSticky!!.id = toggleStickyId
        mediaButton!!.id = mediaButtonId
        mExpandButton!!.id = expandButtonId
        if (mExpansionState != ss.mExpansionState) {
            toggleExpansionState()
        }
        mExpansionState = ss.mExpansionState
    }

    internal class SavedState : BaseSavedState {
        var mChildrenStates: SparseArray<Parcelable>? = null
        var mEditTextId = 0
        var mToggleStickyId = 0
        var mMediaButtonId = 0
        var mExpandButtonId = 0
        var mExpansionState: ExpansionState? = null

        constructor(superState: Parcelable?) : super(superState) {}

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeSparseArray(mChildrenStates)
            out.writeInt(mEditTextId)
            out.writeInt(mToggleStickyId)
            out.writeInt(mMediaButtonId)
            out.writeInt(mExpandButtonId)
            out.writeSerializable(mExpansionState)
        }

        private constructor(`in`: Parcel, loader: ClassLoader) : super(`in`) {
            @KotlinCleanup("fix usage of kotlin keyword from java code")
            mChildrenStates = `in`.readSparseArray(loader)
            mEditTextId = `in`.readInt()
            mToggleStickyId = `in`.readInt()
            mMediaButtonId = `in`.readInt()
            mExpandButtonId = `in`.readInt()
            mExpansionState = `in`.readSerializable() as ExpansionState?
        }

        companion object {
            // required field that makes Parcelables from a Parcel
            @JvmField val CREATOR: Parcelable.Creator<SavedState> = object : ClassLoaderCreator<SavedState> {
                @KotlinCleanup("fix usage of kotlin keyword from java code")
                override fun createFromParcel(`in`: Parcel, loader: ClassLoader): SavedState {
                    return SavedState(`in`, loader)
                }

                override fun createFromParcel(source: Parcel): SavedState {
                    throw IllegalStateException()
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    enum class ExpansionState {
        EXPANDED, COLLAPSED
    }
}
