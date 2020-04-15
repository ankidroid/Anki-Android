package com.ichi2.libanki.sync;

import com.ichi2.libanki.Consts;

/**
 * The server provides hostNum in the /sync/meta call. All requests after that (including future meta requests)
 * should use that hostNum to construct the sync URL, until a future /sync/meta call advises otherwise.
 *
 * This class is not part of libAnki directly, but abstracts Preference saving to a libAnki context
 *
 * This is defined as an integer to avoid string formatting attacks on the URL.
 * Confirmed to always be an integer or null in AnkiWeb
 * https://github.com/ankidroid/Anki-Android/pull/6004#issuecomment-613731597
 *
 * This should be wiped:
 * * On Logoff
 * * On Change of Sync Server
 *
 * As new user data will likely not be under the same hostNum
 * */
public class HostNum {
    private Integer mHostNum;

    public HostNum(Integer hostNum) {
        mHostNum = hostNum;
    }

    public Integer getHostNum() {
        return mHostNum;
    }

    public void setHostNum(Integer newHostNum) {
        mHostNum = newHostNum;
    }

    public void reset() {
        mHostNum = getDefaultHostNum();
    }

    protected static Integer getDefaultHostNum() {
        return Consts.DEFAULT_HOST_NUM;
    }
}
