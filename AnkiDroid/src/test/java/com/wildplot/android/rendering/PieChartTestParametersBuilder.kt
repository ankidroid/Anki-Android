//noinspection MissingCopyrightHeader #8659

package com.wildplot.android.rendering;


import com.wildplot.android.rendering.graphics.wrapper.ColorWrap;

import static java.lang.String.format;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class PieChartTestParametersBuilder {
    private final double[] mValues;
    private final int mNumberOfValues;
    private final double mSum;
    private final double mFirstSectorAngle;
    private final double[] mStartAngles;
    private final double[] mArcLengths;
    private final ColorWrap[] mColors;

    PieChartTestParametersBuilder(double[] values,
                                  double firstSectorAngle) {
        if (values.length < 1) {
            throw new IllegalArgumentException("Empty array of values");
        }
        mValues = values;
        mNumberOfValues = values.length;
        mSum = calcSum(values);
        if (mSum == 0) {
            throw new IllegalArgumentException(
                    format("All %d values are zero", values.length));
        }
        mFirstSectorAngle = firstSectorAngle;
        mStartAngles = new double[mNumberOfValues];
        mArcLengths = new double[mNumberOfValues];
        mColors = new ColorWrap[mNumberOfValues];
        calcArcLengths();
        calcStartAngles();
        fillColors();
    }

    private double calcSum(double[] values) {
        double sum = 0.;
        for (double v : values) {
            sum += v;
        }
        return sum;
    }

    private void calcArcLengths() {
        for (int i = 0; i < mNumberOfValues; i++) {
            mArcLengths[i] = 360.0 * mValues[i] / mSum;
        }
    }

    private void calcStartAngles() {
        mStartAngles[0] = mFirstSectorAngle;
        for (int i = 1; i < mNumberOfValues; i++) {
            mStartAngles[i] = mStartAngles[i - 1] + mArcLengths[i - 1];
        }
    }

    private void fillColors() {
        for (int i = 0; i < mNumberOfValues; i++) {
            mColors[i] = createColorMock(i);
        }
    }

    private ColorWrap createColorMock(int i) {
        ColorWrap c = mock(ColorWrap.class);
        when(c.getColorValue()).thenReturn(i);
        return c;
    }

    double[] getStartAngles() {
        return mStartAngles;
    }

    double[] getArcLengths() {
        return mArcLengths;
    }

    ColorWrap[] getColors() {
        return mColors;
    }
}
