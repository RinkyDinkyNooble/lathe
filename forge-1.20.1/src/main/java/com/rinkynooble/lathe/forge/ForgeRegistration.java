package com.rinkynooble.lathe.forge;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Registration;
import com.rinkynooble.lathe.Rules;
import com.rinkynooble.lathe.Shape;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Registers the shapes on Forge. Runs last among block registrations (see {@link LatheForge}), so the source blocks
 * already exist and can be copied.
 */
final class ForgeRegistration {
    private ForgeRegistration() {
    }

    static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) registerBlocks(event);
        else if (event.getRegistryKey().equals(Registries.ITEM)) registerItems(event);
        else if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) registerTab(event);
    }

    private static void registerBlocks(RegisterEvent event) {
        Rules rules = Lathe.rules;
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
                    Registration.warnUnknown(line, id);
                    continue;
                }
            }
            int used = 0;
            for (ResourceLocation id : matched) {
                if (Registration.apply(line, id, ForgeRegistries.BLOCKS.getValue(id))) used++;
            }
            if (line.isGlob()) Registration.warnGlob(line, matched.size(), used);
        }

        for (Material material : Registration.MATERIALS.values()) {
            for (Map.Entry<Shape, Block> made : Registration.makeBlocks(material).entrySet()) {
                Block block = made.getValue();
                event.register(Registries.BLOCK, material.id(made.getKey()), () -> block);
            }
        }
        Registration.logSummary(rules);
    }

    private static void registerItems(RegisterEvent event) {
        for (Material material : Registration.MATERIALS.values()) {
            for (Map.Entry<Shape, BlockItem> made : Registration.makeItems(material).entrySet()) {
                BlockItem item = made.getValue();
                Registration.ITEMS.add(item);
                event.register(Registries.ITEM, material.id(made.getKey()), () -> item);
            }
        }
    }

    private static void registerTab(RegisterEvent event) {
        if (Registration.ITEMS.isEmpty()) return;
        event.register(Registries.CREATIVE_MODE_TAB, Registration.TAB_ID, () -> Registration.makeTab(CreativeModeTab.builder()));
    }
}
