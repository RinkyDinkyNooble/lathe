package com.rinkynooble.lathe.neoforge.mixin;

import com.rinkynooble.lathe.neoforge.RulesCheck;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.neoforge.network.ConfigurationInitialization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Queues Lathe's rules check first when a player joins, ahead of NeoForge's registry sync: a player whose rules
 * differ then hears why, instead of NeoForge's list of unknown registry entries. NeoForge's event for configuration
 * tasks only adds them after the sync.
 */
@Mixin(ConfigurationInitialization.class)
abstract class ConfigurationInitializationMixin {
    @Inject(method = "configureEarlyTasks", at = @At("HEAD"))
    private static void lathe$rulesCheckFirst(ServerConfigurationPacketListener listener, Consumer<ConfigurationTask> tasks, CallbackInfo ci) {
        RulesCheck.addTask(listener, tasks);
    }
}
