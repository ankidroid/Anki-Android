package com.ichi2.utils;

import java.io.File;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;

@SuppressLint("NewApi")
public class ExifUtil
{
    public static Bitmap rotateFromCamera(File theFile, Bitmap bmp)
    {
        try
        {
            ExifInterface exif = new ExifInterface(theFile.getPath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
       
            int angle = 0;
       
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90)
            {
                angle = 90;
            }
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_180)
            {
                angle = 180;
            }
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_270)
            {
                angle = 270;
            }
       
            Matrix mat = new Matrix();
            mat.postRotate(angle);
       
            
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mat, true);
            return bmp;
        }catch (Exception e)
        {
            return bmp;
        }
    }
}
