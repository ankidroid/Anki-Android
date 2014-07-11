/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Michael Goldbach <trashcutter@googlemail.com>                     *
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
package com.ichi2.anki.stats;

import android.graphics.Bitmap;
import android.util.Log;
import android.widget.ImageView;
import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Stats;
import com.wildplot.android.rendering.*;
import com.wildplot.android.rendering.graphics.wrapper.BufferedImage;
import com.wildplot.android.rendering.graphics.wrapper.Color;
import com.wildplot.android.rendering.graphics.wrapper.Graphics2D;
import com.wildplot.android.rendering.graphics.wrapper.Rectangle;

/**
 * Created by mig on 06.07.2014.
 */
public class Forecast {
    private boolean mIsWholeCollection = false;
    private ImageView mImageView;
    private Collection mCollectionData;

    private int mFrameThickness = 60;

    int mMaxCards = 0;
    private int mType;
    private int[] mValueLabels;
    private int[] mColors;
    private int[] mAxisTitles;
    private double[][] mSeriesList;
    private double barThickness = 0.6;
    private double mLastElement = 0;
    private double[][] mCumulative = null;

    public Forecast(ImageView imageView, Collection collectionData, boolean isWholeCollection){
        mImageView = imageView;
        mCollectionData = collectionData;
        mIsWholeCollection = isWholeCollection;
    }

    private void calcStats(int type){
        mType = type;
        Stats stats = new Stats(mCollectionData, mIsWholeCollection);
        stats.calculateDue(type);
        mCumulative = stats.getCumulative();
        mSeriesList = stats.getSeriesList();
        Object[] metaData = stats.getMetaInfo();
        mValueLabels = (int[]) metaData[3];
        mColors = (int[]) metaData[4];
        mAxisTitles = (int[]) metaData[5];
        mMaxCards = (Integer) metaData[7];
        mLastElement = (Double) metaData[10];
    }

