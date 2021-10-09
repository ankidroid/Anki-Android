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
import com.ichi2.libanki.Decks;

import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Holds the data for a single node (row) in the deck due tree (the user-visible list
 * of decks and their counts). A node also contains a list of nodes that refer to the
 * next level of sub-decks for that particular deck (which can be an empty list).
 *
 * The names field is an array of names that build a deck name from a hierarchy (i.e., a nested
 * deck will have an entry for every level of nesting). While the python version interchanges
 * between a string and a list of strings throughout processing, we always use an array for
 * this field and use getNamePart(0) for those cases.
 *
 * T represents the type of children. Required for typing purpose only.
 */
public abstract class AbstractDeckTreeNode<T extends AbstractDeckTreeNode<T>> implements Comparable<AbstractDeckTreeNode<T>> {
    private final String mName;
    private final String[] mNameComponents;
    private final Collection mCol;
    private final long mDid;
    @Nullable
    private List<T> mChildren = null;

    public AbstractDeckTreeNode(Collection col, String name, long did) {
        this.mCol = col;
        this.mName = name;
        this.mDid = did;
        this.mNameComponents = Decks.path(name);
    }

    /**
     * Sort on the head of the node.
     */
    @Override
    public int compareTo(AbstractDeckTreeNode<T> rhs) {
        int minDepth = Math.min(getDepth(), rhs.getDepth()) + 1;
        // Consider each subdeck name in the ordering
        for (int i = 0; i < minDepth; i++) {
            int cmp = mNameComponents[i].compareTo(rhs.mNameComponents[i]);
            if (cmp == 0) {
                continue;
            }
            return cmp;
        }
        // If we made it this far then the arrays are of different length. The longer one should
        // always come after since it contains all of the sections of the shorter one inside it
        // (i.e., the short one is an ancestor of the longer one).
        return Integer.compare(getDepth(), rhs.getDepth());
    }

    /** Line representing this string without its children. Used in timbers only. */
    protected String toStringLine() {
        return String.format(Locale.US, "%s, %d, %s",
                             mName, mDid, mChildren);
    }

    @Override
    public @NonNull String toString() {
        StringBuffer buf = new StringBuffer();
        toString(buf);
        return buf.toString();
    }

    protected void toString(StringBuffer buf) {
        for (int i = 0; i < getDepth(); i++ ) {
            buf.append("  ");
        }
        buf.append(toStringLine());
        if (mChildren == null) {
            return;
        }
        for (T children : mChildren) {
            children.toString(buf);
        }
    }

    /**
     * @return The full deck name, e.g. "A::B::C"
     * */
    public String getFullDeckName() {
        return mName;
    }

    /**
     * For deck "A::B::C", `getDeckNameComponent(0)` returns "A",
     * `getDeckNameComponent(1)` returns "B", etc...
     */
    public String getDeckNameComponent(int part) {
        return mNameComponents[part];
    }

    /**
     * The part of the name displayed in deck picker, i.e. the
     * part that does not belong to its parents. E.g.  for deck
     * "A::B::C", returns "C".
     */
    public String getLastDeckNameComponent() {
        return getDeckNameComponent(getDepth());
    }

    public long getDid() {
        return mDid;
    }

    /**
     * @return The depth of a deck. Top level decks have depth 0,
     * their children have depth 1, etc... So "A::B::C" would have
     * depth 2.
     */
    public int getDepth() {
        return mNameComponents.length - 1;
    }

    /**
     * @return The children of this deck. Note that they are set
     * in the data structure returned by DeckDueTree but are
     * always empty when the data structure is returned by
     * deckDueList.*/
    public List<T> getChildren() {
        return mChildren;
    }

    /**
     * @return whether this node as any children. */
    public boolean hasChildren() {
        return mChildren != null && !mChildren.isEmpty();
    }

    public void setChildren(@NonNull List<T> children, boolean addRev) {
        // addRev present here because it needs to be overriden
        mChildren = children;
    }

    @Override
    public int hashCode() {
        int childrenHash = mChildren == null ? 0 : mChildren.hashCode();
        return getFullDeckName().hashCode() + childrenHash;
    }


    /**
     * Whether both elements have the same structure and numbers.
     * @param object
     * @return
     */
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof AbstractDeckTreeNode)) {
            return false;
        }
        AbstractDeckTreeNode<?> tree = (AbstractDeckTreeNode) object;
        return Decks.equalName(getFullDeckName(), tree.getFullDeckName()) &&
            (mChildren == null && tree.mChildren == null) || // Would be the case if both are null, or the same pointer
            (mChildren != null && mChildren.equals(tree.mChildren))
            ;
    }

    public Collection getCol() {
        return mCol;
    }

    public boolean shouldDisplayCounts() {
        return false;
    }

    /* Number of new cards to see today known to be in this deck and its descendants. The number to show to user*/
    public int getNewCount() {
        throw new UnsupportedOperationException();
    }

    /* Number of lrn cards (or repetition) to see today known to be in this deck and its descendants. The number to show to user*/
    public int getLrnCount() {
        throw new UnsupportedOperationException();
    }

    /* Number of rev cards to see today known to be in this deck and its descendants. The number to show to user*/
    public int getRevCount() {
        throw new UnsupportedOperationException();
    }


    public boolean knownToHaveRep() {
        return false;
    }


    public abstract T withChildren(List<T> children);
}
