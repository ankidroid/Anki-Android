package com.ichi2.anki.multimediacard;

import java.io.Serializable;

/**
 * @author zaur
 * 
 *         General interface for a field of any type.
 */
public interface IField extends Serializable
{
	EFieldType getType();

	boolean setType(EFieldType type);

	boolean isModified();

	// For mixed type
	String getHtml();

	boolean setHtml(String html);

	// For image type. Resets type.
	// Makes no sense to call when type is not image.
	// the same for other groups below.
	boolean setImagePath(String pathToImage);

	String getImagePath();

	// For Audio type
	boolean setAudioPath(String pathToAudio);

	String getAudioPath();

	// For Text type
	String getText();

	boolean setText(String text);

	/**
	 * Mark if the current media path is temporary and if it should be deleted
	 * once the media has been processed.
	 * 
	 * @param hasTemporaryMedia
	 *            True if the media is temporary, False if it is existing media.
	 * @return
	 */
	public void setHasTemporaryMedia(boolean hasTemporaryMedia);

	public boolean hasTemporaryMedia();

	public String getName();

	public void setName(String name);

	/**
	 * Returns the formatted value for this field.
	 * 
	 * Each implementation of IField should return in a format which will be
	 * used to store in the database
	 * 
	 * @return
	 */
	public String getFormattedValue();
}
