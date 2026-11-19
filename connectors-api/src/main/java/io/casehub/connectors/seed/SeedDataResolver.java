package io.casehub.connectors.seed;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class SeedDataResolver {

    private static volatile Path seedDir;

    public static void setSeedDir(Path dir) {
        seedDir = dir;
    }

    public static Optional<InputStream> resolve(String domain, String filename) {
        Path dir = seedDir;
        if (dir != null) {
            Path file = dir.resolve(domain).resolve(filename);
            if (Files.exists(file)) {
                try {
                    return Optional.of(Files.newInputStream(file));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        InputStream is = SeedDataResolver.class.getResourceAsStream("/seed/" + domain + "/" + filename);
        return Optional.ofNullable(is);
    }

    private SeedDataResolver() {}
}
