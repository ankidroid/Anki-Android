/****************************************************************************************
 * Copyright (c) 2014 Michael Goldbach <michael@wildplot.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.wildplot.android.rendering.graphics.wrapper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PorterDuff.Mode;

public class BufferedImageWrap {
    private Bitmap bitmap;
    
    public static final Bitmap.Config TYPE_INT_ARGB = Bitmap.Config.ARGB_8888;
    
    public BufferedImageWrap(int width, int height, Bitmap.Config bitmapConfig){
        bitmap = Bitmap.createBitmap(width, height, bitmapConfig);
    }
    
    public GraphicsWrap createGraphics(){
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(android.graphics.Color.TRANSPARENT, Mode.CLEAR);
        Paint paint = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Style.STROKE);
//        System.err.println("XFERMODE: "+paint.getXfermode().toString());
//        Paint transPainter = new Paint();
//        transPainter.setXfermode(new PorterDuffXfermode(Mode.CLEAR));
//                   
//        canvas.drawRect(0, 0, bitmap.getWidth(), bitmap.getHeight(), transPainter);
        return new GraphicsWrap(canvas, paint);
    }
    
    public GraphicsWrap getGraphics(){
        return createGraphics();
    }

    public Bitmap getBitmap() {
        return bitmap;
    }
    
    
}
