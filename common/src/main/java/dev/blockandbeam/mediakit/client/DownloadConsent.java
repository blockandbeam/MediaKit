package dev.blockandbeam.mediakit.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.logging.LogUtils;
import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.events.client.ClientGuiEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

import org.slf4j.Logger;

import dev.blockandbeam.mediakit.api.Consent;
import dev.blockandbeam.mediakit.api.ExternalDependency;
import dev.blockandbeam.mediakit.api.FFmpeg;
import dev.blockandbeam.mediakit.api.YtDlp;

/**
 * Asks the player for permission before MediaKit downloads a binary. The
 * question is shown over the title screen on first launch and, as a fallback,
 * on the render thread while the calling worker thread waits.
 */
public final class DownloadConsent {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DownloadConsent() {
    }

    private static boolean promptedAtMainMenu;

    private static List<ExternalDependency> dependencies() {
        return List.of(FFmpeg.dependency(), YtDlp.dependency());
    }

    /**
     * Shows the consent screen over the first title screen while the player
     * has not decided yet. Accepting kicks off downloads of whatever is still
     * missing; declining (or pressing ESC) records the choice.
     */
    public static void askOnMainMenu() {
        ClientGuiEvent.SET_SCREEN.register(screen -> {
            if (!promptedAtMainMenu && screen instanceof TitleScreen && Consent.state() == Consent.State.UNDECIDED) {
                promptedAtMainMenu = true;
                return CompoundEventResult.interruptTrue(new ConsentScreen(dependencies(), accepted -> {
                    if (accepted) {
                        downloadMissing();
                    }
                }));
            }
            return CompoundEventResult.pass();
        });
    }

    /** Downloads whatever the player approved that is not already present, off the render thread. */
    private static void downloadMissing() {
        for (ExternalDependency dep : dependencies()) {
            if (dep.location() != null) {
                continue;
            }
            LOGGER.info("MediaKit: {} not found, downloading", dep.name());
            Thread thread = new Thread(() -> {
                try {
                    Path path = dep.resolver().resolve();
                    LOGGER.info("MediaKit: {} ready at {}", dep.name(), path);
                } catch (IOException e) {
                    LOGGER.error("MediaKit: failed to download {}", dep.name(), e);
                    ClientNotifications.announce("MediaKit couldn't download " + dep.name() + ": " + e.getMessage());
                }
            }, "MediaKit download: " + dep.name());
            thread.setDaemon(true);
            thread.start();
        }
    }

    /**
     * Whether MediaKit may download a binary, asking via the consent screen if
     * the player has not decided yet.
     *
     * @return {@code true} to allow the download
     */
    public static boolean await() {
        if (Consent.accepted()) {
            return true;
        }
        if (Consent.declined()) {
            return false;
        }
        if (Minecraft.getInstance().isSameThread()) {
            return false; // Never block the render thread.
        }
        CompletableFuture<Boolean> answer = new CompletableFuture<>();
        Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new ConsentScreen(dependencies(), answer::complete)));
        return answer.join();
    }
}
