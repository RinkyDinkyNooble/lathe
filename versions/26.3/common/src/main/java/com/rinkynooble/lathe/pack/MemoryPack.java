package com.rinkynooble.lathe.pack;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.InclusiveRange;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/** A resource or data pack held in memory, made the first time the game reads it. */
public final class MemoryPack implements PackResources {
    private final PackLocationInfo location;
    private final PackType type;
    private final Supplier<Map<Identifier, byte[]>> generator;
    private volatile Map<Identifier, byte[]> files;

    public MemoryPack(PackLocationInfo location, PackType type, Supplier<Map<Identifier, byte[]>> generator) {
        this.location = location;
        this.type = type;
        this.generator = generator;
    }

    private Map<Identifier, byte[]> files() {
        Map<Identifier, byte[]> result = files;
        if (result == null) {
            synchronized (this) {
                result = files;
                if (result == null) files = result = generator.get();
            }
        }
        return result;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        if (type != this.type) return null;
        byte[] bytes = files().get(location);
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != this.type) return;
        String prefix = path.endsWith("/") ? path : path + "/";
        for (Map.Entry<Identifier, byte[]> entry : files().entrySet()) {
            Identifier location = entry.getKey();
            if (location.getNamespace().equals(namespace) && location.getPath().startsWith(prefix)) {
                byte[] bytes = entry.getValue();
                output.accept(location, () -> new ByteArrayInputStream(bytes));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> namespaces = new TreeSet<>();
        if (type == this.type) {
            for (Identifier location : files().keySet()) namespaces.add(location.getNamespace());
        }
        return namespaces;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getMetadataSection(MetadataSectionType<T> section) {
        // Since 1.21.9 a pack names the range of formats it supports; this one is made for the running game's.
        if (section == PackMetadataSection.forPackType(type)) {
            return (T) new PackMetadataSection(Component.literal("Lathe's shapes"), new InclusiveRange<>(SharedConstants.getCurrentVersion().packVersion(type)));
        }
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }

    // Forge hides the pack from the pack screen (no @Override: vanilla and Fabric have no such method).
    public boolean isHidden() {
        return true;
    }

    @Override
    public void close() {
    }
}
