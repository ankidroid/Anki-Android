/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com> 
 */

package com.ichi2.anim;

import com.ichi2.anki.Reviewer;

import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.graphics.Camera;
import android.graphics.Matrix;

public class Animation3D extends Animation {
    private final float mValueX;
    private final float mValueY;
    private final float mDepthZ;
    private Camera mCamera;
    private Reviewer mReviewer;
    boolean mDirection;
    boolean mFlipped = false;
    boolean mTurn;
    boolean mRealTurn;

    public Animation3D(float valueX, float valueY, float depthZ, boolean direction, boolean turn, boolean realturn, Reviewer reviewer) {
        mValueX = valueX;
        mValueY = valueY;
        mDepthZ = depthZ;
        mReviewer = reviewer;
        mDirection = direction;
        mTurn = turn;
        mRealTurn = realturn;
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        mCamera = new Camera();
    }


    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        float centerX;
        float centerY;
        final Camera camera = mCamera;
        final Matrix matrix = t.getMatrix();
        camera.save();
        float time;

        if (mTurn) {
        	if (mRealTurn) {
                time = interpolatedTime >= 0.5f ? (interpolatedTime - 1.0f) : interpolatedTime;        		
        	} else {
                time = interpolatedTime >= 0.5f ? -(interpolatedTime - 1.0f) : interpolatedTime;
        	}
        	float degrees = time * (mDirection ? -180 : 180);
            if (interpolatedTime >= 0.5f && !mFlipped) {
                mReviewer.fillFlashcard(false);
                mFlipped = true;
            }
            camera.translate(0.0f, 0.0f, mDepthZ * Math.abs(degrees));
            if (mDirection) {
                centerX = mValueX / 2;
                centerY = mValueY / (mRealTurn ? 2 : 3);
            	camera.rotateX(degrees);
            } else {
                centerX = mValueX / (mRealTurn ? 2 : 3);
                centerY = mValueY / 2;
            	camera.rotateY(degrees);
            }
        } else {
            if (mDirection) {
            	time = interpolatedTime >= 0.5f ? -(interpolatedTime - 1.0f) : -interpolatedTime;
            } else {
            	time = interpolatedTime >= 0.5f ? (interpolatedTime - 1.0f) : interpolatedTime;
            }
            if (interpolatedTime >= 0.5f && !mFlipped) {
                mReviewer.fillFlashcard(false);
                mFlipped = true;
            }
            camera.translate(mValueX * time * 2, 0.0f, mDepthZ * Math.abs(time * 180));
            centerX = mValueX / 2;
            centerY = mValueY / 2;
        }

        camera.getMatrix(matrix);
        camera.restore();

        matrix.preTranslate(-centerX, -centerY);
        matrix.postTranslate(centerX, centerY);
    }
}
