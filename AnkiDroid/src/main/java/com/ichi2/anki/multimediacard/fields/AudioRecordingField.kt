//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.multimediacard.fields

/**
 * Implementation of Audio Recording field type
 */

class AudioRecordingField : AudioField() {
    override val type: EFieldType = EFieldType.AUDIO_RECORDING

    override val isModified: Boolean
        get() = thisModified

    override var name: String? = null

    companion object {
        private const val SERIAL_VERSION_UID = 5033819217738174719L
    }
}
