/* 文件职责：按世界目录持久化道路聚合根和 ChunkPos 分片，并原子发布内存快照。 */
package net.shiroha233.roadweaver.persistence.chunk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.persistence.RoadReplacement;
import net.shiroha233.roadweaver.persistence.files.FileStorageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单个世界目录的道路存储。
 *
 * <p>第一次访问时将聚合 NBT 全量解码一次并构造不可变快照。之后查询只读取 AtomicReference，
 * 不持锁、不读取文件，也不解析 JSON/NBT。所有修改在写锁内先落盘新/变 NBT 与分片，
 * 立即发布新快照；index.json 提交点由后台线程按节流间隔批量提交，
 * 消除高频修改下按全部道路数重写索引的写放大。崩溃时 index 保持上一提交态，
 * 加载端一致性校验会触发元数据重建自愈。</p>
 */
public final class RoadChunkStore implements AutoCloseable {
    public static final int SCHEMA_VERSION = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String ROOT_SCHEMA = "roadweaver.roads";
    private static final String INDEX_SCHEMA = "roadweaver.roads.index";
    private static final String CHUNK_SCHEMA = "roadweaver.roads.chunk";
    private static final String SCHEMA_FILE = "schema.json";
    private static final String INDEX_FILE = "index.json";
    private static final String ROAD_DIRECTORY = "roads";
    private static final String CHUNK_DIRECTORY = "chunks";

    private final Path root;
    private final ReentrantLock mutationLock = new ReentrantLock();
    private final AtomicReference<RoadSnapshot> snapshot = new AtomicReference<>(RoadSnapshot.empty());
    private static final long INDEX_COMMIT_INTERVAL_MS = 1000;
    private volatile boolean loaded;
    private long generation;
    private volatile boolean indexDirty;
    private long pendingIndexGeneration = -1L;
    private long lastCommitAttemptMs;
    private final Set<String> pendingDeletions = new LinkedHashSet<>();

