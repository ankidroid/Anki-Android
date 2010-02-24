/****************************************************************************************
* Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
*                                                                                      *
* This program is free software; you can redistribute it and/or modify it under        *
* the terms of the GNU General Public License as published by the Free Software        *
* Foundation; either version 3 of the License, or (at your option) any later           *
* version.                                                                             *
*                                                                                      *
* This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
* PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
*                                                                                      *
* You should have received a copy of the GNU General Public License along with         *
* this program.  If not, see <http://www.gnu.org/licenses/>.                           *
****************************************************************************************/

package com.ichi2.anki;

import java.util.HashMap;

import android.database.Cursor;

/**
 * Card model.
 * See http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Models
 */
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

	/**
	 * Backward reference
	 */
	Model model;

	/**
	 * Constructor.
	 */
	public CardModel(String name, String qformat, String aformat, boolean active) {
		this.name = name;
		this.qformat = qformat;
		this.aformat = aformat;
		this.active = active ? 1 : 0;
		this.id = Util.genID();
	}

	/**
	 * Constructor.
	 */
	public CardModel() {
		this("", "q", "a", true);
	}

	public void fromDb(long id)
	{
		Cursor cursor = null;
		try {
		    cursor = AnkiDb.database.rawQuery(
	                "SELECT id, ordinal, modelId, name, description, qformat, " +
	                "aformat " +
	                "FROM cardModels " +
	                "WHERE id = " +
	                id,
	                null);
	
		    cursor.moveToFirst();
	        this.id = cursor.getLong(0);
	        this.ordinal = cursor.getInt(1);
	        this.modelId = cursor.getLong(2);
	        this.name = cursor.getString(3);
	        this.description = cursor.getString(4);
	        this.qformat = cursor.getString(5);
	        this.aformat = cursor.getString(6);
		} finally {
			if (cursor != null) cursor.close();
		}
	}
	
	/**
	 * Return a copy of this object.
	 */
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

	public static HashMap<String, String> formatQA(Fact fact, CardModel cm) {
	    
	    //Not pretty, I know.
	    String question = cm.qformat;
	    String answer = cm.aformat;
	    
	    int replaceAt = question.indexOf("%(");
	    while (replaceAt != -1)
	    {
	        question = replaceField(question, fact, replaceAt, true);
	        replaceAt = question.indexOf("%(");
	    }
	    
	    replaceAt = answer.indexOf("%(");
        while (replaceAt != -1)
        {
            answer = replaceField(answer, fact, replaceAt, true);
            replaceAt = answer.indexOf("%(");
        }
        
		HashMap<String,String> returnMap = new HashMap<String, String>();
		returnMap.put("question", question);
		returnMap.put("answer", answer);
		
		return returnMap;
	}

    private static String replaceField(String replaceFrom, Fact fact, int replaceAt, boolean isQuestion) {
        int endIndex = replaceFrom.indexOf(")", replaceAt);
        String fieldName =  replaceFrom.substring(replaceAt + 2, endIndex);
        char fieldType = replaceFrom.charAt(endIndex + 1);
        if (isQuestion)
        {
            String replace = "%(" + fieldName + ")" + fieldType;
            String with = "<span class=\"fm" + fact.getFieldModelId(fieldName) + "\">" +  fact.getFieldValue(fieldName) + "</span>";
            replaceFrom = replaceFrom.replace(replace, with);
        } 
        else
        {
            replaceFrom.replace("%(" + fieldName + ")" + fieldType, "<span class=\"fma" + fact.getFieldModelId(fieldName) + "\">" +  fact.getFieldValue(fieldName) + "</span");
        }
        return replaceFrom;
    }
}
