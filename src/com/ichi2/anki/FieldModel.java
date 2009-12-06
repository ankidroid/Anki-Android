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
