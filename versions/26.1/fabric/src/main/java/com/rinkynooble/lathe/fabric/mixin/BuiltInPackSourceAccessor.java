package com.rinkynooble.lathe.fabric.mixin;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Which kind of packs a built-in source offers: the client's resources, or a server's data. */
@Mixin(BuiltInPackSource.class)
public interface BuiltInPackSourceAccessor {
    @Accessor("packType")
    PackType lathe$packType();
}
