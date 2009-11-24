package com.ichi2.anki;

import java.util.HashMap;

public class CardModel {
	
	// TODO: Javadoc.
	// TODO: Methods for reading/writing from/to DB.
	
	// BEGIN SQL table columns
	long id; // Primary key
	int ordinal;
	long modelId; // Foreign key models.id
	String name;
	String description = "";
	int active = 1;
	// Formats: question/answer/last (not used)
	String qformat;
	String aformat;
	String lformat;
	// Question/answer editor format (not used yet)
	String qedformat;
	String aedformat;
	int questionInAnswer = 0;
	// Display
	String questionFontFamily = "Arial";
	int questionFontSize = 20;
	String questionFontColour = "#000000";
	int questionAlign = 0;
	String answerFontFamily = "Arial";
	int answerFontSize = 20;
	String answerFontColour = "#000000";
	int answerAlign = 0;
	// Not used
	String lastFontFamily = "Arial";
	int lastFontSize = 20;
	// Used as background colour
	String lastFontColour = "#FFFFFF";
	String editQuestionFontFamily = "";
	int editQuestionFontSize = 0;
	String editAnswerFontFamily = "";
	int editAnswerFontSize = 0;
	// Empty answer
	int allowEmptyAnswer = 1;
	String typeAnswer = "";
	// END SQL table entries
	
	// Backward reference
	Model model;
	
	public CardModel(String name, String qformat, String aformat, boolean active) {
		this.name = name;
		this.qformat = qformat;
		this.aformat = aformat;
		this.active = active ? 1 : 0;
		this.id = Util.genID();
	}
	
	public CardModel() {
		this("", "q", "a", true);
	}
	
	public CardModel copy() {
		CardModel cardModel = new CardModel(
				this.name,
				this.qformat,
				this.aformat,
				(this.active == 1) ? true : false);
		cardModel.ordinal = this.ordinal;
		cardModel.modelId = this.modelId;
		cardModel.description = this.description;
		cardModel.lformat = this.lformat;
		cardModel.qedformat = this.qedformat;
		cardModel.aedformat = this.aedformat;
		cardModel.questionInAnswer = this.questionInAnswer;
		cardModel.questionFontFamily = this.questionFontFamily;
		cardModel.questionFontSize = this.questionFontSize;
		cardModel.questionFontColour = this.questionFontColour;
		cardModel.questionAlign = this.questionAlign;
		cardModel.answerFontFamily = this.answerFontFamily;
		cardModel.answerFontSize = this.answerFontSize;
		cardModel.answerFontColour = this.answerFontColour;
		cardModel.answerAlign = this.answerAlign;
		cardModel.lastFontFamily = this.lastFontFamily;
		cardModel.lastFontSize = this.lastFontSize;
		cardModel.lastFontColour = this.lastFontColour;
		cardModel.editQuestionFontFamily = this.editQuestionFontFamily;
		cardModel.editQuestionFontSize = this.editQuestionFontSize;
		cardModel.editAnswerFontFamily = this.editAnswerFontFamily;
		cardModel.editAnswerFontSize = this.editAnswerFontSize;
		cardModel.allowEmptyAnswer = this.allowEmptyAnswer;
		cardModel.typeAnswer = this.typeAnswer;
		cardModel.model = null;
		
		return cardModel;
	}
	
	public static HashMap<String, String> formatQA(long cid, long mid, HashMap<String, HashMap<Long, String>> fact, String[] tags, CardModel cm) {
		// TODO: Port this method from models.py.
		return null;
	}
}
