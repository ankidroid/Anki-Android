package com.ichi2.anki.multimediacard.fields;

/**
 * Implementation of Audio Recording field type
 */
public class AudioRecordingField extends AudioField {
    private static final long serialVersionUID = 5033819217738174719L;


    @Override
    public EFieldType getType() {
        return EFieldType.AUDIO_RECORDING;
    }


    @Override
    public boolean isModified() {
        return getThisModified();
    }


    @Override
    public void setHasTemporaryMedia(boolean hasTemporaryMedia) {
        mHasTemporaryMedia = hasTemporaryMedia;
    }


    @Override
    public boolean hasTemporaryMedia() {
        return mHasTemporaryMedia;
    }


    @Override
    public String getName() {
        return mName;
    }


    @Override
    public void setName(String name) {
        mName = name;
    }

}

