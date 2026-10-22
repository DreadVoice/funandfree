package com.funandfree.events.support;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TestDatabase {

    private TestDatabase() {
    }

    public static String urlFor(String name) {
        Path directory = Path.of("target", "test-db");
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Path database = directory.resolve(name);
        deleteQuietly(database);
        deleteQuietly(directory.resolve(name + "-wal"));
        deleteQuietly(directory.resolve(name + "-shm"));
        String file = database.toAbsolutePath().toString().replace(File.separatorChar, '/');
        return "jdbc:sqlite:" + file
                + "?journal_mode=WAL&busy_timeout=10000&foreign_keys=on&transaction_mode=IMMEDIATE";
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
