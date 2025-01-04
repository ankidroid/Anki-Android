//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki.multimediacard.fields

/**
 * Implementation of Media Clip field type
 */
class MediaClipField : AudioField() {
    override val type: EFieldType = EFieldType.MEDIA_CLIP

    override val isModified: Boolean = false

    override var name: String? = null
}
