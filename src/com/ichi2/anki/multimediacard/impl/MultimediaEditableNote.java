package com.ichi2.anki.multimediacard.impl;

import java.util.ArrayList;

import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;

/**
 * @author zaur
 *
 *      Implementation of the editable note.
 *      
 *      Has to be translate to and from anki db format.
 */

public class MultimediaEditableNote implements IMultimediaEditableNote {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6161821367135636659L;
	boolean mIsModified = false;
	
	ArrayList<IField> mFields = null;
	
	void setThisModified()
	{
		mIsModified = true;
	}
	
	boolean getThisModified()
	{
		return mIsModified;
	}
	
	//package
	void setNumFields(int numberOfFields)
	{
		getFieldsPrivate().clear();
		for(int i =0; i < numberOfFields; ++i)
		{
			getFieldsPrivate().add(null);
		}
	}
	
	private ArrayList<IField> getFieldsPrivate()
	{
		if(mFields == null)
		{
			mFields = new ArrayList<IField>();
		}
		
		return mFields;
	}
	
	@Override
	public int getNumberOfFields() {
		return getFieldsPrivate().size();
	}

	@Override
	public IField getField(int index) {
		if(index >= 0 && index < getNumberOfFields())
		{
			return getFieldsPrivate().get(index);
		}
		return null;
	}

	@Override
	public boolean setField(int index, IField field) {
		if(index >= 0 && index < getNumberOfFields())
		{
			//If the same unchanged field is set.
			if(getField(index) == field)
			{
				if(field.isModified())
				{
					setThisModified();
				}
			}
			else
			{
				setThisModified();
			}
			
			getFieldsPrivate().set(index, field);
			
			return true;
		}
		return false;
	}

	@Override
	public boolean isModified() {
		return getThisModified();
	}

	
	
}
