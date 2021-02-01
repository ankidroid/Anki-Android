package com.ichi2.anki.reviewer;

import android.content.SharedPreferences;
import android.view.KeyEvent;

import com.ichi2.anki.cardviewer.ViewerCommand;

import java.util.HashMap;
import java.util.Map;

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
        String key = "binding_" + command.name();

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

    public boolean onKey(KeyEvent event) {

        Binding.ModifierKeys modifiers = new Binding.ModifierKeys(event.isShiftPressed(), event.isCtrlPressed(), event.isAltPressed());

        ViewerCommand command;

        command = bindingToCommand.get(Binding.keyCode(modifiers, event.getKeyCode()));
        if (command != null) {
            commandProcessor.executeCommand(command);
            return true;
        }

        // passing in metaState: 0 means that Ctrl+1 returns '1' instead of '\0'
        // NOTE: We do not differentiate on upper/lower case via KeyEvent.META_CAPS_LOCK_ON
        int unicodeChar = event.getUnicodeChar(event.getMetaState() & (KeyEvent.META_SHIFT_ON | KeyEvent.META_NUM_LOCK_ON));

        command = bindingToCommand.get(Binding.unicode(modifiers, (char)unicodeChar));
        if (command != null) {
            commandProcessor.executeCommand(command);
            return true;
        }


        return false;
    }
}
