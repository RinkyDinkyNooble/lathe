package com.rinkynooble.lathe.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads files straight from the loaded mods' jars (Minecraft's own jar included). Used to find a source block's
 * model, name and tags while Lathe builds its packs, before the game has loaded them.
 */
public final class ModResources {
    private ModResources() {
    }

    /** Mod files to look in for a namespace: the mod with that id first, then every other one. */
    public static List<IModFile> filesFor(String namespace) {
        List<IModFile> files = new ArrayList<>();
        IModFileInfo own = ModList.get().getModFileById(namespace);
        if (own != null) files.add(own.getFile());
        for (IModFileInfo info : ModList.get().getModFiles()) {
            if (!files.contains(info.getFile())) files.add(info.getFile());
        }
        return files;
    }

    public static List<IModFile> allFiles() {
        List<IModFile> files = new ArrayList<>();
        for (IModFileInfo info : ModList.get().getModFiles()) files.add(info.getFile());
        return files;
    }

    /** {@code path} is inside the jar, like {@code assets/betterblockz/models/block/lablink_blockz_0.json}. */
    public static @Nullable Path find(String namespace, String path) {
        for (IModFile file : filesFor(namespace)) {
            Path found = file.findResource(path.split("/"));
            if (Files.isRegularFile(found)) return found;
        }
        return null;
    }

    public static @Nullable JsonElement readJson(String namespace, String path) {
        Path file = find(namespace, path);
        if (file == null) return null;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
