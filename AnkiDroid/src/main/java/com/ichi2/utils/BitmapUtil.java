/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

package com.ichi2.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import android.widget.ImageView;

import com.ichi2.anki.AnkiDroidApp;

import java.io.File;
import java.io.FileInputStream;

import androidx.annotation.Nullable;
import timber.log.Timber;

public class BitmapUtil {

    @Nullable
    public static Bitmap decodeFile(File theFile, int IMAGE_MAX_SIZE) {
        Bitmap bmp = null;
        try {
            if (!theFile.exists()) {
                Timber.i("not displaying preview - image does not exist: '%s'", theFile.getPath());
                return null;
            }
            // Decode image size
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;

            FileInputStream fis = null;
            try {
                fis = new FileInputStream(theFile);
                BitmapFactory.decodeStream(fis, null, o);
            } finally {
                if (fis != null) {
                    fis.close();
                }
            }

            int scale = 1;
            if (o.outHeight > IMAGE_MAX_SIZE || o.outWidth > IMAGE_MAX_SIZE) {
                scale = (int) Math.pow(
                        2,
                        (int) Math.round(Math.log(IMAGE_MAX_SIZE / (double) Math.max(o.outHeight, o.outWidth))
                                / Math.log(0.5)));
            }

            // Decode with inSampleSize
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = scale;

            try {
                fis = new FileInputStream(theFile);
                bmp = BitmapFactory.decodeStream(fis, null, o2);
            } finally {
                fis.close(); //don't need a null check, as we reuse the variable.
            }
        } catch (Exception e) {
            //#5513 - We don't know the reason for the crash, let's find out.
            AnkiDroidApp.sendExceptionReport(e, "BitmapUtil decodeFile");
        }
        return bmp;
    }


    public static void freeImageView(ImageView imageView) {
        // This code behaves differently on various OS builds. That is why put into try catch.
        try {
            if (imageView != null) {
                Drawable dr = imageView.getDrawable();

                if (dr == null) {
                    return;
                }

                if (!(dr instanceof BitmapDrawable)) {
                    return;
                }
                BitmapDrawable bd = (BitmapDrawable) imageView.getDrawable();
                if (bd.getBitmap() != null) {
                    bd.getBitmap().recycle();
                    imageView.setImageBitmap(null);
                }
            }
        } catch (Exception e) {
            Timber.e(e);
        }
    }

}
