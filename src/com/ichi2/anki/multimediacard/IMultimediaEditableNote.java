package com.ichi2.anki.multimediacard;

import java.io.Serializable;

public interface IMultimediaEditableNote extends Serializable{

	int getNumberOfFields();
	
	IField getField(int index);
	boolean setField(int index, IField field);
	
	boolean isModified();
	
}
