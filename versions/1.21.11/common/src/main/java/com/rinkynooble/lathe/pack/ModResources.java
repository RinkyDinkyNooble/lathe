package com.rinkynooble.lathe.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.Platform.ModFiles;
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
    public static List<ModFiles> filesFor(String namespace) {
        List<ModFiles> all = allFiles();
        List<ModFiles> files = new ArrayList<>();
        for (ModFiles file : all) {
            if (file.modIds().contains(namespace)) {
                files.add(file);
                break;
            }
        }
        for (ModFiles file : all) {
            if (!files.contains(file)) files.add(file);
        }
        return files;
    }

    public static List<ModFiles> allFiles() {
        return Lathe.platform.modFiles();
    }

    /** {@code path} is inside the jar, like {@code assets/betterblockz/models/block/lablink_blockz_0.json}. */
    public static @Nullable Path find(String namespace, String path) {
        for (ModFiles file : filesFor(namespace)) {
            Path found = file.find(path);
            if (found != null && Files.isRegularFile(found)) return found;
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
