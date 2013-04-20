package com.ichi2.anki.multimediacard;

import java.io.Serializable;

/**
 * @author zaur
 *
 *      INterface for a note, which multimedia card editor can process.
 */
public interface IMultimediaEditableNote extends Serializable{

	int getNumberOfFields();
	
	IField getField(int index);
	boolean setField(int index, IField field);
	
	boolean isModified();
	

        public void circularSwap();
	
}