    public RoadChunkStore(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public void preload() {
        ensureLoaded();
    }

    public List<RoadData> queryRect(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        ensureLoaded();
        return snapshot.get().queryRect(minBlockX, minBlockZ, maxBlockX, maxBlockZ);
    }

    public List<RoadData> queryChunk(ChunkPos chunkPos) {
        Objects.requireNonNull(chunkPos, "chunkPos");
        return queryChunk(chunkPos.x, chunkPos.z);
    }

    public List<RoadData> queryChunk(int chunkX, int chunkZ) {
        ensureLoaded();
        return snapshot.get().queryChunk(chunkX, chunkZ);
    }

    public List<RoadData> loadAll() {
        ensureLoaded();
        return snapshot.get().all();
    }

    public RoadData loadByFingerprint(long fingerprint) {
        ensureLoaded();
        return snapshot.get().byFingerprint(fingerprint);
    }

    public boolean hasAnyRoad() {
        ensureLoaded();
        return !snapshot.get().isEmpty();
    }

    public boolean hasRoadInRect(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        ensureLoaded();
        return snapshot.get().hasRoadInRect(minBlockX, minBlockZ, maxBlockX, maxBlockZ);
    }

    public void addRoad(RoadData road) {
        if (!isValidRoad(road)) return;
        mutationLock.lock();
        try {
            ensureLoadedLocked();
            RoadSnapshot previous = snapshot.get();
            RoadData frozen = RoadSnapshot.freezeForStorage(road);
            long fingerprint = RoadFingerprint.compute(frozen);
            RoadSnapshot.Entry existing = previous.entries().get(fingerprint);
            if (existing != null && existing.road().equals(frozen)) return;

            long nextGeneration = nextGeneration();
            LinkedHashMap<Long, RoadSnapshot.Entry> entries = new LinkedHashMap<>(previous.entries());
            entries.put(fingerprint, newEntry(fingerprint, frozen, nextGeneration));
            publish(previous, RoadSnapshot.fromEntryMap(entries), nextGeneration);
        } finally {
            mutationLock.unlock();
        }
    }

    public void replaceRoad(long oldFingerprint, RoadData newRoad) {
        replaceRoads(List.of(new RoadReplacement(oldFingerprint, newRoad)));
    }

    public void replaceRoads(Collection<RoadReplacement> replacements) {
        if (replacements == null || replacements.isEmpty()) return;
        mutationLock.lock();
        try {
            ensureLoadedLocked();
            RoadSnapshot previous = snapshot.get();
            LinkedHashMap<Long, RoadSnapshot.Entry> entries = new LinkedHashMap<>(previous.entries());
            long nextGeneration = nextGeneration();
            boolean changed = false;
            for (RoadReplacement replacement : replacements) {
                if (replacement == null || !isValidRoad(replacement.newRoad())) continue;

                RoadData frozen = RoadSnapshot.freezeForStorage(replacement.newRoad());
                long newFingerprint = RoadFingerprint.compute(frozen);
                RoadSnapshot.Entry existing = entries.get(newFingerprint);
                if (replacement.oldFingerprint() == newFingerprint
                        && existing != null
                        && existing.road().equals(frozen)) {
                    continue;
                }

                entries.remove(replacement.oldFingerprint());
                entries.remove(newFingerprint);
                entries.put(newFingerprint, newEntry(newFingerprint, frozen, nextGeneration));
                changed = true;
            }
            if (changed) {
                publish(previous, RoadSnapshot.fromEntryMap(entries), nextGeneration);
            }
        } finally {
            mutationLock.unlock();
        }
    }

    public void deleteRoad(long fingerprint) {
        mutationLock.lock();
        try {
            ensureLoadedLocked();
            RoadSnapshot previous = snapshot.get();
            if (!previous.entries().containsKey(fingerprint)) return;
            long nextGeneration = nextGeneration();
            LinkedHashMap<Long, RoadSnapshot.Entry> entries = new LinkedHashMap<>(previous.entries());
            entries.remove(fingerprint);
            publish(previous, RoadSnapshot.fromEntryMap(entries), nextGeneration);
        } finally {
            mutationLock.unlock();
        }
    }

    /** 写入路径已异步化；flush 确保待提交索引、首次加载与待修复元数据全部落盘。 */
    public void flush() {
        ensureLoaded();
        commitPendingIndex();
    }

    public void clear() {
        mutationLock.lock();
        try {
            ensureLoadedLocked();
            FileStorageIO.deleteTree(root, LOGGER, "清理道路 ChunkPos 分片失败");
            snapshot.set(RoadSnapshot.empty());
            generation = 0L;
            loaded = true;
            indexDirty = false;
            pendingIndexGeneration = -1L;
            pendingDeletions.clear();
        } finally {
            mutationLock.unlock();
        }
    }

    @Override
    public void close() {
        // 无后台长驻资源；索引由后台调度器或 flush 提交，实例由世界路径注册表负责释放。
    }

    Path chunkPath(int chunkX, int chunkZ) {
        return root.resolve(CHUNK_DIRECTORY).resolve(new RoadChunkKey(chunkX, chunkZ).fileName());
    }

    private void ensureLoaded() {
        if (loaded) return;
        mutationLock.lock();
        try {
            ensureLoadedLocked();
        } finally {
            mutationLock.unlock();
        }
    }

    private void ensureLoadedLocked() {
        if (loaded) return;
        LoadedSnapshot loadedSnapshot;
        try {
            loadedSnapshot = readSnapshot();
        } catch (IOException e) {
            LOGGER.warn("首次载入道路 ChunkPos 存储失败，将使用空快照: {}", root, e);
            loadedSnapshot = new LoadedSnapshot(RoadSnapshot.empty(), 0L, false);
        }
        snapshot.set(loadedSnapshot.snapshot());
        generation = loadedSnapshot.generation();
        loaded = true;

        if (loadedSnapshot.rewriteMetadata()) {
            long repairGeneration = generation == 0L ? 1L : generation;
            try {
                persistSnapshot(RoadSnapshot.empty(), loadedSnapshot.snapshot(), repairGeneration, true);
                generation = repairGeneration;
            } catch (IOException e) {
                // 查询仍使用已完成的一次性内存快照；下次实际修改会重新写完整元数据。
                LOGGER.warn("修复道路 ChunkPos 分片元数据失败: {}", root, e);
            }
            commitPendingIndex();
        }
    }

    private void publish(RoadSnapshot previous, RoadSnapshot next, long nextGeneration) {
        try {
            persistSnapshot(previous, next, nextGeneration, false);
        } catch (IOException e) {
            throw new IllegalStateException("failed to persist road ChunkPos snapshot", e);
        }
        generation = nextGeneration;
        snapshot.set(next);
    }

    private long nextGeneration() {
        return generation == Long.MAX_VALUE ? 1L : generation + 1L;
    }

    private static RoadSnapshot.Entry newEntry(long fingerprint, RoadData road, long generation) {
        String fileName = Long.toUnsignedString(fingerprint)
                + ".g" + Long.toUnsignedString(generation) + ".nbt";
        return new RoadSnapshot.Entry(fingerprint, road, RoadFootprint.from(road), fileName);
    }

    private LoadedSnapshot readSnapshot() throws IOException {
        Path indexPath = root.resolve(INDEX_FILE);
        if (!Files.isRegularFile(indexPath)) return new LoadedSnapshot(RoadSnapshot.empty(), 0L, false);

        IndexDocument document;
        try (Reader reader = Files.newBufferedReader(indexPath, StandardCharsets.UTF_8)) {
            document = GSON.fromJson(reader, IndexDocument.class);
        } catch (RuntimeException | IOException e) {
            FileStorageIO.quarantineCorrupt(indexPath, LOGGER, "道路聚合索引损坏，已隔离");
            return new LoadedSnapshot(RoadSnapshot.empty(), 0L, false);
        }
        if (document == null || document.roads == null) {
            FileStorageIO.quarantineCorrupt(indexPath, LOGGER, "道路聚合索引缺少 roads，已隔离");
            return new LoadedSnapshot(RoadSnapshot.empty(), 0L, false);
        }

        LinkedHashMap<Long, RoadSnapshot.Entry> entries = new LinkedHashMap<>();
        boolean skippedEntry = false;
        for (IndexEntry indexEntry : document.roads) {
            if (indexEntry == null || indexEntry.file == null) {
                skippedEntry = true;
                continue;
            }
            Path roadPath = safeRoadPath(indexEntry.file);
            if (roadPath == null) {
                skippedEntry = true;
                LOGGER.warn("忽略越界道路文件引用: {}", indexEntry.file);
                continue;
            }
            RoadData road;
            try {
                road = readRoadFile(roadPath);
            } catch (IOException readFailure) {
                skippedEntry = true;
                LOGGER.warn("读取道路聚合文件失败，已跳过: {}", roadPath, readFailure);
                continue;
            }
            if (road == null) {
                skippedEntry = true;
                continue;
            }
            try {
                RoadData frozen = RoadSnapshot.freezeForStorage(road);
                long fingerprint = RoadFingerprint.compute(frozen);
                if (fingerprint == 0L) {
                    skippedEntry = true;
                    continue;
                }
                if (fingerprint != indexEntry.fingerprint || entries.containsKey(fingerprint)) {
                    skippedEntry = true;
                }
                entries.put(fingerprint,
                        new RoadSnapshot.Entry(fingerprint, frozen, RoadFootprint.from(frozen), indexEntry.file));
            } catch (RuntimeException malformedRoad) {
                skippedEntry = true;
                LOGGER.warn("道路聚合数据结构无效，已跳过: {}", roadPath, malformedRoad);
            }
        }
        RoadSnapshot loadedSnapshot = RoadSnapshot.fromEntryMap(entries);
        boolean currentIndex = INDEX_SCHEMA.equals(document.schema) && document.version == SCHEMA_VERSION;
        boolean currentRootSchema = hasCurrentRootSchema();
        boolean currentShards = chunkShardsMatch(loadedSnapshot);
        long loadedGeneration = Math.max(0L, document.generation);
        return new LoadedSnapshot(
                loadedSnapshot,
                loadedGeneration,
                skippedEntry || !currentIndex || !currentRootSchema || !currentShards);
    }

    private boolean hasCurrentRootSchema() {
        Path schemaPath = root.resolve(SCHEMA_FILE);
        if (!Files.isRegularFile(schemaPath)) return false;
        try (Reader reader = Files.newBufferedReader(schemaPath, StandardCharsets.UTF_8)) {
            SchemaDocument schema = GSON.fromJson(reader, SchemaDocument.class);
            return schema != null && ROOT_SCHEMA.equals(schema.schema) && schema.version == SCHEMA_VERSION;
        } catch (RuntimeException | IOException e) {
            return false;
        }
    }

    private boolean chunkShardsMatch(RoadSnapshot expected) {
        Path chunkDirectory = root.resolve(CHUNK_DIRECTORY);
        if (!Files.isDirectory(chunkDirectory)) return expected.chunkReferences().isEmpty();
        Map<Long, long[]> actual = new HashMap<>();
        boolean valid = true;
        try (var files = Files.list(chunkDirectory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                RoadChunkKey key = RoadChunkKey.parseFileName(file.getFileName().toString());
                if (key == null) continue;
                ChunkDocument document;
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    document = GSON.fromJson(reader, ChunkDocument.class);
                } catch (RuntimeException | IOException e) {
                    FileStorageIO.quarantineCorrupt(file, LOGGER, "道路区块分片损坏，已隔离");
                    valid = false;
                    continue;
                }
                if (document == null
                        || !CHUNK_SCHEMA.equals(document.schema)
                        || document.version != SCHEMA_VERSION
                        || document.chunkX != key.x()
                        || document.chunkZ != key.z()
                        || document.roads == null) {
                    valid = false;
                    continue;
                }
                long[] references = new long[document.roads.size()];
                for (int index = 0; index < references.length; index++) {
                    Long value = document.roads.get(index);
                    references[index] = value == null ? 0L : value;
                }
                actual.put(key.packed(), references);
            }
        } catch (IOException e) {
            LOGGER.warn("读取道路区块分片目录失败: {}", chunkDirectory, e);
            return false;
        }
        if (!valid || actual.size() != expected.chunkReferences().size()) return false;
        for (Map.Entry<Long, long[]> entry : expected.chunkReferences().entrySet()) {
            if (!Arrays.equals(entry.getValue(), actual.get(entry.getKey()))) return false;
        }
        return true;
    }

