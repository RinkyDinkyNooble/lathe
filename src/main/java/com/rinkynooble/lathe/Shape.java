package com.rinkynooble.lathe;

import java.util.Locale;

/** The shapes Lathe can make. The rule words and id suffixes never change. */
public enum Shape {
    SLAB("slab", "slab", "Slab"),
    VSLAB("vslab", "vslab", "Vertical Slab"),
    STAIRS("stairs", "stairs", "Stairs"),
    VSTAIRS("vstairs", "vstairs", "Vertical Stairs"),
    /** One block holding all 32 corner pieces as states; the rule word is plural, the id singular. */
    CORNER("corners", "corner", "Corner");

    /** The word in a rules file. */
    public final String word;
    /** The end of the block's id: {@code lathe:<source>_<suffix>}. */
    public final String suffix;
    public final String englishName;

    Shape(String word, String suffix, String englishName) {
        this.word = word;
        this.suffix = suffix;
        this.englishName = englishName;
    }

    /** The translation key for "%s Slab" and so on. */
    public String nameKey() {
        return "lathe.shape." + suffix;
    }

    public static Shape byWord(String word) {
        for (Shape shape : values()) {
            if (shape.word.equals(word.toLowerCase(Locale.ROOT))) return shape;
        }
        return null;
    }

    /** The rule words, for messages: "slab, vslab, stairs, vstairs, corners". */
    public static String words() {
        StringBuilder words = new StringBuilder();
        for (Shape shape : values()) {
            if (!words.isEmpty()) words.append(", ");
            words.append(shape.word);
        }
        return words.toString();
    }
}
