package com.ichi2.libanki.sync;

/**
 * The server provides hostNum in the /sync/meta call. All requests after that (including future meta requests)
 * should use that hostNum to construct the sync URL, until a future /sync/meta call advises otherwise.
 *
 * This class is not part of libAnki directly, but abstracts Preference saving to a libAnki context
 * */
public class HostNum {
    private String mHostNum;

    public HostNum(String hostNum) {
        mHostNum = hostNum;
    }

    public String getHostNum() {
        return mHostNum;
    }

    public void setHostNum(String newHostNum) {
        mHostNum = newHostNum;
    }
}