    private void persistSnapshot(RoadSnapshot previous,
                                 RoadSnapshot next,
                                 long targetGeneration,
                                 boolean forceMetadata) throws IOException {
        Files.createDirectories(root);
        writeSchema();

        for (RoadSnapshot.Entry entry : next.entries().values()) {
            RoadSnapshot.Entry old = previous.entries().get(entry.fingerprint());
            Path existingPath = safeRoadPath(entry.fileName());
            if (forceMetadata && old == null && existingPath != null && Files.isRegularFile(existingPath)) continue;
            if (old == null
                    || !old.road().equals(entry.road())
                    || !old.fileName().equals(entry.fileName())) {
                writeRoadFile(requireSafeRoadPath(entry.fileName()), entry.road());
            }
        }

        Set<Long> allChunks = new LinkedHashSet<>(previous.chunkReferences().keySet());
        allChunks.addAll(next.chunkReferences().keySet());
        for (long packedChunk : allChunks) {
            long[] oldReferences = previous.chunkReferences().get(packedChunk);
            long[] newReferences = next.chunkReferences().get(packedChunk);
            RoadChunkKey key = RoadChunkKey.fromPacked(packedChunk);
            Path shardPath = chunkPath(key.x(), key.z());
            if (newReferences == null || newReferences.length == 0) {
                Files.deleteIfExists(shardPath);
            } else if (forceMetadata || !Arrays.equals(oldReferences, newReferences)) {
                writeChunkShard(key, newReferences, targetGeneration);
            }
        }

        // 新/变 NBT 与分片已先行落盘；index.json 提交点与旧文件清理合并到后台批量执行。
        // 崩溃时 index 保持上一提交态，加载端一致性校验会触发元数据重建自愈。
        if (targetGeneration > pendingIndexGeneration) pendingIndexGeneration = targetGeneration;
        for (RoadSnapshot.Entry old : previous.entries().values()) {
            RoadSnapshot.Entry current = next.entries().get(old.fingerprint());
            if (current != null && old.fileName().equals(current.fileName())) continue;
            pendingDeletions.add(old.fileName());
        }
        indexDirty = true;
    }

