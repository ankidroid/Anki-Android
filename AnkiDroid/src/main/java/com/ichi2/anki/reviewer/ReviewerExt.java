/****************************************************************************************
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

package com.ichi2.anki.reviewer;

/**
 * An extension to the reviewer class.
 * <p>
 * This allows splitting parts of the code that offer a specific feature outside the activity itself.
 */
public interface ReviewerExt {

    /**
     * Hook for updating the CSS style used by a card.
     * <p>
     * It should modify the content of the {@link StringBuilder} to reflect the new style.
     * 
     * @param cssStyle containing current style
     */
    void updateCssStyle(StringBuilder cssStyle);

}
