package dev.blockandbeam.mediakit.api;

import java.io.IOException;
import java.nio.file.Path;

/**
 * An external binary MediaKit may download. {@code location} is where a
 * working copy was found, or {@code null} if it would need to be downloaded;
 * {@code resolver} fetches one on demand.
 */
public record ExternalDependency(String name, String description, Path location, String url,
                                 Resolver resolver) {
    @FunctionalInterface
    public interface Resolver {
        Path resolve() throws IOException;
    }
}
