/*
 * Copyright 2014 Alex Curran
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.amlcurran.showcaseview;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.DynamicLayout;
import android.text.Layout;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;

/**
 * Draws the text as required by the ShowcaseView
 */
class TextDrawer {

    private final TextPaint titlePaint;
    private final TextPaint textPaint;
    private final Context context;
    private final ShowcaseAreaCalculator calculator;
    private final float padding;
    private final float actionBarOffset;

    private CharSequence mTitle, mDetails;
    private float[] mBestTextPosition = new float[3];
    private DynamicLayout mDynamicTitleLayout;
    private DynamicLayout mDynamicDetailLayout;
    private TextAppearanceSpan mTitleSpan;
    private TextAppearanceSpan mDetailSpan;
    private boolean hasRecalculated;

    public TextDrawer(Resources resources, ShowcaseAreaCalculator calculator, Context context) {
        padding = resources.getDimension(R.dimen.text_padding);
        actionBarOffset = resources.getDimension(R.dimen.action_bar_offset);

        this.calculator = calculator;
        this.context = context;

        titlePaint = new TextPaint();
        titlePaint.setAntiAlias(true);

        textPaint = new TextPaint();
        textPaint.setAntiAlias(true);
    }

    public void draw(Canvas canvas) {
        if (shouldDrawText()) {
            float[] textPosition = getBestTextPosition();

            if (!TextUtils.isEmpty(mTitle)) {
                canvas.save();
                if (hasRecalculated) {
                    mDynamicTitleLayout = new DynamicLayout(mTitle, titlePaint,
                            (int) textPosition[2], Layout.Alignment.ALIGN_NORMAL,
                            1.0f, 1.0f, true);
                }
                if (mDynamicTitleLayout != null) {
                    canvas.translate(textPosition[0], textPosition[1]);
                    mDynamicTitleLayout.draw(canvas);
                    canvas.restore();
                }
            }

            if (!TextUtils.isEmpty(mDetails)) {
                canvas.save();
                if (hasRecalculated) {
                    mDynamicDetailLayout = new DynamicLayout(mDetails, textPaint,
                            (int) textPosition[2],
                            Layout.Alignment.ALIGN_NORMAL,
                            1.2f, 1.0f, true);
                }
                float offsetForTitle = mDynamicTitleLayout != null ? mDynamicTitleLayout.getHeight() :
                        0;
                if (mDynamicDetailLayout != null) {
                    canvas.translate(textPosition[0], textPosition[1] + offsetForTitle);
                    mDynamicDetailLayout.draw(canvas);
                    canvas.restore();
                }

            }
        }
        hasRecalculated = false;
    }

    public void setContentText(CharSequence details) {
        if (details != null) {
            SpannableString ssbDetail = new SpannableString(details);
            ssbDetail.setSpan(mDetailSpan, 0, ssbDetail.length(), 0);
            mDetails = ssbDetail;
        }
    }

    public void setContentTitle(CharSequence title) {
        if (title != null) {
            SpannableString ssbTitle = new SpannableString(title);
            ssbTitle.setSpan(mTitleSpan, 0, ssbTitle.length(), 0);
            mTitle = ssbTitle;
        }
    }

    /**
     * Calculates the best place to position text
     *  @param canvasW width of the screen
     * @param canvasH height of the screen
     * @param shouldCentreText
     */
    public void calculateTextPosition(int canvasW, int canvasH, ShowcaseView showcaseView, boolean shouldCentreText) {

    	Rect showcase = showcaseView.hasShowcaseView() ?
    			calculator.getShowcaseRect() :
    			new Rect();
    	
    	int[] areas = new int[4]; //left, top, right, bottom
    	areas[0] = showcase.left * canvasH;
    	areas[1] = showcase.top * canvasW;
    	areas[2] = (canvasW - showcase.right) * canvasH;
    	areas[3] = (canvasH - showcase.bottom) * canvasW;
    	
    	int largest = 0;
    	for(int i = 1; i < areas.length; i++) {
    		if(areas[i] > areas[largest])
    			largest = i;
    	}
    	
    	// Position text in largest area
    	switch(largest) {
    	case 0:
    		mBestTextPosition[0] = padding;
    		mBestTextPosition[1] = padding;
    		mBestTextPosition[2] = showcase.left - 2 * padding;
    		break;
    	case 1:
    		mBestTextPosition[0] = padding;
    		mBestTextPosition[1] = padding + actionBarOffset;
    		mBestTextPosition[2] = canvasW - 2 * padding;
    		break;
    	case 2:
    		mBestTextPosition[0] = showcase.right + padding;
    		mBestTextPosition[1] = padding;
    		mBestTextPosition[2] = (canvasW - showcase.right) - 2 * padding;
    		break;
    	case 3:
    		mBestTextPosition[0] = padding;
    		mBestTextPosition[1] = showcase.bottom + padding;
    		mBestTextPosition[2] = canvasW - 2 * padding;
    		break;
    	}
    	if(shouldCentreText) {
	    	// Center text vertically or horizontally
	    	switch(largest) {
	    	case 0:
	    	case 2:
	    		mBestTextPosition[1] += canvasH / 4;
	    		break;
	    	case 1:
	    	case 3:
	    		mBestTextPosition[2] /= 2;
	    		mBestTextPosition[0] += canvasW / 4;
	    		break;
	    	} 
    	} else {
    		// As text is not centered add actionbar padding if the text is left or right
	    	switch(largest) {
	    		case 0:
	    		case 2:
	    			mBestTextPosition[1] += actionBarOffset;
	    			break;
	    	}
    	}

        hasRecalculated = true;
    }

    public void setTitleStyling(int styleId) {
        mTitleSpan = new TextAppearanceSpan(this.context, styleId);
        setContentTitle(mTitle);
    }

    public void setDetailStyling(int styleId) {
        mDetailSpan = new TextAppearanceSpan(this.context, styleId);
        setContentText(mDetails);
    }

    public CharSequence getContentTitle() {
        return mTitle;
    }

    public CharSequence getContentText() {
        return mDetails;
    }

    public float[] getBestTextPosition() {
        return mBestTextPosition;
    }

    public boolean shouldDrawText() {
        return !TextUtils.isEmpty(mTitle) || !TextUtils.isEmpty(mDetails);
    }
}
