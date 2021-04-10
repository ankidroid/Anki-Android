package com.ichi2.utils;

import android.view.KeyEvent;

import junit.framework.TestCase;

import org.junit.Test;
import com.ichi2.utils.KeyUtils.*;

import static org.junit.Assert.*;

public class KeyUtilsTest{

    @Test
    public void isDigit() {
        KeyEvent event=new KeyEvent(1,3,4,KeyEvent.KEYCODE_NUMPAD_0,6);
        boolean value = KeyUtils.isDigit(event);
        assertTrue("The passed value is not a digit",value);
    }

    @Test
    public void getDigit() {
        KeyEvent event=new KeyEvent(1,3,4,KeyEvent.KEYCODE_NUMPAD_0,6);
        int value = KeyUtils.getDigit(event);
        boolean flag=false;
        if((value > 0)&&( value <9)){
            flag=true;
        }
        else{
            flag=false;
        }
        assertTrue("The value received is a digit",flag);
    }
}