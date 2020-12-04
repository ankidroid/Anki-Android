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

import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.stats.Stats;
import com.ichi2.themes.Themes;
import com.wildplot.android.rendering.BarGraph;
import com.wildplot.android.rendering.LegendDrawable;
import com.wildplot.android.rendering.Lines;
import com.wildplot.android.rendering.PieChart;
import com.wildplot.android.rendering.PlotSheet;
import com.wildplot.android.rendering.XAxis;
import com.wildplot.android.rendering.XGrid;
import com.wildplot.android.rendering.YAxis;
import com.wildplot.android.rendering.YGrid;
import com.wildplot.android.rendering.graphics.wrapper.ColorWrap;
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap;

import timber.log.Timber;


public class ChartBuilder {
    private static final float BAR_OPACITY = 0.7f;
    private static final double STARTING_BAR_THICKNESS = 0.6;
    private static final double Y_AXIS_STRETCH_FACTOR = 1.05;

    private final Stats.ChartType mChartType;
    private final long mDeckId;
    private final ChartView mChartView;
    private final Collection mCollectionData;

    int mMaxCards = 0;
    private boolean mBackwards;
    private int[] mValueLabels;
    private int[] mColors;
    private int[] mAxisTitles;
    private double[][] mSeriesList;
    private double mLastElement = 0;
    private double[][] mCumulative = null;
    private double mFirstElement;
    private boolean mHasColoredCumulative;
    private double mMcount;
    private boolean mDynamicAxis;

    public ChartBuilder(ChartView chartView, Collection collectionData, long deckId, Stats.ChartType chartType){
        mChartView = chartView;
        mCollectionData = collectionData;
        mDeckId = deckId;
        mChartType = chartType;
    }

