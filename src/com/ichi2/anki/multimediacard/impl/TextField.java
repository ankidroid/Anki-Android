package com.ichi2.anki.multimediacard.impl;

import com.ichi2.anki.multimediacard.EFieldType;
import com.ichi2.anki.multimediacard.IField;

/**
 * @author zaur
 * 
 *         Text Field implementation.
 * 
 */
public class TextField extends FieldBase implements IField
{
	private static final long serialVersionUID = -6508967905716947525L;
	String mText = " - ";
	private String mName;

	@Override
	public EFieldType getType()
	{
		return EFieldType.TEXT;
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
		return mText;
	}

	@Override
	public boolean setText(String text)
	{
		mText = text;
		setThisModified();
		return true;
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
		return getText();
	}

	@Override
	public void setFormattedString(String value)
	{
		mText = value;
	}
}
