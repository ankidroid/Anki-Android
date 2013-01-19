package com.ichi2.anki.multimediacard.impl;

import com.ichi2.anki.multimediacard.EFieldType;
import com.ichi2.anki.multimediacard.IField;

public class AudioField extends FieldBase implements IField
{

    /**
	 * 
	 */
    private static final long serialVersionUID = 5033819217738174719L;
    String mAudioPath = null;

    @Override
    public EFieldType getType()
    {
        return EFieldType.AUDIO;
    }

    @Override
    public boolean setType(EFieldType type)
    {
        return false;
    }

    @Override
    public boolean isModified()
    {
        return getThisModified();
    }

    @Override
    public String getHtml()
    {
        return null;
    }

    @Override
    public boolean setHtml(String html)
    {
        return false;
    }

    @Override
    public boolean setImagePath(String pathToImage)
    {
        return false;
    }

    @Override
    public String getImagePath()
    {
        return null;
    }

    @Override
    public boolean setAudioPath(String pathToAudio)
    {
        mAudioPath = pathToAudio;
        setThisModified();
        return true;
    }

    @Override
    public String getAudioPath()
    {
        return mAudioPath;
    }

    @Override
    public String getText()
    {
        return null;
    }

    @Override
    public boolean setText(String text)
    {
        return false;
    }

    @Override
    public void setHasTemporaryMedia(boolean hasTemporaryMedia)
    {
    }

    @Override
    public boolean hasTemporaryMedia()
    {
        // TODO Auto-generated method stub
        return false;
    }
}
