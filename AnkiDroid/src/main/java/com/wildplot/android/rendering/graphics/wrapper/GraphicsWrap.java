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

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Wrapper of swing/awt graphics class for android use
 *
 * @author Michael Goldbach
 */
@SuppressLint("NonPublicNonStaticFieldName")
public class GraphicsWrap {
    private final Canvas canvas;
    private final Paint paint;


    public GraphicsWrap(Canvas canvas, Paint paint) {
        super();
        this.canvas = canvas;
        this.paint = paint;
    }


    public void drawLine(float x1, float y1, float x2, float y2) {
        Style oldStyle = paint.getStyle();
        paint.setStyle(Style.FILL_AND_STROKE);
        canvas.drawLine(x1, y1, x2, y2, paint);
        paint.setStyle(oldStyle);
    }


    public void drawRect(float x, float y, float width, float height) {
        Style oldStyle = paint.getStyle();
        paint.setStyle(Style.STROKE);
        canvas.drawRect(x, y, x + width, y + height, paint);
        paint.setStyle(oldStyle);
    }


    public void fillRect(float x, float y, float width, float height) {

        Style oldStyle = paint.getStyle();
        paint.setStyle(Style.FILL);
        canvas.drawRect(x, y, x + width, y + height, paint);
        paint.setStyle(oldStyle);
    }


    public StrokeWrap getStroke() {
        return new StrokeWrap(paint.getStrokeWidth());
    }


    public void setStroke(StrokeWrap stroke) {
        paint.setStrokeWidth(stroke.getStrokeSize());
    }


    public RectangleWrap getClipBounds() {
        return new RectangleWrap(canvas.getClipBounds());
    }
    
    public ColorWrap getColor(){
        return new ColorWrap(paint.getColor());
    }


    public void setColor(ColorWrap color) {
        paint.setColor(color.getColorValue());
    }


    public void drawArc(float x, float y, float width, float height, float startAngle, float arcAngle) {
        if (arcAngle == 0) {
            return;
        }
        Style oldStyle = paint.getStyle();
        paint.setStyle(Style.STROKE);
        RectF rectF = new RectF(x, y, x + width, y + height);
        canvas.drawArc(rectF, startAngle, arcAngle, true, paint);
        paint.setStyle(oldStyle);
    }


    public void fillArc(float x, float y, float width, float height, float startAngle, float arcAngle) {
        if (arcAngle == 0) {
            return;
        }
        Style oldStyle = paint.getStyle();
        paint.setStyle(Style.FILL);
        RectF rectF = new RectF(x, y, x + width, y + height);
        canvas.drawArc(rectF, startAngle, arcAngle, true, paint);
        paint.setStyle(oldStyle);
    }


    public void drawString(String text, float x, float y) {
        Style oldStyle = paint.getStyle();
        paint.setStyle(Style.FILL);
        canvas.drawText(text, x, y, paint);
        paint.setStyle(oldStyle);

    }

    public Paint getPaint() {
        return paint;
    }


    public FontMetricsWrap getFontMetrics() {
        return new FontMetricsWrap(this);
    }


    public int save() {
        return canvas.save();
    }


    public void restore() {
        canvas.restore();
    }


    public void rotate(float degree, float x, float y) {
        canvas.rotate(degree, x, y);
    }


    public float getFontSize() {
        return paint.getTextSize();
    }


    public void setFontSize(float size) {
        paint.setTextSize(size);
    }


    public void setTypeface(Typeface typeface) {
        paint.setTypeface(typeface);
    }


    public Typeface getTypeface() {
        return paint.getTypeface();
    }
}
