package com.rinkynooble.lathe;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Reads {@code config/lathe/*.rules}. */
public final class Rules {
    public static final Set<String> TEXTURE_KEYS = Set.of("all", "top", "bottom", "side", "particle");

    /** One rule line: the shapes, an id or glob, and its options. */
    public record Line(String file, int number, Set<Shape> shapes, String target, Pattern glob, boolean nocombine,
                       Map<String, ResourceLocation> textures) {
        public String where() {
            return file + ":" + number;
        }

        public boolean isGlob() {
            return glob != null;
        }

        public boolean matches(ResourceLocation id) {
            return glob != null ? glob.matcher(id.toString()).matches() : target.equals(id.toString());
        }

        private static Pattern compile(String target) {
            StringBuilder regex = new StringBuilder();
            for (char c : target.toCharArray()) {
                if (c == '*') regex.append("[^:]*");
                else regex.append(Pattern.quote(String.valueOf(c)));
            }
            return Pattern.compile(regex.toString());
        }
    }

    public final List<String> files = new ArrayList<>();
    public final List<Line> lines = new ArrayList<>();
    public final List<String> problems = new ArrayList<>();
    /** A short hash of every rules file, so a client and a server can tell when theirs differ. */
    public String hash = "none";

    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(Lathe.MOD_ID);
    }

    public static Rules load(Logger log) {
        Rules rules = new Rules();
        Path dir = directory();
        try {
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("example.rules"), EXAMPLE, StandardCharsets.UTF_8);
                log.info("Lathe: created {} with an example.rules (all commented out)", dir);
            }
        } catch (IOException e) {
            log.warn("Lathe: couldn't create {}: {}", dir, e.toString());
        }
        List<Path> paths = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".rules") && Files.isRegularFile(p))
                        .sorted()
                        .forEach(paths::add);
            } catch (IOException e) {
                log.warn("Lathe: couldn't list {}: {}", dir, e.toString());
            }
        }
        MessageDigest digest = sha256();
        for (Path path : paths) {
            String name = path.getFileName().toString();
            List<String> text;
            try {
                text = Files.readAllLines(path, StandardCharsets.UTF_8);
            } catch (IOException e) {
                rules.problems.add(name + ": couldn't read it: " + e);
                continue;
            }
            rules.files.add(name);
            digest.update(name.getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < text.size(); i++) {
                String raw = text.get(i);
                // Hash the rules, not the comments or spacing, so a comment edit doesn't lock players out.
                String content = stripComment(raw).trim();
                if (content.isEmpty()) continue;
                digest.update((content.replaceAll("\\s+", " ") + "\n").getBytes(StandardCharsets.UTF_8));
                Line line = parse(name, i + 1, content, rules.problems);
                if (line != null) rules.lines.add(line);
            }
        }
        if (!rules.lines.isEmpty()) rules.hash = HexFormat.of().formatHex(digest.digest()).substring(0, 12);
        return rules;
    }

    private static String stripComment(String raw) {
        int hash = raw.indexOf('#');
        return hash >= 0 ? raw.substring(0, hash) : raw;
    }

    static Line parse(String file, int number, String content, List<String> problems) {
        String where = file + ":" + number;
        Set<Shape> shapes = EnumSet.noneOf(Shape.class);
        String target = null;
        boolean nocombine = false;
        Map<String, ResourceLocation> textures = new LinkedHashMap<>();
        for (String token : content.split("\\s+")) {
            Shape shape = Shape.byWord(token);
            if (shape != null) {
                shapes.add(shape);
            } else if (token.equalsIgnoreCase("nocombine")) {
                nocombine = true;
            } else if (token.contains("=")) {
                String key = token.substring(0, token.indexOf('=')).toLowerCase(Locale.ROOT);
                String value = token.substring(token.indexOf('=') + 1);
                ResourceLocation texture = ResourceLocation.tryParse(value);
                if (!TEXTURE_KEYS.contains(key)) {
                    problems.add(where + ": unknown option '" + key + "' (textures are all=, top=, bottom=, side=, particle=)");
                } else if (texture == null || !value.contains(":")) {
                    problems.add(where + ": '" + value + "' isn't a texture id like betterblockz:block/lablink_blockz_0");
                } else {
                    textures.put(key, texture);
                }
            } else if (token.contains(":")) {
                if (target != null) {
                    problems.add(where + ": more than one block on a line; only '" + target + "' is used");
                    continue;
                }
                target = token.toLowerCase(Locale.ROOT);
            } else {
                problems.add(where + ": unknown word '" + token + "' (shapes are slab, vslab, stairs, vstairs)");
            }
        }
        if (target == null) {
            problems.add(where + ": no block id (like betterblockz:lablink_blockz_0); line skipped");
            return null;
        }
        boolean glob = target.contains("*");
        if (!glob && ResourceLocation.tryParse(target) == null) {
            problems.add(where + ": '" + target + "' isn't a valid block id; line skipped");
            return null;
        }
        if (shapes.isEmpty()) {
            problems.add(where + ": no shapes for " + target + " (slab, vslab, stairs, vstairs); line skipped");
            return null;
        }
        return new Line(file, number, shapes, target, glob ? Line.compile(target) : null, nocombine, textures);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String EXAMPLE = """
            # Lathe rules. Every *.rules file in config/lathe/ is read, in alphabetical order.
            # One file per source mod keeps things tidy (betterblockz.rules, create.rules, ...).
            #
            # One block (or glob) per line, the shapes first:
            #   slab     top and bottom slabs (and vertical slabs too, when the line also has vslab)
            #   vslab    vertical slab
            #   stairs   stairs
            #   vstairs  vertical stairs: a full-height block with one vertical quarter missing
            #
            # Options after the block:
            #   nocombine            two slabs of this block never make a double slab by hand
            #   all= top= bottom= side= particle=   textures to use when the block's model isn't a plain cube
            #
            # '*' matches any part of a name: minecraft:*_concrete
            #
            # The shapes are new blocks, so the client and the server need the same rules.
            # Removing a rule removes its blocks from every world that has them.
            #
            # slab stairs vslab vstairs minecraft:calcite
            # slab vslab minecraft:*_concrete
            # slab vstairs minecraft:glass nocombine
            """;
}
