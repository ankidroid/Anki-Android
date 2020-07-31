package com.ichi2.anki.widgets;

import android.content.Context;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import androidx.appcompat.widget.PopupMenu;

/**
 * A simple little hack to force the icons to display in the PopupMenu
 */
public class PopupMenuWithIcons extends PopupMenu {

    public PopupMenuWithIcons(Context context, View anchor, boolean showIcons) {
        super(context, anchor);
        if (showIcons) {
            try {
                Field[] fields = PopupMenu.class.getDeclaredFields();
                for (Field field : fields) {
                    if ("mPopup".equals(field.getName())) {
                        field.setAccessible(true);
                        Object menuPopupHelper = field.get(this);
                        Class<?> classPopupHelper = Class.forName(menuPopupHelper
                                .getClass().getName());
                        Method setForceIcons = classPopupHelper.getMethod(
                                "setForceShowIcon", boolean.class);
                        setForceIcons.invoke(menuPopupHelper, true);
                        break;
                    }
                }
            } catch (Exception e) {
            }
        }
    }
}