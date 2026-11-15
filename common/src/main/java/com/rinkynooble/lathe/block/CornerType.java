package com.rinkynooble.lathe.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * A corner piece's kind. {@code inner} is a full block missing one eighth. The outer kinds are half a block plus one
 * eighth on the other side; their name says which axis the half is split along: {@code outer_ud} sits on the bottom
 * or top half (vanilla's outer stairs corner), {@code outer_ew} on the east or west half, {@code outer_ns} on the
 * north or south half.
 */
public enum CornerType implements StringRepresentable {
    INNER("inner", null),
    OUTER_UD("outer_ud", Direction.Axis.Y),
    OUTER_EW("outer_ew", Direction.Axis.X),
    OUTER_NS("outer_ns", Direction.Axis.Z);

    private final String name;
    @Nullable
    public final Direction.Axis axis;

    CornerType(String name, @Nullable Direction.Axis axis) {
        this.name = name;
        this.axis = axis;
    }

    /** The outer kind whose half is split along {@code axis}. */
    public static CornerType outer(Direction.Axis axis) {
        return switch (axis) {
            case X -> OUTER_EW;
            case Y -> OUTER_UD;
            case Z -> OUTER_NS;
        };
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
