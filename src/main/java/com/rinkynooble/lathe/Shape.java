package com.rinkynooble.lathe;

import java.util.Locale;

/** The shapes Lathe can make. The rule word is also the id suffix, so these never change. */
public enum Shape {
    SLAB("slab", "Slab"),
    VSLAB("vslab", "Vertical Slab"),
    STAIRS("stairs", "Stairs"),
    VSTAIRS("vstairs", "Vertical Stairs");

    public final String word;
    public final String englishName;

    Shape(String word, String englishName) {
        this.word = word;
        this.englishName = englishName;
    }

    /** The translation key for "%s Slab" and so on. */
    public String nameKey() {
        return "lathe.shape." + word;
    }

    public static Shape byWord(String word) {
        for (Shape shape : values()) {
            if (shape.word.equals(word.toLowerCase(Locale.ROOT))) return shape;
        }
        return null;
    }
}
