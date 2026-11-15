package com.rinkynooble.lathe.fabric;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.Registration;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;

/**
 * The shapes are registry entries, so a client and a server with different rules can't play together. While a
 * player logs in, the server asks for the client's rules hash and, when it differs, says so by name instead of
 * leaving the player with a list of missing blocks. Forge does the same with a channel versioned by the hash.
 */
final class RulesCheck {
    static final ResourceLocation CHANNEL = new ResourceLocation(Lathe.MOD_ID, "rules");

    private RulesCheck() {
    }

    static void registerServer() {
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            // No shapes on the server: nothing to disagree about, and vanilla clients can still join.
            if (Registration.MATERIALS.isEmpty()) return;
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeUtf(Lathe.rules.hash);
            sender.sendPacket(CHANNEL, buf);
        });
        ServerLoginNetworking.registerGlobalReceiver(CHANNEL, (server, handler, understood, buf, synchronizer, responseSender) -> {
            String ours = Lathe.rules.hash;
            Component refusal = null;
            if (!understood) {
                refusal = Component.literal("This server uses Lathe. Install Lathe with the same config/lathe/ rules as the server (rules hash " + ours + ").");
            } else {
                String theirs = buf.readUtf();
                if (!theirs.equals(ours)) {
                    refusal = Component.literal("Lathe's rules differ: the server's hash is " + ours + ", yours is " + theirs
                            + ". The client and the server need the same config/lathe/ rules.");
                }
            }
            if (refusal == null) return;
            // Replies arrive on the network thread; disconnect from the server thread, holding the login until then.
            Component message = refusal;
            synchronizer.waitFor(server.submit(() -> handler.disconnect(message)));
        });
    }

    static void registerClient() {
        ClientLoginNetworking.registerGlobalReceiver(CHANNEL, (client, handler, buf, listenerAdder) -> {
            FriendlyByteBuf response = PacketByteBufs.create();
            response.writeUtf(Lathe.rules.hash);
            return CompletableFuture.completedFuture(response);
        });
    }
}
