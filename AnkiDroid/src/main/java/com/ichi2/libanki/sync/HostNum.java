package com.ichi2.libanki.sync;

/** Not part of libAnki directly, but abstracts out Preferences to a libAnki context */
public abstract class HostNum {
    public abstract String getHostNum();
    public abstract void setHostNum(String newHostNum);
}
