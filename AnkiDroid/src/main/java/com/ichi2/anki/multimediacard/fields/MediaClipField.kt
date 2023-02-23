//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki.multimediacard.fields

/**
 * Implementation of Media Clip field type
 */
class MediaClipField : AudioField() {
    override val type: EFieldType = EFieldType.MEDIA_CLIP

    override val isModified: Boolean = false

    override var name: String? = null

    companion object {
        private const val serialVersionUID = 2937641017832762987L
    }
}
