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
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.Xfermode;

/**
 * Wrapper of swing/awt graphics class for android use
 * @author Michael Goldbach
 *
 */
public class GraphicsWrap {
    private Canvas canvas;
    private Paint paint;
    
    
    
    public GraphicsWrap(Canvas canvas, Paint paint) {
        super();
        this.canvas = canvas;
        this.paint = paint;
    }

    public void drawLine(float x1, float y1, float x2, float y2){
        Style oldStyle = paint.getStyle();
        paint.setStyle(Style.FILL_AND_STROKE);
        canvas.drawLine(x1, y1, x2, y2, paint);
        paint.setStyle(oldStyle);
    }
    
    public void drawRect(float x, float y, float width, float height){
        Style oldStyle = paint.getStyle();
        paint.setStyle(Style.STROKE);
        canvas.drawRect(x, y, x+width, y+height, paint);
        paint.setStyle(oldStyle);
    }
    
    public void fillRect(float x, float y, float width, float height){
//        boolean isAntiAlias = paint.isAntiAlias();
//
//        paint.setAntiAlias(true);
        Style oldStyle = paint.getStyle();
        paint.setStyle(Style.FILL);
        canvas.drawRect(x, y, x+width, y+height, paint);
        paint.setStyle(oldStyle);
//        paint.setAntiAlias(isAntiAlias);
    }

    public StrokeWrap getStroke(){
        return new StrokeWrap(paint.getStrokeWidth());
    }
    
    public void setStroke(StrokeWrap stroke){
        paint.setStrokeWidth(stroke.getStrokeSize());
    }
    
    public RectangleWrap getClipBounds(){
        return new RectangleWrap(canvas.getClipBounds());
    }
    
    public void setClip(RectangleWrap rectangle){
        //seems to be not necessary
    }
    
    public ColorWrap getColor(){
        return new ColorWrap(paint.getColor());
    }
    
    public void setColor(ColorWrap color){
        paint.setColor(color.getColorValue());
    }
    
    public void drawArc(float x, float y, float width, float height, float startAngle, float arcAngle){
        if (arcAngle == 0) {
            return;
        }
        Style oldStyle = paint.getStyle();
        paint.setStyle(Style.STROKE);
        RectF rectF = new RectF(x,y,x+width,y+height);
        canvas.drawArc(rectF,startAngle,arcAngle,true,paint);
        paint.setStyle(oldStyle);
    }
    public void fillArc(float x, float y, float width, float height, float startAngle, float arcAngle){
        if (arcAngle == 0) {
            return;
        }
        Style oldStyle = paint.getStyle();
        paint.setStyle(Style.FILL);
        RectF rectF = new RectF(x,y,x+width,y+height);
        canvas.drawArc(rectF, startAngle, arcAngle, true, paint);
        paint.setStyle(oldStyle);
    }
    
    public void drawImage(BufferedImageWrap image, String tmp, float x, float y){
        //System.err.println("drawImage: " + image.getBitmap().getWidth() + " : "+ image.getBitmap().getHeight());
        Xfermode mode  = paint.getXfermode();
        paint.setXfermode(new PorterDuffXfermode(Mode.SRC_OVER));
        //canvas.drawBitmap(image.getBitmap(), x, y, paint);
        Bitmap bitmap = image.getBitmap();
        bitmap.prepareToDraw();
        canvas.drawBitmap(bitmap, canvas.getClipBounds(), canvas.getClipBounds(), paint);
        paint.setXfermode(mode);
    }
    
    public void drawString(String text, float x, float y){
        Style oldStyle = paint.getStyle();
        paint.setStyle(Style.FILL);
        canvas.drawText(text, x, y, paint);
        paint.setStyle(oldStyle);
        
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public Paint getPaint() {
        return paint;
    }

    public Object getFont() {
        // TODO Auto-generated method stub
        return null;
    }

    public FontMetricsWrap getFontMetrics(Object font) {
        return new FontMetricsWrap(this);
    }
    
    public FontMetricsWrap getFontMetrics() {
        return new FontMetricsWrap(this);
    }
    
    public void dispose(){
        //TODO: search if there is something to do with it
    }

    public int save(){
        return canvas.save();
    }

    public void restore(){
        canvas.restore();
    }

    public void rotate(float degree, float x, float y){
        canvas.rotate(degree, x, y);
    }

    public float getFontSize(){
        return paint.getTextSize();
    }
    public void setFontSize(float size){
        paint.setTextSize(size);
    }

    public void setTypeface(Typeface typeface){
        paint.setTypeface(typeface);
    }
    public Typeface getTypeface(){
        return paint.getTypeface();
    }

    public void setShadow(float radius, float dx, float dy, ColorWrap color){
        int colorVal = color.getColorValue();
        paint.setShadowLayer(radius, dx, dy, colorVal);
    }
    public void unsetShadow(){
        paint.clearShadowLayer();
    }
    
}
