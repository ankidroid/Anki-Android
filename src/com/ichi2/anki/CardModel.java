/****************************************************************************************
* Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
* Copyright (c) 2010 Rick Gruber-Riemer <rick@vanosten.net>                            *
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

import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeMap;

import android.database.Cursor;

/**
 * Card model.
 * 
 * Card models are used to make question/answer pairs for the information you add to facts.
 * You can display any number of fields on the question side and answer side.
 * 
 * @see http://ichi2.net/anki/wiki/ModelProperties#Card_Templates
 */
public class CardModel implements Comparator<CardModel> {

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
		this.id = Utils.genID();
	}

	/**
	 * Constructor.
	 */
	public CardModel() {
		this("", "q", "a", true);
	}

	/** SELECT string with only those fields, which are used in AnkiDroid */
	private final static String SELECT_STRING = "SELECT id, ordinal, modelId, name, description, active, qformat, aformat" //lformat left out
		//qedformat, aedformat, questionInAnswer left out
		+ ", questionFontSize, questionFontColour" //questionFontFamily, questionAlign left out
		+ ", answerFontSize, answerFontColour" //same as for question
		+ ", lastFontColour" //lastFontFamily, lastFontSize left out
		//rest left out
		+ " FROM cardModels";

	/**
	 * 
	 * @param modelId
	 * @param models will be changed by adding all found CardModels into it
	 * @return unordered CardModels which are related to a given Model and eventually active put into the parameter "models"
	 */
	protected static final void fromDb(long modelId, TreeMap<Long, CardModel> models) {
		Cursor cursor = null;
		CardModel myCardModel = null;
		try {
			StringBuffer query = new StringBuffer(SELECT_STRING);
			query.append(" WHERE modelId = ");
			query.append(modelId);
			
			cursor = AnkiDb.database.rawQuery(query.toString(), null);

			if (cursor.moveToFirst()) {
				do {
					myCardModel = new CardModel();
					
					myCardModel.id = cursor.getLong(0);
					myCardModel.ordinal = cursor.getInt(1);
					myCardModel.modelId = cursor.getLong(2);
					myCardModel.name = cursor.getString(3);
					myCardModel.description = cursor.getString(4);
					myCardModel.active = cursor.getInt(5);
					myCardModel.qformat = cursor.getString(6);
					myCardModel.aformat = cursor.getString(7);
					myCardModel.questionFontSize = cursor.getInt(8);
					myCardModel.questionFontColour = cursor.getString(9);
					myCardModel.answerFontSize = cursor.getInt(10);
					myCardModel.answerFontColour = cursor.getString(11);
					myCardModel.lastFontColour = cursor.getString(12);
					models.put(myCardModel.id, myCardModel);
				} while (cursor.moveToNext());
			}
		} finally {
			if (cursor != null && !cursor.isClosed()) {
				cursor.close();
			}
		}
	}
	
	/**
	 * 
	 * @param cardModelId
	 * @return the modelId for a given cardModel or 0, if it cannot be found
	 */
	protected static final long modelIdFromDB(long cardModelId) {
		Cursor cursor = null;
		long modelId = -1;
		try {
			String query = "SELECT modelId FROM cardModels WHERE id = " + cardModelId;
			cursor = AnkiDb.database.rawQuery(query, null);
			cursor.moveToFirst();
			modelId = cursor.getLong(0);
		} finally {
			if (cursor != null && !cursor.isClosed()) {
				cursor.close();
			}
		}
		return modelId;
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
    
	/**
	 * Implements Comparator by comparing the field "ordinal".
	 * @param object1
	 * @param object2
	 * @return 
	 */
	public int compare(CardModel object1, CardModel object2) {
		return object1.ordinal - object2.ordinal;
	}
}
