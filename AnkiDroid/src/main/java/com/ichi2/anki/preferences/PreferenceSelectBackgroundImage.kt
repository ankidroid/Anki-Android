package com.ichi2.anki.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.ichi2.anki.R

interface PreferenceSelectBackgroundImageListener {
    fun onImageSelectClicked()
    fun onImageRemoveClicked()
}

class PreferenceSelectBackgroundImage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int = 0
) : Preference(context, attrs, defStyleAttr) {

    private var preferenceSelectBackgroundImageListener: PreferenceSelectBackgroundImageListener? = null

    init {
        widgetLayoutResource = R.layout.preference_deck_picker_background
    }

    fun preferenceSelectBackgroundImageListener(listener: PreferenceSelectBackgroundImageListener) {
        preferenceSelectBackgroundImageListener = listener
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.findViewById(R.id.deck_picker_background_image_selector).setOnClickListener {
            preferenceSelectBackgroundImageListener?.onImageSelectClicked()
        }

        holder.findViewById(R.id.deck_picker_background_image_remover).setOnClickListener {
            preferenceSelectBackgroundImageListener?.onImageRemoveClicked()
        }
    }
}
