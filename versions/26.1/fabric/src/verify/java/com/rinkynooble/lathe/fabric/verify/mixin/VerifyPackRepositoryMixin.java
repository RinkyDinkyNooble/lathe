package com.rinkynooble.lathe.fabric.verify.mixin;

import com.rinkynooble.lathe.verify.VerifyServer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/** Adds the verify palette pack (Lathe shapes in Lost Cities' default palette) to a server's data packs. */
@Mixin(PackRepository.class)
abstract class VerifyPackRepositoryMixin {
    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void latheverify$addPalette(RepositorySource[] given, CallbackInfo ci) {
        if (!VerifyServer.ENABLED) return;
        boolean serverData = false;
        for (RepositorySource source : given) serverData |= source instanceof ServerPacksSource;
        if (!serverData) return;
        Pack[] made = new Pack[1];
        Set<RepositorySource> withPalette = new LinkedHashSet<>(sources);
        withPalette.add(consumer -> {
            if (made[0] == null) made[0] = VerifyServer.palettePack();
            if (made[0] != null) consumer.accept(made[0]);
        });
        sources = withPalette;
    }
}
