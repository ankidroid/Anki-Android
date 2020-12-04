package com.ichi2.libanki.sched;

import com.ichi2.libanki.Collection;

import java.util.List;

public class DeckTreeNode extends AbstractDeckTreeNode<DeckTreeNode> {
    public DeckTreeNode(Collection col, String mName, long mDid) {
        super(col, mName, mDid);
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
