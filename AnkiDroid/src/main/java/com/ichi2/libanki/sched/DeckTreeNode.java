package com.ichi2.libanki.sched;

import com.ichi2.libanki.Collection;

public class DeckTreeNode extends AbstractDeckTreeNode<DeckTreeNode> {
    public DeckTreeNode(Collection col, String mName, long mDid) {
        super(col, mName, mDid);
    }
}
