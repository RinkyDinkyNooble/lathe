package com.rinkynooble.lathe.fabric.mixin;

import com.rinkynooble.lathe.pack.Packs;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Adds Lathe's generated packs (blockstates, models and names for the client; loot, recipes and tags for a server)
 * to every pack list that has the game's built-in packs, always on. Forge does this with AddPackFindersEvent.
 */
@Mixin(PackRepository.class)
abstract class PackRepositoryMixin {
    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void lathe$addPacks(RepositorySource[] given, CallbackInfo ci) {
        PackType type = null;
        for (RepositorySource source : given) {
            if (source instanceof BuiltInPackSource builtIn) {
                type = ((BuiltInPackSourceAccessor) builtIn).lathe$packType();
                break;
            }
        }
        if (type == null) return;
        PackType packType = type;
        Pack[] made = new Pack[1];
        Set<RepositorySource> withLathe = new LinkedHashSet<>(sources);
        withLathe.add(consumer -> {
            // Made once per pack list, so reloading keeps the same generated files.
            if (made[0] == null) made[0] = Packs.create(packType);
            if (made[0] != null) consumer.accept(made[0]);
        });
        sources = withLathe;
    }
}
