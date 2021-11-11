//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki.multimediacard.fields

/**
 * Implementation of Audio Clip field type
 */
class AudioClipField : AudioField() {
    override fun getType(): EFieldType {
        return EFieldType.AUDIO_CLIP
    }

    override fun isModified(): Boolean {
        return false
    }

    override fun setHasTemporaryMedia(hasTemporaryMedia: Boolean) {
        mHasTemporaryMedia = hasTemporaryMedia
    }

    override fun hasTemporaryMedia(): Boolean {
        return false
    }

    override fun getName(): String? {
        return null
    }

    override fun setName(name: String) {
        // does nothing? FIXME investigate this
    }

    companion object {
        private const val serialVersionUID = 2937641017832762987L
    }
}
