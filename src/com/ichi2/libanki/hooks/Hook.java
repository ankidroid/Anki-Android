
package com.ichi2.libanki.hooks;

/**
 * Basic Hook class. All other hooks should extend this and override either runHook or runFilter.
 * In short if you want result from the hook, override runFilter, otherwise runHook.
 * <p>
 * If the hook you are creating is supposed to have state, meaning that:<ul>
 * <li>It probably uses arguments in its constructor.
 * <li>Can potentially have instances that don't behave identically.
 * <li>Uses private members to store information between runs.
 * </ul>
 * Then you should also override methods equals and hashCode, so that they take into consideration any fields you have added.<p>
 * You can do so using the auto-generate feature from Eclipse: Source->Generate hashCode() and equals() 
 */
public class Hook {
    private final String fName = this.getClass().getCanonicalName();

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((fName == null) ? 0 : fName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Hook other = (Hook) obj;
        if (fName == null) {
            if (other.fName != null)
                return false;
        } else if (!fName.equals(other.fName))
            return false;
        return true;
    }

    public void runHook(Object... args) {
        return;
    }

    public Object runFilter(Object arg, Object... args) {
        return arg;
    }
}
