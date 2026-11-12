package net.shiroha233.roadweaver.util;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

/**
 * 文件型存储的原子写和目录清理辅助。
 */
public final class FileStorageIO {
    private FileStorageIO() {}

    public static void writeBytesAtomic(Path path, byte[] data) throws IOException {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        if (data == null) throw new IllegalArgumentException("data must not be null");
        Path parent = path.getParent();
        if (parent == null) throw new IllegalArgumentException("path must have parent");
        Files.createDirectories(parent);
        Path tempPath = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
        try {
            Files.write(tempPath, data);
            moveIntoPlace(tempPath, path);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {}
            throw e;
        }
    }

    public static void writeStringAtomic(Path path, String data) throws IOException {
        writeBytesAtomic(path, data == null ? new byte[0] : data.getBytes(StandardCharsets.UTF_8));
    }

    public static void moveIntoPlace(Path tempPath, Path path) throws IOException {
        try {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailed) {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void deleteTree(Path root, Logger logger, String reason) {
        if (root == null || !Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    if (logger != null) {
                        logger.warn("{}: {}", reason, path, e);
                    }
                }
            });
        } catch (IOException e) {
            if (logger != null) {
                logger.warn("{}: {}", reason, root, e);
            }
        }
    }

    public static void quarantineCorrupt(Path path, Logger logger, String reason) {
        if (path == null || !Files.exists(path)) return;
        Path target = path.resolveSibling(path.getFileName() + ".corrupt." + System.currentTimeMillis());
        try {
            Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
            if (logger != null) {
                logger.warn("{}: {} -> {}", reason, path, target);
            }
        } catch (IOException e) {
            if (logger != null) {
                logger.warn("{}: {}", reason, path, e);
            }
        }
    }
}
