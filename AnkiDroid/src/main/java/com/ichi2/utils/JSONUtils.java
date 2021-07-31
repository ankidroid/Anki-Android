/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.FloatNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class JSONUtils {

    /**
     * Returns {@link JsonNode} based on an {@link Object} value
     *
     * @param object value to converted
     *              value could be:
     *              {@link String}
     *              {@link CharSequence}
     *              {@link Double}
     *              {@link Float}
     *              {@link Integer}
     *              {@link Boolean}
     *              {@link Long}
     *              {@link JsonNode}
     *              {@link JSONObject}
     *              {@link JSONArray}
     *              {@code null}
     * @return converted json node
     * @throws JSONException if type is not supported
     */
    public static JsonNode objectToJsonNode(@Nullable Object object) {
        if (object == null) {
            return JSONObject.NULL;
        } else if (object instanceof String) {
            return TextNode.valueOf((String) object);
        } else if (object instanceof CharSequence) {
            return TextNode.valueOf(object.toString());
        } else if (object instanceof Double) {
            return DoubleNode.valueOf((Double) object);
        } else if (object instanceof Float) {
            return FloatNode.valueOf((Float) object);
        } else if (object instanceof Integer) {
            return IntNode.valueOf((Integer) object);
        } else if (object instanceof Boolean) {
            return BooleanNode.valueOf((Boolean) object);
        } else if (object instanceof Long) {
            return LongNode.valueOf((Long) object);
        } else if (object instanceof JsonNode) {
            return (JsonNode) object;
        } else if (object instanceof JSONObject) {
            return ((JSONObject) object).getRootJsonNode();
        } else if (object instanceof JSONArray) {
            return ((JSONArray) object).getRootJsonNode();
        } else {
            throw new JSONException("Unsupported type " + object.getClass().getName());
        }
    }


    /**
     * Returns {@link Object} based on an {@link JsonNode} containing value
     *
     * @param node to convert
     *              value could be:
     *              {@link String}
     *              {@link Double}
     *              {@link Float}
     *              {@link Boolean}
     *              {@link Integer}
     *              {@link Long}
     *              {@link JSONObject}
     *              {@link JSONObject#NULL}/{@link NullNode}
     *              {@link JSONArray}
     * @return value contained inside the node
     * @throws JSONException if the node type is not supported
     */
    public static @NonNull Object jsonNodeToObject(@NonNull JsonNode node) {
        if (node.isArray()) {
            return new JSONArray((ArrayNode) node);
        } else if (node.isObject()) {
            return new JSONObject((ObjectNode) node);
        } else if (node.isNull()) {
            return JSONObject.NULL;
        } else if (node.isTextual()) {
            return node.textValue();
        } else if (node.isBoolean()) {
            return node.booleanValue();
        } else if (node.isInt()) {
            return node.intValue();
        }else if (node.isFloat()) {
            return node.floatValue();
        } else if (node.isLong()) {
            return node.longValue();
        } else if (node.isDouble()) {
            return node.doubleValue();
        }
        throw new JSONException("Unsupported type " + node.getClass().getName() +  " - " + node.getNodeType().name());
    }
}
