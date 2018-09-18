package com.ichi2.anki.multimediacard.fields;

/**
 * Implementation of Audio Clip field type
 */
public class AudioClipField extends AudioField {
    private static final long serialVersionUID = 2937641017832762987L;


    @Override
    public EFieldType getType() {
        return EFieldType.AUDIO_CLIP;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public void setHasTemporaryMedia(boolean hasTemporaryMedia) {
        mHasTemporaryMedia = hasTemporaryMedia;
    }

    @Override
    public boolean hasTemporaryMedia() {
        return false;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void setName(String name) {
        // does nothing? FIXME investigate this
    }
}
