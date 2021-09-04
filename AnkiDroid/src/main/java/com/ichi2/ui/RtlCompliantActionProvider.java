/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>
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

package com.ichi2.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.drawable.Drawable;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;

import com.ichi2.anki.R;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.view.ActionProvider;

/**
 * An Rtl version of a normal action view, where the drawable is mirrored
 */
public class RtlCompliantActionProvider extends ActionProvider {

    @NonNull
    private final Context mContext;
    @NonNull
    @VisibleForTesting
    protected final Activity mActivity;


    public RtlCompliantActionProvider(@NonNull Context context) {
        super(context);
        mContext = context;
        mActivity = unwrapContext(context);
    }


    /**
     * Unwrap a context to get the base activity back.
     * @param context a context that may be of type {@link ContextWrapper}
     * @return The activity of the passed context
     */
    @NonNull
    private static Activity unwrapContext(@NonNull Context context){
        while (!(context instanceof Activity) && context instanceof ContextWrapper) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        if (context instanceof Activity) {
            return (Activity) context;
        } else {
            throw new ClassCastException("Passed context should be either an instanceof Activity or a ContextWrapper wrapping an Activity");
        }
    }

    /**
     * Deprecated method, no need to set it up.
     * https://developer.android.com/reference/kotlin/androidx/core/view/ActionProvider#oncreateactionview
     */
    @Deprecated
    @Override
    public View onCreateActionView() {
        return null;
    }


    @Override
    public View onCreateActionView(MenuItem forItem) {
        ImageButton actionView = new ImageButton(mContext, null, R.attr.actionButtonStyle);

        TooltipCompat.setTooltipText(actionView, forItem.getTitle());

        final Drawable iconDrawable = forItem.getIcon();
        iconDrawable.setAutoMirrored(true);
        actionView.setImageDrawable(iconDrawable);

        actionView.setId(R.id.action_undo);

        actionView.setOnClickListener(v -> {
            if (!forItem.isEnabled()) {
                return;
            }
            mActivity.onOptionsItemSelected(forItem);
        });

        return actionView;
    }
}
