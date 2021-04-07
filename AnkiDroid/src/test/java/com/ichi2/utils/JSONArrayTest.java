package com.ichi2.utils;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class JSONArrayTest{
    JSONArray x = new JSONArray();

// we don't need to test other put functions as it's the same way of work
    // what we have from this test is every entry is put in the correct place in the arraylist.
     @Test
    public void putTest(){
         JSONArray array = null;
         for(int index = 0 , value = 100 ; index<100 ; index++ , value-- ){
             array = x.put(index ,value) ;
             Assertions.assertEquals(value,array.getInt(index));
         }
    }
}
