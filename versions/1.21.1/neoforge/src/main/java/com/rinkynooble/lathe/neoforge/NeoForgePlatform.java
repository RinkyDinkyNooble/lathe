package com.rinkynooble.lathe.neoforge;

import com.rinkynooble.lathe.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class NeoForgePlatform implements Platform {
    /** Vanilla's tool tags plus NeoForge's extra tiers ({@code neoforge:needs_wood_tool}, {@code needs_netherite_tool}...). */
    private static final Pattern TOOL_TAGS = Pattern.compile("(minecraft|neoforge):(mineable/[a-z0-9_./-]+|needs_[a-z0-9_]+_tool)");

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public List<ModFiles> modFiles() {
        List<ModFiles> files = new ArrayList<>();
        for (IModFileInfo info : ModList.get().getModFiles()) files.add(new NeoForgeModFiles(info));
        return files;
    }

    /** The player's block reach (vanilla's attribute since 1.20.5: 4.5, or 5 in creative). */
    @Override
    public double blockReach(Player player) {
        return player.blockInteractionRange();
    }

    @Override
    public boolean canHarvest(BlockState source, BlockGetter level, BlockPos pos, Player player) {
        return source.canHarvestBlock(level, pos, player);
    }

    @Override
    public Pattern toolTags() {
        return TOOL_TAGS;
    }

    private record NeoForgeModFiles(IModFile file, Set<String> modIds) implements ModFiles {
        NeoForgeModFiles(IModFileInfo info) {
            this(info.getFile(), ids(info));
        }

        private static Set<String> ids(IModFileInfo info) {
            Set<String> ids = new LinkedHashSet<>();
            for (IModInfo mod : info.getMods()) ids.add(mod.getModId());
            return ids;
        }

        @Override
        public Path find(String path) {
            return file.findResource(path.split("/"));
        }

        @Override
        public String name() {
            return file.getFileName();
        }
    }
}
