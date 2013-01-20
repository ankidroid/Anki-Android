package com.ichi2.anki.multimediacard.impl;

import com.ichi2.anki.multimediacard.EFieldType;
import com.ichi2.anki.multimediacard.IField;

public class ImageField extends FieldBase implements IField
{

    /**
	 * 
	 */
    private static final long serialVersionUID = 4431611060655809687L;
    String mImagePath = null;
    private boolean mHasTemporaryMedia = false;

    @Override
    public EFieldType getType()
    {
        return EFieldType.IMAGE;
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
        mImagePath = pathToImage;
        setThisModified();
        return true;
    }

    @Override
    public String getImagePath()
    {
        return mImagePath;
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
        mHasTemporaryMedia = hasTemporaryMedia;
    }

    @Override
    public boolean hasTemporaryMedia()
    {
        return mHasTemporaryMedia;
    }
}
