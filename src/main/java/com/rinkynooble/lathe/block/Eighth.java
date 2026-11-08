package com.rinkynooble.lathe.block;

import com.rinkynooble.lathe.Octants;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/** One of the eight eighths of a block, named by its three sides: {@code top_north_east}. */
public enum Eighth implements StringRepresentable {
    BOTTOM_NORTH_WEST(0, 0, 0),
    BOTTOM_NORTH_EAST(1, 0, 0),
    BOTTOM_SOUTH_WEST(0, 0, 1),
    BOTTOM_SOUTH_EAST(1, 0, 1),
    TOP_NORTH_WEST(0, 1, 0),
    TOP_NORTH_EAST(1, 1, 0),
    TOP_SOUTH_WEST(0, 1, 1),
    TOP_SOUTH_EAST(1, 1, 1);

    /** 0 for the low half of each axis (west, bottom, north), 1 for the high half. */
    public final int x, y, z;
    private final String name;

    Eighth(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.name = (y == 1 ? "top" : "bottom") + "_" + (z == 1 ? "south" : "north") + "_" + (x == 1 ? "east" : "west");
    }

    public static Eighth of(int x, int y, int z) {
        for (Eighth e : values()) {
            if (e.x == x && e.y == y && e.z == z) return e;
        }
        throw new IllegalArgumentException(x + "," + y + "," + z);
    }

    /** The eighth at octant index {@code x | y<<1 | z<<2} (see {@link Octants}). */
    public static Eighth ofIndex(int index) {
        return of(index & 1, (index >> 1) & 1, (index >> 2) & 1);
    }

    public int index() {
        return Octants.index(x, y, z);
    }

    public int bit() {
        return 1 << index();
    }

    /** The side of the block this eighth is on, along one axis. */
    public Direction side(Direction.Axis axis) {
        int high = switch (axis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
        return Direction.get(high == 1 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE, axis);
    }

    /** The eighth on the given sides, one per axis. */
    public static Eighth of(Direction a, Direction b, Direction c) {
        int[] xyz = new int[3];
        for (Direction d : new Direction[]{a, b, c}) {
            xyz[d.getAxis().ordinal()] = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
        }
        return of(xyz[0], xyz[1], xyz[2]);
    }

    public Eighth rotate(Rotation rotation) {
        return of(rotation.rotate(side(Direction.Axis.X)), side(Direction.Axis.Y), rotation.rotate(side(Direction.Axis.Z)));
    }

    public Eighth mirror(Mirror mirror) {
        return of(mirror.mirror(side(Direction.Axis.X)), side(Direction.Axis.Y), mirror.mirror(side(Direction.Axis.Z)));
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
