package com.ichi2.anki;

import com.ichi2.libanki.sched.AbstractDeckTreeNode;

import java.util.Comparator;

public class AbstractDeckTreeComparator implements Comparator<AbstractDeckTreeNode<?>> {


        @Override
        public int compare(AbstractDeckTreeNode<?> o1, AbstractDeckTreeNode<?> o2) {
            int minDepth = Math.min(o1.getDepth(), o2.getDepth()) + 1;
            String [] mNameComponentsO1 =o1.getmNameComponents();
            String [] mNameComponetsO2 =o2.getmNameComponents();
            // Consider each subdeck name in the ordering
            for (int i = 0; i < minDepth; i++) {
                int result = mNameComponentsO1[i].compareToIgnoreCase(mNameComponetsO2[i]);
                return result;
            }
            return Integer.compare(o1.getDepth(), o2.getDepth());
        }
    }


