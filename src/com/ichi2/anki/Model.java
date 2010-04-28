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

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import android.database.Cursor;

/**
 * Anki model.
 * A model describes the type of information you want to input, and the type of cards which should be generated.
 * See http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Models
 * There can be several models in a Deck.
 * A Model is related to a Deck via attribute deckId.
 * A CardModel is related to a Model via CardModel's modelId.
 * A FieldModel is related to a Model via FieldModel's modelId
 * A Card has a link to CardModel via Card's cardModelId
 * A Card has a link to a Fact via Card's factId
 * A Field has a link to a Fact via Field's factId
 * A Field has a link to a FieldModel via Field's fieldModelId
 * => In order to get the CardModel and all FieldModels for a given Card:
 *     % the CardModel can directly be retrieved from the DB using the Card's cardModelId
 *     % then from the retrieved CardModel we can get the modelId
 *     % using the modelId we can get all FieldModels from the DB
 *     % (alternatively in the CardModel the qformat and aformat fields could be parsed for relevant field names and 
 *     then this used to only get the necessary fields. But this adds a lot overhead vs. using a bit more memory)
 */
public class Model {
	
	/** Singleton */
	//private static Model currentModel;
	
	/**
	 * A Map of the currently loaded Models. The Models are loaded from the database as soon
	 * as they are needed for the first time. This is a compromise between RAM need, speed
	 * and the probability with which more than one Model is needed.
	 * If only one model is needed, then RAM consumption is basically the same as having a 
	 * static "currentModel" variable. If more than one Model is needed, then more RAM is
	 * needed, but on the other hand side Model and its related CardModel and FieldModel are not
	 * reloaded again and again.
	 * 
	 * This Map uses the Model.id field as key
	 */
	private static HashMap<Long,Model> models = new HashMap<Long, Model>();
	
	/**
	 * As above but mapping from CardModel to related Model (because when one has a Card, then 
	 * you need to jump from CardModel to Model.
	 */
	private static HashMap<Long,Model> cardModelToModelMap = new HashMap<Long, Model>();

	// TODO: Javadoc.
	// TODO: Methods for reading/writing from/to DB.

	// BEGIN SQL table entries
	long id; // Primary key
	long deckId; // Foreign key
	double created = System.currentTimeMillis() / 1000.0;
	double modified = System.currentTimeMillis() / 1000.0;
	String tags = "";
	String name;
	String description = "";
	String features = ""; // obsolete
	double spacing = 0.1;
	double initialSpacing = 60;
	int source = 0;
	// BEGIN SQL table entries

	
	/** Map for convenience and speed which contains CardModels from current model */
	private TreeMap<Long, CardModel> cardModelsMap = new TreeMap<Long, CardModel>();
	
	/** Map for convenience and speed which contains FieldModels from current model */
	private TreeMap<Long, FieldModel> fieldModelsMap = new TreeMap<Long, FieldModel>();
	
	/** Map for convenience and speed which contains the CSS code related to a CardModel */
	private HashMap<Long, String> cssCardModelMap = new HashMap<Long, String>();

	private Model(String name) {
		this.name = name;
		this.id = Utils.genID();
	}

	private Model() {
		this("");
	}

	public void setModified() {
		this.modified = System.currentTimeMillis() / 1000.0;
	}
	
	/**
	 * FIXME: this should be called whenever the deck is changed.
	 * Otherwise unnecessary space will be used.
	 */
	protected static final void reset() {
		models = new HashMap<Long, Model>();
		cardModelToModelMap = new HashMap<Long, Model>();
	}
	
	/**
	 * Returns a Model based on the submitted identifier.
	 * If a model id is submitted (isModelId = true), then the Model data and all related CardModel and FieldModel data are loaded,
	 * unless the id is the same as one of the currentModel.
	 * If a cardModel id is submitted, then the related Model data and all related CardModel and FieldModel data are loaded
	 * unless the cardModel id is already in the cardModel map.
	 * FIXME: nothing is done to treat db failure or non-existing identifiers
	 * @param identifier a cardModel id or a model id
	 * @param isModelId if true then the submitted identifier is a model id; otherwise the identifier is a cardModel id
	 * @return
	 */
	protected static Model getModel(long identifier, boolean isModelId) {
		if (false == isModelId) {
			//check whether the identifier is in the cardModelToModelMap
			if (false == cardModelToModelMap.containsKey(identifier)) {
				//get the modelId
				long myModelId = CardModel.modelIdFromDB(identifier);
				//get the model
				loadFromDBPlusRelatedModels(myModelId);
			}
			return cardModelToModelMap.get(identifier);
		}
		//else it is a modelId
		if (false == models.containsKey(identifier)) {
			//get the model
			loadFromDBPlusRelatedModels(identifier);
		}
		return models.get(identifier);
	}
	
	protected final CardModel getCardModel(long identifier) {
		return cardModelsMap.get(identifier);
	}
	
	/**
	 * Loads the Model from the database.
	 * then loads the related CardModels and FieldModels from the database.
	 * @param modelId
	 */
	private static final void loadFromDBPlusRelatedModels(long modelId) {
		Model currentModel = fromDb(modelId);
		
		//load related card models
		CardModel.fromDb(currentModel.id, currentModel.cardModelsMap);
		
		//load related field models
		FieldModel.fromDb(modelId, currentModel.fieldModelsMap);
		
		//prepare CSS for each card model in stead of doing it again and again
		currentModel.prepareCSSForCardModels();
		
		//make relations to maps
		models.put(currentModel.id, currentModel);
		CardModel myCardModel = null;
		for (Map.Entry<Long, CardModel> entry : currentModel.cardModelsMap.entrySet()) {
			myCardModel = entry.getValue();
			cardModelToModelMap.put(myCardModel.id, currentModel);
		}
	}
	
