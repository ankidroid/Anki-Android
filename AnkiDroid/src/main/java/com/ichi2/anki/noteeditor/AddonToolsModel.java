package com.ichi2.anki.noteeditor;

public class AddonToolsModel {
    String name;
    Character icon;

    public AddonToolsModel(String name, Character icon) {
        this.name = name;
        this.icon = icon;
    }

    public String getName() {
        return name;
    }

    public Character getIcon() {
        return icon;
    }
}
