package com.rinkynooble.lathe.fabric;

import com.rinkynooble.lathe.Platform;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class FabricPlatform implements Platform {
    /** Vanilla's tool tags plus Fabric's mining levels ({@code fabric:needs_tool_level_4}). */
    private static final Pattern TOOL_TAGS = Pattern.compile("(minecraft|fabric):(mineable/[a-z0-9_./-]+|needs_[a-z0-9_]+_tool|needs_tool_level_[0-9]+)");

    private List<ModFiles> files;

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public synchronized List<ModFiles> modFiles() {
        if (files == null) {
            List<ModFiles> list = new ArrayList<>();
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) list.add(new FabricModFiles(mod));
            files = List.copyOf(list);
        }
        return files;
    }

    /** Vanilla's reach, which is what Forge's block reach is without mods changing it. */
    @Override
    public double blockReach(Player player) {
        return player.isCreative() ? 5.0 : 4.5;
    }

    @Override
    public boolean canHarvest(BlockState source, BlockGetter level, BlockPos pos, Player player) {
        return player.hasCorrectToolForDrops(source);
    }

    @Override
    public Pattern toolTags() {
        return TOOL_TAGS;
    }

    private record FabricModFiles(ModContainer mod) implements ModFiles {
        @Override
        public Set<String> modIds() {
            return Set.of(mod.getMetadata().getId());
        }

        @Override
        public @Nullable Path find(String path) {
            for (Path root : mod.getRootPaths()) {
                Path found = root.resolve(path);
                if (Files.exists(found)) return found;
            }
            return null;
        }

        @Override
        public String name() {
            return mod.getMetadata().getId();
        }
    }
}
