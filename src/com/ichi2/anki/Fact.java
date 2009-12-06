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
import java.util.Iterator;
import java.util.TreeSet;

public class Fact {

	// TODO: Javadoc.
	// TODO: Finish porting from facts.py.
	// TODO: Methods to read/write from/to DB.

	long id;
	long modelId;

	Model model;
	TreeSet<Field> fields;

	public Fact(Model model) {
		this.model = model;
		this.id = Util.genID();
		if (model != null) {
			Iterator<FieldModel> iter = model.fieldModels.iterator();
			while (iter.hasNext()) {
				this.fields.add(new Field(iter.next()));
			}
		}
	}

	public String getFieldValue(String fieldModelName) {
		Iterator<Field> iter = fields.iterator();
		while (iter.hasNext()) {
			Field f = iter.next();
			if (f.fieldModel.name.equals("fieldModelName")) {
				return f.value;
			}
		}
		return null;
	}

	public static final class FieldOrdinalComparator implements Comparator<Field> {
		public int compare(Field object1, Field object2) {
			return object1.ordinal - object2.ordinal;
		}
	}

	public class Field {

		// TODO: Javadoc.
		// Methods for reading/writing from/to DB.

		// BEGIN SQL table entries
		long id; // Primary key
		long factId; // Foreign key facts.id
		long fieldModelId; // Foreign key fieldModel.id
		int ordinal;
		String value;
		// END SQL table entries

		// BEGIN JOINed entries
		FieldModel fieldModel;
		// END JOINed entries

		// Backward reference
		Fact fact;

		public Field(FieldModel fieldModel) {
			if (fieldModel != null) {
				this.fieldModel = fieldModel;
				this.ordinal = fieldModel.ordinal;
			}
			this.value = "";
			this.id = Util.genID();
		}
	}

}
