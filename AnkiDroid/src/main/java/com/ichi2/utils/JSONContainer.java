package com.ichi2.utils;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ichi2.anki.AnkiSerialization;

import org.jetbrains.annotations.NotNull;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.ichi2.utils.JSONTypeConverters.checkDouble;
import static com.ichi2.utils.JSONUtils.jsonNodeToObject;
import static java.lang.System.identityHashCode;

public abstract class JSONContainer<AccessType,
        UnderlyingType extends ContainerNode<UnderlyingType>,
        InheritedType extends JSONContainer<AccessType, UnderlyingType, InheritedType>> {
    public static final JsonNode NULL = NullNode.getInstance();


    protected final @NonNull UnderlyingType mNode;


    public JSONContainer(@NonNull UnderlyingType node) {
        mNode = node;
    }

    /**
     * Deserializes a string to to a {@link JSONObject} using the default {@link ObjectMapper}
     * @param source the json string
     *
     * @throws JSONException if the string couldn't be parsed.
     * the encapsulated exception is either {@link JsonProcessingException} or {@link JsonMappingException}
     */
    public JSONContainer(@NonNull String source, Class<UnderlyingType> clazz) {
        try {
            mNode = (UnderlyingType) AnkiSerialization.getObjectMapper().readTree(source);
            if (mNode.getClass() != clazz) {
                throw new JSONException("Expected JSONObject, got wrong type: " + source);
            }
        } catch (Exception e) {
            throw new JSONException(e);
        }
    }

    protected abstract InheritedType thisAsInheritedType();

    /**
     * @return the backing {@link ObjectNode}
     *
     * Changing the content of the returned node will result
     * in a change in this instance of JSONObject.
     *
     * This method should not be used directly to change the
     * content of the json object, but can be used for jackson
     * deserialization and for changing the wrapper class around
     * the {@link ObjectNode} like {@link com.ichi2.libanki.Deck#from(JSONObject)}
     */
    @NonNull
    @JsonValue
    public UnderlyingType getRootJsonNode() {
        return mNode;
    }

    public abstract @NonNull InheritedType put(@NonNull AccessType indexOrName, @NonNull JsonNode node);

    @NonNull
    public InheritedType put(@NonNull AccessType indexOrName, Boolean value) {
        return put(indexOrName, (boolean) value);
    }

    @NonNull
    public InheritedType put(@NonNull AccessType indexOrName, boolean value) {
        return this.put(indexOrName, mNode.booleanNode(value));
    }

    @NonNull
    public InheritedType put(@NonNull AccessType indexOrName, Double value) throws JSONException {
        return put(indexOrName, (double) value);
    }

    @NonNull
    public InheritedType put(@NonNull AccessType indexOrName, double value) throws JSONException {
        return this.put(indexOrName, mNode.numberNode(checkDouble(value)));
    }

    @NonNull
    public InheritedType put(@NonNull AccessType indexOrName, Integer value) {
        return put(indexOrName, (long) value);
    }

    @NonNull
    public InheritedType put(@NonNull AccessType indexOrName, int value) {
        return this.put(indexOrName, mNode.numberNode(value));
    }

    @NonNull
    public InheritedType put(@NonNull AccessType indexOrName, Long value) {
        return put(indexOrName, (long) value);
    }

    @NonNull
    public InheritedType put(@NonNull AccessType indexOrName, long value) {
        return this.put(indexOrName, mNode.numberNode(value));
    }


    /**
     * Put a key/value pair in the InheritedType. If the value is <code>null</code>, then the
     * key will be removed from the InheritedType if it is present.
     *
     * @throws JSONException if passed type is not supported
     * @see JSONUtils#objectToJsonNode(Object) for supported types
     */
    @NonNull
    public InheritedType put(@NonNull AccessType indexOrName, @Nullable Object value) throws JSONException {
        return put(indexOrName, JSONUtils.objectToJsonNode(value));
    }

    /**
     * Same as put, but assume the object is valid to store (usually because it was already stored in jackson)
     * @throws JSONException if passed type is not supported
     * @see JSONUtils#objectToJsonNode(Object) for supported types
     */
    @NonNull
    protected InheritedType putSafe(@NonNull AccessType indexOrName, @Nullable Object value) {
        return put(indexOrName, JSONUtils.objectToJsonNode(value));
    }


    /**
     * Put a key/value pair in the InheritedType, but only if the key and the value
     * are both non-null.
     */
    @NonNull
    public InheritedType putOpt(@Nullable AccessType indexOrName, @Nullable Object value) throws JSONException {
        if (indexOrName == null || value == null) {
            return thisAsInheritedType();
        }
        if (value instanceof Number) {
            // deviate from the original by checking all Numbers, not just floats & doubles
            checkDouble(((Number) value).doubleValue());
        }
        return put(indexOrName, value);
    }

    @NonNull
    public InheritedType put(@NonNull AccessType indexOrName, @NonNull CharSequence value) {
        return this.put(indexOrName, mNode.textNode(value.toString()));
    }


    @NonNull
    public InheritedType put(@NonNull AccessType indexOrName, @NonNull JSONArray value) {
        return this.put(indexOrName, value.getRootJsonNode());
    }


    @NonNull
    public InheritedType put(@NonNull AccessType indexOrName, @NonNull JSONObject value) {
        return this.put(indexOrName, value.getRootJsonNode());
    }

    protected abstract @NonNull JsonNode getJsonNode(@NonNull AccessType indexOrName);



    /**
     * Get the value object associated with a indexOrName.
     */
    @CheckResult
    @NonNull
    public Object get(@NonNull AccessType indexOrName) {
        JsonNode node;
        try {
            node = getJsonNode(indexOrName);
        } catch (AssertionError e) {
            throw new JSONException(e);
        }
        return jsonNodeToObject(node);
    }


    @CheckResult
    @NonNull
    public <T> T getConverter(JSONTypeConverters.@NotNull ObjectToJsonValue<T> converter, @NonNull AccessType indexOrName) {
        return converter.convert(indexOrName, get(indexOrName));
    }

    /**
     * @return the value at "indexOrName" converted to boolean.
     * @throws JSONException if value didn't exist, or it couldn't be
     *                       converted to the correct type
     */
    @CheckResult
    public boolean getBoolean(AccessType indexOrName) {
        return getConverter(JSONTypeConverters.sToBoolean, indexOrName);
    }


    /**
     * @return the value at "indexOrName" converted to double.
     * @throws JSONException if value didn't exist, or it couldn't be
     *                       converted to the correct type
     */
    @CheckResult
    public double getDouble(AccessType indexOrName) {
        return getConverter(JSONTypeConverters.sToDouble, indexOrName);
    }


    /**
     * @return the value at "indexOrName" converted to int.
     * @throws JSONException if value didn't exist, or it couldn't be
     *                       converted to the correct type
     */
    @CheckResult
    public int getInt(AccessType indexOrName) {
        return getConverter(JSONTypeConverters.sToInteger, indexOrName);
    }


    /**
     * @return the value at "indexOrName" converted to long.
     * @throws JSONException if value didn't exist, or it couldn't be
     *                       converted to the correct type
     */
    @CheckResult
    public long getLong(AccessType indexOrName) {
        return getConverter(JSONTypeConverters.sToLong, indexOrName);
    }


    /**
     * @return the value at "indexOrName" converted to string.
     * @throws JSONException if value didn't exist, or it couldn't be
     *                       converted to the correct type
     */
    @CheckResult
    @NonNull
    public String getString(AccessType indexOrName) {
        return getConverter(JSONTypeConverters.sToString, indexOrName);
    }


    /**
     * @return array at "indexOrName"
     * @throws JSONException if value didn't exist, or it couldn't be
     *                       converted to the correct type
     */
    @CheckResult
    @NonNull
    public JSONArray getJSONArray(AccessType indexOrName) {
        return getConverter(JSONTypeConverters.sToArray, indexOrName);
    }


    /**
     * @return object at "indexOrName"
     * @throws JSONException if value didn't exist, or it couldn't be
     *                       converted to the correct type
     */
    @CheckResult
    @NonNull
    public JSONObject getJSONObject(AccessType indexOrName) {
        return getConverter(JSONTypeConverters.sToObject, indexOrName);
    }


    /**
     * @return value at indexOrName if it exists, null otherwise.
     */
    protected abstract @Nullable JsonNode optNode(@Nullable AccessType indexOrName);



    /**
     * @return JSONArray at indexOrName if it exists otherwise null
     */
    @Nullable
    @CheckResult
    public JSONArray optJSONArray(@Nullable AccessType indexOrName) {
        return JSONTypeConverters.sToArray.convertIfNecessaryOrNull(opt(indexOrName));
    }


    /**
     * @return JSONObject at indexOrName if it exists otherwise null
     */
    @Nullable
    @CheckResult
    public JSONObject optJSONObject(@Nullable AccessType indexOrName) {
        return JSONTypeConverters.sToObject.convertIfNecessaryOrNull(opt(indexOrName));
    }


    /**
     * @param indexOrName Index of the element to get
     * @return The element at this index or name if it exists. null if index is null or the object does not exists
     */
    @Nullable
    public Object opt(@Nullable AccessType indexOrName) {
        if (indexOrName == null) {
            return null;
        }
        JsonNode node = optNode(indexOrName);
        if (node == null) {
            return null;
        }
        return jsonNodeToObject(node);
    }

    public boolean optBoolean(@Nullable AccessType indexOrName, boolean defaultValue) {
        return JSONTypeConverters.sToBoolean.convertIfNecessaryOrNull(opt(indexOrName), defaultValue);
    }


    public int optInt(@Nullable AccessType indexOrName) {
        return JSONTypeConverters.sToInteger.convertIfNecessaryOrNull(opt(indexOrName));
    }

    public int optInt(@Nullable AccessType indexOrName, int defaultValue) {
        return JSONTypeConverters.sToInteger.convertIfNecessaryOrNull(opt(indexOrName), defaultValue);
    }


    public long optLong(@Nullable AccessType indexOrName) {
        return JSONTypeConverters.sToLong.convertIfNecessaryOrNull(opt(indexOrName));
    }

    public long optLong(@Nullable AccessType indexOrName, long defaultValue) {
        return JSONTypeConverters.sToLong.convertIfNecessaryOrNull(opt(indexOrName), defaultValue);
    }

    public double optDouble(@Nullable AccessType indexOrName) {
        return JSONTypeConverters.sToDouble.convertIfNecessaryOrNull(opt(indexOrName));
    }

    public double optDouble(@Nullable AccessType indexOrName, double defaultValue) {
        return JSONTypeConverters.sToDouble.convertIfNecessaryOrNull(opt(indexOrName), defaultValue);
    }

    public String optString(AccessType indexOrName) {
        return JSONTypeConverters.sToString.convertIfNecessaryOrNull(opt(indexOrName));
    }

    public String optString(AccessType indexOrName, String defaultValue) {
        return JSONTypeConverters.sToString.convertIfNecessaryOrNull(opt(indexOrName), defaultValue);
    }

    @Override
    public String toString() {
        return mNode.toString();
    }

    public String toPrettyString() {
        return mNode.toPrettyString();
    }

    /* Present in org.json.JSON. Not implemented here because too complex and not useful in ankidroid.
    Must be implemented to be a perfect port of this library.
    public String toString(int indentation/*not used) {
        return toPrettyString();
    }
    */


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InheritedType other = (InheritedType) o;
        return mNode.equals(other.getRootJsonNode());
    }

    public boolean isNull(AccessType indexOrName) {
        JsonNode node = optNode(indexOrName);
        return node == null || node.isNull();
    }


    public boolean optBoolean(AccessType indexOrName) {
        return optBoolean(indexOrName, false);
    }


    @Override
    public int hashCode() {
        return mNode.hashCode();
    }

    public int length() {
        return mNode.size();
    }

    /**
     * Remove the field entry from this ObjectNode.
     *
     * @return Value of specified field, if it existed; null if not
     */
    // Return JsonNode and not underlying value to avoid the work of converting when we don't actually need it.
    public Object remove(AccessType indexOrName) {
        JsonNode node = removeNode(indexOrName);
        if (node == null) {
            return null;
        }
        return jsonNodeToObject(node);
    }

    /**
     * Method for removing field entry from this ObjectNode.
     *
     * @return JsonNode of specified field, if it existed; null if not
     */
    // Return JsonNode and not underlying value to avoid the work of converting when we don't actually need it.
    protected abstract JsonNode removeNode(AccessType indexOrName);
}
