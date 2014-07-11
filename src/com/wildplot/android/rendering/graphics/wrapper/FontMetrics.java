package com.wildplot.android.rendering.graphics.wrapper;

public class FontMetrics {
    private Graphics g;

    public FontMetrics(Graphics g) {
        super();
        this.g = g;
    }
    
    public float stringWidth(String text){
        return g.getPaint().measureText(text);
        
    }

    public float getHeight() {
        return g.getPaint().getTextSize();
    }

    public float getHeight(boolean foo) {
        return g.getPaint().getTextSize();
    }
}
