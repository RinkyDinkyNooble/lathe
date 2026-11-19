package com.rinkynooble.lathe.forge;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.Registration;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.network.ChannelRegistrationChangeEvent;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.event.network.GatherLoginConfigurationTasksEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.ChannelListManager;
import net.minecraftforge.network.ConnectionType;
import net.minecraftforge.network.NetworkContext;
import net.minecraftforge.network.NetworkProtocol;
import net.minecraftforge.network.SimpleChannel;
import net.minecraftforge.network.config.ConfigurationTaskContext;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * The shapes are registry entries, so a client and a server with different rules can't play together. While a
 * player joins, the server sends its rules hash and the client answers with its own; when they differ, or the client
 * has no Lathe, the server says so by name instead of leaving the player with a list of missing blocks. Skipped when
 * the server has no shapes, so clients without Lathe can join. The Fabric and NeoForge jars do the same, with the
 * same messages.
 * <p>
 * The check is the first configuration task, ahead of Forge's own (channel lists, then the registry sync, which would
 * otherwise refuse the player first with its own message). To tell a client without Lathe, which never answers the
 * hash, it also asks the client for its channel list (Forge's {@code minecraft:register} exchange, which Forge's own
 * first task would start a moment later): a list without {@code lathe:rules} means no Lathe.
 */
final class RulesCheck {
    private static final ResourceLocation NAME = ResourceLocation.fromNamespaceAndPath(Lathe.MOD_ID, "rules");
    private static final ConfigurationTask.Type TASK = new ConfigurationTask.Type(Lathe.MOD_ID + ":rules");
    /** Connections whose check is running: the server waits for their hash. */
    private static final Set<Connection> WAITING = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static SimpleChannel channel;

    private RulesCheck() {
    }

    /** One side's rules hash. */
    private record Hash(String hash) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(hash);
        }

        static Hash decode(FriendlyByteBuf buf) {
            return new Hash(buf.readUtf());
        }
    }

    static void register() {
        // Optional both ways: a client without Lathe gets past Forge's channel check, so the task can tell it what's
        // missing, and a client with Lathe can still join servers without it.
        channel = ChannelBuilder.named(NAME)
                .networkProtocolVersion(1)
                .optional()
                .simpleChannel()
                .messageBuilder(Hash.class, NetworkProtocol.CONFIGURATION)
                .encoder(Hash::encode)
                .decoder(Hash::decode)
                .consumerMainThread(RulesCheck::onHash)
                .add()
                .build();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, RulesCheck::onGatherTasks);
        MinecraftForge.EVENT_BUS.addListener(RulesCheck::onChannelsChanged);
    }

    private static void onGatherTasks(GatherLoginConfigurationTasksEvent event) {
        // No shapes on the server: nothing to disagree about, and clients without Lathe can still join.
        if (Registration.MATERIALS.isEmpty()) return;
        // Singleplayer: the client and its own server read the same rules.
        if (event.getConnection().isMemoryConnection()) return;
        event.addTask(new Task());
    }

    private static void onHash(Hash payload, CustomPayloadEvent.Context context) {
        if (context.isClientSide()) {
            // The client: answer with our own hash.
            channel.reply(new Hash(Lathe.rules.hash), context);
            return;
        }
        Connection connection = context.getConnection();
        if (!WAITING.remove(connection)) return;
        String ours = Lathe.rules.hash;
        String theirs = payload.hash();
        if (theirs.equals(ours)) {
            if (connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl listener) listener.finishCurrentTask(TASK);
        } else {
            disconnect(connection, Component.literal("Lathe's rules differ: the server's hash is " + ours + ", yours is " + theirs
                    + ". The client and the server need the same config/lathe/ rules."));
        }
    }

    /** The joining client's channel list, its answer to ours: no {@code lathe:rules} means no Lathe. */
    private static void onChannelsChanged(ChannelRegistrationChangeEvent event) {
        if (event.getType() != ChannelRegistrationChangeEvent.Type.REGISTER || event.getChannels().contains(NAME)) return;
        Connection connection = event.getSource();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !WAITING.contains(connection)) return;
        // Disconnect from the server thread, so the client is told why.
        server.execute(() -> {
            if (WAITING.remove(connection)) disconnect(connection, missing());
        });
    }

    private static Component missing() {
        return Component.literal("This server uses Lathe. Install Lathe with the same config/lathe/ rules as the server (rules hash "
                + Lathe.rules.hash + ").");
    }

    private static void disconnect(Connection connection, Component reason) {
        if (connection.getPacketListener() instanceof ServerCommonPacketListenerImpl listener) listener.disconnect(reason);
        else connection.disconnect(reason);
    }

    private static final class Task implements ConfigurationTask {
        @Override
        public void start(ConfigurationTaskContext context) {
            Connection connection = context.getConnection();
            // A vanilla client can't have Lathe.
            if (NetworkContext.get(connection).getType() != ConnectionType.MODDED) {
                disconnect(connection, missing());
                return;
            }
            WAITING.add(connection);
            channel.send(new Hash(Lathe.rules.hash), connection);
            ChannelListManager.addChannels(connection);
        }

        @Override
        public void start(Consumer<Packet<?>> sender) {
            throw new IllegalStateException("Forge starts configuration tasks with a context");
        }

        @Override
        public ConfigurationTask.Type type() {
            return TASK;
        }
    }
}
