package com.ichi2.anki;

import com.ichi2.anki.multimediacard.IField;

public interface IControllerFactory {

	IFieldController createControllerForField(IField field);
	
}
