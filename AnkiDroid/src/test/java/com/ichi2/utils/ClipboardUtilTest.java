//noinspection MissingCopyrightHeader #8659

package com.ichi2.utils;

import android.content.ClipDescription;
import android.content.ClipboardManager;

import org.junit.Test;
import static com.ichi2.utils.ClipboardUtil.hasImage;
import static org.junit.Assert.*;


public class ClipboardUtilTest {

    @Test
    public void hasImageClipboardManagerNullTest() {
        ClipboardManager clipboardManager = null;
        assertFalse(hasImage(clipboardManager));
    }

    @Test
    public void hasImageDescriptionNullTest() {
        ClipDescription clipDescription = null;
        assertFalse(hasImage(clipDescription));
    }

}
