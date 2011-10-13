/****************************************************************************************
 * Copyright (c) 2011 Flavio Lerda <flerda@gmail.com>                                   *
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

package com.ichi2.compat;

import android.app.Activity;

/**
 * This interface defines a set of functions that are not available on all platforms.
 * <p>
 * A set of implementations for the supported platforms are available.
 * <p>
 * Each implementation ends with a {@code V<n>} prefix, identifying the minimum API version on which this implementation
 * can be used. For example, see {@link CompatV11}.
 */
public interface Compat {
    public void invalidateOptionsMenu(Activity activity);
}