    /** 由后台调度器周期调用：按节流间隔提交待落盘索引。 */
    public void commitPendingIndexIfDue() {
        if (!indexDirty) return;
        long now = System.currentTimeMillis();
        if (now - lastCommitAttemptMs < INDEX_COMMIT_INTERVAL_MS) return;
        commitPendingIndex();
    }

    /** 立即同步提交待落盘索引，并在提交成功后清理被替换的旧道路文件。 */
    public void commitPendingIndex() {
        lastCommitAttemptMs = System.currentTimeMillis();
        Set<String> deletions;
        long targetGeneration;
        mutationLock.lock();
        try {
            if (!indexDirty) return;
            deletions = Set.copyOf(pendingDeletions);
            targetGeneration = Math.max(pendingIndexGeneration, generation);
        } finally {
            mutationLock.unlock();
        }

        RoadSnapshot current = snapshot.get();
        try {
            Files.createDirectories(root);
            writeIndex(current, targetGeneration);
        } catch (IOException e) {
            LOGGER.warn("道路索引延迟提交失败，稍后重试: {}", root, e);
            return;
        }

        mutationLock.lock();
        try {
            indexDirty = false;
            pendingIndexGeneration = -1L;
            for (String fileName : deletions) {
                Path oldPath = safeRoadPath(fileName);
                if (oldPath == null) continue;
                try {
                    Files.deleteIfExists(oldPath);
                } catch (IOException e) {
                    LOGGER.warn("清理旧道路聚合文件失败: {}", oldPath, e);
                }
            }
            pendingDeletions.removeAll(deletions);
        } finally {
            mutationLock.unlock();
        }
    }

