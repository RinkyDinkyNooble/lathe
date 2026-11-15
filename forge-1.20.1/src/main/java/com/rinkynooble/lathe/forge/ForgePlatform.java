package com.rinkynooble.lathe.forge;

import com.rinkynooble.lathe.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class ForgePlatform implements Platform {
    private static final Pattern TOOL_TAGS = Pattern.compile("(minecraft|forge):(mineable/[a-z0-9_./-]+|needs_[a-z0-9_]+_tool)");

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
        for (IModFileInfo info : ModList.get().getModFiles()) files.add(new ForgeModFiles(info));
        return files;
    }

    @Override
    public double blockReach(Player player) {
        return player.getBlockReach();
    }

    @Override
    public boolean canHarvest(BlockState source, BlockGetter level, BlockPos pos, Player player) {
        return source.canHarvestBlock(level, pos, player);
    }

    @Override
    public Pattern toolTags() {
        return TOOL_TAGS;
    }

    private record ForgeModFiles(IModFile file, Set<String> modIds) implements ModFiles {
        ForgeModFiles(IModFileInfo info) {
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
