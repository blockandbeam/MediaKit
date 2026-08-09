package dev.blockandbeam.mediakit.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.architectury.platform.Platform;

/**
 * Persistent record of the player's consent to MediaKit's network use, stored
 * in {@code config/mediakit/consent.json}. Bumping {@link #VERSION} re-asks
 * whenever what the mod does changes.
 */
public final class Consent {
    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum State {
        ACCEPTED, DECLINED, UNDECIDED
    }

    private Consent() {
    }

    /** The player's current consent state; {@link State#UNDECIDED} until they answer. */
    public static State state() {
        try {
            Path file = file();
            if (!Files.exists(file)) {
                return State.UNDECIDED;
            }
            JsonObject json = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            JsonElement version = json == null ? null : json.get("version");
            JsonElement accepted = json == null ? null : json.get("accepted");
            if (version == null || version.getAsInt() != VERSION
                    || accepted == null || !accepted.isJsonPrimitive()) {
                return State.UNDECIDED;
            }
            return accepted.getAsBoolean() ? State.ACCEPTED : State.DECLINED;
        } catch (IOException | RuntimeException e) {
            return State.UNDECIDED;
        }
    }

    public static boolean accepted() {
        return state() == State.ACCEPTED;
    }

    public static boolean declined() {
        return state() == State.DECLINED;
    }

    public static void accept() {
        save(true);
    }

    public static void decline() {
        save(false);
    }

    private static void save(boolean accepted) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("version", VERSION);
            json.addProperty("accepted", accepted);
            Path file = file();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Consent is best-effort; commands fall back to asking again.
        }
    }

    private static Path file() {
        return Platform.getConfigFolder().resolve("mediakit").resolve("consent.json");
    }
}
