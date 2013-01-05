package com.ichi2.anki.multimediacard.impl;

public class FieldBase {
	boolean mIsModified = false;
	
	void setThisModified()
	{
		mIsModified = true;
	}
	
	boolean getThisModified()
	{
		return mIsModified;
	}
	
	
}
