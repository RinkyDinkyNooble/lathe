package com.rinkynooble.lathe;

import com.rinkynooble.lathe.block.LatheCornerBlock;
import com.rinkynooble.lathe.block.LatheEighthBlock;
import com.rinkynooble.lathe.block.LatheQuarterBlock;
import com.rinkynooble.lathe.block.LatheSlabBlock;
import com.rinkynooble.lathe.block.LatheStairBlock;
import com.rinkynooble.lathe.block.LatheVerticalSlabBlock;
import com.rinkynooble.lathe.block.LatheVerticalStairBlock;
import com.rinkynooble.lathe.item.LatheBlockItem;
import com.rinkynooble.lathe.item.LatheSlabItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the shapes are made from and how. Each loader registers them its own way, after the source blocks exist,
 * using the pieces here.
 */
public final class Registration {
    /** Every material, in rule order. Filled while blocks register. */
    public static final Map<Identifier, Material> MATERIALS = new LinkedHashMap<>();
    public static final List<String> WARNINGS = new ArrayList<>();
    /** Every Lathe item, in the order the creative tab shows them. */
    public static final List<Item> ITEMS = new ArrayList<>();
    /** For the startup log: the source blocks and shapes each rules file made. */
    private static final Map<String, Set<Identifier>> BLOCKS_PER_FILE = new LinkedHashMap<>();
    private static final Map<String, Set<String>> SHAPES_PER_FILE = new LinkedHashMap<>();

    private Registration() {
    }

    public static void warn(String message) {
        WARNINGS.add(message);
        Lathe.LOG.warn("Lathe: {}", message);
    }

    /**
     * Applies one rule line to one source block it names: makes or extends the block's material. Returns false,
     * with a warning for a line naming the block itself, when the block can't be shaped.
     */
    public static boolean apply(Rules.Line line, Identifier id, Block source) {
        String unsuitable = unsuitable(source);
        if (unsuitable != null) {
            if (!line.isGlob()) warn(line.where() + ": skipped " + id + ": " + unsuitable);
            return false;
        }
        Material material = MATERIALS.computeIfAbsent(id, Material::new);
        material.source = source;
        material.shapes.addAll(line.shapes());
        if (line.nocombine()) material.combine = false;
        material.textures.putAll(line.textures());
        BLOCKS_PER_FILE.computeIfAbsent(line.file(), k -> new LinkedHashSet<>()).add(id);
        for (Shape shape : line.shapes()) SHAPES_PER_FILE.computeIfAbsent(line.file(), k -> new LinkedHashSet<>()).add(id + "/" + shape.word);
        return true;
    }

    public static void warnUnknown(Rules.Line line, Identifier id) {
        boolean modLoaded = Lathe.platform.isModLoaded(id.getNamespace());
        warn(line.where() + ": unknown block " + id + (modLoaded ? "" : " (mod '" + id.getNamespace() + "' isn't loaded)"));
    }

    public static void warnGlob(Rules.Line line, int matched, int used) {
        int skipped = matched - used;
        warn(line.where() + ": " + line.target() + " made shapes for " + used + " blocks ("
                + (used * line.shapes().size()) + " shapes)" + (skipped > 0 ? ", skipped " + skipped + " that can't be shaped (not full blocks, or redacted by Scalpel)" : ""));
    }

    /** Makes a material's blocks (not yet registered), in shape order. */
    public static Map<Shape, Block> makeBlocks(Material material) {
        Map<Shape, Block> made = new EnumMap<>(Shape.class);
        for (Shape shape : EnumSet.copyOf(material.shapes)) {
            Block block = switch (shape) {
                case SLAB -> new LatheSlabBlock(material);
                case VSLAB -> new LatheVerticalSlabBlock(material);
                case STAIRS -> new LatheStairBlock(material);
                case VSTAIRS -> new LatheVerticalStairBlock(material);
                case CORNER -> new LatheCornerBlock(material);
                case QUARTER -> new LatheQuarterBlock(material);
                case EIGHTH -> new LatheEighthBlock(material);
            };
            material.blocks.put(shape, block);
            made.put(shape, block);
        }
        return made;
    }

    /**
     * Makes a material's items (not yet registered), in shape order. Vertical slabs get none when the slab item
     * places them.
     */
    public static Map<Shape, BlockItem> makeItems(Material material) {
        Map<Shape, BlockItem> made = new EnumMap<>(Shape.class);
        for (Shape shape : Shape.values()) {
            Block block = material.block(shape);
            if (block == null) continue;
            BlockItem item;
            if (shape == Shape.SLAB) {
                item = new LatheSlabItem(block, material, itemProperties(material, shape));
            } else if (shape == Shape.VSLAB) {
                if (material.has(Shape.SLAB)) continue; // the slab item places these
                item = new LatheSlabItem(block, material, itemProperties(material, shape));
            } else {
                item = new LatheBlockItem(block, itemProperties(material, shape));
            }
            material.items.put(shape, item);
            made.put(shape, item);
        }
        return made;
    }

    /**
     * An item's properties: since 1.21.2 an item knows its id (the same as its block's) from the start, and takes
     * the block's name key as vanilla's block items do.
     */
    private static Item.Properties itemProperties(Material material, Shape shape) {
        return new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, material.id(shape)))
                .useBlockDescriptionPrefix();
    }

    public static final Identifier TAB_ID = Identifier.fromNamespaceAndPath(Lathe.MOD_ID, "shapes");

    public static CreativeModeTab makeTab(CreativeModeTab.Builder builder) {
        return builder
                .title(Component.translatable("itemGroup.lathe"))
                .icon(() -> new ItemStack(ITEMS.get(0)))
                .displayItems((parameters, output) -> ITEMS.forEach(output::accept))
                .build();
    }

    /** The startup log's summary: blocks and shapes per rules file, and in total. */
    public static void logSummary(Rules rules) {
        for (String file : rules.files) {
            Lathe.LOG.info("Lathe: {}: {} blocks, {} shapes", file, BLOCKS_PER_FILE.getOrDefault(file, Set.of()).size(),
                    SHAPES_PER_FILE.getOrDefault(file, Set.of()).size());
        }
        if (!MATERIALS.isEmpty()) {
            int shapes = MATERIALS.values().stream().mapToInt(m -> m.blocks.size()).sum();
            Lathe.LOG.info("Lathe: {} shapes for {} blocks", shapes, MATERIALS.size());
        }
    }

    /** Why a block can't be shaped, or null when it can. */
    private static @Nullable String unsuitable(Block source) {
        if (source == null || source == Blocks.AIR || source.defaultBlockState().isAir()) return "it's air";
        if (isScalpelPlaceholder(source.getClass())) return "Scalpel redacted it";
        try {
            if (!Block.isShapeFullBlock(source.defaultBlockState().getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))) {
                return "it isn't a full block";
            }
        } catch (RuntimeException e) {
            return "couldn't read its shape (" + e + ")";
        }
        return null;
    }

    /**
     * Scalpel swaps a redacted block for a "Redacted" placeholder while blocks register, whatever the mod order.
     * Shapes of a placeholder would be "Redacted Slab", so they're skipped. Matched by name, so Lathe doesn't need
     * Scalpel.
     */
    private static boolean isScalpelPlaceholder(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Class<?> i : c.getInterfaces()) {
                if (i.getName().equals("com.rinkynooble.scalpel.forge.content.Redacted")) return true;
            }
        }
        return false;
    }
}