    public Bitmap renderChart(int type){
        calcStats(type);


        int height = mImageView.getMeasuredHeight();
        int width = mImageView.getMeasuredWidth();

        Log.d(AnkiStatsTaskHandler.TAG,"heigth: " + height + ", width: " + width + ", " + mImageView.getWidth());

        if(height <=0 || width <= 0){
            return null;
        }

        BufferedImage bufferedFrameImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bufferedFrameImage.createGraphics();
        Rectangle rect = new Rectangle(width, height);
        g.setClip(rect);
        g.setColor(Color.BLACK);
        float textSize = AnkiStatsTaskHandler.getInstance().getmStandardTextSize()*0.75f;
        g.setFontSize(textSize);

        float FontHeigth = g.getFontMetrics().getHeight(true);
        mFrameThickness = Math.round( FontHeigth * 4.0f);
        //System.out.println("frame thickness: " + mFrameThickness);

        int end = 0;
        switch (mType){
            case Stats.TYPE_MONTH:
                end = 31;
                break;
            case Stats.TYPE_YEAR:
                end = 52;
                break;
            default:
            case Stats.TYPE_LIFE:
                end = (int)mLastElement;
                break;

        }

        PlotSheet plotSheet = new PlotSheet(-0.5, end + 0.5, 0, mMaxCards*1.1);
        double xTics = ticsCalcX(150, rect, 0, end);
        double yTics = ticsCalcY(150, rect, 0, mMaxCards);


        plotSheet.setFrameThickness(mFrameThickness);

        //no title because of tab title
        //plotSheet.setTitle(mImageView.getResources().getString(mTitle));



        XAxis xaxis = new XAxis(plotSheet, 0, xTics, xTics/2.0);
        YAxis yaxis = new YAxis(plotSheet, 0, yTics, yTics/2.0);
        xaxis.setOnFrame();
        xaxis.setName(mImageView.getResources().getStringArray(R.array.due_x_axis_title)[mAxisTitles[0]]);
        xaxis.setIntegerNumbering(true);
        yaxis.setIntegerNumbering(true);
        yaxis.setName(mImageView.getResources().getString(mAxisTitles[1]));
        yaxis.setOnFrame();


        double[][] bars = new double[2][];
        bars[0] = mSeriesList[0];
        bars[1] = mSeriesList[1];
        BarGraph barGraph = new BarGraph(plotSheet,barThickness, bars, new Color(mImageView.getResources().getColor(mColors[0])));
        barGraph.setFilling(true);
        barGraph.setName(mImageView.getResources().getString(mValueLabels[0]));
        //barGraph.setFillColor(Color.GREEN.darker());
        barGraph.setFillColor(new Color(mImageView.getResources().getColor(mColors[0])));
        double[][] bars2 = new double[2][];
        bars2[0] = mSeriesList[0];
        bars2[1] = mSeriesList[2];
        BarGraph barGraphMature = new BarGraph(plotSheet,barThickness, bars2, new Color(mImageView.getResources().getColor(mColors[1])));
        barGraphMature.setFilling(true);
        barGraphMature.setFillColor(new Color(mImageView.getResources().getColor(mColors[1])));
        barGraphMature.setName(mImageView.getResources().getString(mValueLabels[1]));

        double[][] cumulative = mCumulative;
        PlotSheet hiddenPlotSheet = new PlotSheet(-0.5, end + 0.5, 0, cumulative[1][cumulative[1].length-1]*1.1);     //for second y-axis

        Lines lines = new Lines(hiddenPlotSheet,cumulative ,Color.black);
        lines.setSize(3f);
        lines.setName(mImageView.getResources().getString(R.string.stats_cumulative));
        lines.setShadow(5f, 3f, 3f, Color.BLACK);

        hiddenPlotSheet.setFrameThickness(mFrameThickness);
        double rightYtics = ticsCalc(150, rect,  cumulative[1][cumulative[1].length-1]*1.1);
        YAxis rightYaxis = new YAxis(hiddenPlotSheet, 0, rightYtics, rightYtics/2.0);
        rightYaxis.setIntegerNumbering(true);
        rightYaxis.setName(mImageView.getResources().getString(mAxisTitles[2]));
        rightYaxis.setOnRightSideFrame();

        int red = Color.LIGHT_GRAY.getRed();
        int green = Color.LIGHT_GRAY.getGreen();
        int blue = Color.LIGHT_GRAY.getBlue();

        Color newGridColor = new Color(red,green,blue, 222);

        XGrid xGrid = new XGrid(plotSheet, 0, 150);
        YGrid yGrid = new YGrid(plotSheet, 0, 150);

        xGrid.setColor(newGridColor);
        yGrid.setColor(newGridColor);
        plotSheet.setFontSize(textSize);

        plotSheet.addDrawable(barGraph);
        plotSheet.addDrawable(barGraphMature);
        plotSheet.addDrawable(lines);
        plotSheet.addDrawable(xaxis);
        plotSheet.addDrawable(yaxis);
        plotSheet.addDrawable(rightYaxis);
        plotSheet.addDrawable(xGrid);
        plotSheet.addDrawable(yGrid);
        plotSheet.paint(g);
        Bitmap bitmap = bufferedFrameImage.getBitmap();
        bitmap.prepareToDraw();
        return bitmap;
    }




    public double ticsCalcX(int pixelDistance, Rectangle field, int start, int end){
        double deltaRange = end - start;
        int ticlimit = field.width/pixelDistance;
        double tics = Math.pow(10, (int)Math.log10(deltaRange/ticlimit));
        while(2.0*(deltaRange/(tics)) <= ticlimit) {
            tics /= 2.0;
        }
        while((deltaRange/(tics))/2 >= ticlimit) {
            tics *= 2.0;
        }
        return tics;
    }

    public double ticsCalcY(int pixelDistance, Rectangle field, int start, int end){
        double deltaRange = end - start;
        int ticlimit = field.height/pixelDistance;
        double tics = Math.pow(10, (int)Math.log10(deltaRange/ticlimit));
        while(2.0*(deltaRange/(tics)) <= ticlimit) {
            tics /= 2.0;
        }
        while((deltaRange/(tics))/2 >= ticlimit) {
            tics *= 2.0;
        }
        return tics;
    }

    public double ticsCalc(int pixelDistance, Rectangle field, double deltaRange){
        int ticlimit = field.height/pixelDistance;
        double tics = Math.pow(10, (int)Math.log10(deltaRange/ticlimit));
        while(2.0*(deltaRange/(tics)) <= ticlimit) {
            tics /= 2.0;
        }
        while((deltaRange/(tics))/2 >= ticlimit) {
            tics *= 2.0;
        }
        return tics;
    }


}
