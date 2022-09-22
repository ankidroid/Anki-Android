//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.multimediacard.fields

/**
 * Implementation of Audio Recording field type
 */

class AudioRecordingField : AudioField() {
    override val type: EFieldType = EFieldType.AUDIO_RECORDING

    override val isModified: Boolean
        get() = thisModified

    override fun setHasTemporaryMedia(hasTemporaryMedia: Boolean) {
        currentHasTemporaryMedia = hasTemporaryMedia
    }

    override fun hasTemporaryMedia(): Boolean {
        return currentHasTemporaryMedia
    }

    override var name: String?
        get() = currentName
        set(value) {
            currentName = value
        }

    companion object {
        private const val serialVersionUID = 5033819217738174719L
    }
}
