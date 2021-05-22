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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ichi2.utils.JSONObject;

import androidx.annotation.CheckResult;

public class Deck extends JSONObject {


    /**
     * @see  Deck#from(JSONObject)
     */
    @JsonCreator
    protected Deck(ObjectNode node) {
        super(node);
    }


    /**
     * Creates a Deck object from the underlying
     * {@link ObjectNode} in the passed {@link JSONObject}
     *
     * NOTE: The passed node will be used directly, so
     * any change in the node will result in a change in
     * this object
     */
    public static Deck from(JSONObject json) {
        return new Deck(json.getRootJsonNode());
    }


    /**
     * Creates a copy from {@link JSONObject} and use it as a string
     *
     * This function will perform deepCopy on the passed object
     *
     * @see Deck#from(JSONObject) if you want to create a
     *                            Deck without deepCopy
     */
    public Deck(JSONObject json) {
        super(json);
    }

    /**
     * Creates a deck object form a json string
     */
    public Deck(String json) {
        super(json);
    }


    /**
     * Creates a new empty deck object
     */
    public Deck() {
        super();
    }

    @Override
    @CheckResult
    public Deck deepClone() {
        return new Deck(this);
    }

    public boolean isDyn() {
        return getInt("dyn") == Consts.DECK_DYN;
    }

    public boolean isStd() {
        return getInt("dyn") == Consts.DECK_STD;
    }
}
