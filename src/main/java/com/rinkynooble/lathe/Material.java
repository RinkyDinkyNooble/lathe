package com.rinkynooble.lathe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * One source block and the shapes made from it. Built from the rules while blocks register, then filled in with
 * the registered blocks and items.
 */
public final class Material {
    public final ResourceLocation sourceId;
    public final Set<Shape> shapes = EnumSet.noneOf(Shape.class);
    /** False when a rule says {@code nocombine}: two slabs never make a double slab by hand. */
    public boolean combine = true;
    /** Texture overrides from the rules: all, top, bottom, side, particle. */
    public final Map<String, ResourceLocation> textures = new LinkedHashMap<>();

    public Block source;
    public final Map<Shape, Block> blocks = new EnumMap<>(Shape.class);
    public final Map<Shape, BlockItem> items = new EnumMap<>(Shape.class);

    public Material(ResourceLocation sourceId) {
        this.sourceId = sourceId;
    }

    /** {@code <namespace>__<path>}, the start of every shape id. */
    public String baseName() {
        return sourceId.getNamespace() + "__" + sourceId.getPath();
    }

    public ResourceLocation id(Shape shape) {
        return new ResourceLocation(Lathe.MOD_ID, baseName() + "_" + shape.suffix);
    }

    public Block block(Shape shape) {
        return blocks.get(shape);
    }

    public boolean has(Shape shape) {
        return blocks.containsKey(shape);
    }

    /**
     * The item that places a shape. Vertical slabs share the slab's item when both exist, so the vertical slab
     * has no item of its own then.
     */
    public Item itemFor(Shape shape) {
        if (shape == Shape.VSLAB && items.containsKey(Shape.SLAB)) return items.get(Shape.SLAB);
        return items.get(shape);
    }

    /** True when the slab item can place both horizontal and vertical slabs. */
    public boolean slabItemPlacesBoth() {
        return has(Shape.SLAB) && has(Shape.VSLAB);
    }
}
