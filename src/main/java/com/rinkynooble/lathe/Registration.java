package com.rinkynooble.lathe;

import com.rinkynooble.lathe.block.LatheSlabBlock;
import com.rinkynooble.lathe.block.LatheStairBlock;
import com.rinkynooble.lathe.block.LatheVerticalSlabBlock;
import com.rinkynooble.lathe.block.LatheVerticalStairBlock;
import com.rinkynooble.lathe.item.LatheBlockItem;
import com.rinkynooble.lathe.item.LatheSlabItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registers the shapes. Runs last among block registrations, so the source blocks already exist and can be copied.
 */
public final class Registration {
    /** Every material, in rule order. Filled while blocks register. */
    public static final Map<ResourceLocation, Material> MATERIALS = new LinkedHashMap<>();
    public static final List<String> WARNINGS = new ArrayList<>();
    /** Every Lathe item, in the order the creative tab shows them. */
    public static final List<Item> ITEMS = new ArrayList<>();

    private Registration() {
    }

    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) registerBlocks(event);
        else if (event.getRegistryKey().equals(Registries.ITEM)) registerItems(event);
        else if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) registerTab(event);
    }

    private static void warn(String message) {
        WARNINGS.add(message);
        Lathe.LOG.warn("Lathe: {}", message);
    }

    private static void registerBlocks(RegisterEvent event) {
        Rules rules = Lathe.rules;
        Map<String, Set<ResourceLocation>> blocksPerFile = new LinkedHashMap<>();
        Map<String, Set<String>> shapesPerFile = new LinkedHashMap<>();
        for (String file : rules.files) {
            blocksPerFile.put(file, new LinkedHashSet<>());
            shapesPerFile.put(file, new LinkedHashSet<>());
        }
        List<ResourceLocation> allBlocks = new ArrayList<>(ForgeRegistries.BLOCKS.getKeys());
        Collections.sort(allBlocks);
        for (Rules.Line line : rules.lines) {
            List<ResourceLocation> matched = new ArrayList<>();
            if (line.isGlob()) {
                for (ResourceLocation id : allBlocks) {
                    if (!id.getNamespace().equals(Lathe.MOD_ID) && line.matches(id)) matched.add(id);
                }
            } else {
                ResourceLocation id = new ResourceLocation(line.target());
                if (ForgeRegistries.BLOCKS.containsKey(id)) {
                    matched.add(id);
                } else {
                    boolean modLoaded = net.minecraftforge.fml.ModList.get().isLoaded(id.getNamespace());
                    warn(line.where() + ": unknown block " + id + (modLoaded ? "" : " (mod '" + id.getNamespace() + "' isn't loaded)"));
                    continue;
                }
            }
            int used = 0;
            for (ResourceLocation id : matched) {
                Block source = ForgeRegistries.BLOCKS.getValue(id);
                String unsuitable = unsuitable(id, source);
                if (unsuitable != null) {
                    if (!line.isGlob()) warn(line.where() + ": skipped " + id + ": " + unsuitable);
                    continue;
                }
                used++;
                Material material = MATERIALS.computeIfAbsent(id, Material::new);
                material.source = source;
                material.shapes.addAll(line.shapes());
                if (line.nocombine()) material.combine = false;
                material.textures.putAll(line.textures());
                blocksPerFile.get(line.file()).add(id);
                for (Shape shape : line.shapes()) shapesPerFile.get(line.file()).add(id + "/" + shape.word);
            }
            if (line.isGlob()) {
                int skipped = matched.size() - used;
                warn(line.where() + ": " + line.target() + " made shapes for " + used + " blocks ("
                        + (used * line.shapes().size()) + " shapes)" + (skipped > 0 ? ", skipped " + skipped + " that can't be shaped (not full blocks, or redacted by Scalpel)" : ""));
            }
        }

        for (Material material : MATERIALS.values()) {
            for (Shape shape : EnumSet.copyOf(material.shapes)) {
                Block block = switch (shape) {
                    case SLAB -> new LatheSlabBlock(material);
                    case VSLAB -> new LatheVerticalSlabBlock(material);
                    case STAIRS -> new LatheStairBlock(material);
                    case VSTAIRS -> new LatheVerticalStairBlock(material);
                };
                material.blocks.put(shape, block);
                event.register(Registries.BLOCK, material.id(shape), () -> block);
            }
        }

        for (String file : rules.files) {
            Lathe.LOG.info("Lathe: {}: {} blocks, {} shapes", file, blocksPerFile.get(file).size(), shapesPerFile.get(file).size());
        }
        if (!MATERIALS.isEmpty()) {
            int shapes = MATERIALS.values().stream().mapToInt(m -> m.blocks.size()).sum();
            Lathe.LOG.info("Lathe: {} shapes for {} blocks", shapes, MATERIALS.size());
        }
    }

    /** Why a block can't be shaped, or null when it can. */
    private static String unsuitable(ResourceLocation id, Block source) {
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

    private static void registerItems(RegisterEvent event) {
        for (Material material : MATERIALS.values()) {
            for (Shape shape : Shape.values()) {
                Block block = material.block(shape);
                if (block == null) continue;
                BlockItem item;
                if (shape == Shape.SLAB) {
                    item = new LatheSlabItem(block, material, new Item.Properties());
                } else if (shape == Shape.VSLAB) {
                    if (material.has(Shape.SLAB)) continue; // the slab item places these
                    item = new LatheSlabItem(block, material, new Item.Properties());
                } else {
                    item = new LatheBlockItem(block, new Item.Properties());
                }
                material.items.put(shape, item);
                ITEMS.add(item);
                event.register(Registries.ITEM, material.id(shape), () -> item);
            }
        }
    }

    private static void registerTab(RegisterEvent event) {
        if (ITEMS.isEmpty()) return;
        event.register(Registries.CREATIVE_MODE_TAB, new ResourceLocation(Lathe.MOD_ID, "shapes"), () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.lathe"))
                .icon(() -> new ItemStack(ITEMS.get(0)))
                .displayItems((parameters, output) -> ITEMS.forEach(output::accept))
                .build());
    }
}