	/**
	 * Loads a model from the database based on the id
	 * FIXME: nothing is done in case of db error or no returned row
	 * @param id
	 * @return
	 */
	private static final Model fromDb(long id) {
		Cursor cursor = null;
		Model model = null;
		try {
			StringBuffer query = new StringBuffer();
			query.append("SELECT id, deckId, created, modified, tags, name, description");
			query.append(", features, spacing, initialSpacing, source");
			query.append(" FROM models");
			query.append(" WHERE id = ").append(id);
			cursor = AnkiDb.database.rawQuery(query.toString(), null);

			cursor.moveToFirst();
			model = new Model();
			
			model.id = cursor.getLong(0); // Primary key
			model.deckId = cursor.getLong(1); // Foreign key
			model.created = cursor.getDouble(2);
			model.modified = cursor.getDouble(3);
			model.tags = cursor.getString(4);
			model.name = cursor.getString(5);
			model.description = cursor.getString(6);
			model.features = cursor.getString(7);
			model.spacing = cursor.getDouble(8);
			model.initialSpacing = cursor.getDouble(9);
			model.source = cursor.getInt(10);
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return model;
	}
	
	/**
	 * Prepares the CSS for all CardModels in this Model
	 */
	private final void prepareCSSForCardModels() {
		CardModel myCardModel = null;
		String cssString = null;
		for (Map.Entry<Long, CardModel> entry : cardModelsMap.entrySet()) {
			myCardModel = entry.getValue();
			cssString = createCSSForFontColorSize(myCardModel.id);
			this.cssCardModelMap.put(myCardModel.id, cssString);
		}
	}
	
	/**
	 * Returns a cached CSS for the font color and font size of a given CardModel taking into account the included fields
	 * @param myCardModelId
	 * @return the html contents surrounded by a css style which contains class styles for answer/question and fields
	 */
	protected final String getCSSForFontColorSize(long myCardModelId) {
		return this.cssCardModelMap.get(myCardModelId);
	}
	
	/**
	 * @param myCardModelId
	 * @return the html contents surrounded by a css style which contains class styles for answer/question and fields
	 */
	private final String createCSSForFontColorSize(long myCardModelId) {
		StringBuffer sb = new StringBuffer();
		sb.append("<style type=\"text/css\">\n");
		CardModel myCardModel = cardModelsMap.get(myCardModelId);
		
		int referenceFontSize = 20; //this is the default in Anki. Only used if the question font for some reason is not set
		if (0 < myCardModel.questionFontSize) {
			referenceFontSize = myCardModel.questionFontSize;
		}

		//body background
		if (null != myCardModel.lastFontColour && 0 < myCardModel.lastFontColour.trim().length()) {
			sb.append("body {background-color:").append(myCardModel.lastFontColour).append(";}\n");
		}
		//question font size and color
		sb.append(".").append(AnkiDroid.QUESTION_CLASS).append(" {\n");
		if (null != myCardModel.questionFontColour && 0 < myCardModel.questionFontColour.trim().length()) {
			sb.append("color:").append(myCardModel.questionFontColour).append(";\n");
		}
		sb.append("font-size:100%;\n");
		sb.append("}\n");
		//answer font size and color
		sb.append(".").append(AnkiDroid.ANSWER_CLASS).append(" {\n");
		if (null != myCardModel.answerFontColour && 0 < myCardModel.answerFontColour.trim().length()) {
			sb.append("color:").append(myCardModel.answerFontColour).append(";\n");
		}
		if (0 < myCardModel.answerFontSize) {
			sb.append(calculateRelativeFontSize(referenceFontSize, myCardModel.answerFontSize));
		}
		sb.append("}\n");
		//css for fields. Gets css for all fields no matter whether they actually are used in a given card model
		FieldModel myFieldModel = null;
		String hexId = null; //a FieldModel id in unsigned hexa code for the class attribute
		for (Map.Entry<Long, FieldModel> entry : fieldModelsMap.entrySet()) {
			myFieldModel = entry.getValue();
			hexId = "fm" + Long.toHexString(myFieldModel.id);
			sb.append(".").append(hexId).append(" {\n");
			if (null != myFieldModel.quizFontColour && 0 < myFieldModel.quizFontColour.trim().length()) {
				sb.append("color: ").append(myFieldModel.quizFontColour).append(";\n");
			}
			if (0 < myFieldModel.quizFontSize) {
				sb.append(calculateRelativeFontSize(referenceFontSize, myFieldModel.quizFontSize));
			}
			sb.append("}\n");
		}
		
		//finish
		sb.append("</style>");
		return sb.toString();
	}

	/**
	 * 
	 * @param reference
	 * @param current
	 * @return a css entry for relative font size as a percentage of the current in relation to the reference
	 */
	private final static String calculateRelativeFontSize(int reference, int current) {
		StringBuffer sb = new StringBuffer();
		sb.append("font-size:");
		sb.append((100 * current)/reference);
		sb.append("%;\n");
		return sb.toString();
	}

}
