package com.ichi2.utils;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import static org.junit.Assert.assertEquals;

public class HashUtilTest {

    /** Size of the table used to save a map.*/
    private int mapThreshold(HashMap<?, ?> map) throws NoSuchFieldException, IllegalAccessException {
        Field thresholdField = HashMap.class.getDeclaredField("threshold");
        thresholdField.setAccessible(true);
        return thresholdField.getInt(map);
    }

    /** Threshold of a set. Not a setter*/
    private int setThreshold(HashSet<?> set) throws NoSuchFieldException, IllegalAccessException {
        Field map = HashMap.class.getDeclaredField("map");
        map.setAccessible(true);
        return mapThreshold((HashMap) map.get(set));
    }

    @Test
    public void thresholdIsNotModifiedAfterCreationWithFixedSizeCollection() throws NoSuchFieldException, IllegalAccessException {
        for (int threshold : new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 20, 100, 1000}) {
            HashSet<Integer> set = HashUtil.HashSetInit(threshold);
            HashMap<Integer, Integer> map = HashUtil.HashMapInit(threshold);
            int init = mapThreshold(map);
            //assertEquals(init, setThreshold(set));
            for (Integer i = 0; i < threshold; i++) {
                set.add(i);
                map.put(i, i);
            }
            assertEquals(init, mapThreshold(map));
            //assertEquals(init, setThreshold(set));
        }
    }


}
