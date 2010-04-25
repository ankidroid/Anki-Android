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

import java.util.TreeMap;
import java.util.TreeSet;

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
	private static Model currentModel;

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

	// BEGIN JOINed entries
	TreeSet<FieldModel> fieldModels; //FIXME: is this used at all?
	TreeSet<CardModel> cardModels; //FIXME: is this used at all?
	// END JOINed entries
	
	/** Map for convenience and speed which contains CardModels from current model */
	private TreeMap<Long, CardModel> cardModelsMap = new TreeMap<Long, CardModel>();
	
	/** Map for convenience and speed which contains FieldModels from current model */
	private TreeMap<Long, FieldModel> fieldModelsMap = new TreeMap<Long, FieldModel>();

	private Model(String name) {
		this.fieldModels = new TreeSet<FieldModel>();
		this.cardModels = new TreeSet<CardModel>();
		this.name = name;
		this.id = Utils.genID();
	}

	private Model() {
		this("");
	}

	public void setModified() {
		this.modified = System.currentTimeMillis() / 1000.0;
	}

	public void addFieldModel(FieldModel field) {
		field.model = this;
		this.fieldModels.add(field);
		//this.toDB();
	}

	public void addCardModel(CardModel card) {
		card.model = this;
		this.cardModels.add(card);
		//this.toDB();
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
			//check whether the identifier is in the cardModelsMap
			if (null == currentModel || currentModel.cardModelsMap.containsKey(identifier)) {
				//get the modelId
				long myModelId = CardModel.modelIdFromDB(identifier);
				//get the model
				loadFromDBPlusRelatedModels(myModelId);
			}
		} else {
			if (null == currentModel || currentModel.id != identifier) {
				//get the model
				loadFromDBPlusRelatedModels(identifier);
			}
		}
		return currentModel;
	}
	
	protected final CardModel getCardModel(long identifier) {
		return cardModelsMap.get(identifier);
	}
	
	private static final void loadFromDBPlusRelatedModels(long modelId) {
		currentModel = fromDb(modelId);
		//load related card models
		CardModel.fromDb(currentModel.id, true, currentModel.cardModelsMap);
		//load related field models
		//SELECT id FROM fieldmodels where modelid = -4541298410707851455
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

}