    private void writeSchema() throws IOException {
        SchemaDocument document = new SchemaDocument();
        document.schema = ROOT_SCHEMA;
        document.version = SCHEMA_VERSION;
        document.index = INDEX_FILE;
        document.roadDirectory = ROAD_DIRECTORY;
        document.chunkDirectory = CHUNK_DIRECTORY;
        FileStorageIO.writeStringAtomic(root.resolve(SCHEMA_FILE), GSON.toJson(document));
    }

    private void writeIndex(RoadSnapshot next, long targetGeneration) throws IOException {
        IndexDocument document = new IndexDocument();
        document.schema = INDEX_SCHEMA;
        document.version = SCHEMA_VERSION;
        document.generation = targetGeneration;
        document.roads = new ArrayList<>(next.entries().size());
        for (RoadSnapshot.Entry entry : next.entries().values()) {
            RoadFootprint footprint = entry.footprint();
            IndexEntry indexEntry = new IndexEntry();
            indexEntry.fingerprint = entry.fingerprint();
            indexEntry.width = entry.road().width();
            indexEntry.roadType = entry.road().roadType();
            indexEntry.minX = footprint.isEmpty() ? 0 : footprint.minX();
            indexEntry.minZ = footprint.isEmpty() ? 0 : footprint.minZ();
            indexEntry.maxX = footprint.isEmpty() ? 0 : footprint.maxX();
            indexEntry.maxZ = footprint.isEmpty() ? 0 : footprint.maxZ();
            indexEntry.file = entry.fileName();
            document.roads.add(indexEntry);
        }
        FileStorageIO.writeStringAtomic(root.resolve(INDEX_FILE), GSON.toJson(document));
    }

