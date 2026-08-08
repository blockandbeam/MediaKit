package dev.blockandbeam.mediakit.fabric;

import net.fabricmc.api.ModInitializer;

import dev.blockandbeam.mediakit.MediaKit;

public final class MediaKitFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with caution.

        // Run our common setup.
        MediaKit.init();
    }
}
