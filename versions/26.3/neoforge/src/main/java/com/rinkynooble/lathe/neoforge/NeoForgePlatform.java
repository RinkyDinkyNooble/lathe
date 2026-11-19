package com.rinkynooble.lathe.neoforge;

import com.rinkynooble.lathe.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import com.rinkynooble.lathe.Lathe;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    /**
     * One entry per content root of each mod file: a jar, or (in a dev run) a folder. In a dev run Minecraft's file
     * has two jars (classes, and its assets and data), so tags and models are looked up in each.
     */
    @Override
    public List<ModFiles> modFiles() {
        List<ModFiles> files = new ArrayList<>();
        for (IModFileInfo info : ModList.get().getModFiles()) {
            IModFile file = info.getFile();
            Set<String> ids = new LinkedHashSet<>();
            for (IModInfo mod : info.getMods()) ids.add(mod.getModId());
            Collection<Path> roots = file.getContents().getContentRoots();
            for (Path root : roots) {
                String name = roots.size() == 1 ? file.getFileName() : file.getFileName() + " (" + root.getFileName() + ")";
                files.add(new NeoForgeModFiles(root, ids, name));
            }
        }
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

    /** Jars opened as file systems, kept open for the game's lifetime (packs are rebuilt on every reload). */
    private static final Map<Path, FileSystem> JARS = new ConcurrentHashMap<>();

    private record NeoForgeModFiles(Path root, Set<String> modIds, String name) implements ModFiles {
        @Override
        public @Nullable Path find(String path) {
            if (Files.isDirectory(root)) return root.resolve(path);
            FileSystem jar = JARS.computeIfAbsent(root, NeoForgeModFiles::open);
            return jar == null ? null : jar.getPath(path);
        }

        private static @Nullable FileSystem open(Path jar) {
            try {
                return FileSystems.newFileSystem(jar);
            } catch (IOException | RuntimeException e) {
                Lathe.LOG.debug("Lathe: couldn't open {}: {}", jar, e.toString());
                return null;
            }
        }
    }
}
