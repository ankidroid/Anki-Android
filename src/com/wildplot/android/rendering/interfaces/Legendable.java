package com.wildplot.android.rendering.interfaces;

import com.wildplot.android.rendering.graphics.wrapper.Color;

/**
 * Created by mig on 07.07.2014.
 */
public interface Legendable {
    public Color getColor();
    public String getName();
    public boolean nameIsSet();
}