    private void writeChunkShard(RoadChunkKey key, long[] references, long targetGeneration) throws IOException {
        ChunkDocument document = new ChunkDocument();
        document.schema = CHUNK_SCHEMA;
        document.version = SCHEMA_VERSION;
        document.generation = targetGeneration;
        document.chunkX = key.x();
        document.chunkZ = key.z();
        document.roads = new ArrayList<>(references.length);
        for (long fingerprint : references) document.roads.add(fingerprint);
        FileStorageIO.writeStringAtomic(chunkPath(key.x(), key.z()), GSON.toJson(document));
    }

    private Path safeRoadPath(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        Path roadDirectory = root.resolve(ROAD_DIRECTORY).normalize();
        Path candidate = roadDirectory.resolve(fileName).normalize();
        if (!candidate.getParent().equals(roadDirectory)
                || !candidate.getFileName().toString().equals(fileName)
                || !fileName.endsWith(".nbt")) {
            return null;
        }
        return candidate;
    }

    private Path requireSafeRoadPath(String fileName) {
        Path path = safeRoadPath(fileName);
        if (path == null) throw new IllegalArgumentException("invalid road file name: " + fileName);
        return path;
    }

    private static void writeRoadFile(Path file, RoadData road) throws IOException {
        CompoundTag compound = new CompoundTag();
        Tag encoded = RoadData.CODEC.encodeStart(NbtOps.INSTANCE, road)
                .result()
                .orElseThrow(() -> new IllegalStateException("road codec encode failed"));
        compound.put("road", encoded);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(output)) {
            NbtIo.write(compound, data);
        }
        FileStorageIO.writeBytesAtomic(file, output.toByteArray());
    }

    private static RoadData readRoadFile(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return null;
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length == 0) {
            FileStorageIO.quarantineCorrupt(file, LOGGER, "道路 NBT 文件为空，已隔离");
            return null;
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            CompoundTag compound = NbtIo.read(input);
            if (compound == null || !compound.contains("road")) {
                FileStorageIO.quarantineCorrupt(file, LOGGER, "道路 NBT 缺少聚合根，已隔离");
                return null;
            }
            Tag tag = compound.get("road");
            RoadData road = RoadData.CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, tag)).result().orElse(null);
            if (road == null) FileStorageIO.quarantineCorrupt(file, LOGGER, "道路 NBT 解码失败，已隔离");
            return road;
        } catch (RuntimeException e) {
            FileStorageIO.quarantineCorrupt(file, LOGGER, "道路 NBT 损坏，已隔离");
            return null;
        }
    }

    private static boolean isValidRoad(RoadData road) {
        return road != null && road.roadSegmentList() != null && !road.roadSegmentList().isEmpty();
    }

    private record LoadedSnapshot(RoadSnapshot snapshot, long generation, boolean rewriteMetadata) {}

    private static final class SchemaDocument {
        String schema;
        int version;
        String index;
        String roadDirectory;
        String chunkDirectory;
    }

    private static final class IndexDocument {
        String schema;
        int version;
        long generation;
        List<IndexEntry> roads = new ArrayList<>();
    }

    private static final class IndexEntry {
        long fingerprint;
        int width;
        int roadType;
        int minX;
        int minZ;
        int maxX;
        int maxZ;
        String file;
    }

    private static final class ChunkDocument {
        String schema;
        int version;
        long generation;
        int chunkX;
        int chunkZ;
        List<Long> roads = new ArrayList<>();
    }
}
