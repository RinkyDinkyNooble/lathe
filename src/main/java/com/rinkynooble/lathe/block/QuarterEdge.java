package com.rinkynooble.lathe.block;

import com.rinkynooble.lathe.Octants;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * One of the 12 edges of a block, where a quarter runs. Named by its two sides in the order y, z, x, as
 * {@link Eighth} names are: upright poles {@code north_west} and so on, beams along x {@code bottom_north}, beams
 * along z {@code bottom_west}.
 */
public enum QuarterEdge implements StringRepresentable {
    NORTH_WEST(Direction.NORTH, Direction.WEST),
    NORTH_EAST(Direction.NORTH, Direction.EAST),
    SOUTH_WEST(Direction.SOUTH, Direction.WEST),
    SOUTH_EAST(Direction.SOUTH, Direction.EAST),
    BOTTOM_NORTH(Direction.DOWN, Direction.NORTH),
    BOTTOM_SOUTH(Direction.DOWN, Direction.SOUTH),
    TOP_NORTH(Direction.UP, Direction.NORTH),
    TOP_SOUTH(Direction.UP, Direction.SOUTH),
    BOTTOM_WEST(Direction.DOWN, Direction.WEST),
    BOTTOM_EAST(Direction.DOWN, Direction.EAST),
    TOP_WEST(Direction.UP, Direction.WEST),
    TOP_EAST(Direction.UP, Direction.EAST);

    /** The two sides of the block the quarter is against. */
    public final Direction a, b;
    private final String name;

    QuarterEdge(Direction a, Direction b) {
        this.a = a;
        this.b = b;
        this.name = sideName(a) + "_" + sideName(b);
    }

    private static String sideName(Direction side) {
        return switch (side) {
            case DOWN -> "bottom";
            case UP -> "top";
            default -> side.getSerializedName();
        };
    }

    /** The edge between two sides on different axes, in either order. */
    public static QuarterEdge of(Direction p, Direction q) {
        for (QuarterEdge edge : values()) {
            if ((edge.a == p && edge.b == q) || (edge.a == q && edge.b == p)) return edge;
        }
        throw new IllegalArgumentException(p + "," + q);
    }

    /** The axis the quarter runs along: Y for a pole, X or Z for a beam. */
    public Direction.Axis axis() {
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != a.getAxis() && axis != b.getAxis()) return axis;
        }
        throw new IllegalStateException();
    }

    /** The eighths the quarter fills (see {@link Octants}). */
    public int mask() {
        return Octants.half(a) & Octants.half(b);
    }

    /** Turns both sides; a quarter turn makes a beam along x one along z and back. */
    public QuarterEdge rotate(Rotation rotation) {
        return of(rotation.rotate(a), rotation.rotate(b));
    }

    public QuarterEdge mirror(Mirror mirror) {
        return of(mirror.mirror(a), mirror.mirror(b));
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
