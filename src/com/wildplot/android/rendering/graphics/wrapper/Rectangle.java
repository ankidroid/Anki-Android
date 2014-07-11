package com.wildplot.android.rendering.graphics.wrapper;

import android.graphics.Rect;

public class Rectangle {
    Rect rect;
    public int x;
    public int y;
    public int width;
    public int height;
    
    public Rectangle(int width, int heigth){
        this(new Rect(0,0,width,heigth));
    }
    
    public Rectangle(Rect rect) {
        super();
        this.rect = rect;
        
        this.x=rect.left;
        this.y=rect.top;
        this.height = rect.height();
        this.width = rect.width();
    }
    
    public int width(){
        return width;
    }
    
    public int height(){
        return height;
    }
    
    public Rect getRect(){
        return new Rect(x, y, x+width, y+height);
    }
    
}
