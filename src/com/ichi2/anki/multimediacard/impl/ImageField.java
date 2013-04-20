package com.ichi2.anki.multimediacard.impl;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.multimediacard.EFieldType;
import com.ichi2.anki.multimediacard.IField;
import com.ichi2.libanki.Collection;

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

	private static final String PATH_REGEX = "<img.*src=[\"'](.*)[\"'].*/?>";

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

	@Override
	public void setFormattedString(String value)
	{
		Pattern p = Pattern.compile(PATH_REGEX);
		Matcher m = p.matcher(value);
		String res = "";
		if (m.find())
		{
			res = m.group(1);
		}
		Collection col = AnkiDroidApp.getCol();
		String mediaDir = col.getMedia().getDir() + "/";
		setImagePath(mediaDir + res);
	}
}
