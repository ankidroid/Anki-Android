package com.ichi2.anki.reviewer;

import android.content.SharedPreferences;
import android.view.KeyEvent;

import com.ichi2.anki.cardviewer.Gesture;
import com.ichi2.anki.cardviewer.ViewerCommand;

import java.util.HashMap;
import java.util.Map;

/**
 * Process {@link ViewerCommand}s in reaction to key presses.
 */
public class KeyProcessor {

    private final ViewerCommand.CommandProcessor commandProcessor;

    private Map<Binding, ViewerCommand> bindingToCommand = new HashMap<>();

    public KeyProcessor(ViewerCommand.CommandProcessor commandProcessor) {
        this.commandProcessor = commandProcessor;
    }

    public void setup(SharedPreferences preferences) {

        for (ViewerCommand command : ViewerCommand.values()) {
            setupKey(preferences, command);
        }
    }

    private void setupKey(SharedPreferences preferences, ViewerCommand command) {
        String key = command.getPreferenceKey();

        String value = preferences.getString(key, "");
        if (value != null) {
            for (String split : value.split(" ")) {
                Binding binding = Binding.fromString(split);

                if (binding.isKey()) {
                    add(binding, command);
                }
            }
        }
    }

    public void add(Binding binding, ViewerCommand command) {
        bindingToCommand.put(binding, command);
    }


    /**
     * Process a key with a {@link ViewerCommand}.
     */
    public boolean onKey(KeyEvent event) {

        ViewerCommand command = bindingToCommand.get(Binding.key(event));
        if (command != null) {
            commandProcessor.executeCommand(command);
            return true;
        }

        return false;
    }
}
