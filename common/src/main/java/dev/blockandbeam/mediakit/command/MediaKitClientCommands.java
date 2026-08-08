package dev.blockandbeam.mediakit.command;

import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent.ClientCommandSourceStack;

import dev.blockandbeam.mediakit.api.media.Media;
import dev.blockandbeam.mediakit.api.media.MediaAPI;
import dev.blockandbeam.mediakit.api.media.MediaException;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client chat commands for loading media. */
public final class MediaKitClientCommands {
    private MediaKitClientCommands() {
    }

    public static void register(CommandDispatcher<ClientCommandSourceStack> dispatcher) {
        dispatcher.register(ClientCommandRegistrationEvent.literal("mediakit")
                .then(ClientCommandRegistrationEvent.literal("load")
                        .then(ClientCommandRegistrationEvent.argument("source", StringArgumentType.greedyString())
                                .executes(MediaKitClientCommands::load))));
    }

    private static int load(CommandContext<ClientCommandSourceStack> context) {
        String source = StringArgumentType.getString(context, "source");
        chat("Loading: " + source);
        CompletableFuture.runAsync(() -> {
            try {
                Media media = MediaAPI.load(source);
                chat("Loaded: " + media);
            } catch (MediaException e) {
                chat("Failed: " + e.getMessage());
            }
        });
        return 1;
    }

    private static void chat(String message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.displayClientMessage(Component.literal(message), false);
            }
        });
    }
}
