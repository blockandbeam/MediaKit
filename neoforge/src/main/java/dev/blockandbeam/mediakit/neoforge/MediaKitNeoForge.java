package dev.blockandbeam.mediakit.neoforge;

import net.neoforged.fml.common.Mod;

import dev.blockandbeam.mediakit.MediaKit;

@Mod(MediaKit.MOD_ID)
public final class MediaKitNeoForge {
    public MediaKitNeoForge() {
        // Run our common setup.
        MediaKit.init();
    }
}
