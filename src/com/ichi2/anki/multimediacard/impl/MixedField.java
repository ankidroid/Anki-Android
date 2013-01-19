package com.ichi2.anki.multimediacard.impl;

import com.ichi2.anki.multimediacard.EFieldType;
import com.ichi2.anki.multimediacard.IField;

public class MixedField extends FieldBase implements IField
{

    /**
	 * 
	 */
    private static final long serialVersionUID = 410929010345486137L;
    String mHtml = null;

    @Override
    public EFieldType getType()
    {
        return EFieldType.MIXED;
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
        return mHtml;
    }

    @Override
    public boolean setHtml(String html)
    {
        mHtml = html;
        setThisModified();
        return true;
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
        return false;
    }

    @Override
    public String getAudioPath()
    {
        return null;
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
