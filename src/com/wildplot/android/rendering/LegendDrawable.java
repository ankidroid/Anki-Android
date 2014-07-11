package com.wildplot.android.rendering;

import com.wildplot.android.rendering.graphics.wrapper.Color;
import com.wildplot.android.rendering.graphics.wrapper.Graphics;
import com.wildplot.android.rendering.interfaces.Drawable;
import com.wildplot.android.rendering.interfaces.Legendable;

/**
 * Created by mig on 10.07.2014.
 */
public class LegendDrawable implements Drawable, Legendable {

    private String mName = "";
    private boolean mNameIsSet = false;



    private Color color = Color.BLACK;

    @Override
    public void paint(Graphics g) {

    }

    @Override
    public boolean isOnFrame() {
        return false;
    }

    @Override
    public void abortAndReset() {

    }

    @Override
    public boolean isClusterable() {
        return false;
    }

    @Override
    public boolean isCritical() {
        return false;
    }

    @Override
    public Color getColor() {
        return color;
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public boolean nameIsSet() {
        return mNameIsSet;
    }

    public void setName(String name){
        mName = name;
        mNameIsSet = true;
    }
    public void setColor(Color color){
        this.color = color;
    }
}
