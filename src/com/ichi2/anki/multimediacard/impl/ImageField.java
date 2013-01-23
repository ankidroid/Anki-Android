package com.ichi2.anki.multimediacard.impl;

import java.io.File;

import com.ichi2.anki.multimediacard.EFieldType;
import com.ichi2.anki.multimediacard.IField;

/**
 * @author zaur
 * 
 *         Field with an image.
 * 
 */
public class ImageField extends FieldBase implements IField
{
	private static final long serialVersionUID = 4431611060655809687L;
	String mImagePath;
	private boolean mHasTemporaryMedia = false;
	private String mName;

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

	@Override
	public String getName()
	{
		return mName;
	}

	@Override
	public void setName(String name)
	{
		mName = name;
	}

	@Override
	public String getFormattedValue()
	{
		File file = new File(getImagePath());
		if (file.exists())
		{
			return String.format("<img src='%s'/>", file.getName());
		}
		else
		{
			return "";
		}
	}
}
