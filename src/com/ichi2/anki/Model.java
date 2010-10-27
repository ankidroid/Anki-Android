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

import android.database.Cursor;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Anki model. A model describes the type of information you want to input, and the type of cards which should be
 * generated. See http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Models There can be several models in a Deck. A Model
 * is related to a Deck via attribute deckId. A CardModel is related to a Model via CardModel's modelId. A FieldModel is
 * related to a Model via FieldModel's modelId A Card has a link to CardModel via Card's cardModelId A Card has a link
 * to a Fact via Card's factId A Field has a link to a Fact via Field's factId A Field has a link to a FieldModel via
 * Field's fieldModelId => In order to get the CardModel and all FieldModels for a given Card: % the CardModel can
 * directly be retrieved from the DB using the Card's cardModelId % then from the retrieved CardModel we can get the
 * modelId % using the modelId we can get all FieldModels from the DB % (alternatively in the CardModel the qformat and
 * aformat fields could be parsed for relevant field names and then this used to only get the necessary fields. But this
 * adds a lot overhead vs. using a bit more memory)
 */
public class Model {

    /** Singleton */
    // private static Model currentModel;

    /**
     * A Map of the currently loaded Models. The Models are loaded from the database as soon as they are needed for the
     * first time. This is a compromise between RAM need, speed and the probability with which more than one Model is
     * needed. If only one model is needed, then RAM consumption is basically the same as having a static "currentModel"
     * variable. If more than one Model is needed, then more RAM is needed, but on the other hand side Model and its
     * related CardModel and FieldModel are not reloaded again and again. This Map uses the Model.id field as key
     */
    private static HashMap<Long, Model> models = new HashMap<Long, Model>();

    /**
     * As above but mapping from CardModel to related Model (because when one has a Card, then you need to jump from
     * CardModel to Model.
     */
    private static HashMap<Long, Model> cardModelToModelMap = new HashMap<Long, Model>();

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

    Deck deck;

    /** Map for convenience and speed which contains CardModels from current model */
    private TreeMap<Long, CardModel> cardModelsMap = new TreeMap<Long, CardModel>();

    /** Map for convenience and speed which contains FieldModels from current model */
    private TreeMap<Long, FieldModel> fieldModelsMap = new TreeMap<Long, FieldModel>();

    /** Map for convenience and speed which contains the CSS code related to a CardModel */
    private HashMap<Long, String> cssCardModelMap = new HashMap<Long, String>();

    /**
     * The percentage chosen in preferences for font sizing at the time when the css for the CardModels related to this
     * Model was calcualted in prepareCSSForCardModels.
     */
    private transient int displayPercentage = 0;

    /** Text align constants */
    private final static String[] align_text = { "center", "left", "right" };


    private Model(Deck deck, String name) {
        this.deck = deck;
        this.name = name;
        id = Utils.genID();
    }


    private Model(Deck deck) {
        this(deck, "");
    }


    public void setModified() {
        modified = System.currentTimeMillis() / 1000.0;
    }


    /**
     * FIXME: this should be called whenever the deck is changed. Otherwise unnecessary space will be used.
     */
    protected static final void reset() {
        models = new HashMap<Long, Model>();
        cardModelToModelMap = new HashMap<Long, Model>();
    }


    /**
     * Returns a Model based on the submitted identifier. If a model id is submitted (isModelId = true), then the Model
     * data and all related CardModel and FieldModel data are loaded, unless the id is the same as one of the
     * currentModel. If a cardModel id is submitted, then the related Model data and all related CardModel and
     * FieldModel data are loaded unless the cardModel id is already in the cardModel map. FIXME: nothing is done to
     * treat db failure or non-existing identifiers
     * 
     * @param deck The deck we are working with
     * @param identifier a cardModel id or a model id
     * @param isModelId if true then the submitted identifier is a model id; otherwise the identifier is a cardModel id
     * @return
     */
    protected static Model getModel(Deck deck, long identifier, boolean isModelId) {
        if (false == isModelId) {
            // check whether the identifier is in the cardModelToModelMap
            if (false == cardModelToModelMap.containsKey(identifier)) {
                // get the modelId
                long myModelId = CardModel.modelIdFromDB(deck, identifier);
                // get the model
                loadFromDBPlusRelatedModels(deck, myModelId);
            }
            return cardModelToModelMap.get(identifier);
        }
        // else it is a modelId
        if (false == models.containsKey(identifier)) {
            // get the model
            loadFromDBPlusRelatedModels(deck, identifier);
        }
        return models.get(identifier);
    }

    
    public static HashMap<Long, Model> getModels(Deck deck) {
    	Model mModel ; 
    	HashMap<Long, Model> mModels= new HashMap<Long, Model>() ;
    	
    	Cursor mCursor =null;
    	AnkiDb ankiDB=AnkiDatabaseManager.getDatabase(deck.deckPath);
    	try{
    		mCursor = ankiDB.database.rawQuery("SELECT id FROM models", null);
    		if (!mCursor.moveToFirst()) {
    			return mModels;
    		}
            do{
            	Long id=mCursor.getLong(0);
            	mModel=getModel(deck, id, true);
            	mModels.put(id, mModel);

            } while (mCursor.moveToNext());

    	} finally {
            if (mCursor != null) {
                mCursor.close();
            }
        }
		return mModels;
	}


    protected final CardModel getCardModel(long identifier) {
        return cardModelsMap.get(identifier);
    }


