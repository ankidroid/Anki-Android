package com.ichi2.anki.multimediacard.impl;



/**
 * @author zaur
 * 
 *      Base for all field types.
 *      
 *      Controlls modifications. This is done to not to save anything, if the field has not been modified.
 *
 */
public class FieldBase
{
    boolean mIsModified = false;

    void setThisModified()
    {
        mIsModified = true;
    }

    boolean getThisModified()
    {
        return mIsModified;
    }
}
