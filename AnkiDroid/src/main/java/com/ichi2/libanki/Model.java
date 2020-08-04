package com.ichi2.libanki;


import com.ichi2.utils.JSONObject;

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
