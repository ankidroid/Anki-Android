package com.ichi2.libanki.decks;

import com.ichi2.utils.JSONObject;

public class NewConf extends ReviewingConf {
    public NewConf() {
        super();
    }
    public NewConf(JSONObject conf) {
        super(conf);
    }

    public void putOrder(int n) {
        put("order", n);
    }
}
