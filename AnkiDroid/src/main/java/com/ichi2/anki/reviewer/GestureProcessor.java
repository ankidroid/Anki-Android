package com.ichi2.anki.reviewer;

import android.content.SharedPreferences;

import com.ichi2.anki.cardviewer.Gesture;
import com.ichi2.anki.cardviewer.ViewerCommand;

import java.util.HashMap;
import java.util.Map;

/**
 * Process {@link ViewerCommand}s in reaction to {@link Gesture}s.
 */
public class GestureProcessor {

    private final ViewerCommand.CommandProcessor commandProcessor;

    private Map<Binding, ViewerCommand> bindingToCommand = new HashMap<>();

    private boolean mEnabled;

    private GestureMapper gestureMapper = new GestureMapper();

    public GestureProcessor(ViewerCommand.CommandProcessor commandProcessor) {
        this.commandProcessor = commandProcessor;
    }

    public void init(SharedPreferences preferences) {
        mEnabled = preferences.getBoolean("gestures", false);

        for (ViewerCommand command : ViewerCommand.values()) {
            setupGesture(preferences, command);
        }

        int sensitivity = preferences.getInt("swipeSensitivity", 100);
        boolean mUseCornerTouch = isBound(Gesture.TAP_TOP_LEFT, Gesture.TAP_TOP_RIGHT, Gesture.TAP_CENTER, Gesture.TAP_BOTTOM_LEFT, Gesture.TAP_BOTTOM_RIGHT);
        gestureMapper.init(sensitivity, mUseCornerTouch);
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    private void setupGesture(SharedPreferences preferences, ViewerCommand command) {
        String key = command.getPreferenceKey();

        String value = preferences.getString(key, "");

        if (value != null) {
            for (String split : value.split(" ")) {
                Binding binding = Binding.fromString(split);

                if (binding.isGesture()) {
                    add(binding, command);
                }
            }
        }
    }

    public void add(Binding binding, ViewerCommand command) {
        bindingToCommand.put(binding, command);
    }

    /**
     * Process a gesture with a {@link ViewerCommand}.
     */
    public void onFling(float dx, float dy, float velocityX, float velocityY,
                        boolean isSelecting, boolean isXScrolling, boolean isYScrolling) {

        Gesture gesture = gestureMapper.gesture(dx, dy, velocityX, velocityY, isSelecting, isXScrolling, isYScrolling);
        if (gesture != null) {
            execute(gesture);
        }
    }

    /**
     * Process a gesture with a {@link ViewerCommand}.
     */
    public void onDoubleTab() {
        execute(Gesture.DOUBLE_TAP);
    }

    /**
     * Process a gesture with a {@link ViewerCommand}.
     */
    public void onLongTap() {
        execute(Gesture.LONG_TAP);
    }

    /**
     * Process a gesture with a {@link ViewerCommand}.
     */
    public void onTap(int height, int width, float posX, float posY) {
        Gesture gesture = gestureMapper.gesture(height, width, posX, posY);
        if (gesture != null) {
            execute(gesture);
        }
    }

    private void execute(Gesture gesture) {
        ViewerCommand command = bindingToCommand.get(Binding.gesture(gesture));
        if (command != null) {
            commandProcessor.executeCommand(command);
        }
    }

    public boolean isBound(Gesture... gestures) {
        for (Gesture gesture : gestures) {
            if (bindingToCommand.get(Binding.gesture(gesture)) != null) {
                return true;
            }
        }

        return false;
    }
}
