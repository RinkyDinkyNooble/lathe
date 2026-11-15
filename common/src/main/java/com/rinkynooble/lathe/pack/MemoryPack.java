package com.rinkynooble.lathe.pack;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/** A resource or data pack held in memory, made the first time the game reads it. */
public final class MemoryPack implements PackResources {
    private final String id;
    private final PackType type;
    private final Supplier<Map<ResourceLocation, byte[]>> generator;
    private volatile Map<ResourceLocation, byte[]> files;

    public MemoryPack(String id, PackType type, Supplier<Map<ResourceLocation, byte[]>> generator) {
        this.id = id;
        this.type = type;
        this.generator = generator;
    }

    private Map<ResourceLocation, byte[]> files() {
        Map<ResourceLocation, byte[]> result = files;
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
    public @Nullable IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != this.type) return null;
        byte[] bytes = files().get(location);
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != this.type) return;
        String prefix = path.endsWith("/") ? path : path + "/";
        for (Map.Entry<ResourceLocation, byte[]> entry : files().entrySet()) {
            ResourceLocation location = entry.getKey();
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
            for (ResourceLocation location : files().keySet()) namespaces.add(location.getNamespace());
        }
        return namespaces;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        if (serializer == PackMetadataSection.TYPE) {
            return (T) new PackMetadataSection(Component.literal("Lathe's shapes"), SharedConstants.getCurrentVersion().getPackVersion(type));
        }
        return null;
    }

    @Override
    public String packId() {
        return id;
    }

    @Override
    public boolean isBuiltin() {
        return true;
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void close() {
    }
}
