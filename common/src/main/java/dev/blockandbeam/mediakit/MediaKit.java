package dev.blockandbeam.mediakit;

import dev.architectury.event.events.client.ClientCommandRegistrationEvent;

import dev.blockandbeam.mediakit.command.MediaKitClientCommands;

public final class MediaKit {
    public static final String MOD_ID = "mediakit";

    public static void init() {
        ClientCommandRegistrationEvent.EVENT.register((dispatcher, buildContext) ->
                MediaKitClientCommands.register(dispatcher));
    }
}
