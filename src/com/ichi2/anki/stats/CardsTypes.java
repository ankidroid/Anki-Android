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
import android.widget.ImageView;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Stats;
import com.wildplot.android.rendering.LegendDrawable;
import com.wildplot.android.rendering.PieChart;
import com.wildplot.android.rendering.PlotSheet;
import com.wildplot.android.rendering.graphics.wrapper.BufferedImage;
import com.wildplot.android.rendering.graphics.wrapper.Color;
import com.wildplot.android.rendering.graphics.wrapper.Graphics2D;
import com.wildplot.android.rendering.graphics.wrapper.Rectangle;

/**
 * Created by mig on 06.07.2014.
 */
public class CardsTypes {

    private final boolean mIsWholeCollection;
    private ImageView mImageView;
    private Collection mCollectionData;
    private int mFrameThickness = 60;
    private int mMaxCards = 0;
    private int mMaxElements = 0;
    private int[] mValueLabels;
    private int[] mColors;
    private double[][] mSeriesList;


    public CardsTypes(ImageView imageView, Collection collectionData, boolean isWholeCollection){
        mImageView = imageView;
        mCollectionData = collectionData;
        mIsWholeCollection = isWholeCollection;
    }
    private void calcStats(int type){
        Stats stats = new Stats(mCollectionData, mIsWholeCollection);
        stats.calculateCardsTypes(type);
        mSeriesList = stats.getSeriesList();
        Object[] metaData = stats.getMetaInfo();
        mValueLabels = (int[]) metaData[3];
        mColors = (int[]) metaData[4];
        mMaxCards = (Integer) metaData[7];
        mMaxElements = (Integer)metaData[8];

    }

    public Bitmap renderChart(int type) {
        calcStats(type);
        int height = mImageView.getMeasuredHeight();
        int width = mImageView.getMeasuredWidth();

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

        PlotSheet plotSheet = new PlotSheet(0, 15, 0, 15);
        plotSheet.setFrameThickness(mFrameThickness);
        plotSheet.unsetBorder();
        PieChart pieChart = new PieChart(plotSheet, mSeriesList[0]);

        Color[] colors = {new Color(mImageView.getResources().getColor(mColors[0])),
                new Color(mImageView.getResources().getColor(mColors[1])),
                new Color(mImageView.getResources().getColor(mColors[2])),
                new Color(mImageView.getResources().getColor(mColors[3]))};
        pieChart.setColors(colors);
        pieChart.setName(mImageView.getResources().getString(mValueLabels[0]) + ": " + (int)mSeriesList[0][0]);
        LegendDrawable legendDrawable1 = new LegendDrawable();
        LegendDrawable legendDrawable2 = new LegendDrawable();
        LegendDrawable legendDrawable3 = new LegendDrawable();
        legendDrawable1.setColor(new Color(mImageView.getResources().getColor(mColors[1])));
        legendDrawable2.setColor(new Color(mImageView.getResources().getColor(mColors[2])));
        legendDrawable3.setColor(new Color(mImageView.getResources().getColor(mColors[3])));

        legendDrawable1.setName(mImageView.getResources().getString(mValueLabels[1]) + ": " + (int)mSeriesList[0][1]);
        legendDrawable2.setName(mImageView.getResources().getString(mValueLabels[2]) + ": " + (int)mSeriesList[0][2]);
        legendDrawable3.setName(mImageView.getResources().getString(mValueLabels[3]) + ": " + (int)mSeriesList[0][3]);

        plotSheet.setFontSize(textSize);
        plotSheet.addDrawable(pieChart);
        plotSheet.addDrawable(legendDrawable1);
        plotSheet.addDrawable(legendDrawable2);
        plotSheet.addDrawable(legendDrawable3);

        plotSheet.paint(g);
        Bitmap bitmap = bufferedFrameImage.getBitmap();
        bitmap.prepareToDraw();
        return bitmap;
    }

}
