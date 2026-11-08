package com.rinkynooble.lathe;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Every Lathe shape is made of eighths of a block. A shape is an 8-bit mask: bit {@code x | y<<1 | z<<2} is set when
 * the eighth at (x, y, z) is filled, where 0 is the low half of an axis and 1 the high half.
 */
public final class Octants {
    public static final int FULL = 0xFF;
    private static final VoxelShape[] SHAPES = new VoxelShape[256];

    private Octants() {
    }

    public static int index(int x, int y, int z) {
        return x | (y << 1) | (z << 2);
    }

    /** The half of the block on side {@code side}. */
    public static int half(Direction side) {
        int mask = 0;
        for (int i = 0; i < 8; i++) {
            int x = i & 1, y = (i >> 1) & 1, z = (i >> 2) & 1;
            boolean in = switch (side) {
                case DOWN -> y == 0;
                case UP -> y == 1;
                case NORTH -> z == 0;
                case SOUTH -> z == 1;
                case WEST -> x == 0;
                case EAST -> x == 1;
            };
            if (in) mask |= 1 << i;
        }
        return mask;
    }

    public static VoxelShape shape(int mask) {
        VoxelShape cached = SHAPES[mask];
        if (cached != null) return cached;
        VoxelShape shape = Shapes.empty();
        for (int i = 0; i < 8; i++) {
            if ((mask & (1 << i)) == 0) continue;
            double x = (i & 1) * 0.5, y = ((i >> 1) & 1) * 0.5, z = ((i >> 2) & 1) * 0.5;
            shape = Shapes.or(shape, Shapes.box(x, y, z, x + 0.5, y + 0.5, z + 0.5));
        }
        shape = shape.optimize();
        SHAPES[mask] = shape;
        return shape;
    }

    /** Which eighths a shape fills, judged by their centres. */
    public static int maskOf(VoxelShape shape) {
        int mask = 0;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) * 0.5 + 0.25, y = ((i >> 1) & 1) * 0.5 + 0.25, z = ((i >> 2) & 1) * 0.5 + 0.25;
            for (AABB box : shape.toAabbs()) {
                if (box.minX <= x && x <= box.maxX && box.minY <= y && y <= box.maxY && box.minZ <= z && z <= box.maxZ) {
                    mask |= 1 << i;
                    break;
                }
            }
        }
        return mask;
    }
}
