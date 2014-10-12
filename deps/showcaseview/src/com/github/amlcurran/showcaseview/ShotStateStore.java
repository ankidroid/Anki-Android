/*
 * Copyright 2014 Alex Curran
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.amlcurran.showcaseview;

import android.content.Context;
import android.content.SharedPreferences;

class ShotStateStore {

    private static final String PREFS_SHOWCASE_INTERNAL = "showcase_internal";
    private static final int INVALID_SHOT_ID = -1;

    long shotId = INVALID_SHOT_ID;

    private final Context context;

    public ShotStateStore(Context context) {
        this.context = context;
    }

    boolean hasShot() {
        return isSingleShot() && context
                .getSharedPreferences(PREFS_SHOWCASE_INTERNAL, Context.MODE_PRIVATE)
                .getBoolean("hasShot" + shotId, false);
    }

    boolean isSingleShot() {
        return shotId != INVALID_SHOT_ID;
    }

    void storeShot() {
        if (isSingleShot()) {
            SharedPreferences internal = context.getSharedPreferences(PREFS_SHOWCASE_INTERNAL, Context.MODE_PRIVATE);
            internal.edit().putBoolean("hasShot" + shotId, true).apply();
        }
    }

    void setSingleShot(long shotId) {
        this.shotId = shotId;
    }

}