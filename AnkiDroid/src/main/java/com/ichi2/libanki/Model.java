/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                             *
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

package com.ichi2.libanki;


import com.ichi2.utils.JSONObject;

import androidx.annotation.CheckResult;

/**
 * Represents a note type, a.k.a. Model.
 * The content of an object is described in https://github.com/ankidroid/Anki-Android/wiki/Database-Structure
 * Each time the object is modified, `Models.save(this)` should be called, otherwise the change will not be synchronized
 * If a change affect card generation, (i.e. any change on the list of field, or the question side of a card type), `Models.save(this, true)` should be called. However, you should do the change in batch and change only when aall are done, because recomputing the list of card is an expensive operation.
 */
public class Model extends JSONObject {
    public Model() {
        super();
    }

    public Model(JSONObject json) {
        super(json);
    }

    public Model(String json) {
        super(json);
    }

    @Override
    @CheckResult
    public Model deepClone() {
        Model clone = new Model();
        return deepClonedInto(clone);
    }
}
