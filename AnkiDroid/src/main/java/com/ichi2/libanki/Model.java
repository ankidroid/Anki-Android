package com.ichi2.libanki;


import com.ichi2.utils.JSONObject;

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
    public Model deepClone() {
        Model clone = new Model();
        deepClonedInto(clone);
        return clone;
    }
}
