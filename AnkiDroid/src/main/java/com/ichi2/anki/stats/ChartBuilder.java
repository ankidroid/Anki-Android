/****************************************************************************************
 * Copyright (c) 2014 Michael Goldbach <michael@m-goldbach.net>                         *
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

import android.graphics.Paint;

import android.widget.TextView;
import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Stats;
import com.wildplot.android.rendering.*;
import com.wildplot.android.rendering.graphics.wrapper.ColorWrap;
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap;

import timber.log.Timber;


public class ChartBuilder {
    private final double yAxisStretchFactor = 1.05;
    private final Stats.ChartType mChartType;
    private boolean mIsWholeCollection = false;
    private ChartView mChartView;
    private Collection mCollectionData;
    private int mDesiredPixelDistanceBetweenTicks = 200;
    private float barOpacity = 0.7f;

    private int mFrameThickness = 60;

    int mMaxCards = 0;
    private int mType;
    private int[] mValueLabels;
    private int[] mColors;
    private int[] mAxisTitles;
    private double[][] mSeriesList;
    private double mBarThickness = 0.6;
    private double mLastElement = 0;
    private double[][] mCumulative = null;
    private double mFirstElement;
    private boolean mBackwards;
    private boolean mHasColoredCumulative;
    private double mMcount;
    private boolean mDynamicAxis;

    public ChartBuilder(ChartView chartView, Collection collectionData, boolean isWholeCollection, Stats.ChartType chartType){
        mChartView = chartView;
        mCollectionData = collectionData;
        mIsWholeCollection = isWholeCollection;
        mChartType = chartType;
    }

    private void calcStats(int type){
        mType = type;
        Stats stats = new Stats(mCollectionData, mIsWholeCollection);
        switch (mChartType){
            case FORECAST:
                stats.calculateDue(mType);
                break;
            case REVIEW_COUNT:
                stats.calculateDone(mType, true);
                break;
            case REVIEW_TIME:
                stats.calculateDone(mType, false);
                break;
            case INTERVALS:
                stats.calculateIntervals(mType);
                break;
            case HOURLY_BREAKDOWN:
                stats.calculateBreakdown(mType);
                break;
            case WEEKLY_BREAKDOWN:
                stats.calculateWeeklyBreakdown(mType);
                break;
            case ANSWER_BUTTONS:
                stats.calculateAnswerButtons(mType);
                break;
            case CARDS_TYPES:
                stats.calculateCardsTypes(mType);
                break;
        }
        mCumulative = stats.getCumulative();
        mSeriesList = stats.getSeriesList();
        Object[] metaData = stats.getMetaInfo();
        mBackwards = (Boolean) metaData[2];
        mValueLabels = (int[]) metaData[3];
        mColors = (int[]) metaData[4];
        mAxisTitles = (int[]) metaData[5];
        mMaxCards = (Integer) metaData[7];
        mLastElement = (Double) metaData[10];
        mFirstElement = (Double) metaData[9];
        mHasColoredCumulative = (Boolean) metaData[19];
        mMcount = (Double) metaData[18];
        mDynamicAxis = (Boolean) metaData[20];
    }

    public PlotSheet renderChart(int type){
        calcStats(type);
        Paint paint = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);


        int height = mChartView.getMeasuredHeight();
        int width = mChartView.getMeasuredWidth();
        Timber.d("height: %d, width: %d, %d", height, width, mChartView.getWidth());


        if(height <=0 || width <= 0){
            return null;
        }


        RectangleWrap rect = new RectangleWrap(width, height);
        float textSize = AnkiStatsTaskHandler.getInstance().getmStandardTextSize()*0.85f;
        paint.setTextSize(textSize);
        float FontHeigth = paint.getTextSize();
        mDesiredPixelDistanceBetweenTicks = Math.round(paint.measureText("100000")*2.6f);
        mFrameThickness = Math.round( FontHeigth * 4.0f);

        //System.out.println("frame thickness: " + mFrameThickness);

        PlotSheet plotSheet = new PlotSheet(mFirstElement-0.5, mLastElement + 0.5, 0, mMaxCards*yAxisStretchFactor);
        plotSheet.setFrameThickness(mFrameThickness*0.66f, mFrameThickness*0.66f, mFrameThickness, mFrameThickness*0.9f);
        plotSheet.setFontSize(textSize);

        if(mChartType == Stats.ChartType.CARDS_TYPES){
            return createPieChart(plotSheet);
        }

        PlotSheet hiddenPlotSheet = new PlotSheet(mFirstElement-0.5, mLastElement + 0.5, 0, mMcount*yAxisStretchFactor);     //for second y-axis
        hiddenPlotSheet.setFrameThickness(mFrameThickness*0.66f, mFrameThickness*0.66f, mFrameThickness, mFrameThickness*0.9f);

        setupBarGraphs(plotSheet, hiddenPlotSheet);
        setupCumulative(plotSheet, hiddenPlotSheet);

        double xTicks = ticksCalcX(mDesiredPixelDistanceBetweenTicks, rect, mFirstElement, mLastElement);
        setupXaxis(plotSheet, xTicks, true);

        double yTicks = ticksCalcY(mDesiredPixelDistanceBetweenTicks, rect, 0, mMaxCards*yAxisStretchFactor);
        setupYaxis(plotSheet,hiddenPlotSheet, yTicks, mAxisTitles[1], false, true);
        double rightYtics = ticsCalc(mDesiredPixelDistanceBetweenTicks, rect,  mMcount*yAxisStretchFactor);
        setupYaxis(plotSheet,hiddenPlotSheet, rightYtics, mAxisTitles[2], true, true);
        setupGrid(plotSheet, yTicks*0.5, xTicks*0.5);

        return plotSheet;
    }

    private PlotSheet createPieChart(PlotSheet plotSheet){
        PieChart pieChart = new PieChart(plotSheet, mSeriesList[0]);

        ColorWrap[] colors = {new ColorWrap(mChartView.getResources().getColor(mColors[0])),
                new ColorWrap(mChartView.getResources().getColor(mColors[1])),
                new ColorWrap(mChartView.getResources().getColor(mColors[2])),
                new ColorWrap(mChartView.getResources().getColor(mColors[3]))};
        pieChart.setColors(colors);
        pieChart.setName(mChartView.getResources().getString(mValueLabels[0]) + ": " + (int)mSeriesList[0][0]);
        LegendDrawable legendDrawable1 = new LegendDrawable();
        LegendDrawable legendDrawable2 = new LegendDrawable();
        LegendDrawable legendDrawable3 = new LegendDrawable();
        legendDrawable1.setColor(new ColorWrap(mChartView.getResources().getColor(mColors[1])));
        legendDrawable2.setColor(new ColorWrap(mChartView.getResources().getColor(mColors[2])));
        legendDrawable3.setColor(new ColorWrap(mChartView.getResources().getColor(mColors[3])));

        legendDrawable1.setName(mChartView.getResources().getString(mValueLabels[1]) + ": " + (int)mSeriesList[0][1]);
        legendDrawable2.setName(mChartView.getResources().getString(mValueLabels[2]) + ": " + (int)mSeriesList[0][2]);
        legendDrawable3.setName(mChartView.getResources().getString(mValueLabels[3]) + ": " + (int)mSeriesList[0][3]);


        plotSheet.unsetBorder();
        plotSheet.addDrawable(pieChart);
        plotSheet.addDrawable(legendDrawable1);
        plotSheet.addDrawable(legendDrawable2);
        plotSheet.addDrawable(legendDrawable3);

        return plotSheet;
    }

    private void setupBarGraphs(PlotSheet plotSheet, PlotSheet hiddenPlotSheet){
        int length = mSeriesList.length;
        if(mChartType == Stats.ChartType.HOURLY_BREAKDOWN || mChartType == Stats.ChartType.WEEKLY_BREAKDOWN){
            length--;   //there is data in hourly breakdown that is never used (even in Anki-Desktop)
        }
        for(int i = 1; i< length; i++){
            double[][] bars = new double[2][];
            bars[0] = mSeriesList[0];
            bars[1] = mSeriesList[i];

            PlotSheet usedPlotSheet = plotSheet;
            double barThickness = mBarThickness;
            if((mChartType == Stats.ChartType.HOURLY_BREAKDOWN || mChartType == Stats.ChartType.WEEKLY_BREAKDOWN)){
                barThickness = 0.8;
                if(i == 2) {
                    usedPlotSheet = hiddenPlotSheet;
                    barThickness = 0.2;
                }
            }
            ColorWrap color;
            switch (mChartType){
                case ANSWER_BUTTONS:
                case HOURLY_BREAKDOWN:
                case WEEKLY_BREAKDOWN:
                case INTERVALS:
                    color =new ColorWrap(mChartView.getResources().getColor(mColors[i-1]), barOpacity);
                    break;
                case REVIEW_COUNT:
                case REVIEW_TIME:
                case FORECAST:
                    if(i == 1){
                        color =new ColorWrap(mChartView.getResources().getColor(mColors[i-1]), barOpacity);
                        break;
                    }
                default:
                    color =new ColorWrap(mChartView.getResources().getColor(mColors[i-1]));
            }

            BarGraph barGraph = new BarGraph(usedPlotSheet, barThickness, bars, color);
            barGraph.setFilling(true);
            barGraph.setName(mChartView.getResources().getString(mValueLabels[i - 1]));
            //barGraph.setFillColor(Color.GREEN.darker());
            barGraph.setFillColor(color);
            plotSheet.addDrawable(barGraph);
        }
    }

    private void setupCumulative(PlotSheet plotSheet, PlotSheet hiddenPlotSheet){
        if(mCumulative == null)
            return;
        for(int i = 1; i< mCumulative.length; i++){
            double[][] cumulative = {mCumulative[0], mCumulative[i]};

            ColorWrap usedColor = ColorWrap.BLACK;
            String name = mChartView.getResources().getString(R.string.stats_cumulative);
            if(mHasColoredCumulative){      //also non colored Cumulatives have names!
                usedColor = new ColorWrap(mChartView.getResources().getColor(mColors[i-1]));

            } else {
                if(mChartType == Stats.ChartType.INTERVALS){
                    name = mChartView.getResources().getString(R.string.stats_cumulative_percentage);
                }
            }

            Lines lines = new Lines(hiddenPlotSheet,cumulative ,usedColor);
            lines.setSize(3f);
            lines.setShadow(5f, 2f, 2f, ColorWrap.BLACK);
            if(!mHasColoredCumulative){
                lines.setName(name);
            }
            plotSheet.addDrawable(lines);
        }

    }

    private void setupXaxis(PlotSheet plotSheet, double xTicks, boolean hasName){
        XAxis xAxis = new XAxis(plotSheet, 0, xTicks, xTicks/2.0);
        xAxis.setOnFrame();
        if(hasName) {
            if (mDynamicAxis)
                xAxis.setName(mChartView.getResources().getStringArray(R.array.due_x_axis_title)[mAxisTitles[0]]);
            else {
                xAxis.setName(mChartView.getResources().getString(mAxisTitles[0]));
            }
        }
        double[] timePositions;

        //some explicit x-axis naming:
        switch (mChartType){
            case ANSWER_BUTTONS:
                timePositions = new double[]{1,2,3,6,7,8,9,11,12,13,14};
                xAxis.setExplicitTicks(timePositions, mChartView.getResources().getStringArray(R.array.stats_eases_ticks));
                break;
            case HOURLY_BREAKDOWN:
                timePositions = new double[]{0, 6, 12, 18, 23};
                xAxis.setExplicitTicks(timePositions, mChartView.getResources().getStringArray(R.array.stats_day_time_strings));
                break;
            case WEEKLY_BREAKDOWN:
                timePositions = new double[]{0, 1, 2, 3, 4, 5, 6};
                xAxis.setExplicitTicks(timePositions, mChartView.getResources().getStringArray(R.array.stats_week_days));
                break;
        }

        xAxis.setIntegerNumbering(true);
        plotSheet.addDrawable(xAxis);
    }

    private void setupYaxis(PlotSheet plotSheet, PlotSheet hiddenPlotSheet, double yTicks, int title, boolean isOnRight, boolean hasName){
        YAxis yAxis;
        if(isOnRight && hiddenPlotSheet != null)
            yAxis = new YAxis(hiddenPlotSheet, 0, yTicks, yTicks/2.0);
        else
            yAxis  = new YAxis(plotSheet, 0, yTicks, yTicks/2.0);

        yAxis.setIntegerNumbering(true);
        if(hasName)
            yAxis.setName(mChartView.getResources().getString(title));
        if(isOnRight)
            yAxis.setOnRightSideFrame();
        else
            yAxis.setOnFrame();

        yAxis.setHasNumbersRotated();
        plotSheet.addDrawable(yAxis);
    }

    private void setupGrid(PlotSheet plotSheet, double yTicks, double xTicks){
        int red = ColorWrap.LIGHT_GRAY.getRed();
        int green = ColorWrap.LIGHT_GRAY.getGreen();
        int blue = ColorWrap.LIGHT_GRAY.getBlue();

        ColorWrap newGridColor = new ColorWrap(red,green,blue, 222);

        XGrid xGrid = new XGrid(plotSheet, 0, yTicks);  //ticks are not wrong, xgrid is vertical to yaxis -> yticks
        YGrid yGrid = new YGrid(plotSheet, 0, xTicks);

        double[] timePositions;

        //some explicit x-axis naming:
        switch (mChartType){
            case ANSWER_BUTTONS:
                timePositions = new double[]{1,2,3,6,7,8,9,11,12,13,14};
                yGrid.setExplicitTicks(timePositions);
                break;
            case HOURLY_BREAKDOWN:
                timePositions = new double[]{0, 6, 12, 18, 23};
                yGrid.setExplicitTicks(timePositions);
                break;
            case WEEKLY_BREAKDOWN:
                timePositions = new double[]{0, 1, 2, 3, 4, 5, 6};
                yGrid.setExplicitTicks(timePositions);
                break;
        }
        xGrid.setColor(newGridColor);
        yGrid.setColor(newGridColor);
        plotSheet.addDrawable(xGrid);
        plotSheet.addDrawable(yGrid);
    }

    public PlotSheet createSmallDueChart(double[][] serieslist){
        int height = mChartView.getMeasuredHeight();
        int width = mChartView.getMeasuredWidth();
        Timber.d("SmallDueChart: height: %d, width: %d", height, width);
        Paint paint = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);

        if(height <=0 || width <= 0){
            return null;
        }
        RectangleWrap rect = new RectangleWrap(width, height);
        float textSize = new TextView(mChartView.getContext()).getTextSize()*0.85f;
        paint.setTextSize(textSize);
        float FontHeigth = paint.getTextSize();
        mDesiredPixelDistanceBetweenTicks = Math.round(paint.measureText("100000")*2.6f);
        mFrameThickness = Math.round( FontHeigth * 4.0f);

        //System.out.println("frame thickness: " + mFrameThickness);

        double maxValueY = maxValue(serieslist, 1);
        if(maxValueY <= 0){
            maxValueY = 10;
        }

        PlotSheet plotSheet = new PlotSheet(-0.5, 7.5, 0, maxValueY*yAxisStretchFactor);
        plotSheet.setFrameThickness(mFrameThickness*0.5f, 2f, 2f, mFrameThickness*0.5f);
        plotSheet.setFontSize(textSize);

        BarGraph barGraphYoung = new BarGraph(plotSheet, mBarThickness, new double[][]{serieslist[0], serieslist[1]},
                new ColorWrap(mChartView.getResources().getColor(R.color.stats_young)));
        barGraphYoung.setFilling(true);
        //barGraphYoung.setFillColor(Color.GREEN.darker());
        barGraphYoung.setFillColor(new ColorWrap(mChartView.getResources().getColor(R.color.stats_young)));

        BarGraph barGraphMature = new BarGraph(plotSheet, mBarThickness, new double[][]{serieslist[0], serieslist[2]},
                new ColorWrap(mChartView.getResources().getColor(R.color.stats_mature)));
        barGraphMature.setFilling(true);
        barGraphMature.setFillColor(new ColorWrap(mChartView.getResources().getColor(R.color.stats_mature)));

        plotSheet.addDrawable(barGraphYoung);
        plotSheet.addDrawable(barGraphMature);

        double xTicks = 2;
        setupXaxis(plotSheet, xTicks, false);

        double yTicks = ticksCalcY(mDesiredPixelDistanceBetweenTicks, rect, 0, maxValueY*yAxisStretchFactor);
        setupYaxis(plotSheet, null, yTicks, -1, false, false);
        setupGrid(plotSheet, yTicks*0.5, xTicks*0.5);

        return plotSheet;
    }

    private double maxValue(double[][] array, int startIndex){
        double max = Double.NEGATIVE_INFINITY;
        for(int i = startIndex; i<array.length; i++){
            double thisArrayMax = maxValue(array[i]);
            if(thisArrayMax > max)
                max = thisArrayMax;
        }
        return max;
    }
    private double maxValue(double[] array){
        double max = Double.NEGATIVE_INFINITY;
        for(int i = 0; i<array.length; i++){
            if(array[i]>max)
                max = array[i];
        }

        return max;
    }


    public double ticksCalcX(int pixelDistance, RectangleWrap field, double start, double end){
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

    public double ticksCalcY(int pixelDistance, RectangleWrap field, double start, double end){
        double deltaRange = end - start;
        int ticlimit = field.height/pixelDistance;
        double tics = Math.pow(10, (int)Math.log10(deltaRange/ticlimit));
        while(2.0*(deltaRange/(tics)) <= ticlimit) {
            tics /= 2.0;
        }
        while((deltaRange/(tics))/2 >= ticlimit) {
            tics *= 2.0;
        }
        Timber.d("ChartBuilder ticksCalcY: pixelDistance: %d, ticks: %,.2f", pixelDistance, tics);
        return tics;
    }

    public double ticsCalc(int pixelDistance, RectangleWrap field, double deltaRange){
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
