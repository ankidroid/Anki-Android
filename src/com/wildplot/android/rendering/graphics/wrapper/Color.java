package com.wildplot.android.rendering.graphics.wrapper;

public class Color {
    //android.graphics.Color
    private int colorValue;
    public static final Color red = new Color(android.graphics.Color.RED);
    public static final Color RED = new Color(android.graphics.Color.RED);
    
    public static final Color BLACK   = new Color(android.graphics.Color.BLACK);
    public static final Color black   = new Color(android.graphics.Color.BLACK);
    
    public static final Color BLUE = new Color(android.graphics.Color.BLUE);
    public static final Color blue = new Color(android.graphics.Color.BLUE);
    
    public static final Color CYAN = new Color(android.graphics.Color.CYAN);
    public static final Color cyan = new Color(android.graphics.Color.CYAN);
    
    public static final Color DARK_GRAY = new Color(android.graphics.Color.DKGRAY);
    public static final Color darkgray = new Color(android.graphics.Color.DKGRAY);
    
    public static final Color GRAY = new Color(android.graphics.Color.GRAY);
    public static final Color gray = new Color(android.graphics.Color.GRAY);
    
    public static final Color GREEN = new Color(android.graphics.Color.GREEN);
    public static final Color green = new Color(android.graphics.Color.GREEN);
    
    public static final Color LIGHT_GRAY = new Color(android.graphics.Color.LTGRAY);
    public static final Color lightGray = new Color(android.graphics.Color.LTGRAY);
    
    public static final Color MAGENTA = new Color(android.graphics.Color.MAGENTA);
    public static final Color magenta = new Color(android.graphics.Color.MAGENTA);
    
    public static final Color TRANSPARENT = new Color(android.graphics.Color.TRANSPARENT);
    
    public static final Color WHITE = new Color(android.graphics.Color.WHITE);
    public static final Color white = new Color(android.graphics.Color.WHITE);
    
    public static final Color YELLOW = new Color(android.graphics.Color.YELLOW);
    public static final Color yellow = new Color(android.graphics.Color.YELLOW);
    public Color(int colorValue) {
        super();
        this.colorValue = colorValue;
    }
    
    public Color(int r, int g, int b){
        this.colorValue = android.graphics.Color.rgb(r, g, b);
    }
    
    public Color(int r, int g, int b, int a){
        this.colorValue = android.graphics.Color.argb(a, r, g, b);
    }
    
    public Color(float r, float g, float b, float a){
        this.colorValue = android.graphics.Color.argb((int)(a*255), (int)(r*255), (int)(g*255), (int)(b*255));
    }
    
    public Color(float r, float g, float b){
        this.colorValue = android.graphics.Color.rgb((int)(r*255), (int)(g*255), (int)(b*255));
    }

    
    
    
    public int getColorValue() {
        return colorValue;
    }
    
    public int getRed(){
        return android.graphics.Color.red(colorValue);
    }
    
    public int getGreen(){
        return android.graphics.Color.green(colorValue);
    }
    
    public int getBlue(){
        return android.graphics.Color.blue(colorValue);
    }
    
    public Color brighter(){
        
        float[] hsv = new float[3];
        int newColor = 0;
        android.graphics.Color.colorToHSV(colorValue, hsv);
        hsv[2] *= 1.6f; // value component
        return new Color(android.graphics.Color.HSVToColor(hsv));
    }
    
    public Color darker(){
        
        float[] hsv = new float[3];
        int newColor = 0;
        android.graphics.Color.colorToHSV(colorValue, hsv);
        hsv[2] *= 0.8f; // value component
        return new Color(android.graphics.Color.HSVToColor(hsv));
    }
    
}
