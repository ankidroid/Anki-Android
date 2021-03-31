package com.ichi2.utils;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;

import org.junit.Test;
import static com.ichi2.utils.ClipboardUtil.hasImage;
import static org.junit.Assert.*;

import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;



@RunWith(MockitoJUnitRunner.class)

public class ClipboardUtilTest {

    @Mock
    Context context;

    @Test
    public void hasImageClipboardmanagernulltest() {
        ClipboardManager clipboardManager = null;
        assertFalse(hasImage(clipboardManager));
    }

    @Test
    public void hasImageClipboardmanagerprimarycliptest() {
        android.content.ClipboardManager clipboardManager = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(null, "sample text");
        clipboardManager.setPrimaryClip(clip);
        assertFalse(hasImage(clipboardManager));
    }

    @Test
    public void hasImageDescriptionnulltest() {
        ClipDescription clipDescription = null;
        assertFalse(hasImage(clipDescription));
    }
    
}
