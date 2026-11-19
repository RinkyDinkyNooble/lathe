package com.rinkynooble.lathe.neoforge;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.Registration;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.Consumer;

/**
 * The shapes are registry entries, so a client and a server with different rules can't play together. While a
 * player joins, the server sends its rules hash and the client answers with its own; when they differ, or the client
 * has no Lathe, the server says so by name instead of leaving the player with a list of missing blocks. This runs
 * before NeoForge syncs the registries (see {@code ConfigurationInitializationMixin}), since that sync would refuse
 * the player first with its own message. Skipped when the server has no shapes, so clients without Lathe can join.
 * The Fabric jar does the same with a login query, with the same messages.
 */
public final class RulesCheck {
    public static final CustomPacketPayload.Type<Hash> PAYLOAD = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Lathe.MOD_ID, "rules"));
    private static final ConfigurationTask.Type TASK = new ConfigurationTask.Type(Lathe.MOD_ID + ":rules");

    private RulesCheck() {
    }

    /** One side's rules hash. */
    public record Hash(String hash) implements CustomPacketPayload {
        static final StreamCodec<ByteBuf, Hash> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Hash::hash, Hash::new);

        @Override
        public CustomPacketPayload.Type<Hash> type() {
            return PAYLOAD;
        }
    }

    static void register(IEventBus modBus) {
        modBus.addListener(RulesCheck::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // Optional: a client without Lathe gets past NeoForge's channel check, so the task can tell it what's missing.
        event.registrar("1").optional().configurationBidirectional(PAYLOAD, Hash.CODEC, RulesCheck::onHash);
    }

    /** Queues the check for a joining player, ahead of NeoForge's registry sync. Called by the mixin. */
    public static void addTask(ServerConfigurationPacketListener listener, Consumer<ConfigurationTask> tasks) {
        // No shapes on the server: nothing to disagree about, and clients without Lathe can still join.
        if (Registration.MATERIALS.isEmpty()) return;
        tasks.accept(new Task(listener));
    }

    private static void onHash(Hash payload, IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            // The client: answer with our own hash.
            context.reply(new Hash(Lathe.rules.hash));
            return;
        }
        String ours = Lathe.rules.hash;
        String theirs = payload.hash();
        if (theirs.equals(ours)) {
            context.finishCurrentTask(TASK);
        } else {
            context.disconnect(Component.literal("Lathe's rules differ: the server's hash is " + ours + ", yours is " + theirs
                    + ". The client and the server need the same config/lathe/ rules."));
        }
    }

    private record Task(ServerConfigurationPacketListener listener) implements ICustomConfigurationTask {
        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            if (listener.hasChannel(PAYLOAD)) {
                sender.accept(new Hash(Lathe.rules.hash));
            } else {
                listener.disconnect(Component.literal("This server uses Lathe. Install Lathe with the same config/lathe/ rules as the server (rules hash "
                        + Lathe.rules.hash + ")."));
            }
        }

        @Override
        public ConfigurationTask.Type type() {
            return TASK;
        }
    }
}
