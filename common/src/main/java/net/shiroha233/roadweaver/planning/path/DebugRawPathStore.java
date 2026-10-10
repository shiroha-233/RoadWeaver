package net.shiroha233.roadweaver.planning.path;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.persistence.files.FileStorageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Optional, per-connection debug data. Headers contain bounds so viewport queries need not decompress distant paths. */
final class DebugRawPathStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final int MAGIC = 0x52574442;
    private static final int VERSION = 1;
    private static final int MAX_POINTS_PER_PATH = 100_000;
    private static final int MAX_FILE_BYTES = 4_000_000;
    private static final int MAX_PAYLOAD_BYTES = MAX_POINTS_PER_PATH * 24 + 32;

    private final Path root;
    private final Map<Path, Bounds> index = new HashMap<>();
    private boolean indexed;

    DebugRawPathStore(Path root) {
        this.root = root;
    }

    synchronized void recordSearch(PlannedPathKey key, TerrainSamplingMode mode, List<BlockPos> raw) throws IOException {
        if (raw == null || raw.size() < 2) return;
        Path file = fileFor(key);
        // A later duplicate planning pass must not replace the already refined nodes.
        if (Files.exists(file)) {
            try {
                if (!read(file).accurate().isEmpty()) return;
            } catch (IOException ignored) {
                // A damaged file is replaced by the new search result.
            }
        }
        write(file, new DebugRawPath(mode,
                mode == TerrainSamplingMode.COARSE_CORRIDOR ? raw : List.of(),
                mode == TerrainSamplingMode.FULL_REGION ? raw : List.of()));
    }

    synchronized void recordResolved(PlannedPathKey key, TerrainSamplingMode mode,
                                     List<BlockPos> raw, List<BlockPos> resolved) throws IOException {
        if (raw == null || resolved == null || resolved.size() < 2 || raw.size() < 2) return;
        write(fileFor(key), new DebugRawPath(mode,
                mode == TerrainSamplingMode.COARSE_CORRIDOR ? raw : List.of(), resolved));
    }

    synchronized List<DebugRawPath> query(int minX, int minZ, int maxX, int maxZ, int maxPoints) throws IOException {
        ensureIndex();
        ArrayList<DebugRawPath> result = new ArrayList<>();
        int remaining = maxPoints;
        int centerX = minX + (maxX - minX) / 2;
        int centerZ = minZ + (maxZ - minZ) / 2;
        ArrayList<Map.Entry<Path, Bounds>> candidates = new ArrayList<>();
        for (Map.Entry<Path, Bounds> entry : index.entrySet()) {
            if (entry.getValue().intersects(minX, minZ, maxX, maxZ)) candidates.add(entry);
        }
        candidates.sort(Comparator.comparingLong(entry -> entry.getValue().distanceSquared(centerX, centerZ)));
        for (Map.Entry<Path, Bounds> entry : candidates) {
            if (remaining < 2) break;
            try {
                DebugRawPath path = read(entry.getKey());
                int coarseBudget = path.accurate().isEmpty() ? remaining : remaining / 2;
                for (List<BlockPos> run : clipRuns(path.coarse(), minX, minZ, maxX, maxZ)) {
                    coarseBudget = appendRun(result, path.mode(), run, true, coarseBudget);
                    if (coarseBudget < 2) break;
                }
                remaining = path.accurate().isEmpty() ? coarseBudget : remaining / 2 + coarseBudget;
                for (List<BlockPos> run : clipRuns(path.accurate(), minX, minZ, maxX, maxZ)) {
                    remaining = appendRun(result, path.mode(), run, false, remaining);
                    if (remaining < 2) break;
                }
            } catch (IOException damaged) {
                LOGGER.warn("Cannot read raw path debug file {}", entry.getKey(), damaged);
            }
        }
        return List.copyOf(result);
    }

    private static int appendRun(List<DebugRawPath> result, TerrainSamplingMode mode,
                                 List<BlockPos> run, boolean coarse, int budget) {
        int offset = 0;
        while (budget >= 2 && offset < run.size() - 1) {
            int end = Math.min(run.size(), offset + budget);
            List<BlockPos> part = List.copyOf(run.subList(offset, end));
            result.add(new DebugRawPath(mode, coarse ? part : List.of(), coarse ? List.of() : part));
            budget -= part.size();
            offset = end - 1; // Maintain continuity only within the same original run.
        }
        return budget;
    }

    /** Split disjoint visible sections rather than joining them with invented segments. */
    static List<List<BlockPos>> clipRuns(List<BlockPos> nodes, int minX, int minZ, int maxX, int maxZ) {
        ArrayList<List<BlockPos>> runs = new ArrayList<>();
        ArrayList<BlockPos> current = new ArrayList<>();
        for (int i = 1; i < nodes.size(); i++) {
            BlockPos a = nodes.get(i - 1), b = nodes.get(i);
            boolean visible = Math.max(a.getX(), b.getX()) >= minX && Math.min(a.getX(), b.getX()) <= maxX
                    && Math.max(a.getZ(), b.getZ()) >= minZ && Math.min(a.getZ(), b.getZ()) <= maxZ;
            if (visible) {
                if (current.isEmpty()) current.add(a);
                current.add(b);
            } else if (!current.isEmpty()) {
                runs.add(List.copyOf(current));
                current.clear();
            }
        }
        if (!current.isEmpty()) runs.add(List.copyOf(current));
        return runs;
    }

    private void ensureIndex() throws IOException {
        if (indexed) return;
        if (Files.isDirectory(root)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(root, "*.rwdebug")) {
                for (Path file : files) {
                    try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
                        index.put(file, readHeader(input));
                    } catch (IOException damaged) {
                        LOGGER.warn("Cannot index raw path debug file {}", file, damaged);
                    }
                }
            }
        }
        indexed = true;
    }

    private void write(Path file, DebugRawPath path) throws IOException {
        int total = path.coarse().size() + path.accurate().size();
        if (total > MAX_POINTS_PER_PATH * 2) return;
        Bounds bounds = Bounds.of(path);
        ByteArrayOutputStream rawBytes = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(rawBytes)) {
            writeNodes(data, path.coarse());
            writeNodes(data, path.accurate());
        }
        byte[] raw = rawBytes.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(raw);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (DataOutputStream header = new DataOutputStream(encoded)) {
            header.writeInt(MAGIC);
            header.writeInt(VERSION);
            header.writeUTF(path.mode().name());
            header.writeInt(bounds.minX());
            header.writeInt(bounds.minZ());
            header.writeInt(bounds.maxX());
            header.writeInt(bounds.maxZ());
            header.writeLong(crc.getValue());
            try (DeflaterOutputStream zipped = new DeflaterOutputStream(header)) {
                zipped.write(raw);
            }
        }
        FileStorageIO.writeBytesAtomic(file, encoded.toByteArray());
        index.put(file, bounds);
    }

    private static DebugRawPath read(Path file) throws IOException {
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("debug path file too large");
        try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
            readHeaderPrefix(input);
            TerrainSamplingMode mode = TerrainSamplingMode.valueOf(input.readUTF());
            input.skipNBytes(4L * 4);
            long checksum = input.readLong();
            byte[] raw;
            try (InflaterInputStream zipped = new InflaterInputStream(input)) {
                raw = zipped.readNBytes(MAX_PAYLOAD_BYTES + 1);
            }
            if (raw.length > MAX_PAYLOAD_BYTES) throw new IOException("debug path payload too large");
            CRC32 crc = new CRC32();
            crc.update(raw);
            if (crc.getValue() != checksum) throw new IOException("debug path checksum mismatch");
            try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(raw))) {
                List<BlockPos> coarse = readNodes(data);
                List<BlockPos> accurate = readNodes(data);
                if (data.available() != 0) throw new IOException("trailing debug path data");
                return new DebugRawPath(mode, coarse, accurate);
            }
        } catch (IllegalArgumentException invalidMode) {
            throw new IOException("unknown debug path sampling mode", invalidMode);
        }
    }

    private static Bounds readHeader(DataInputStream input) throws IOException {
        readHeaderPrefix(input);
        input.readUTF();
        return new Bounds(input.readInt(), input.readInt(), input.readInt(), input.readInt());
    }

    private static void readHeaderPrefix(DataInputStream input) throws IOException {
        if (input.readInt() != MAGIC || input.readInt() != VERSION) throw new IOException("unknown debug path schema");
    }

    private static void writeNodes(DataOutputStream output, List<BlockPos> nodes) throws IOException {
        output.writeInt(nodes.size());
        for (BlockPos pos : nodes) {
            output.writeInt(pos.getX());
            output.writeInt(pos.getY());
            output.writeInt(pos.getZ());
        }
    }

    private static List<BlockPos> readNodes(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_POINTS_PER_PATH) throw new IOException("invalid debug path node count");
        ArrayList<BlockPos> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) nodes.add(new BlockPos(input.readInt(), input.readInt(), input.readInt()));
        return List.copyOf(nodes);
    }

    private Path fileFor(PlannedPathKey key) {
        String endpoints = key.first() + ":" + key.second();
        return root.resolve(UUID.nameUUIDFromBytes(endpoints.getBytes(StandardCharsets.UTF_8)) + ".rwdebug");
    }

    private record Bounds(int minX, int minZ, int maxX, int maxZ) {
        static Bounds of(DebugRawPath path) {
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (List<BlockPos> nodes : List.of(path.coarse(), path.accurate())) {
                for (BlockPos node : nodes) {
                    minX = Math.min(minX, node.getX());
                    minZ = Math.min(minZ, node.getZ());
                    maxX = Math.max(maxX, node.getX());
                    maxZ = Math.max(maxZ, node.getZ());
                }
            }
            return new Bounds(minX, minZ, maxX, maxZ);
        }

        boolean intersects(int x1, int z1, int x2, int z2) {
            return maxX >= x1 && minX <= x2 && maxZ >= z1 && minZ <= z2;
        }

        long distanceSquared(int x, int z) {
            long dx = Math.max(0L, Math.max((long) minX - x, (long) x - maxX));
            long dz = Math.max(0L, Math.max((long) minZ - z, (long) z - maxZ));
            return dx * dx + dz * dz;
        }
    }
}
