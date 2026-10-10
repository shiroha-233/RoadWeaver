/* 文件职责：以校验过的 Deflate 二进制文件持久化待生成道路路径。 */
package net.shiroha233.roadweaver.planning.path;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.persistence.files.FileStorageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * 每个规范化端点对对应一个原子替换文件。
 */
public final class FilePlannedPathStore implements PlannedPathStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final int MAGIC = 0x52575050;
    private static final int SCHEMA = 1;
    private static final int MAX_PATH_POINTS = 4_000_000;

    private static final ThreadLocal<MessageDigest> DIGEST =
            ThreadLocal.withInitial(FilePlannedPathStore::createDigest);
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private static MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private final Path root;

    public FilePlannedPathStore(Path root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    @Override
    public Optional<List<BlockPos>> load(PlannedPathKey key, String fingerprint) throws IOException {
        Objects.requireNonNull(key, "key");
        Path file = fileFor(key);
        if (!Files.exists(file)) return Optional.empty();
        try {
            StoredPath stored = decode(Files.readAllBytes(file));
            if (!key.equals(stored.key()) || !Objects.equals(normalize(fingerprint), stored.fingerprint())) {
                return Optional.empty();
            }
            return Optional.of(stored.path());
        } catch (RuntimeException | IOException corrupt) {
            FileStorageIO.quarantineCorrupt(file, LOGGER, "待生成路径文件损坏，已隔离");
            if (corrupt instanceof IOException io) throw io;
            throw new IOException("failed to decode planned path", corrupt);
        }
    }

    @Override
    public void save(PlannedPathKey key, String fingerprint, List<BlockPos> path) throws IOException {
        Objects.requireNonNull(key, "key");
        if (path == null || path.isEmpty()) return;
        FileStorageIO.writeBytesAtomic(fileFor(key), encode(new StoredPath(
                key, normalize(fingerprint), List.copyOf(path))));
    }

    @Override
    public void delete(PlannedPathKey key) throws IOException {
        Objects.requireNonNull(key, "key");
        Files.deleteIfExists(fileFor(key));
    }

    private Path fileFor(PlannedPathKey key) {
        return root.resolve(fileName(key) + ".rwpath");
    }

    private static byte[] encode(StoredPath stored) throws IOException {
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream payload = new DataOutputStream(payloadBytes)) {
            writeEndpoint(payload, stored.key().first());
            writeEndpoint(payload, stored.key().second());
            payload.writeUTF(stored.fingerprint());
            payload.writeInt(stored.path().size());
            for (BlockPos point : stored.path()) {
                payload.writeInt(point.getX());
                payload.writeInt(point.getY());
                payload.writeInt(point.getZ());
            }
        }
        byte[] uncompressed = payloadBytes.toByteArray();
        CRC32 checksum = new CRC32();
        checksum.update(uncompressed);

        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        try (DataOutputStream header = new DataOutputStream(outputBytes)) {
            header.writeInt(MAGIC);
            header.writeInt(SCHEMA);
            header.writeLong(checksum.getValue());
            try (DeflaterOutputStream deflated = new DeflaterOutputStream(header)) {
                deflated.write(uncompressed);
            }
        }
        return outputBytes.toByteArray();
    }

    private static StoredPath decode(byte[] encoded) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) throw new IOException("invalid planned path magic");
            if (input.readInt() != SCHEMA) throw new IOException("unsupported planned path schema");
            long expectedChecksum = input.readLong();
            byte[] payload;
            try (InflaterInputStream inflated = new InflaterInputStream(input)) {
                payload = inflated.readAllBytes();
            }
            CRC32 checksum = new CRC32();
            checksum.update(payload);
            if (checksum.getValue() != expectedChecksum) throw new IOException("planned path checksum mismatch");

            try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(payload))) {
                PlannedPathKey key = new PlannedPathKey(readEndpoint(data), readEndpoint(data));
                String fingerprint = data.readUTF();
                int count = data.readInt();
                if (count <= 0 || count > MAX_PATH_POINTS) throw new IOException("invalid planned path size");
                ArrayList<BlockPos> path = new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    path.add(new BlockPos(data.readInt(), data.readInt(), data.readInt()));
                }
                if (data.available() != 0) throw new IOException("trailing planned path data");
                return new StoredPath(key, fingerprint, List.copyOf(path));
            }
        }
    }

    private static void writeEndpoint(DataOutputStream output, PlannedPathKey.Endpoint endpoint) throws IOException {
        output.writeInt(endpoint.x());
        output.writeInt(endpoint.y());
        output.writeInt(endpoint.z());
    }

    private static PlannedPathKey.Endpoint readEndpoint(DataInputStream input) throws IOException {
        return new PlannedPathKey.Endpoint(input.readInt(), input.readInt(), input.readInt());
    }

    private static String fileName(PlannedPathKey key) {
        String value = key.first().x() + ":" + key.first().y() + ":" + key.first().z() + "|"
                + key.second().x() + ":" + key.second().y() + ":" + key.second().z();
        // 按线程复用：getInstance 每次都做 JCA provider 查找并分配新实例；
        // MessageDigest 非线程安全，故不能用单一静态实例
        MessageDigest digest = DIGEST.get();
        digest.reset();
        return HEX_FORMAT.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String normalize(String fingerprint) {
        return fingerprint == null ? "" : fingerprint;
    }

    private record StoredPath(PlannedPathKey key, String fingerprint, List<BlockPos> path) {}
}
