package com.wildplot.android.rendering;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import com.ichi2.anki.stats.AnkiStatsTaskHandler;
import com.wildplot.android.rendering.graphics.wrapper.Graphics;
import com.wildplot.android.rendering.graphics.wrapper.Graphics2D;
import com.wildplot.android.rendering.graphics.wrapper.Rectangle;

public class ChartView extends View{

    private Rectangle mRectangle;
    private PlotSheet mPlotSheet;
    private boolean mDataIsSet;

    public ChartView(Context context) {
        super(context);
    }

    public ChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onDraw(Canvas canvas) {
        if(mDataIsSet){
            Paint paint = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            float textSize = AnkiStatsTaskHandler.getInstance().getmStandardTextSize()*0.75f;
            paint.setTextSize(textSize);
            Graphics g = new Graphics2D(canvas, paint);

            Rect field = new Rect();
            this.getDrawingRect(field);
            mRectangle = new Rectangle(field);
            g.setClip(mRectangle);
            if(mPlotSheet != null){
                mPlotSheet.paint(g);
            }
            else {
                super.onDraw(canvas);
            }
        } else {
            super.onDraw(canvas);
        }

    }

    public void setData(PlotSheet plotSheet){
        mPlotSheet = plotSheet;
        mDataIsSet = true;
    }
}
