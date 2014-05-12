/***************************************************************************************
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

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
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Hook other = (Hook) obj;
        if (fName == null) {
            if (other.fName != null) {
                return false;
            }
        } else if (!fName.equals(other.fName)) {
            return false;
        }
        return true;
    }

    public void runHook(Object... args) {
        return;
    }

    public Object runFilter(Object arg, Object... args) {
        return arg;
    }
}
