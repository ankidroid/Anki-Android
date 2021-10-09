/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.libanki.sched;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Decks;
import com.ichi2.utils.JSONObject;

import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;

/**
 * Holds the data for a single node (row) in the deck due tree (the user-visible list
 * of decks and their counts). A node also contains a list of nodes that refer to the
 * next level of sub-decks for that particular deck (which can be an empty list).
 *
 * The names field is an array of names that build a deck name from a hierarchy (i.e., a nested
 * deck will have an entry for every level of nesting). While the python version interchanges
 * between a string and a list of strings throughout processing, we always use an array for
 * this field and use getNamePart(0) for those cases.
 */
public class DeckDueTreeNode extends AbstractDeckTreeNode<DeckDueTreeNode> {
    private int mRevCount;
    private int mLrnCount;
    private int mNewCount;

    public DeckDueTreeNode(Collection col, String name, long did, int revCount, int lrnCount, int newCount) {
        super(col, name, did);
        this.mRevCount = revCount;
        this.mLrnCount = lrnCount;
        this.mNewCount = newCount;
    }

    @Override
    public @NonNull String toString() {
        return String.format(Locale.US, "%s, %d, %d, %d, %d, %s",
                             getFullDeckName(), getDid(), mRevCount, mLrnCount, mNewCount, getChildren());
    }


    public int getRevCount() {
        return mRevCount;
    }

    private void limitRevCount(int limit) {
        mRevCount = Math.max(0, Math.min(mRevCount, limit));
    }

    public int getNewCount() {
        return mNewCount;
    }

    private void limitNewCount(int limit) {
        mNewCount = Math.max(0, Math.min(mNewCount, limit));
    }

    public int getLrnCount() {
        return mLrnCount;
    }

    public void setChildren(@NonNull List<DeckDueTreeNode> children, boolean addRev) {
        super.setChildren(children, addRev);
        // tally up children counts
        for (DeckDueTreeNode ch : children) {
            mLrnCount += ch.getLrnCount();
            mNewCount += ch.getNewCount();
            if (addRev) {
                mRevCount += ch.getRevCount();
            }
        }
        // limit the counts to the deck's limits
        DeckConfig conf = getCol().getDecks().confForDid(getDid());
        if (conf.isStd()) {
            Deck deck = getCol().getDecks().get(getDid());
            limitNewCount(conf.getJSONObject("new").getInt("perDay") - deck.getJSONArray("newToday").getInt(1));
            if (addRev) {
                limitRevCount(conf.getJSONObject("rev").getInt("perDay") - deck.getJSONArray("revToday").getInt(1));
            }
        }
    }

    @Override
    public int hashCode() {
        int childrenHash = getChildren() == null ? 0 : getChildren().hashCode();
        return getFullDeckName().hashCode() + mRevCount + mLrnCount + mNewCount + childrenHash;
    }


    /**
     * Whether both elements have the same structure and numbers.
     * @param object
     * @return
     */
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof DeckDueTreeNode)) {
            return false;
        }
        DeckDueTreeNode tree = (DeckDueTreeNode) object;
        return Decks.equalName(getFullDeckName(), tree.getFullDeckName()) &&
            mRevCount == tree.mRevCount &&
            mLrnCount == tree.mLrnCount &&
            mNewCount == tree.mNewCount &&
            (getChildren() == tree.getChildren() || // Would be the case if both are null, or the same pointer
             getChildren().equals(tree.getChildren()))
            ;
    }

    /** Line representing this string without its children. Used in timbers only. */
    protected String toStringLine() {
        return String.format(Locale.US, "%s, %d, %d, %d, %d\n",
                             getFullDeckName(), getDid(), mRevCount, mLrnCount, mNewCount);
    }

    public boolean shouldDisplayCounts() {
        return true;
    }

    public boolean knownToHaveRep() {
        return mRevCount > 0 || mNewCount > 0 || mLrnCount > 0;
    }


    @Override
    public DeckDueTreeNode withChildren(List<DeckDueTreeNode> children) {
        Collection col = getCol();
        String name = getFullDeckName();
        long did = getDid();
        DeckDueTreeNode node = new DeckDueTreeNode(col, name, did, mRevCount, mLrnCount, mNewCount);
        // We've already calculated the counts, don't bother doing it again, just set the variable.
        node.setChildrenSuper(children);
        return node;
    }

    private void setChildrenSuper(List<DeckDueTreeNode> children) {
        super.setChildren(children, false);
    }
}
