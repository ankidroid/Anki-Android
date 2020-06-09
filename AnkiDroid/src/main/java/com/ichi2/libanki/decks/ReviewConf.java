package com.ichi2.libanki.decks;

import com.ichi2.utils.JSONObject;

public class ReviewConf extends ReviewingConf {
    public ReviewConf() {
        super();
    }
    public ReviewConf(JSONObject conf) {
        super(conf);
    }

    public void putEase4(float f) {
        put("ease4", f);
    }

    public void putIvlFact(float ivlFct) {
        put("ivlFct", ivlFct);
    }

    public void putTimeoutAnswer(Object o) {
        put("timeoutAnswer", o);
    }
}
