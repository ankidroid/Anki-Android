package com.ichi2.anki.multimediacard;

import java.io.Serializable;

public interface IField extends Serializable {
	EFieldType getType();
	boolean setType(EFieldType type);
	
	boolean isModified();
	
	//For mixed type
	String getHtml();
	boolean setHtml(String html);
	
	//For image type. Resets type.
	//Makes no sense to call when type is not image.
	//the same for other groups below.
	boolean setImagePath(String pathToImage);
	String getImagePath();
	
	//For Audio type
	boolean setAudioPath(String pathToAudio);
	String getAudioPath();
	
	//For Text type
	String getText();
	boolean setText(String text);
}
