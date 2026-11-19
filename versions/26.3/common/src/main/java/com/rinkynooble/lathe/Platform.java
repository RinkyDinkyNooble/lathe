package com.rinkynooble.lathe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** The few things each loader does its own way. One implementation per loader folder. */
public interface Platform {
    /** The game's config folder ({@code config/}). */
    Path configDir();

    boolean isModLoaded(String modId);

    /** Every loaded mod's files, Minecraft's own included, in load order. */
    List<ModFiles> modFiles();

    /** How far the player can reach to break a block. */
    double blockReach(Player player);

    /** Whether the player's tool gets drops from {@code source}, as that block itself decides. */
    boolean canHarvest(BlockState source, BlockGetter level, BlockPos pos, Player player);

    /** The tool tags copied from a source block to its shapes, so other mods see the same tool needs. */
    Pattern toolTags();

    /** One mod file (a jar, or a folder in a dev run) and the mods in it. */
    interface ModFiles {
        Set<String> modIds();

        /** {@code path} inside the file, like {@code data} or {@code assets/minecraft/models/block/stone.json}; null or missing when it isn't there. */
        @Nullable
        Path find(String path);

        String name();
    }
}
