package com.rinkynooble.lathe.block;

import net.minecraft.util.StringRepresentable;

public enum VerticalSlabType implements StringRepresentable {
    SINGLE("single"),
    DOUBLE("double");

    private final String name;

    VerticalSlabType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
