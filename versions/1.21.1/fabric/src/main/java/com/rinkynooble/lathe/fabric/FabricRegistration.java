package com.rinkynooble.lathe.fabric;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Registration;
import com.rinkynooble.lathe.Rules;
import com.rinkynooble.lathe.Shape;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers the shapes on Fabric. Fabric has no "every mod has registered" moment, so Lathe makes a source block's
 * shapes the moment that block registers, whichever mod registers it and whenever: blocks already there when Lathe
 * starts, then each new one. The startup log and the creative tab's order wait for {@link #finish()}, which runs
 * once every mod's main entry point has run.
 */
final class FabricRegistration {
    /** For each glob line (by index): how many blocks it matched, and how many of those could be shaped. */
    private static int[][] globCounts;
    private static boolean finished;
    private static boolean tabRegistered;

    private FabricRegistration() {
    }

    static void start() {
        globCounts = new int[Lathe.rules.lines.size()][2];
        if (Lathe.rules.lines.isEmpty()) return;
        List<ResourceLocation> existing = new ArrayList<>(BuiltInRegistries.BLOCK.keySet());
        Collections.sort(existing);
        RegistryEntryAddedCallback.event(BuiltInRegistries.BLOCK).register((rawId, id, block) -> onBlock(id, block));
        for (ResourceLocation id : existing) onBlock(id, BuiltInRegistries.BLOCK.get(id));
    }

    private static void onBlock(ResourceLocation id, Block block) {
        if (id.getNamespace().equals(Lathe.MOD_ID)) return;
        List<Rules.Line> lines = Lathe.rules.lines;
        boolean used = false;
        for (int i = 0; i < lines.size(); i++) {
            Rules.Line line = lines.get(i);
            if (!line.matches(id)) continue;
            boolean applied = Registration.apply(line, id, block);
            if (line.isGlob()) {
                globCounts[i][0]++;
                if (applied) globCounts[i][1]++;
            }
            used |= applied;
        }
        if (!used) return;

        Material material = Registration.MATERIALS.get(id);
        for (Map.Entry<Shape, Block> made : Registration.makeBlocks(material).entrySet()) {
            Registry.register(BuiltInRegistries.BLOCK, material.id(made.getKey()), made.getValue());
        }
        for (Map.Entry<Shape, BlockItem> made : Registration.makeItems(material).entrySet()) {
            BlockItem item = made.getValue();
            Registry.register(BuiltInRegistries.ITEM, material.id(made.getKey()), item);
            // Fabric API does this for every block item too; doing it here as well keeps pick-block and the
            // vertical slab's drop working on loaders that don't.
            item.registerBlocks(Item.BY_BLOCK, item);
            Registration.ITEMS.add(item);
        }
        if (!tabRegistered && !Registration.ITEMS.isEmpty()) {
            tabRegistered = true;
            Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Registration.TAB_ID, Registration.makeTab(FabricItemGroup.builder()));
        }
    }

    /**
     * Once every mod's blocks are in: warns about rules that named no block, logs the summary, and puts the
     * materials and items in rule order, as on Forge. Runs from the client or server entry point; only once.
     */
    static synchronized void finish() {
        if (finished) return;
        finished = true;
        Rules rules = Lathe.rules;
        for (int i = 0; i < rules.lines.size(); i++) {
            Rules.Line line = rules.lines.get(i);
            if (line.isGlob()) {
                Registration.warnGlob(line, globCounts[i][0], globCounts[i][1]);
            } else {
                ResourceLocation id = ResourceLocation.parse(line.target());
                if (!BuiltInRegistries.BLOCK.containsKey(id)) Registration.warnUnknown(line, id);
            }
        }

        // Rule order, then id order within a glob: the order Forge registers them in.
        Map<ResourceLocation, Material> ordered = new LinkedHashMap<>();
        List<ResourceLocation> sorted = new ArrayList<>(Registration.MATERIALS.keySet());
        Collections.sort(sorted);
        for (Rules.Line line : rules.lines) {
            for (ResourceLocation id : sorted) {
                if (line.matches(id)) ordered.putIfAbsent(id, Registration.MATERIALS.get(id));
            }
        }
        ordered.putAll(Registration.MATERIALS); // anything else keeps its place at the end
        Registration.MATERIALS.clear();
        Registration.MATERIALS.putAll(ordered);
        Registration.ITEMS.clear();
        for (Material material : Registration.MATERIALS.values()) {
            for (Shape shape : Shape.values()) {
                BlockItem item = material.items.get(shape);
                if (item != null) Registration.ITEMS.add(item);
            }
        }
        Registration.logSummary(rules);
    }
}
