package com.ichi2.anki.reviewer;

import android.view.KeyEvent;

import com.ichi2.anki.cardviewer.Gesture;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class BindingTest {
    @Test
    public void testToString() {
        assertEquals(Binding.KEY_PREFIX + "Ä", Binding.unicode('Ä').toString());
        assertEquals(Binding.KEY_PREFIX + "Ctrl+Ä", Binding.unicode(Binding.ModifierKeys.ctrl(), 'Ä').toString());
        assertEquals(Binding.KEY_PREFIX + "Shift+Ä", Binding.unicode(Binding.ModifierKeys.shift(), 'Ä').toString());
        assertEquals(Binding.KEY_PREFIX + "Alt+Ä", Binding.unicode(Binding.ModifierKeys.alt(), 'Ä').toString());

        assertEquals(Binding.KEY_PREFIX + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_MEDIA_NEXT), Binding.keyCode(KeyEvent.KEYCODE_MEDIA_NEXT).toString());
        assertEquals(Binding.KEY_PREFIX + "Ctrl+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_MEDIA_PREVIOUS), Binding.keyCode(Binding.ModifierKeys.ctrl(), KeyEvent.KEYCODE_MEDIA_PREVIOUS).toString());
        assertEquals(Binding.KEY_PREFIX + "Shift+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_VOLUME_DOWN), Binding.keyCode(Binding.ModifierKeys.shift(), KeyEvent.KEYCODE_VOLUME_DOWN).toString());
        assertEquals(Binding.KEY_PREFIX + "Alt+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_VOLUME_UP), Binding.keyCode(Binding.ModifierKeys.alt(), KeyEvent.KEYCODE_VOLUME_UP).toString());

        assertEquals(Binding.GESTURE_PREFIX + Gesture.TAP_TOP.name(), Binding.gesture(Gesture.TAP_TOP).toString());
    }

    @Test
    public void testFromString() {
        assertEquals(Binding.unicode('Ä'), Binding.fromString(Binding.KEY_PREFIX + "Ä"));
        assertEquals(Binding.unicode(Binding.ModifierKeys.ctrl(), 'Ä'), Binding.fromString(Binding.KEY_PREFIX + "Ctrl+Ä"));
        assertEquals(Binding.unicode(Binding.ModifierKeys.shift(), 'Ä'), Binding.fromString(Binding.KEY_PREFIX + "Shift+Ä"));
        assertEquals(Binding.unicode(Binding.ModifierKeys.alt(), 'Ä'), Binding.fromString(Binding.KEY_PREFIX + "Alt+Ä"));

        assertEquals(Binding.keyCode(KeyEvent.KEYCODE_MEDIA_NEXT) , Binding.fromString(Binding.KEY_PREFIX + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_MEDIA_NEXT)));
        assertEquals(Binding.keyCode(Binding.ModifierKeys.ctrl(), KeyEvent.KEYCODE_MEDIA_PREVIOUS), Binding.fromString(Binding.KEY_PREFIX + "Ctrl+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_MEDIA_PREVIOUS)));
        assertEquals(Binding.keyCode(Binding.ModifierKeys.shift(), KeyEvent.KEYCODE_VOLUME_DOWN), Binding.fromString(Binding.KEY_PREFIX + "Shift+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_VOLUME_DOWN)));
        assertEquals(Binding.keyCode(Binding.ModifierKeys.alt(), KeyEvent.KEYCODE_VOLUME_UP), Binding.fromString(Binding.KEY_PREFIX + "Alt+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_VOLUME_UP)));

        assertEquals(Binding.gesture(Gesture.TAP_TOP), Binding.fromString(Binding.GESTURE_PREFIX + Gesture.TAP_TOP.name()));
    }
}
