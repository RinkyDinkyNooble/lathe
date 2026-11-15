package com.rinkynooble.lathe.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Stairs, vertical stairs, corner, quarter and eighth items. Named after the block, so "Lab Link Base Stairs" in every language. */
public class LatheBlockItem extends BlockItem {
    public LatheBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return getBlock().getName();
    }
}
