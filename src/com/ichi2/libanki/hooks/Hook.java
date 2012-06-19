
package com.ichi2.libanki.hooks;

/**
 * Basic Hook class. All other hooks should extend this and override either runHook or runFilter. In short if you want
 * result from the hook, use runFilter, otherwise runHook. If the hook you are creating is supposed to have state,
 * meaning that: - It probably uses arguments in its constructor. - Can potentially have instances that don't behave
 * identically. - Uses private members to store information between runs. Then: - You should also override methods
 * equals and hashCode, so that you can differentiate between different instances of your hook.
 */
public class Hook {
    public void runHook(Object... args) {
        return;
    }


    public Object runFilter(Object arg, Object... args) {
        return arg;
    }
}
