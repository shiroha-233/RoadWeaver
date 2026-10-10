/* 文件职责：以稀疏瓦片文件实现精确高度样本持久化。 */
package net.shiroha233.roadweaver.persistence.files;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightChunk;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightGrid;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSample;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateSampleStore;
import net.shiroha233.roadweaver.persistence.WorldgenFingerprint;
import net.shiroha233.roadweaver.persistence.WorldgenFingerprintService;
import net.shiroha233.roadweaver.util.StripedLocks;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32C;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * 基于 16x16 区块稀疏瓦片的精确高度样本文件存储。
 */
public final class FileBackedAccurateSampleStore implements AccurateSampleStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final String CATEGORY = "accurate_samples";
    private static final int STORE_MAGIC = 0x52574153;
    private static final int TILE_SIZE_CHUNKS = 16;
    private static final int TILE_SIZE_BLOCKS = TILE_SIZE_CHUNKS * 16;
    private static final int TILE_COLUMN_COUNT = TILE_SIZE_BLOCKS * TILE_SIZE_BLOCKS;
    private static final int COVERAGE_WORD_COUNT = TILE_COLUMN_COUNT / Long.SIZE;
    private static final int DECODED_TILE_CACHE_CAPACITY = 128;

    private final Path dimensionRoot;
    private final Path namespaceRoot;
    private final WorldgenFingerprint fingerprint;
    private final Object decodedTileCacheLock = new Object();
    private final Map<TileCoord, SparseTile> decodedTileCache = new LinkedHashMap<>(32, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TileCoord, SparseTile> eldest) {
            return size() > DECODED_TILE_CACHE_CAPACITY;
        }
    };

    public static AccurateSampleStore create(ServerLevel level, WorldgenFingerprint fingerprint) {
        if (level == null || fingerprint == null) {
            return AccurateSampleStore.noop();
        }
        try {
            return new FileBackedAccurateSampleStore(
                    FileStoragePathResolver.categoryRoot(level, CATEGORY),
                    fingerprint);
        } catch (RuntimeException failure) {
            LOGGER.warn("初始化精确样本文件存储失败，回退为仅内存缓存", failure);
            return AccurateSampleStore.noop();
        }
    }

    public static AccurateSampleStore create(ServerLevel level) {
        if (level == null) return AccurateSampleStore.noop();
        try {
            return create(level, WorldgenFingerprintService.forLevel(level));
        } catch (RuntimeException failure) {
            LOGGER.warn("计算精确样本世界指纹失败，回退为仅内存缓存", failure);
            return AccurateSampleStore.noop();
        }
    }

    public FileBackedAccurateSampleStore(Path dimensionRoot, WorldgenFingerprint fingerprint) {
        this.dimensionRoot = Objects.requireNonNull(dimensionRoot, "dimensionRoot").toAbsolutePath().normalize();
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.namespaceRoot = this.dimensionRoot.resolve(fingerprint.namespace());
        initializeNamespace();
    }

    @Override
    public Map<Long, AccurateHeightChunk> loadChunks(Collection<Long> chunkKeys) {
        if (chunkKeys == null || chunkKeys.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<TileCoord, LongArrayList> grouped = new LinkedHashMap<>();
        for (long chunkKey : new LinkedHashSet<>(chunkKeys)) {
            grouped.computeIfAbsent(TileCoord.fromChunkKey(chunkKey), ignored -> new LongArrayList()).add(chunkKey);
        }
        LinkedHashMap<Long, AccurateHeightChunk> result = new LinkedHashMap<>();
        for (Map.Entry<TileCoord, LongArrayList> entry : grouped.entrySet()) {
            TileCoord tileCoord = entry.getKey();
            synchronized (lockFor(tileCoord)) {
                SparseTile tile = loadTileForRead(tileCoord);
                if (tile == null) {
                    continue;
                }
                LongArrayList tileChunkKeys = entry.getValue();
                for (int index = 0; index < tileChunkKeys.size(); index++) {
                    long chunkKey = tileChunkKeys.getLong(index);
                    AccurateHeightChunk chunk = tile.loadChunk(chunkKey);
                    if (chunk != null) {
                        result.put(chunkKey, chunk);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public Map<Long, AccurateHeightSample> loadSamples(Collection<Long> sampleKeys) {
        if (sampleKeys == null || sampleKeys.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<TileCoord, LongArrayList> grouped = new LinkedHashMap<>();
        for (long sampleKey : new LinkedHashSet<>(sampleKeys)) {
            grouped.computeIfAbsent(TileCoord.fromSampleKey(sampleKey), ignored -> new LongArrayList()).add(sampleKey);
        }
        LinkedHashMap<Long, AccurateHeightSample> result = new LinkedHashMap<>();
        for (Map.Entry<TileCoord, LongArrayList> entry : grouped.entrySet()) {
            TileCoord tileCoord = entry.getKey();
            synchronized (lockFor(tileCoord)) {
                SparseTile tile = loadTileForRead(tileCoord);
                if (tile == null) {
                    continue;
                }
                LongArrayList tileKeys = entry.getValue();
                for (int index = 0; index < tileKeys.size(); index++) {
                    long sampleKey = tileKeys.getLong(index);
                    AccurateHeightSample sample = tile.loadSample(sampleKey);
                    if (sample != null) {
                        result.put(sampleKey, sample);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void saveChunks(Map<Long, AccurateHeightChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        LinkedHashMap<TileCoord, LinkedHashMap<Long, AccurateHeightChunk>> grouped = new LinkedHashMap<>();
        for (Map.Entry<Long, AccurateHeightChunk> entry : chunks.entrySet()) {
            AccurateHeightChunk chunk = entry.getValue();
            if (chunk == null) {
                continue;
            }
            grouped.computeIfAbsent(TileCoord.fromChunkKey(entry.getKey()), ignored -> new LinkedHashMap<>())
                    .put(entry.getKey(), chunk);
        }

        for (Map.Entry<TileCoord, LinkedHashMap<Long, AccurateHeightChunk>> entry : grouped.entrySet()) {
            TileCoord tileCoord = entry.getKey();
            synchronized (lockFor(tileCoord)) {
                SparseTile tile = loadTileForWrite(tileCoord);
                if (tile == null) {
                    continue;
                }
                for (AccurateHeightChunk chunk : entry.getValue().values()) {
                    tile.putChunk(chunk);
                }
                writeTile(tileCoord, tile);
            }
        }
    }

    @Override
    public void saveSamples(Map<Long, AccurateHeightSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return;
        }
        LinkedHashMap<TileCoord, LinkedHashMap<Long, AccurateHeightSample>> grouped = new LinkedHashMap<>();
        for (Map.Entry<Long, AccurateHeightSample> entry : samples.entrySet()) {
            AccurateHeightSample sample = entry.getValue();
            if (sample == null) {
                continue;
            }
            grouped.computeIfAbsent(TileCoord.fromSampleKey(entry.getKey()), ignored -> new LinkedHashMap<>())
                    .put(entry.getKey(), sample);
        }

        for (Map.Entry<TileCoord, LinkedHashMap<Long, AccurateHeightSample>> entry : grouped.entrySet()) {
            TileCoord tileCoord = entry.getKey();
            synchronized (lockFor(tileCoord)) {
                SparseTile tile = loadTileForWrite(tileCoord);
                if (tile == null) {
                    continue;
                }
                for (Map.Entry<Long, AccurateHeightSample> sampleEntry : entry.getValue().entrySet()) {
                    tile.putSample(sampleEntry.getKey(), sampleEntry.getValue());
                }
                writeTile(tileCoord, tile);
            }
        }
    }

    @Override
    public void saveGrid(AccurateHeightGrid grid) {
        if (grid == null) {
            return;
        }
        LinkedHashMap<TileCoord, SparseTile> updates = new LinkedHashMap<>();
        for (int index = 0; index < grid.request().sampleCount(); index++) {
            long sampleKey = AccurateHeightSample.key(grid.request().blockX(index), grid.request().blockZ(index));
            TileCoord tileCoord = TileCoord.fromSampleKey(sampleKey);
            SparseTile tile = updates.computeIfAbsent(
                    tileCoord,
                    ignored -> new SparseTile(tileCoord.tileX(), tileCoord.tileZ()));
            tile.putSample(
                    sampleKey,
                    new AccurateHeightSample(
                            grid.worldSurface()[index],
                            grid.oceanFloor()[index],
                            grid.motionBlocking()[index]));
        }

        for (Map.Entry<TileCoord, SparseTile> entry : updates.entrySet()) {
            TileCoord tileCoord = entry.getKey();
            synchronized (lockFor(tileCoord)) {
                SparseTile tile = loadTileForWrite(tileCoord);
                if (tile == null) {
                    continue;
                }
                tile.mergeFrom(entry.getValue());
                writeTile(tileCoord, tile);
            }
        }
    }

    @Override
    public void close() {
        synchronized (decodedTileCacheLock) {
            decodedTileCache.clear();
        }
    }

    Path namespaceRoot() {
        return namespaceRoot;
    }

    Path tilePath(int tileX, int tileZ) {
        return tilePath(new TileCoord(tileX, tileZ));
    }

    private void initializeNamespace() {
        try {
            Files.createDirectories(dimensionRoot);
            clearStaleFingerprints();
            Files.createDirectories(namespaceRoot);
        } catch (IOException failure) {
            throw new IllegalStateException("failed to initialize accurate sample namespace", failure);
        }
    }

    private void clearStaleFingerprints() {
        try (var stream = Files.list(dimensionRoot)) {
            stream.filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().equals(fingerprint.namespace()))
                    .forEach(path -> FileStorageIO.deleteTree(path, LOGGER, "清理过期精确样本指纹命名空间失败"));
        } catch (IOException failure) {
            LOGGER.warn("扫描精确样本指纹命名空间失败: {}", dimensionRoot, failure);
        }
    }

    /** 采样瓦片坐标无上界，按坐标缓存锁对象会只增不减，故用分片锁。 */
    private Object lockFor(TileCoord tileCoord) {
        return StripedLocks.lockFor(tileCoord == null ? 0 : tileCoord.hashCode());
    }

    private SparseTile loadTileForRead(TileCoord tileCoord) {
        SparseTile cached = getCachedTile(tileCoord);
        if (cached != null) {
            return cached;
        }

        Path path = tilePath(tileCoord);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            byte[] data = Files.readAllBytes(path);
            SparseTile tile = decode(tileCoord, data);
            if (tile == null) {
                evictTile(tileCoord);
                FileStorageIO.quarantineCorrupt(path, LOGGER, "精确样本瓦片损坏，已隔离");
                return null;
            }
            cacheTile(tileCoord, tile);
            return tile;
        } catch (IOException failure) {
            LOGGER.warn("读取精确样本瓦片失败: {}", path, failure);
            return null;
        }
    }

    private SparseTile loadTileForWrite(TileCoord tileCoord) {
        SparseTile cached = getCachedTile(tileCoord);
        if (cached != null) {
            return cached;
        }

        Path path = tilePath(tileCoord);
        if (!Files.exists(path)) {
            return new SparseTile(tileCoord.tileX(), tileCoord.tileZ());
        }
        try {
            byte[] data = Files.readAllBytes(path);
            SparseTile tile = decode(tileCoord, data);
            if (tile == null) {
                evictTile(tileCoord);
                FileStorageIO.quarantineCorrupt(path, LOGGER, "精确样本瓦片损坏，已隔离");
                return new SparseTile(tileCoord.tileX(), tileCoord.tileZ());
            }
            cacheTile(tileCoord, tile);
            return tile;
        } catch (IOException failure) {
            LOGGER.warn("读取精确样本瓦片失败，跳过本次写入: {}", path, failure);
            return null;
        }
    }

    private SparseTile getCachedTile(TileCoord tileCoord) {
        synchronized (decodedTileCacheLock) {
            return decodedTileCache.get(tileCoord);
        }
    }

    private void cacheTile(TileCoord tileCoord, SparseTile tile) {
        synchronized (decodedTileCacheLock) {
            decodedTileCache.put(tileCoord, tile);
        }
    }

    private void evictTile(TileCoord tileCoord) {
        synchronized (decodedTileCacheLock) {
            decodedTileCache.remove(tileCoord);
        }
    }

    private void writeTile(TileCoord tileCoord, SparseTile tile) {
        Path path = tilePath(tileCoord);
        try {
            byte[] data = encode(tile);
            FileStorageIO.writeBytesAtomic(path, data);
            cacheTile(tileCoord, tile);
        } catch (IOException failure) {
            LOGGER.warn("写入精确样本瓦片失败: {}", path, failure);
        }
    }

    private Path tilePath(TileCoord tileCoord) {
        return namespaceRoot
                .resolve(Integer.toString(tileCoord.tileX()))
                .resolve(tileCoord.tileZ() + ".bin");
    }

    private byte[] encode(SparseTile tile) throws IOException {
        byte[] body = tile.encodeBody();
        int checksum = checksum(body);

        ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream(body.length / 2 + 64);
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressedOutput)) {
            deflater.write(body);
        }
        byte[] compressedBody = compressedOutput.toByteArray();

        ByteArrayOutputStream raw = new ByteArrayOutputStream(compressedBody.length + 128);
        byte[] fingerprintBytes = fingerprint.namespace().getBytes(StandardCharsets.UTF_8);
        try (DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(STORE_MAGIC);
            out.writeInt(fingerprint.schemaVersion());
            out.writeInt(tile.tileX());
            out.writeInt(tile.tileZ());
            out.writeInt(fingerprintBytes.length);
            out.write(fingerprintBytes);
            out.writeInt(tile.presentCount());
            out.writeInt(checksum);
            out.writeInt(compressedBody.length);
            out.write(compressedBody);
        }
        return raw.toByteArray();
    }

    private SparseTile decode(TileCoord expectedTile, byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readInt() != STORE_MAGIC) {
                return null;
            }
            if (in.readInt() != fingerprint.schemaVersion()) {
                return null;
            }
            int tileX = in.readInt();
            int tileZ = in.readInt();
            if (tileX != expectedTile.tileX() || tileZ != expectedTile.tileZ()) {
                return null;
            }
            int fingerprintLength = in.readInt();
            if (fingerprintLength < 0 || fingerprintLength > 512) {
                return null;
            }
            byte[] fingerprintBytes = in.readNBytes(fingerprintLength);
            if (fingerprintBytes.length != fingerprintLength) {
                return null;
            }
            if (!fingerprint.namespace().equals(new String(fingerprintBytes, StandardCharsets.UTF_8))) {
                return null;
            }
            int sampleCount = in.readInt();
            if (sampleCount < 0 || sampleCount > TILE_COLUMN_COUNT) {
                return null;
            }
            int checksum = in.readInt();
            int compressedLength = in.readInt();
            if (compressedLength < 0) {
                return null;
            }
            byte[] compressedBody = in.readNBytes(compressedLength);
            if (compressedBody.length != compressedLength || in.read() != -1) {
                return null;
            }
            byte[] body;
            try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(compressedBody))) {
                body = inflater.readAllBytes();
            }
            if (checksum(body) != checksum) {
                return null;
            }
            return SparseTile.decodeBody(expectedTile.tileX(), expectedTile.tileZ(), sampleCount, body);
        } catch (IOException | RuntimeException failure) {
            return null;
        }
    }

    private static int checksum(byte[] body) {
        CRC32C crc32c = new CRC32C();
        crc32c.update(body, 0, body.length);
        return (int) crc32c.getValue();
    }

    private record TileCoord(int tileX, int tileZ) {
        private static TileCoord fromChunkKey(long chunkKey) {
            return new TileCoord(
                    Math.floorDiv(ChunkPos.getX(chunkKey), TILE_SIZE_CHUNKS),
                    Math.floorDiv(ChunkPos.getZ(chunkKey), TILE_SIZE_CHUNKS));
        }

        private static TileCoord fromSampleKey(long sampleKey) {
            return new TileCoord(
                    Math.floorDiv(blockX(sampleKey), TILE_SIZE_BLOCKS),
                    Math.floorDiv(blockZ(sampleKey), TILE_SIZE_BLOCKS));
        }
    }

    private static final class SparseTile {
        private final int tileX;
        private final int tileZ;
        private final long[] coverage = new long[COVERAGE_WORD_COUNT];
        private final Int2ObjectOpenHashMap<AccurateHeightSample> samples;
        private int presentCount;

        private SparseTile(int tileX, int tileZ) {
            this(tileX, tileZ, 16);
        }

        private SparseTile(int tileX, int tileZ, int expectedSamples) {
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.samples = new Int2ObjectOpenHashMap<>(Math.max(16, expectedSamples));
        }

        private int tileX() {
            return tileX;
        }

        private int tileZ() {
            return tileZ;
        }

        private int presentCount() {
            return presentCount;
        }

        private AccurateHeightSample loadSample(long sampleKey) {
            return samples.get(localIndex(sampleKey));
        }

        private AccurateHeightChunk loadChunk(long chunkKey) {
            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            int[] worldSurface = new int[AccurateHeightChunk.COLUMN_COUNT];
            int[] oceanFloor = new int[AccurateHeightChunk.COLUMN_COUNT];
            int[] motionBlocking = new int[AccurateHeightChunk.COLUMN_COUNT];

            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int blockX = (chunkX << 4) + localX;
                    int blockZ = (chunkZ << 4) + localZ;
                    AccurateHeightSample sample = samples.get(localIndex(blockX, blockZ));
                    if (sample == null) {
                        return null;
                    }
                    int index = localX + (localZ << 4);
                    worldSurface[index] = sample.worldSurfaceWg();
                    oceanFloor[index] = sample.oceanFloorWg();
                    motionBlocking[index] = sample.motionBlockingNoLeaves();
                }
            }
            return new AccurateHeightChunk(chunkX, chunkZ, worldSurface, oceanFloor, motionBlocking);
        }

        private void putSample(long sampleKey, AccurateHeightSample sample) {
            putSample(localIndex(sampleKey), sample);
        }

        private void putChunk(AccurateHeightChunk chunk) {
            int baseX = chunk.chunkX() << 4;
            int baseZ = chunk.chunkZ() << 4;
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int blockX = baseX + localX;
                    int blockZ = baseZ + localZ;
                    int column = localX + (localZ << 4);
                    putSample(
                            localIndex(blockX, blockZ),
                            new AccurateHeightSample(
                                    chunk.worldSurfaceWgAt(column),
                                    chunk.oceanFloorWgAt(column),
                                    chunk.motionBlockingNoLeavesAt(column)));
                }
            }
        }

        private void mergeFrom(SparseTile other) {
            for (var entry : other.samples.int2ObjectEntrySet()) {
                putSample(entry.getIntKey(), entry.getValue());
            }
        }

        private byte[] encodeBody() throws IOException {
            ByteArrayOutputStream raw = new ByteArrayOutputStream(COVERAGE_WORD_COUNT * Long.BYTES + presentCount * 12);
            try (DataOutputStream out = new DataOutputStream(raw)) {
                for (long word : coverage) {
                    out.writeLong(word);
                }
                for (int index = 0; index < TILE_COLUMN_COUNT; index++) {
                    if (!contains(index)) {
                        continue;
                    }
                    AccurateHeightSample sample = samples.get(index);
                    if (sample == null) {
                        throw new IllegalStateException("coverage/sample mismatch at index " + index);
                    }
                    out.writeInt(sample.worldSurfaceWg());
                    out.writeInt(sample.oceanFloorWg());
                    out.writeInt(sample.motionBlockingNoLeaves());
                }
            }
            return raw.toByteArray();
        }

        private static SparseTile decodeBody(int tileX, int tileZ, int sampleCount, byte[] body) throws IOException {
            SparseTile tile = new SparseTile(tileX, tileZ, sampleCount);
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
                for (int wordIndex = 0; wordIndex < COVERAGE_WORD_COUNT; wordIndex++) {
                    tile.coverage[wordIndex] = in.readLong();
                }
                int coverageCount = 0;
                for (long word : tile.coverage) {
                    coverageCount += Long.bitCount(word);
                }
                if (coverageCount != sampleCount) {
                    return null;
                }
                tile.presentCount = sampleCount;
                for (int index = 0; index < TILE_COLUMN_COUNT; index++) {
                    if (!tile.contains(index)) {
                        continue;
                    }
                    tile.samples.put(index, new AccurateHeightSample(
                            in.readInt(),
                            in.readInt(),
                            in.readInt()));
                }
                if (in.read() != -1) {
                    return null;
                }
            }
            return tile;
        }

        private void putSample(int localIndex, AccurateHeightSample sample) {
            if (sample == null) {
                return;
            }
            if (!contains(localIndex)) {
                coverage[localIndex >>> 6] |= 1L << (localIndex & 63);
                presentCount++;
            }
            samples.put(localIndex, sample);
        }

        private boolean contains(int localIndex) {
            return (coverage[localIndex >>> 6] & (1L << (localIndex & 63))) != 0L;
        }

        private int localIndex(int blockX, int blockZ) {
            return localIndex(blockX, blockZ, tileX, tileZ);
        }

        private int localIndex(long sampleKey) {
            return localIndex(blockX(sampleKey), blockZ(sampleKey));
        }

        private static int localIndex(int blockX, int blockZ, int tileX, int tileZ) {
            int localX = blockX - tileX * TILE_SIZE_BLOCKS;
            int localZ = blockZ - tileZ * TILE_SIZE_BLOCKS;
            if (localX < 0 || localX >= TILE_SIZE_BLOCKS || localZ < 0 || localZ >= TILE_SIZE_BLOCKS) {
                throw new IllegalArgumentException("column is outside tile bounds");
            }
            return localX + localZ * TILE_SIZE_BLOCKS;
        }
    }

    private static int blockX(long sampleKey) {
        return (int) (sampleKey >> 32);
    }

    private static int blockZ(long sampleKey) {
        return (int) sampleKey;
    }
}
