package dev.blockandbeam.mediakit.command;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent.ClientCommandSourceStack;

import dev.blockandbeam.mediakit.api.media.Media;
import dev.blockandbeam.mediakit.api.media.MediaAPI;
import dev.blockandbeam.mediakit.api.media.MediaException;
import dev.blockandbeam.mediakit.api.media.MediaPlayer;
import dev.blockandbeam.mediakit.api.media.MediaSourceManager;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client chat commands for loading and playing media. */
public final class MediaKitClientCommands {
    private static final float DEFAULT_VOLUME = 1.0f;
    private static final boolean DEFAULT_LOOP = false;
    private static final float DEFAULT_START = 0.0f;

    private MediaKitClientCommands() {
    }

    /** Registers the client-side {@code /mediakit} commands. */
    public static void register(CommandDispatcher<ClientCommandSourceStack> dispatcher) {
        dispatcher.register(ClientCommandRegistrationEvent.literal("mediakit")
                .then(ClientCommandRegistrationEvent.literal("client:load")
                        .then(ClientCommandRegistrationEvent.argument("source", StringArgumentType.greedyString())
                                .executes(MediaKitClientCommands::load)))
                .then(ClientCommandRegistrationEvent.literal("client:play")
                        .then(ClientCommandRegistrationEvent.argument("source", StringArgumentType.string())
                                .executes(ctx -> play(ctx, DEFAULT_VOLUME, DEFAULT_LOOP, DEFAULT_START))
                                .then(ClientCommandRegistrationEvent.argument("volume",
                                                FloatArgumentType.floatArg(0.0f, 1.0f))
                                        .executes(ctx -> play(ctx,
                                                FloatArgumentType.getFloat(ctx, "volume"),
                                                DEFAULT_LOOP, DEFAULT_START))
                                        .then(ClientCommandRegistrationEvent.argument("loop",
                                                        BoolArgumentType.bool())
                                                .executes(ctx -> play(ctx,
                                                        FloatArgumentType.getFloat(ctx, "volume"),
                                                        BoolArgumentType.getBool(ctx, "loop"),
                                                        DEFAULT_START))
                                                .then(ClientCommandRegistrationEvent.argument("start",
                                                                FloatArgumentType.floatArg(0.0f))
                                                        .executes(ctx -> play(ctx,
                                                                FloatArgumentType.getFloat(ctx, "volume"),
                                                                BoolArgumentType.getBool(ctx, "loop"),
                                                                FloatArgumentType.getFloat(ctx, "start"))))))))
                .then(ClientCommandRegistrationEvent.literal("client:stop")
                        .executes(MediaKitClientCommands::stop)));
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

    private static int play(CommandContext<ClientCommandSourceStack> context, float volume, boolean loop, float start) {
        String source = StringArgumentType.getString(context, "source");
        chat("Loading: " + source);
        CompletableFuture.runAsync(() -> {
            try {
                Media media = MediaAPI.load(source);
                chat("Loaded: " + media);
                MediaPlayer.INSTANCE.play(media, volume, loop, start);
                chat(withBrand("Playing: " + media.name(), media));
            } catch (MediaException e) {
                chat("Failed: " + e.getMessage());
            }
        });
        return 1;
    }

    private static int stop(CommandContext<ClientCommandSourceStack> context) {
        if (MediaPlayer.INSTANCE.isPlaying()) {
            MediaPlayer.INSTANCE.stop();
            chat("Stopped");
        } else {
            chat("Nothing playing");
        }
        return 1;
    }

    /** Appends the source's platform tag, e.g. {@code [SoundCloud]}, to a message. */
    private static Component withBrand(String text, Media media) {
        Optional<Component> brand = MediaSourceManager.brand(media.source());
        if (brand.isEmpty()) {
            return Component.literal(text);
        }
        return Component.literal(text + " [").append(brand.get()).append("]");
    }

    private static void chat(String message) {
        chat(Component.literal(message));
    }

    private static void chat(Component message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.displayClientMessage(message, false);
            }
        });
    }
}
