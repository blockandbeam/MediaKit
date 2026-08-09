package dev.blockandbeam.mediakit.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Shows one-shot notices to the player as a chat line, or as a toast when no
 * player is loaded yet.
 */
public final class ClientNotifications {
    private static volatile String lastAnnounced;

    private ClientNotifications() {
    }

    /** Shows {@code message} once per session, as a chat line or toast. */
    public static void announce(String message) {
        if (message.equals(lastAnnounced)) {
            return;
        }
        lastAnnounced = message;
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            if (client.player != null) {
                client.player.displayClientMessage(Component.literal(message), false);
            } else {
                client.getToasts().addToast(new SystemToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("MediaKit"), Component.literal(message)));
            }
        });
    }
}
