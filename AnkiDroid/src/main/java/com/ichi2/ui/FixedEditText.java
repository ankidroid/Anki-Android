/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  This file incorporates work covered by the following copyright and
 *  permission notice:
 *
 *   This file is part of FairEmail.
 *
 *   FairEmail is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   FairEmail is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.
 *
 *   Copyright 2018-2020 by Marcel Bokhorst (M66B)
 *
 * Source: https://github.com/M66B/FairEmail/blob/75fe7d0ec92a9874a98c22b61eeb8e6a8906a9ea/app/src/main/java/eu/faircode/email/FixedEditText.java
*/

package com.ichi2.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import timber.log.Timber;

public class FixedEditText extends AppCompatEditText {
    public FixedEditText(@NonNull Context context) {
        super(context);
    }

    public FixedEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FixedEditText(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setSelection(int index) {
        try {
            super.setSelection(index);
        } catch (Throwable ex) {
            Timber.w(ex);
            /*
                java.lang.IndexOutOfBoundsException: setSpan (2 ... 2) ends beyond length 0
                        at android.text.SpannableStringBuilder.checkRange(SpannableStringBuilder.java:1265)
                        at android.text.SpannableStringBuilder.setSpan(SpannableStringBuilder.java:684)
                        at android.text.SpannableStringBuilder.setSpan(SpannableStringBuilder.java:677)
                        at android.text.Selection.setSelection(Selection.java:76)
                        at android.widget.EditText.setSelection(EditText.java:96)
                        at android.widget.NumberPicker$SetSelectionCommand.run(NumberPicker.java:2246)
                        at android.os.Handler.handleCallback(Handler.java:754)
                        at android.os.Handler.dispatchMessage(Handler.java:95)
             */
        }
    }

    @Override
    public void setSelection(int start, int stop) {
        try {
            super.setSelection(start, stop);
        } catch (Throwable ex) {
            Timber.w(ex);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            super.onDraw(canvas);
        } catch (Throwable ex) {
            Timber.w(ex);
            /*
                java.lang.ArrayIndexOutOfBoundsException: length=39; index=-3
                  at android.text.DynamicLayout.getBlockIndex(DynamicLayout.java:648)
                  at android.widget.Editor.drawHardwareAccelerated(Editor.java:1703)
                  at android.widget.Editor.onDraw(Editor.java:1672)
                  at android.widget.TextView.onDraw(TextView.java:6914)
                  at android.view.View.draw(View.java:19200)
            */
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            return super.onTouchEvent(event);
        } catch (Throwable ex) {
            Timber.w(ex);
            return false;
        }
    }

    @Override
    public boolean performLongClick() {
        try {
            return super.performLongClick();
        } catch (Throwable ex) {
/*
            java.lang.IllegalStateException: Drag shadow dimensions must be positive
                    at android.view.View.startDragAndDrop(View.java:27316)
                    at android.widget.Editor.startDragAndDrop(Editor.java:1340)
                    at android.widget.Editor.performLongClick(Editor.java:1374)
                    at android.widget.TextView.performLongClick(TextView.java:13544)
                    at android.view.View.performLongClick(View.java:7928)
                    at android.view.View$CheckForLongPress.run(View.java:29321)
*/
            Timber.w(ex);
            return false;
        }
    }
}