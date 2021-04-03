/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import com.ichi2.anki.AnkiDroidApp;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import timber.log.Timber;

public class ViewGroupUtils {
    @NonNull
    public static List<View> getAllChildren(@NonNull ViewGroup viewGroup) {
        int childrenCount = viewGroup.getChildCount();
        List<View> views = new ArrayList<>(childrenCount);
        for (int i = 0; i < childrenCount; i++) {
            views.add(viewGroup.getChildAt(i));
        }
        return views;
    }

    @NonNull
    public static List<View> getAllChildrenRecursive(@NonNull ViewGroup viewGroup) {
        List<View> views = new ArrayList<>();
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            views.add(child);
            if (child instanceof ViewGroup) {
                views.addAll(getAllChildrenRecursive((ViewGroup) child));
            }
        }
        return views;
    }

    public static void setRenderWorkaround(Activity activity) {
        if (AnkiDroidApp.getSharedPrefs(activity).getBoolean("softwareRender", false)) {
            Timber.i("ViewGroupUtils::setRenderWorkaround - software render requested, altering Views...");
            ViewGroupUtils.setContentViewLayerTypeSoftware(activity);
        } else {
            Timber.i("ViewGroupUtils::setRenderWorkaround - using default / hardware rendering");
        }
    }

    /**
     * Gets all the Views for the given Activity's ContentView, and sets their layerType
     * to the given layerType
     *
     * @param activity Activity containing the View hierarchy to alter
     */
    private static void setContentViewLayerTypeSoftware(@NonNull Activity activity) {
        ViewGroup rootViewGroup =
                (ViewGroup) ((ViewGroup) activity.findViewById(android.R.id.content))
                        .getChildAt(0);
        List<View> allViews = getAllChildrenRecursive(rootViewGroup);
        allViews.add(rootViewGroup);
        for (View v : allViews) {
            Timber.d("ViewGroupUtils::setContentViewLayerTypeSoftware for view %s",
                    v.getId());
            v.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
    }
}
