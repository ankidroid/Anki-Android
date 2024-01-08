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
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.os.ParcelCompat
import androidx.core.view.ViewCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.ichi2.anki.UIUtils.getDensityAdjustedValue
import com.ichi2.ui.AnimationUtil.collapseView
import com.ichi2.ui.AnimationUtil.expandView
import com.ichi2.utils.KotlinCleanup
import java.util.*

@KotlinCleanup("replace _name with `field`")
@KotlinCleanup("remove setTypeface")
@KotlinCleanup("replace setEnableAnimation with var")
class FieldEditLine : FrameLayout {
    val editText: FieldEditText
    private val label: TextView
    val toggleSticky: ImageButton
    val mediaButton: ImageButton
    private val expandButton: ImageButton
    private var _name: String? = null
    private var expansionState = ExpansionState.EXPANDED
    private var enableAnimation = true

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        LayoutInflater.from(context).inflate(R.layout.card_multimedia_editline, this, true)
        editText = findViewById(R.id.id_note_editText)
        label = findViewById(R.id.id_label)
        toggleSticky = findViewById(R.id.id_toggle_sticky_button)
        mediaButton = findViewById(R.id.id_media_button)
        val constraintLayout: ConstraintLayout = findViewById(R.id.constraint_layout)
        expandButton = findViewById(R.id.id_expand_button)
        // 7433 -
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            editText.id = ViewCompat.generateViewId()
            toggleSticky.id = ViewCompat.generateViewId()
            mediaButton.id = ViewCompat.generateViewId()
            expandButton.id = ViewCompat.generateViewId()
            editText.nextFocusForwardId = toggleSticky.id
            toggleSticky.nextFocusForwardId = mediaButton.id
            mediaButton.nextFocusForwardId = expandButton.id
            ConstraintSet().apply {
                clone(constraintLayout)
                connect(toggleSticky.id, ConstraintSet.END, mediaButton.id, ConstraintSet.START)
                connect(mediaButton.id, ConstraintSet.END, expandButton.id, ConstraintSet.START)
                applyTo(constraintLayout)
            }
        }
        setExpanderBackgroundImage()
        expandButton.setOnClickListener { toggleExpansionState() }
        editText.init()
        label.setPadding(getDensityAdjustedValue(context, 3.4f).toInt(), 0, 0, 0)
    }

    private fun toggleExpansionState() {
        expansionState = when (expansionState) {
            ExpansionState.EXPANDED -> {
                collapseView(editText, enableAnimation)
                ExpansionState.COLLAPSED
            }
            ExpansionState.COLLAPSED -> {
                expandView(editText, enableAnimation)
                ExpansionState.EXPANDED
            }
        }
        setExpanderBackgroundImage()
    }

    private fun setExpanderBackgroundImage() {
        when (expansionState) {
            ExpansionState.COLLAPSED -> expandButton.background = getBackgroundImage(R.drawable.ic_expand_more_black_24dp_xml)
            ExpansionState.EXPANDED -> expandButton.background = getBackgroundImage(R.drawable.ic_expand_less_black_24dp)
        }
    }

    private fun getBackgroundImage(@DrawableRes idRes: Int): Drawable? {
        return VectorDrawableCompat.create(this.resources, idRes, context.theme)
    }

    fun setActionModeCallbacks(callback: ActionMode.Callback?) {
        editText.customSelectionActionModeCallback = callback
        editText.customInsertionActionModeCallback = callback
    }

    fun setTypeface(typeface: Typeface?) {
        if (typeface != null) {
            editText.typeface = typeface
        }
    }

    fun setHintLocale(hintLocale: Locale?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && hintLocale != null) {
            editText.setHintLocale(hintLocale)
        }
    }

    fun setContent(content: String?, replaceNewline: Boolean) {
        editText.setContent(content, replaceNewline)
    }

    fun setOrd(i: Int) {
        editText.ord = i
    }

    fun setEnableAnimation(value: Boolean) {
        enableAnimation = value
    }

    var name: String?
        get() = _name
        set(name) {
            _name = name
            editText.contentDescription = name
            label.text = name
        }

    val lastViewInTabOrder: View
        get() = expandButton

    fun loadState(state: AbsSavedState) {
        onRestoreInstanceState(state)
    }

    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>) {
        dispatchFreezeSelfOnly(container)
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>) {
        dispatchThawSelfOnly(container)
    }

    // keeps platform defined nullability
    @Suppress("RedundantNullableReturnType")
    public override fun onSaveInstanceState(): Parcelable? {
        val state = super.onSaveInstanceState()
        val savedState = SavedState(state)
        savedState.childrenStates = SparseArray()
        savedState.editTextId = editText.id
        savedState.toggleStickyId = toggleSticky.id
        savedState.mediaButtonId = mediaButton.id
        savedState.expandButtonId = expandButton.id
        for (i in 0 until childCount) {
            getChildAt(i).saveHierarchyState(savedState.childrenStates)
        }
        savedState.expansionState = expansionState
        return savedState
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        val editTextId = editText.id
        val toggleStickyId = toggleSticky.id
        val mediaButtonId = mediaButton.id
        val expandButtonId = expandButton.id
        editText.id = state.editTextId
        toggleSticky.id = state.toggleStickyId
        mediaButton.id = state.mediaButtonId
        expandButton.id = state.expandButtonId
        super.onRestoreInstanceState(state.superState)
        for (i in 0 until childCount) {
            getChildAt(i).restoreHierarchyState(state.childrenStates)
        }
        editText.id = editTextId
        toggleSticky.id = toggleStickyId
        mediaButton.id = mediaButtonId
        expandButton.id = expandButtonId
        if (expansionState != state.expansionState) {
            toggleExpansionState()
        }
        expansionState = state.expansionState ?: ExpansionState.EXPANDED
    }

    @KotlinCleanup("convert to parcelable")
    internal class SavedState : BaseSavedState {
        var childrenStates: SparseArray<Parcelable>? = null
        var editTextId = 0
        var toggleStickyId = 0
        var mediaButtonId = 0
        var expandButtonId = 0
        var expansionState: ExpansionState? = null

        constructor(superState: Parcelable?) : super(superState)

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeSparseArray(childrenStates)
            out.writeInt(editTextId)
            out.writeInt(toggleStickyId)
            out.writeInt(mediaButtonId)
            out.writeInt(expandButtonId)
            out.writeSerializable(expansionState)
        }

        private constructor(source: Parcel, loader: ClassLoader) : super(source) {
            childrenStates = ParcelCompat.readSparseArray(source, loader, Parcelable::class.java)
            editTextId = source.readInt()
            toggleStickyId = source.readInt()
            mediaButtonId = source.readInt()
            expandButtonId = source.readInt()
            expansionState = ParcelCompat.readSerializable(
                source,
                ExpansionState::class.java.classLoader,
                ExpansionState::class.java
            )
        }

        companion object {
            @JvmField // required field that makes Parcelables from a Parcel
            @Suppress("unused")
            val CREATOR: Parcelable.Creator<SavedState> = object : ClassLoaderCreator<SavedState> {
                override fun createFromParcel(source: Parcel, loader: ClassLoader): SavedState {
                    return SavedState(source, loader)
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
