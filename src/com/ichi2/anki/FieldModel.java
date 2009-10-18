package com.ichi2.anki;

public class FieldModel {
	
	// BEGIN SQL table entries
	long id;
	int ordinal;
	long modelId;
	String name = "";
	String description = "";
	// Reused as RTL marker
	String features = "";
	int required = 1;
	int unique = 1;
	int numeric = 0;
	// Display
	String quizFontFamily;
	int quizFontSize;
	String quizFontColour;
	String editFontFamily;
	int editFontSize = 20;
	// END SQL table entries
	
	// Backward reference
	Model model;
	
	public FieldModel(String name, boolean required, boolean unique) {
		this.name = name;
		this.required = required ? 1 : 0;
		this.unique = unique ? 1 : 0;
		this.id = Util.genID();
	}
	
	public FieldModel() {
		this("", true, true);
	}
	
	public FieldModel copy() {
		FieldModel fieldModel = new FieldModel(
				this.name,
				(this.required == 1) ? true : false,
				(this.unique == 1) ? true : false);
		fieldModel.ordinal = this.ordinal;
		fieldModel.modelId = this.modelId;
		fieldModel.description = this.description;
		fieldModel.features = this.features;
		fieldModel.numeric = this.numeric;
		fieldModel.quizFontFamily = this.quizFontFamily;
		fieldModel.quizFontSize = this.quizFontSize;
		fieldModel.quizFontColour = this.quizFontColour;
		fieldModel.editFontFamily = this.editFontFamily;
		fieldModel.editFontSize = this.editFontSize;
		fieldModel.model = null;
		
		return fieldModel;
	}
}
