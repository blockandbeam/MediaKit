package dev.blockandbeam.mediakit.fabric;

import net.fabricmc.api.ModInitializer;

import dev.blockandbeam.mediakit.MediaKit;

public final class MediaKitFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        MediaKit.init();
    }
}
