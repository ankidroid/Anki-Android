/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
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

package com.ichi2.libanki.sched;

import com.ichi2.libanki.Collection;

import java.util.List;

public class DeckTreeNode extends AbstractDeckTreeNode<DeckTreeNode> {
    public DeckTreeNode(Collection col, String name, long did) {
        super(col, name, did);
    }


    @Override
    public DeckTreeNode withChildren(List<DeckTreeNode> children) {
        Collection col = getCol();
        String name = getFullDeckName();
        long did = getDid();
        DeckTreeNode node = new DeckTreeNode(col, name, did);
        node.setChildren(children, false);
        return node;
    }
}
