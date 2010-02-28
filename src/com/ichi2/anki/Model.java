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

import java.util.Comparator;
import java.util.TreeSet;

/**
 * Anki model.
 * A model describes the type of information you want to input, and the type of cards which should be generated.
 * See http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Models
 */
public class Model {

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
	TreeSet<FieldModel> fieldModels;
	TreeSet<CardModel> cardModels;
	// END JOINed entries

	public Model(String name) {
		this.fieldModels = new TreeSet<FieldModel>(new FieldModelOrdinalComparator());
		this.cardModels = new TreeSet<CardModel>(new CardModelOrdinalComparator());
		this.name = name;
		this.id = Util.genID();
	}

	public Model() {
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


	public static final class FieldModelOrdinalComparator implements Comparator<FieldModel> {
		public int compare(FieldModel object1, FieldModel object2) {
			return object1.ordinal - object2.ordinal;
		}
	}

	public static final class CardModelOrdinalComparator implements Comparator<CardModel> {
		public int compare(CardModel object1, CardModel object2) {
			return object1.ordinal - object2.ordinal;
		}
	}

}