    /**
     * Loads the Model from the database. then loads the related CardModels and FieldModels from the database.
     * 
     * @param deck
     * @param modelId
     */
    private static final void loadFromDBPlusRelatedModels(Deck deck, long modelId) {
        Model currentModel = fromDb(deck, modelId);

        // load related card models
        CardModel.fromDb(deck, currentModel.id, currentModel.cardModelsMap);

        // load related field models
        FieldModel.fromDb(deck, modelId, currentModel.fieldModelsMap);

        // make relations to maps
        models.put(currentModel.id, currentModel);
        CardModel myCardModel = null;
        for (Map.Entry<Long, CardModel> entry : currentModel.cardModelsMap.entrySet()) {
            myCardModel = entry.getValue();
            cardModelToModelMap.put(myCardModel.id, currentModel);
        }
    }


    /**
     * Loads a model from the database based on the id FIXME: nothing is done in case of db error or no returned row
     * 
     * @param deck
     * @param id
     * @return
     */
    private static final Model fromDb(Deck deck, long id) {
        Cursor cursor = null;
        Model model = null;
        try {
            StringBuffer query = new StringBuffer();
            query.append("SELECT id, deckId, created, modified, tags, name, description");
            query.append(", features, spacing, initialSpacing, source");
            query.append(" FROM models");
            query.append(" WHERE id = ").append(id);
            cursor = AnkiDatabaseManager.getDatabase(deck.deckPath).database.rawQuery(query.toString(), null);

            cursor.moveToFirst();
            model = new Model(deck);

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
            if (cursor != null) {
                cursor.close();
            }
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
            cssString = createCSSForFontColorSize(myCardModel.id, displayPercentage);
            cssCardModelMap.put(myCardModel.id, cssString);
        }
    }


    /**
     * Returns a cached CSS for the font color and font size of a given CardModel taking into account the included
     * fields
     * 
     * @param myCardModelId
     * @param percentage the preference factor to use for calculating the display font size from the cardmodel and
     *            fontmodel font size
     * @return the html contents surrounded by a css style which contains class styles for answer/question and fields
     */
    protected final String getCSSForFontColorSize(long myCardModelId, int percentage) {
        // tjek whether the percentage is this the same as last time
        if (displayPercentage != percentage) {
            displayPercentage = percentage;
            prepareCSSForCardModels();
        }
        return cssCardModelMap.get(myCardModelId);
    }


    /**
     * @param myCardModelId
     * @param percentage the factor to apply to the font size in card model to the display size (in %)
     * @return the html contents surrounded by a css style which contains class styles for answer/question and fields
     */
    private final String createCSSForFontColorSize(long myCardModelId, int percentage) {
        StringBuffer sb = new StringBuffer();
        sb.append("<!-- ").append(percentage).append(" % display font size-->");
        sb.append("<style type=\"text/css\">\n");
        CardModel myCardModel = cardModelsMap.get(myCardModelId);

        // body background
        if (null != myCardModel.lastFontColour && 0 < myCardModel.lastFontColour.trim().length()) {
            sb.append("body {background-color:").append(myCardModel.lastFontColour).append(";}\n");
        }
        // question
        sb.append(".").append(Reviewer.QUESTION_CLASS).append(" {\n");
        sb.append(calculateDisplay(percentage, myCardModel.questionFontFamily, myCardModel.questionFontSize,
                myCardModel.questionFontColour, myCardModel.questionAlign, false));
        sb.append("}\n");
        // answer
        sb.append(".").append(Reviewer.ANSWER_CLASS).append(" {\n");
        sb.append(calculateDisplay(percentage, myCardModel.answerFontFamily, myCardModel.answerFontSize,
                myCardModel.answerFontColour, myCardModel.answerAlign, false));
        sb.append("}\n");
        // css for fields. Gets css for all fields no matter whether they actually are used in a given card model
        FieldModel myFieldModel = null;
        String hexId = null; // a FieldModel id in unsigned hexa code for the class attribute
        for (Map.Entry<Long, FieldModel> entry : fieldModelsMap.entrySet()) {
            myFieldModel = entry.getValue();
            hexId = "fm" + Long.toHexString(myFieldModel.id);
            sb.append(".").append(hexId).append(" {\n");
            sb.append(calculateDisplay(percentage, myFieldModel.quizFontFamily, myFieldModel.quizFontSize,
                    myFieldModel.quizFontColour, 0, true));
            sb.append("}\n");
        }

        // finish
        sb.append("</style>");
        return sb.toString();
    }


    private final static String calculateDisplay(int percentage, String fontFamily, int fontSize, String fontColour,
            int align, boolean isField) {
        StringBuffer sb = new StringBuffer();
        if (null != fontFamily && 0 < fontFamily.trim().length()) {
            sb.append("font-family:\"").append(fontFamily).append("\";\n");
        }
        if (null != fontColour && 0 < fontColour.trim().length()) {
            sb.append("color:").append(fontColour).append(";\n");
        }
        if (0 < fontSize) {
            sb.append("font-size:");
            sb.append((percentage * fontSize) / 100);
            sb.append("px;\n");
        }

        if (!isField) {
            sb.append("text-align:");
            sb.append(align_text[align]);
            sb.append(";\n");
            sb.append("padding-left:5px;\n");
            sb.append("padding-right:5px;\n");
        }

        return sb.toString();
    }

}
