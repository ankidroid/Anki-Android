package com.ichi2.libanki.sched;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Decks;
import com.ichi2.utils.JSONObject;

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
 */
public class DeckDueTreeNode implements Comparable {
    private final Collection mCol;
    private final String mName;
    private final String[] mNameComponents;
    private long mDid;
    private int mRevCount;
    private int mLrnCount;
    private int mNewCount;
    @Nullable
    private List<DeckDueTreeNode> mChildren = null;

    public DeckDueTreeNode(Collection col, String mName, long mDid, int mRevCount, int mLrnCount, int mNewCount) {
        this.mCol = col;
        this.mName = mName;
        this.mDid = mDid;
        this.mRevCount = mRevCount;
        this.mLrnCount = mLrnCount;
        this.mNewCount = mNewCount;
        this.mNameComponents = Decks.path(mName);
    }

    /**
     * Sort on the head of the node.
     */
    @Override
    public int compareTo(Object other) {
        DeckDueTreeNode rhs = (DeckDueTreeNode) other;
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
        if (rhs.getDepth() > getDepth()) {
            return -1;
        } else {
            return 1;
        }
    }

    @Override
    public String toString() {
        StringBuffer buf = new StringBuffer();
        toString(buf);
        return buf.toString();
    }

    public void toString(StringBuffer buf) {
        for (int i = 0; i < getDepth(); i++ ) {
            buf.append("  ");
        }
        buf.append(String.format(Locale.US, "%s, %d, %d, %d, %d\n",
                mName, mDid, mRevCount, mLrnCount, mNewCount));
        if (mChildren == null) {
            return;
        }
        for (DeckDueTreeNode children : mChildren) {
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

    /**
     * @return The children of this deck. Note that they are set
     * in the data structure returned by DeckDueTree but are
     * always empty when the data structure is returned by
     * deckDueList.*/
    public List<DeckDueTreeNode> getChildren() {
        return mChildren;
    }

    /**
     * @return whether this node as any children. */
    public boolean hasChildren() {
        return mChildren != null && !mChildren.isEmpty();
    }

    public void setChildren(@NonNull List<DeckDueTreeNode> children, boolean addRev) {
        mChildren = children;
        // tally up children counts
        for (DeckDueTreeNode ch : children) {
            mLrnCount += ch.getLrnCount();
            mNewCount += ch.getNewCount();
            if (addRev) {
                mRevCount += ch.getRevCount();
            }
        }
        // limit the counts to the deck's limits
        JSONObject conf = mCol.getDecks().confForDid(mDid);
        if (conf.getInt("dyn") == 0) {
            JSONObject deck = mCol.getDecks().get(mDid);
            limitNewCount(conf.getJSONObject("new").getInt("perDay") - deck.getJSONArray("newToday").getInt(1));
            if (addRev) {
                limitRevCount(conf.getJSONObject("rev").getInt("perDay") - deck.getJSONArray("revToday").getInt(1));
            }
        }
    }

    @Override
    public int hashCode() {
        int childrenHash = mChildren.hashCode();
        return getFullDeckName().hashCode() + mRevCount + mLrnCount + mNewCount + (int) (childrenHash ^ (childrenHash >>> 32));
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
                (mChildren == tree.mChildren || // Would be the case if both are null, or the same pointer
                mChildren.equals(tree.mChildren))
                ;
    }
}
