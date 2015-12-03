
package com.ichi2.compat;

import android.annotation.TargetApi;
import android.content.res.TypedArray;
import android.view.View;

/** Implementation of {@link Compat} for SDK level 19 */
@TargetApi(19)
public class CompatV21 extends CompatV19 implements Compat {
    @Override
    public void setSelectableBackground(View view) {
        // Ripple effect
        int[] attrs = new int[] {android.R.attr.selectableItemBackground};
        TypedArray ta = view.getContext().obtainStyledAttributes(attrs);
        view.setBackgroundResource(ta.getResourceId(0, 0));
        ta.recycle();
    }
}