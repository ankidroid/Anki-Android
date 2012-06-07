package com.ichi2.libanki.hooks;

/**
 * Basic Hook class.
 * All other hooks should extend this and override either runHook or runFilter.
 * In short if you want result from the hook, use runFilter, otherwise runHook.
 */
public class Hook {
    public void runHook(Object... args) {
        return;
    }
    public Object runFilter(Object arg, Object... args) {
        return arg;
    }
}