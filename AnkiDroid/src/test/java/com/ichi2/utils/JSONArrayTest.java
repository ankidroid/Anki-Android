package com.ichi2.utils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class JSONArrayTest{
    JSONArray x ;
    JSONArray array ;
    int initialLength;

    @Before
    public void init(){
        x = new JSONArray() ;
    }

    public void initializeArray(){
        for(int index = 0 , value = 1 ; index<100 ; index++  ){
            array = x.put(index ,value) ;
        }
        initialLength = 100 ;
    }

     @Test
    public void putTestInExistingArray(){
        initializeArray();
        for(int i = 10 ; i < 20 ; i++ ) {
             x.put(i , 20) ;
        }
         for(int i =10 ; i < 20 ; i++){
             Assert.assertEquals(20 , array.get(i));
         }
         for(int i = 0 , j =20 ;i <10 ; i++ , j++ ){
             Assert.assertEquals(1 , array.get(i));
         }
         Assert.assertEquals(initialLength , array.length());

    }
    @Test
    public void putTestInEmptyArray(){
        array = x.put(200 , null);
        Assert.assertEquals(201 , array.length());
    }

    /**
     * Actually the array will not expand as this value is set to the in the index in arraylist not add to .
     */
    @Test(expected = AssertionError.class)
    public void putTestAtTheBeginningOfExistingArrayButNotExpands(){
        initializeArray();
        array = x.put(0 , 200) ;
        Assert.assertEquals(initialLength +1 , array.length());
    }

    /**
     * Actually the array will not expand as this value is set to the in the index in arraylist not add to .
     */
    @Test(expected = AssertionError.class)
    public void putTestAtTheMidleOfExistingArrayButNotExpands(){
        initializeArray();
        array = x.put(array.length()/2,1);
        Assert.assertEquals(initialLength +1  , array.length());
    }

    /**
     * Actually the array will  expand as this value is set to the in the index in arraylist,
     * but he check first if the array.length < index , then fill every empty indexes with null until reach index  .
     */
    @Test
    public void putTestAtTheEndOfExistingArray(){
        initializeArray();
        array = x.put(102,1);
        Assert.assertEquals(103 , array.length());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void putTestOutOfBoundary(){
        x.put(-1 ,100) ;
    }

    @Test
    public void getTest(){
        x.put(200 , 1150) ;
        Assert.assertEquals(1150 , x.get(200));

    }
    @Test(expected = JSONException.class)
    public void getTestNull(){
        x.put(199 , null) ;
        x.get(199);
    }

    @Test(expected = JSONException.class)
    public void getTestValueOutOfIndex(){
        x.get(200) ;
    }

}
