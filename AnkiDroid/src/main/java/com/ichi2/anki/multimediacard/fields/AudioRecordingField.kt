//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.multimediacard.fields

/**
 * Implementation of Audio Recording field type
 */

class AudioRecordingField : AudioField() {
    override fun getType(): EFieldType {
        return EFieldType.AUDIO_RECORDING
    }

    override fun isModified(): Boolean {
        return thisModified
    }

    override fun setHasTemporaryMedia(hasTemporaryMedia: Boolean) {
        currentHasTemporaryMedia = hasTemporaryMedia
    }

    override fun hasTemporaryMedia(): Boolean {
        return currentHasTemporaryMedia
    }

    override fun getName(): String? {
        return currentName
    }

    override fun setName(name: String) {
        currentName = name
    }

    companion object {
        private const val serialVersionUID = 5033819217738174719L
    }
}