    private void calcStats(Stats.AxisType type){
        Stats stats = new Stats(mCollectionData, mDeckId);
        switch (mChartType){
            case FORECAST:
                stats.calculateDue(mChartView.getContext(), type);
                break;
            case REVIEW_COUNT:
                stats.calculateReviewCount(type);
                break;
            case REVIEW_TIME:
                stats.calculateReviewTime(type);
                break;
            case INTERVALS:
                stats.calculateIntervals(mChartView.getContext(), type);
                break;
            case HOURLY_BREAKDOWN:
                stats.calculateBreakdown(type);
                break;
            case WEEKLY_BREAKDOWN:
                stats.calculateWeeklyBreakdown(type);
                break;
            case ANSWER_BUTTONS:
                stats.calculateAnswerButtons(type);
                break;
            case CARDS_TYPES:
                stats.calculateCardTypes(type);
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

    public PlotSheet renderChart(Stats.AxisType type){
        calcStats(type);
        Paint paint = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);

        int height = mChartView.getMeasuredHeight();
        int width = mChartView.getMeasuredWidth();
        Timber.d("height: %d, width: %d, %d", height, width, mChartView.getWidth());

        if (height <= 0 || width <= 0) {
            return null;
        }

        RectangleWrap rect = new RectangleWrap(width, height);
        float textSize = AnkiStatsTaskHandler.getInstance().getmStandardTextSize() * 0.85f;
        paint.setTextSize(textSize);
        float FontHeight = paint.getTextSize();
        int desiredPixelDistanceBetweenTicks = Math.round(paint.measureText("100000") * 2.6f);
        int frameThickness = Math.round(FontHeight * 4.0f);

        //System.out.println("frame thickness: " + mFrameThickness);

        PlotSheet plotSheet = new PlotSheet(mFirstElement - 0.5, mLastElement + 0.5, 0, mMaxCards * Y_AXIS_STRETCH_FACTOR);
        plotSheet.setFrameThickness(frameThickness * 0.66f, frameThickness * 0.66f, frameThickness, frameThickness * 0.9f);
        plotSheet.setFontSize(textSize);
        int backgroundColor = Themes.getColorFromAttr(mChartView.getContext(), android.R.attr.colorBackground);
        plotSheet.setBackgroundColor(new ColorWrap(backgroundColor));
        int textColor = Themes.getColorFromAttr(mChartView.getContext(), android.R.attr.textColor);
        plotSheet.setTextColor(new ColorWrap(textColor));
        plotSheet.setIsBackwards(mBackwards);

        if (mChartType == Stats.ChartType.CARDS_TYPES) {
            return createPieChart(plotSheet);
        }

        PlotSheet hiddenPlotSheet = new PlotSheet(mFirstElement - 0.5, mLastElement + 0.5, 0, mMcount * Y_AXIS_STRETCH_FACTOR);     //for second y-axis
        hiddenPlotSheet.setFrameThickness(frameThickness * 0.66f, frameThickness * 0.66f, frameThickness, frameThickness * 0.9f);

        setupCumulative(plotSheet, hiddenPlotSheet);
        setupBarGraphs(plotSheet, hiddenPlotSheet);

        double xTicks = ticksCalcX(desiredPixelDistanceBetweenTicks, rect, mFirstElement, mLastElement);
        setupXaxis(plotSheet, xTicks, true);

        double yTicks = ticksCalcY(desiredPixelDistanceBetweenTicks, rect, 0, mMaxCards * Y_AXIS_STRETCH_FACTOR);
        setupYaxis(plotSheet, hiddenPlotSheet, yTicks, mAxisTitles[1], false, true);

        //0 = X-axis title
        //1 = Y-axis title left
        //2 = Y-axis title right (optional)
        if(mAxisTitles.length == 3) {
            double rightYtics = ticsCalc(desiredPixelDistanceBetweenTicks, rect, mMcount * Y_AXIS_STRETCH_FACTOR);
            setupYaxis(plotSheet, hiddenPlotSheet, rightYtics, mAxisTitles[2], true, true);
        }
        setupGrid(plotSheet, yTicks * 0.5, xTicks * 0.5);

        return plotSheet;
    }


    private PlotSheet createPieChart(PlotSheet plotSheet) {
        ColorWrap[] colors = {new ColorWrap(Themes.getColorFromAttr(mChartView.getContext(), mColors[0])),
                              new ColorWrap(Themes.getColorFromAttr(mChartView.getContext(), mColors[1])),
                              new ColorWrap(Themes.getColorFromAttr(mChartView.getContext(), mColors[2])),
                              new ColorWrap(Themes.getColorFromAttr(mChartView.getContext(), mColors[3])),
                              new ColorWrap(Themes.getColorFromAttr(mChartView.getContext(), mColors[4]))};

        PieChart pieChart = new PieChart(plotSheet, mSeriesList[0], colors);
        pieChart.setName(mChartView.getResources().getString(mValueLabels[0]) + ": " + (int) mSeriesList[0][0]);
        LegendDrawable legendDrawable1 = new LegendDrawable();
        LegendDrawable legendDrawable2 = new LegendDrawable();
        LegendDrawable legendDrawable3 = new LegendDrawable();
        LegendDrawable legendDrawable4 = new LegendDrawable();
        legendDrawable1.setColor(new ColorWrap(Themes.getColorFromAttr(mChartView.getContext(), mColors[1])));
        legendDrawable2.setColor(new ColorWrap(Themes.getColorFromAttr(mChartView.getContext(), mColors[2])));
        legendDrawable3.setColor(new ColorWrap(Themes.getColorFromAttr(mChartView.getContext(), mColors[3])));
        legendDrawable4.setColor(new ColorWrap(Themes.getColorFromAttr(mChartView.getContext(), mColors[4])));

        legendDrawable1.setName(mChartView.getResources().getString(mValueLabels[1]) + ": " + (int) mSeriesList[0][1]);
        legendDrawable2.setName(mChartView.getResources().getString(mValueLabels[2]) + ": " + (int) mSeriesList[0][2]);
        legendDrawable3.setName(mChartView.getResources().getString(mValueLabels[3]) + ": " + (int) mSeriesList[0][3]);
        legendDrawable4.setName(mChartView.getResources().getString(mValueLabels[4]) + ": " + (int) mSeriesList[0][4]);

        plotSheet.unsetBorder();
        plotSheet.addDrawable(pieChart);
        plotSheet.addDrawable(legendDrawable1);
        plotSheet.addDrawable(legendDrawable2);
        plotSheet.addDrawable(legendDrawable3);
        plotSheet.addDrawable(legendDrawable4);

        return plotSheet;
    }


    private void setupBarGraphs(PlotSheet plotSheet, PlotSheet hiddenPlotSheet) {
        int length = mSeriesList.length;
        if (mChartType == Stats.ChartType.HOURLY_BREAKDOWN || mChartType == Stats.ChartType.WEEKLY_BREAKDOWN) {
            length--;   //there is data in hourly breakdown that is never used (even in Anki-Desktop)
        }
        for (int i = 1; i < length; i++) {
            double[][] bars = new double[2][];
            bars[0] = mSeriesList[0];
            bars[1] = mSeriesList[i];

            PlotSheet usedPlotSheet = plotSheet;
            double barThickness = STARTING_BAR_THICKNESS;
            if ((mChartType == Stats.ChartType.HOURLY_BREAKDOWN || mChartType == Stats.ChartType.WEEKLY_BREAKDOWN)) {
                barThickness = 0.8;
                if (i == 2) {
                    usedPlotSheet = hiddenPlotSheet;
                    barThickness = 0.2;
                }
            }
            ColorWrap color;
            switch (mChartType) {
                case ANSWER_BUTTONS:
                case HOURLY_BREAKDOWN:
                case WEEKLY_BREAKDOWN:
                case INTERVALS:
                    color = new ColorWrap(Themes.getColorFromAttr(mChartView.getContext(), mColors[i - 1]), BAR_OPACITY);
                    break;
                case REVIEW_COUNT:
                case REVIEW_TIME:
                case FORECAST:
                    if (i == 1) {
                        color = new ColorWrap(Themes.getColorFromAttr(mChartView.getContext(), mColors[i - 1]), BAR_OPACITY);
                        break;
                    }
                    color = new ColorWrap(Themes.getColorFromAttr(mChartView.getContext(), mColors[i - 1]));
                    break;
                default:
                    color = new ColorWrap(Themes.getColorFromAttr(mChartView.getContext(), mColors[i - 1]));
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
        if (mCumulative == null) {
            return;
        }
        for (int i = 1; i < mCumulative.length; i++) {
            double[][] cumulative = {mCumulative[0], mCumulative[i]};

            ColorWrap usedColor = new ColorWrap(Themes.getColorFromAttr(mChartView.getContext(), R.attr.stats_cumulative));
            String name = mChartView.getResources().getString(R.string.stats_cumulative);
            if (mHasColoredCumulative) {      //also non colored Cumulatives have names!
                usedColor = new ColorWrap(Themes.getColorFromAttr(mChartView.getContext(), mColors[i - 1]));
            } else {
                if (mChartType == Stats.ChartType.INTERVALS) {
                    name = mChartView.getResources().getString(R.string.stats_cumulative_percentage);
                }
            }

            Lines lines = new Lines(hiddenPlotSheet, cumulative, usedColor);
            lines.setSize(3f);
            lines.setShadow(2f, 2f, ColorWrap.BLACK);
            if (!mHasColoredCumulative) {
                lines.setName(name);
            }
            plotSheet.addDrawable(lines);
        }
    }


    private void setupXaxis(PlotSheet plotSheet, double xTicks, boolean hasName) {
        XAxis xAxis = new XAxis(plotSheet, 0, xTicks, xTicks / 2.0);
        xAxis.setOnFrame();
        if (hasName) {
            if (mDynamicAxis) {
                xAxis.setName(mChartView.getResources().getStringArray(R.array.due_x_axis_title)[mAxisTitles[0]]);
            } else {
                xAxis.setName(mChartView.getResources().getString(mAxisTitles[0]));
            }
        }
        double[] timePositions;

        //some explicit x-axis naming:
        switch (mChartType) {
            case ANSWER_BUTTONS:
                if (mCollectionData.schedVer() == 1) {
                    timePositions = new double[]{1, 2, 3, 6, 7, 8, 9, 11, 12, 13, 14};
                    xAxis.setExplicitTicks(timePositions, mChartView.getResources().getStringArray(R.array.stats_eases_ticks));
                } else {
                    timePositions = new double[]{1, 2, 3, 4, 6, 7, 8, 9, 11, 12, 13, 14};
                    xAxis.setExplicitTicks(timePositions, mChartView.getResources().getStringArray(R.array.stats_eases_ticks_schedv2));
                }
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


    private void setupYaxis(PlotSheet plotSheet, PlotSheet hiddenPlotSheet, double yTicks, int title,
                            boolean isOnRight, boolean hasName) {
        YAxis yAxis;
        if (isOnRight && hiddenPlotSheet != null) {
            yAxis = new YAxis(hiddenPlotSheet, 0, yTicks, yTicks / 2.0);
        } else {
            yAxis = new YAxis(plotSheet, 0, yTicks, yTicks / 2.0);
        }

        yAxis.setIntegerNumbering(true);
        if (hasName) {
            yAxis.setName(mChartView.getResources().getString(title));
        }
        if (isOnRight) {
            yAxis.setOnRightSideFrame();
        } else {
            yAxis.setOnFrame();
        }

        yAxis.setHasNumbersRotated();
        plotSheet.addDrawable(yAxis);
    }


    private void setupGrid(PlotSheet plotSheet, double yTicks, double xTicks) {
        int red = ColorWrap.LIGHT_GRAY.getRed();
        int green = ColorWrap.LIGHT_GRAY.getGreen();
        int blue = ColorWrap.LIGHT_GRAY.getBlue();

        ColorWrap newGridColor = new ColorWrap(red, green, blue, 222);

        XGrid xGrid = new XGrid(plotSheet, 0, yTicks);  //ticks are not wrong, xgrid is vertical to yaxis -> yticks
        YGrid yGrid = new YGrid(plotSheet, 0, xTicks);

        double[] timePositions;

        //some explicit x-axis naming:
        switch (mChartType) {
            case ANSWER_BUTTONS:
                if (mCollectionData.schedVer() == 1) {
                    timePositions = new double[]{1, 2, 3, 6, 7, 8, 9, 11, 12, 13, 14};
                } else {
                    timePositions = new double[]{1, 2, 3, 4, 6, 7, 8, 9, 11, 12, 13, 14};
                }
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


    public double ticksCalcX(int pixelDistance, RectangleWrap field, double start, double end) {
        double deltaRange = end - start;
        int ticlimit = field.width / pixelDistance;
        double tics = Math.pow(10, (int) Math.log10(deltaRange / ticlimit));
        while (2.0 * (deltaRange / (tics)) <= ticlimit) {
            tics /= 2.0;
        }
        while ((deltaRange / (tics)) / 2 >= ticlimit) {
            tics *= 2.0;
        }
        return tics;
    }

    public double ticksCalcY(int pixelDistance, RectangleWrap field, double start, double end) {

        double size = ticsCalc(pixelDistance, field, end - start);
        Timber.d("ChartBuilder ticksCalcY: pixelDistance: %d, ticks: %,.2f, start: %,.2f, end: %,.2f, height: %d", pixelDistance, size, start, end, field.height);
        return size;
    }

    public double ticsCalc(int pixelDistance, RectangleWrap field, double deltaRange) {

        //Make approximation of number of ticks based on desired number of pixels per tick
        double numTicks = field.height / pixelDistance;

        //Compute size of one tick in graph-units
        double delta = deltaRange / numTicks;

        //Write size of one tick in the form norm * magn
        double dec = Math.floor(Math.log(delta) / Math.log(10));
        double magn = Math.pow(10, dec);

        double norm = delta / magn; // norm is between 1.0 and 10.0

        //Write size of one tick in the form size * magn
        //Where size in (1, 2, 2.5, 5, 10)
        double size;

        if (norm < 1.5) {
            size = 1;
        } else if (norm < 3) {
            size = 2;
            // special case for 2.5, requires an extra decimal
            if (norm > 2.25) {
                size = 2.5;
            }
        } else if (norm < 7.5) {
            size = 5;
        } else {
            size = 10;
        }

        //Compute size * magn so that we return one number
        size *= magn;

        Timber.d("ChartBuilder ticksCalc : pixelDistance: %d, ticks: %,.2f, deltaRange: %,.2f, height: %d", pixelDistance, size, deltaRange, field.height);

        return size;
    }
}
