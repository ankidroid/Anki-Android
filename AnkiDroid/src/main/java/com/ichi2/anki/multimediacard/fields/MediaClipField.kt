//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki.multimediacard.fields

/**
 * Implementation of Media Clip field type
 */
class MediaClipField : AudioField() {
    override fun getType(): EFieldType {
        return EFieldType.MEDIA_CLIP
    }

    override fun isModified(): Boolean {
        return false
    }

    override fun setHasTemporaryMedia(hasTemporaryMedia: Boolean) {
        currentHasTemporaryMedia = hasTemporaryMedia
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
