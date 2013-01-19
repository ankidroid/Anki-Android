package com.ichi2.anki.multimediacard.impl;

import com.ichi2.anki.multimediacard.IMultimediaEditableNote;

public class MockNoteFactory {
	public static IMultimediaEditableNote makeNote()
	{
		MultimediaEditableNote note = new MultimediaEditableNote();
		note.setNumFields(3);
		
		TextField tf = new TextField();
		tf.setText("Hello world");
		note.setField(0, tf);
		
		TextField tf2 = new TextField();
		tf2.setText("Hallo Welt");
		note.setField(1, tf2);
		
		ImageField imageField = new ImageField();
		imageField.setImagePath("/mnt/sdcard/img/1.jpg");
		note.setField(2, imageField);
		
		
		return note;
	}
}
