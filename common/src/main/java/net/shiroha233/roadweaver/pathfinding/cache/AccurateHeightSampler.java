package net.shiroha233.roadweaver.pathfinding.cache;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.shiroha233.roadweaver.persistence.files.FileBackedAccurateSampleStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 精确高度采样统一门面。
 */
public final class AccurateHeightSampler {
    private static final int CHUNK_CACHE_CAPACITY = 256;
    private static final int COLUMN_CACHE_CAPACITY = 8_192;
    private static final Map<ServerLevel, AccurateHeightSampler> SHARED_SAMPLERS = new IdentityHashMap<>();

    private final ServerLevel level;
    private final int seaLevel;
    private final AccurateHeightBackend backend;
    private final AccurateSampleStore store;
    private final Object cacheLock = new Object();
    private final Map<Long, AccurateHeightChunk> chunkCache = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, AccurateHeightChunk> eldest) {
            return size() > CHUNK_CACHE_CAPACITY;
        }
    };
    private final Map<Long, AccurateHeightSample> columnCache = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, AccurateHeightSample> eldest) {
            return size() > COLUMN_CACHE_CAPACITY;
        }
    };
    private final ConcurrentHashMap<Long, CompletableFuture<AccurateHeightChunk>> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CompletableFuture<Void>> tileInFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CompletableFuture<Void>> acceleratedTileInFlight = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    AccurateHeightSampler(ServerLevel level, AccurateHeightBackend backend) {
        this(level, backend, AccurateSampleStore.noop());
    }

    AccurateHeightSampler(ServerLevel level, AccurateHeightBackend backend, AccurateSampleStore store) {
        this.level = level;
        this.seaLevel = level == null ? 63 : level.getSeaLevel();
        this.backend = Objects.requireNonNull(backend, "backend");
        this.store = store == null ? AccurateSampleStore.noop() : store;
    }

    public static AccurateHeightSampler create(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        synchronized (SHARED_SAMPLERS) {
            AccurateHeightSampler existing = SHARED_SAMPLERS.get(level);
            if (existing != null && !existing.closed.get()) {
                return existing;
            }
            CpuAccurateHeightBackend cpu = CpuAccurateHeightBackend.create(level);
            AccurateHeightSampler created = new AccurateHeightSampler(
                    level,
                    AccurateHeightBackends.create(level, cpu),
                    FileBackedAccurateSampleStore.create(level));
            SHARED_SAMPLERS.put(level, created);
            return created;
        }
    }

    public int motionBlockingNoLeaves(int x, int z) {
        return chunkAt(x, z).motionBlockingNoLeaves(x, z);
    }

    public int worldSurfaceWg(int x, int z) {
        return chunkAt(x, z).worldSurfaceWg(x, z);
    }

    public int oceanFloorWg(int x, int z) {
        return chunkAt(x, z).oceanFloorWg(x, z);
    }

    public int surfaceHeight(int x, int z) {
        AccurateHeightChunk chunk = chunkAt(x, z);
        int motion = chunk.motionBlockingNoLeaves(x, z);
        return motion > seaLevel + 2 ? motion : chunk.worldSurfaceWg(x, z);
    }

    public Map<Long, AccurateHeightChunk> sampleChunks(Collection<Long> chunkKeys) {
        if (chunkKeys == null || chunkKeys.isEmpty()) {
            return Map.of();
        }
        ensureOpen();

        LinkedHashSet<Long> uniqueKeys = new LinkedHashSet<>(chunkKeys);
        LinkedHashMap<Long, AccurateHeightChunk> result = new LinkedHashMap<>(uniqueKeys.size());
        LinkedHashSet<Long> unresolved = new LinkedHashSet<>(uniqueKeys.size());
        LinkedHashMap<Long, CompletableFuture<AccurateHeightChunk>> owned = new LinkedHashMap<>();
        LinkedHashMap<Long, CompletableFuture<AccurateHeightChunk>> waiting = new LinkedHashMap<>();

        for (long key : uniqueKeys) {
            AccurateHeightChunk cached = getCached(key);
            if (cached != null) {
                AccurateSamplingStats.recordCacheHit();
                result.put(key, cached);
                continue;
            }
            AccurateSamplingStats.recordCacheMiss();
            unresolved.add(key);
        }

        resolveStoredChunks(unresolved, result);
        for (long key : unresolved) {
            CompletableFuture<AccurateHeightChunk> future = new CompletableFuture<>();
            CompletableFuture<AccurateHeightChunk> existing = inFlight.putIfAbsent(key, future);
            if (existing == null) {
                owned.put(key, future);
                continue;
            }
            waiting.put(key, existing);
        }

        if (!owned.isEmpty()) {
            completeOwned(owned, result);
        }
        joinWaiting(waiting, result);
        return orderedChunks(uniqueKeys, result);
    }

    public List<BlockPos> samplePathHeights(List<BlockPos> path, int divisor) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        List<BlockPos> positions = interpolatePositions(path, Math.max(1, divisor));
        Map<Long, AccurateHeightSample> samples = samplePositions(positions);
        List<BlockPos> result = new ArrayList<>(positions.size());
        for (BlockPos position : positions) {
            AccurateHeightSample sample = samples.get(AccurateHeightSample.key(position.getX(), position.getZ()));
            int height = sample != null ? surfaceHeight(sample) : surfaceHeight(position.getX(), position.getZ());
            result.add(new BlockPos(position.getX(), height, position.getZ()));
        }
        return result;
    }

    public Map<Long, AccurateHeightSample> samplePositions(Collection<BlockPos> positions) {
        return samplePositions(positions, AccurateSamplingProgress.NONE);
    }

    public Map<Long, AccurateHeightSample> samplePositions(Collection<BlockPos> positions,
                                                           AccurateSamplingProgress progress) {
        return samplePositionsInternal(positions, progress, true, false);
    }

    public AccurateHeightGrid sampleGrid(AccurateHeightGridRequest request) {
        return sampleGrid(request, AccurateSamplingProgress.NONE);
    }

    public AccurateHeightGrid sampleGrid(AccurateHeightGridRequest request,
                                         AccurateSamplingProgress progress) {
        return sampleGridInternal(request, progress, false);
    }

    public boolean supportsAcceleratedSampling() {
        return backend.supportsAcceleratedSampling();
    }

    public AccurateHeightGrid sampleAcceleratedGrid(AccurateHeightGridRequest request,
                                                     AccurateSamplingProgress progress) {
        return sampleGridInternal(request, progress, true);
    }

    public Optional<AccurateHeightGrid> loadStoredGrid(AccurateHeightGridRequest request) {
        ensureOpen();
        int sampleCount = request.sampleCount();
        int[] worldSurface = new int[sampleCount];
        int[] oceanFloor = new int[sampleCount];
        int[] motionBlocking = new int[sampleCount];
        LinkedHashMap<Long, Integer> missingIndexes = new LinkedHashMap<>();
        for (int index = 0; index < sampleCount; index++) {
            int blockX = request.blockX(index);
            int blockZ = request.blockZ(index);
            long key = AccurateHeightSample.key(blockX, blockZ);
            AccurateHeightSample sample = cachedSample(key, blockX, blockZ);
            if (sample == null) {
                missingIndexes.put(key, index);
            } else {
                fillGridColumn(worldSurface, oceanFloor, motionBlocking, index, sample);
            }
        }
        if (!missingIndexes.isEmpty()) {
            Map<Long, AccurateHeightSample> stored = store.loadSamples(missingIndexes.keySet());
            for (Map.Entry<Long, Integer> entry : missingIndexes.entrySet()) {
                AccurateHeightSample sample = stored.get(entry.getKey());
                if (sample == null) return Optional.empty();
                putColumnCached(entry.getKey(), sample);
                fillGridColumn(worldSurface, oceanFloor, motionBlocking, entry.getValue(), sample);
            }
        }
        return Optional.of(new AccurateHeightGrid(request, worldSurface, oceanFloor, motionBlocking));
    }

    private AccurateHeightGrid sampleGridInternal(AccurateHeightGridRequest request,
                                                   AccurateSamplingProgress progress,
                                                   boolean acceleratedOnly) {
        ensureOpen();
        AccurateSamplingProgress sink = progress == null ? AccurateSamplingProgress.NONE : progress;
        int sampleCount = request.sampleCount();
        int[] worldSurface = null;
        int[] oceanFloor = null;
        int[] motionBlocking = null;
        IntArrayList missingIndexes = new IntArrayList(sampleCount);
        int resolvedCount = 0;

        for (int index = 0; index < sampleCount; index++) {
            int blockX = request.blockX(index);
            int blockZ = request.blockZ(index);
            long key = AccurateHeightSample.key(blockX, blockZ);
            AccurateHeightSample cached = cachedSample(key, blockX, blockZ);
            if (cached != null) {
                if (worldSurface == null) {
                    worldSurface = new int[sampleCount];
                    oceanFloor = new int[sampleCount];
                    motionBlocking = new int[sampleCount];
                }
                AccurateSamplingStats.recordColumnCacheHit();
                fillGridColumn(worldSurface, oceanFloor, motionBlocking, index, cached);
                resolvedCount++;
                continue;
            }
            AccurateSamplingStats.recordColumnCacheMiss();
            missingIndexes.add(index);
        }

        if (missingIndexes.isEmpty()) {
            return new AccurateHeightGrid(request, worldSurface, oceanFloor, motionBlocking);
        }

        LongArrayList missingKeys = new LongArrayList(missingIndexes.size());
        for (int offset = 0; offset < missingIndexes.size(); offset++) {
            int index = missingIndexes.getInt(offset);
            missingKeys.add(AccurateHeightSample.key(request.blockX(index), request.blockZ(index)));
        }
        Map<Long, AccurateHeightSample> stored = store.loadSamples(missingKeys);
        if (stored.isEmpty() && resolvedCount == 0 && missingIndexes.size() == sampleCount) {
            return sampleWholeGridWithTileLease(request, sink, acceleratedOnly);
        }

        if (worldSurface == null) {
            worldSurface = new int[sampleCount];
            oceanFloor = new int[sampleCount];
            motionBlocking = new int[sampleCount];
        }

        IntArrayList unresolvedIndexes = new IntArrayList(missingIndexes.size());
        for (int offset = 0; offset < missingIndexes.size(); offset++) {
            int index = missingIndexes.getInt(offset);
            long key = AccurateHeightSample.key(request.blockX(index), request.blockZ(index));
            AccurateHeightSample sample = stored.get(key);
            if (sample != null) {
                putColumnCached(key, sample);
                fillGridColumn(worldSurface, oceanFloor, motionBlocking, index, sample);
                resolvedCount++;
                continue;
            }
            unresolvedIndexes.add(index);
        }

        if (!unresolvedIndexes.isEmpty()) {
            ArrayList<BlockPos> unresolvedPositions = new ArrayList<>(unresolvedIndexes.size());
            for (int offset = 0; offset < unresolvedIndexes.size(); offset++) {
                int index = unresolvedIndexes.getInt(offset);
                unresolvedPositions.add(new BlockPos(request.blockX(index), 0, request.blockZ(index)));
            }
            Map<Long, AccurateHeightSample> sampled = samplePositionsInternal(
                    unresolvedPositions, sink, false, acceleratedOnly);
            for (int offset = 0; offset < unresolvedIndexes.size(); offset++) {
                int index = unresolvedIndexes.getInt(offset);
                long key = AccurateHeightSample.key(request.blockX(index), request.blockZ(index));
                AccurateHeightSample sample = sampled.get(key);
                if (sample == null) {
                    throw new IllegalStateException("Missing accurate height column "
                            + request.blockX(index) + "," + request.blockZ(index));
                }
                fillGridColumn(worldSurface, oceanFloor, motionBlocking, index, sample);
            }
        }

        return new AccurateHeightGrid(request, worldSurface, oceanFloor, motionBlocking);
    }

    public void prefetchPositions(Collection<BlockPos> positions) {
        samplePositions(positions);
    }

    public String backendName() {
        return backend.backendName();
    }

    public String deviceName() {
        return backend.deviceName();
    }

    public void clear() {
        synchronized (SHARED_SAMPLERS) {
            if (level != null && SHARED_SAMPLERS.get(level) == this) {
                SHARED_SAMPLERS.remove(level);
            }
        }
        closeInternal();
    }

    private void closeInternal() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (cacheLock) {
            chunkCache.clear();
            columnCache.clear();
        }
        IllegalStateException failure = new IllegalStateException("Accurate height sampler is closed");
        inFlight.values().forEach(future -> future.completeExceptionally(failure));
        inFlight.clear();
        tileInFlight.values().forEach(future -> future.completeExceptionally(failure));
        tileInFlight.clear();
        acceleratedTileInFlight.values().forEach(future -> future.completeExceptionally(failure));
        acceleratedTileInFlight.clear();
        store.close();
        backend.close();
    }

    public static void clearForLevel(ServerLevel level) {
        if (level == null) {
            return;
        }
        AccurateHeightSampler sampler;
        synchronized (SHARED_SAMPLERS) {
            sampler = SHARED_SAMPLERS.remove(level);
        }
        if (sampler != null) {
            sampler.closeInternal();
        }
    }

    public static void clearAll() {
        List<AccurateHeightSampler> samplers;
        synchronized (SHARED_SAMPLERS) {
            samplers = new ArrayList<>(SHARED_SAMPLERS.values());
            SHARED_SAMPLERS.clear();
        }
        for (AccurateHeightSampler sampler : samplers) {
            sampler.closeInternal();
        }
    }

    private AccurateHeightChunk chunkAt(int x, int z) {
        long key = ChunkPos.asLong(x >> 4, z >> 4);
        AccurateHeightChunk cached = getCached(key);
        if (cached != null) {
            AccurateSamplingStats.recordCacheHit();
            return cached;
        }
        AccurateHeightChunk sampled = sampleChunks(List.of(key)).get(key);
        if (sampled == null) {
            throw new IllegalStateException("Accurate height backend returned no chunk for " + (x >> 4) + "," + (z >> 4));
        }
        return sampled;
    }

    private Map<Long, AccurateHeightSample> samplePositionsInternal(Collection<BlockPos> positions,
                                                                    AccurateSamplingProgress progress,
                                                                    boolean recordStats,
                                                                    boolean acceleratedOnly) {
        if (positions == null || positions.isEmpty()) {
            return Map.of();
        }
        ensureOpen();

        LinkedHashMap<Long, BlockPos> unique = uniquePositions(positions);
        if (unique.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<Long, AccurateHeightSample> result = new LinkedHashMap<>(unique.size());
        LinkedHashMap<Long, BlockPos> unresolved = new LinkedHashMap<>(unique.size());

        for (Map.Entry<Long, BlockPos> entry : unique.entrySet()) {
            long key = entry.getKey();
            BlockPos position = entry.getValue();
            AccurateHeightSample cached = cachedSample(key, position.getX(), position.getZ());
            if (cached != null) {
                if (recordStats) {
                    AccurateSamplingStats.recordColumnCacheHit();
                }
                result.put(key, cached);
                continue;
            }
            if (recordStats) {
                AccurateSamplingStats.recordColumnCacheMiss();
            }
            unresolved.put(key, position);
        }

        resolveStoredColumns(unresolved, result);
        ConcurrentHashMap<Long, CompletableFuture<Void>> coalescer = acceleratedOnly
                ? acceleratedTileInFlight
                : tileInFlight;
        while (!unresolved.isEmpty()) {
            LinkedHashMap<Long, LinkedHashMap<Long, BlockPos>> byTile = groupColumnsByTile(unresolved);
            LinkedHashMap<Long, CompletableFuture<Void>> owned = new LinkedHashMap<>();
            LinkedHashMap<Long, CompletableFuture<Void>> waiting = new LinkedHashMap<>();
            synchronized (coalescer) {
                for (long tileKey : byTile.keySet()) {
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    CompletableFuture<Void> existing = coalescer.putIfAbsent(tileKey, future);
                    if (existing == null) {
                        owned.put(tileKey, future);
                    } else {
                        waiting.put(tileKey, existing);
                    }
                }
            }
            if (!owned.isEmpty()) {
                completeOwnedColumnTiles(
                        owned, byTile, unresolved, result, progress, acceleratedOnly, coalescer);
            }
            joinWaitingTiles(waiting);
            resolveCachedColumns(unresolved, result);
            resolveStoredColumns(unresolved, result);
        }
        return orderedSamples(unique.keySet(), result);
    }

    private void completeOwned(Map<Long, CompletableFuture<AccurateHeightChunk>> owned,
                               Map<Long, AccurateHeightChunk> result) {
        try {
            Map<Long, AccurateHeightChunk> sampled = backend.sampleChunks(owned.keySet());
            LinkedHashMap<Long, AccurateHeightChunk> verified = new LinkedHashMap<>(owned.size());
            for (Map.Entry<Long, CompletableFuture<AccurateHeightChunk>> entry : owned.entrySet()) {
                long key = entry.getKey();
                AccurateHeightChunk chunk = sampled.get(key);
                if (chunk == null) {
                    throw new IllegalStateException("Missing accurate height chunk " + ChunkPos.getX(key) + "," + ChunkPos.getZ(key));
                }
                verified.put(key, chunk);
            }
            store.saveChunks(verified);
            for (Map.Entry<Long, CompletableFuture<AccurateHeightChunk>> entry : owned.entrySet()) {
                long key = entry.getKey();
                AccurateHeightChunk chunk = verified.get(key);
                putCached(key, chunk);
                result.put(key, chunk);
                entry.getValue().complete(chunk);
            }
        } catch (Throwable failure) {
            for (CompletableFuture<AccurateHeightChunk> future : owned.values()) {
                future.completeExceptionally(failure);
            }
            throw failure;
        } finally {
            for (Map.Entry<Long, CompletableFuture<AccurateHeightChunk>> entry : owned.entrySet()) {
                inFlight.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void joinWaiting(Map<Long, CompletableFuture<AccurateHeightChunk>> waiting,
                                    Map<Long, AccurateHeightChunk> result) {
        for (Map.Entry<Long, CompletableFuture<AccurateHeightChunk>> entry : waiting.entrySet()) {
            result.put(entry.getKey(), entry.getValue().join());
        }
    }

    private void completeOwnedColumnTiles(
            Map<Long, CompletableFuture<Void>> owned,
            Map<Long, LinkedHashMap<Long, BlockPos>> positionsByTile,
            Map<Long, BlockPos> unresolved,
            Map<Long, AccurateHeightSample> result,
            AccurateSamplingProgress progress,
            boolean acceleratedOnly,
            ConcurrentHashMap<Long, CompletableFuture<Void>> coalescer) {
        try {
            LinkedHashMap<Long, BlockPos> requestedByKey = new LinkedHashMap<>();
            for (long tileKey : owned.keySet()) {
                requestedByKey.putAll(positionsByTile.get(tileKey));
            }
            AccurateSamplingProgress sink = progress == null ? AccurateSamplingProgress.NONE : progress;
            Map<Long, AccurateHeightSample> sampled = acceleratedOnly
                    ? backend.sampleAcceleratedPositions(requestedByKey.values(), sink)
                    : backend.samplePositions(requestedByKey.values(), sink);
            LinkedHashMap<Long, AccurateHeightSample> verified = new LinkedHashMap<>(requestedByKey.size());
            for (Map.Entry<Long, BlockPos> entry : requestedByKey.entrySet()) {
                long key = entry.getKey();
                AccurateHeightSample sample = sampled.get(key);
                if (sample == null) {
                    BlockPos position = entry.getValue();
                    throw new IllegalStateException("Missing accurate height column " + position.getX() + "," + position.getZ());
                }
                verified.put(key, sample);
            }
            store.saveSamples(verified);
            for (Map.Entry<Long, AccurateHeightSample> entry : verified.entrySet()) {
                long key = entry.getKey();
                AccurateHeightSample sample = entry.getValue();
                putColumnCached(key, sample);
                result.put(key, sample);
                unresolved.remove(key);
            }
            owned.values().forEach(future -> future.complete(null));
        } catch (Throwable failure) {
            for (CompletableFuture<Void> future : owned.values()) {
                future.completeExceptionally(failure);
            }
            throw failure;
        } finally {
            for (Map.Entry<Long, CompletableFuture<Void>> entry : owned.entrySet()) {
                coalescer.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void joinWaitingTiles(Map<Long, CompletableFuture<Void>> waiting) {
        for (CompletableFuture<Void> future : waiting.values()) {
            try {
                future.join();
            } catch (CancellationException cancelled) {
                throw cancelled;
            } catch (CompletionException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof CancellationException cancelled) throw cancelled;
                if (cause instanceof RuntimeException runtime) throw runtime;
                throw failure;
            }
        }
    }

    private AccurateHeightGrid sampleWholeGridWithTileLease(AccurateHeightGridRequest request,
                                                            AccurateSamplingProgress progress,
                                                            boolean acceleratedOnly) {
        ConcurrentHashMap<Long, CompletableFuture<Void>> coalescer = acceleratedOnly
                ? acceleratedTileInFlight
                : tileInFlight;
        LinkedHashSet<Long> tileKeys = new LinkedHashSet<>();
        for (int index = 0; index < request.sampleCount(); index++) {
            tileKeys.add(tileKey(request.blockX(index), request.blockZ(index)));
        }
        LinkedHashMap<Long, CompletableFuture<Void>> owned = new LinkedHashMap<>();
        LinkedHashMap<Long, CompletableFuture<Void>> waiting = new LinkedHashMap<>();
        synchronized (coalescer) {
            for (long tileKey : tileKeys) {
                CompletableFuture<Void> existing = coalescer.get(tileKey);
                if (existing != null) waiting.put(tileKey, existing);
            }
            if (waiting.isEmpty()) {
                for (long tileKey : tileKeys) {
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    coalescer.put(tileKey, future);
                    owned.put(tileKey, future);
                }
            }
        }
        if (!waiting.isEmpty()) {
            joinWaitingTiles(waiting);
            return sampleGridInternal(request, progress, acceleratedOnly);
        }
        try {
            AccurateHeightGrid sampled = acceleratedOnly
                    ? backend.sampleAcceleratedGrid(request, progress)
                    : backend.sampleGrid(request, progress);
            cacheGridColumns(sampled);
            store.saveGrid(sampled);
            owned.values().forEach(future -> future.complete(null));
            return sampled;
        } catch (Throwable failure) {
            owned.values().forEach(future -> future.completeExceptionally(failure));
            throw failure;
        } finally {
            synchronized (coalescer) {
                for (Map.Entry<Long, CompletableFuture<Void>> entry : owned.entrySet()) {
                    coalescer.remove(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private AccurateHeightChunk getCached(long key) {
        synchronized (cacheLock) {
            return chunkCache.get(key);
        }
    }

    private void putCached(long key, AccurateHeightChunk chunk) {
        synchronized (cacheLock) {
            chunkCache.put(key, chunk);
        }
    }

    private AccurateHeightSample cachedSample(long key, int blockX, int blockZ) {
        synchronized (cacheLock) {
            AccurateHeightSample sample = columnCache.get(key);
            if (sample != null) {
                return sample;
            }
            AccurateHeightChunk chunk = chunkCache.get(ChunkPos.asLong(blockX >> 4, blockZ >> 4));
            if (chunk == null) {
                return null;
            }
            AccurateHeightSample fromChunk = sampleFromChunk(chunk, blockX, blockZ);
            columnCache.put(key, fromChunk);
            return fromChunk;
        }
    }

    private void putColumnCached(long key, AccurateHeightSample sample) {
        synchronized (cacheLock) {
            columnCache.put(key, sample);
        }
    }

    public int surfaceHeight(AccurateHeightSample sample) {
        return sample.motionBlockingNoLeaves() > seaLevel + 2
                ? sample.motionBlockingNoLeaves()
                : sample.worldSurfaceWg();
    }

    private static LinkedHashMap<Long, BlockPos> uniquePositions(Collection<BlockPos> positions) {
        LinkedHashMap<Long, BlockPos> unique = new LinkedHashMap<>();
        for (BlockPos position : positions) {
            if (position != null) {
                unique.putIfAbsent(AccurateHeightSample.key(position.getX(), position.getZ()), position);
            }
        }
        return unique;
    }

    private static LinkedHashMap<Long, LinkedHashMap<Long, BlockPos>> groupColumnsByTile(
            Map<Long, BlockPos> positions) {
        LinkedHashMap<Long, LinkedHashMap<Long, BlockPos>> grouped = new LinkedHashMap<>();
        for (Map.Entry<Long, BlockPos> entry : positions.entrySet()) {
            BlockPos position = entry.getValue();
            long tileKey = tileKey(position.getX(), position.getZ());
            grouped.computeIfAbsent(tileKey, ignored -> new LinkedHashMap<>())
                    .put(entry.getKey(), position);
        }
        return grouped;
    }

    private static long tileKey(int blockX, int blockZ) {
        return ChunkPos.asLong(Math.floorDiv(blockX, 256), Math.floorDiv(blockZ, 256));
    }

    private void resolveCachedColumns(LinkedHashMap<Long, BlockPos> unresolved,
                                      Map<Long, AccurateHeightSample> result) {
        Iterator<Map.Entry<Long, BlockPos>> iterator = unresolved.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, BlockPos> entry = iterator.next();
            BlockPos position = entry.getValue();
            AccurateHeightSample sample = cachedSample(entry.getKey(), position.getX(), position.getZ());
            if (sample == null) continue;
            result.put(entry.getKey(), sample);
            iterator.remove();
        }
    }

    private static List<BlockPos> interpolatePositions(List<BlockPos> path, int divisor) {
        if (divisor <= 1 || path.size() == 1) {
            return new ArrayList<>(path);
        }
        List<BlockPos> result = new ArrayList<>((path.size() - 1) * divisor + 1);
        for (int i = 0; i < path.size() - 1; i++) {
            BlockPos start = path.get(i);
            BlockPos end = path.get(i + 1);
            for (int part = 0; part < divisor; part++) {
                double t = (double) part / divisor;
                int x = (int) Math.round(start.getX() + (end.getX() - start.getX()) * t);
                int z = (int) Math.round(start.getZ() + (end.getZ() - start.getZ()) * t);
                result.add(new BlockPos(x, 0, z));
            }
        }
        BlockPos last = path.get(path.size() - 1);
        result.add(new BlockPos(last.getX(), 0, last.getZ()));
        return result;
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Accurate height sampler is closed");
        }
    }

    private void resolveStoredChunks(LinkedHashSet<Long> unresolved,
                                     Map<Long, AccurateHeightChunk> result) {
        if (unresolved.isEmpty()) {
            return;
        }
        Map<Long, AccurateHeightChunk> stored = store.loadChunks(unresolved);
        if (stored.isEmpty()) {
            return;
        }
        Iterator<Long> iterator = unresolved.iterator();
        while (iterator.hasNext()) {
            long key = iterator.next();
            AccurateHeightChunk chunk = stored.get(key);
            if (chunk == null) {
                continue;
            }
            putCached(key, chunk);
            result.put(key, chunk);
            iterator.remove();
        }
    }

    private void resolveStoredColumns(LinkedHashMap<Long, BlockPos> unresolved,
                                      Map<Long, AccurateHeightSample> result) {
        if (unresolved.isEmpty()) {
            return;
        }
        Map<Long, AccurateHeightSample> stored = store.loadSamples(unresolved.keySet());
        if (stored.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Long, BlockPos>> iterator = unresolved.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, BlockPos> entry = iterator.next();
            AccurateHeightSample sample = stored.get(entry.getKey());
            if (sample == null) {
                continue;
            }
            putColumnCached(entry.getKey(), sample);
            result.put(entry.getKey(), sample);
            iterator.remove();
        }
    }

    private void fillGridColumn(int[] worldSurface,
                                int[] oceanFloor,
                                int[] motionBlocking,
                                int index,
                                AccurateHeightSample sample) {
        worldSurface[index] = sample.worldSurfaceWg();
        oceanFloor[index] = sample.oceanFloorWg();
        motionBlocking[index] = sample.motionBlockingNoLeaves();
    }

    private void cacheGridColumns(AccurateHeightGrid grid) {
        for (int index = 0; index < grid.request().sampleCount(); index++) {
            putColumnCached(
                    AccurateHeightSample.key(grid.request().blockX(index), grid.request().blockZ(index)),
                    new AccurateHeightSample(
                            grid.worldSurface()[index],
                            grid.oceanFloor()[index],
                            grid.motionBlocking()[index]));
        }
    }

    private static AccurateHeightSample sampleFromChunk(AccurateHeightChunk chunk, int blockX, int blockZ) {
        return new AccurateHeightSample(
                chunk.worldSurfaceWg(blockX, blockZ),
                chunk.oceanFloorWg(blockX, blockZ),
                chunk.motionBlockingNoLeaves(blockX, blockZ));
    }

    private static Map<Long, AccurateHeightChunk> orderedChunks(Collection<Long> order,
                                                                Map<Long, AccurateHeightChunk> chunks) {
        LinkedHashMap<Long, AccurateHeightChunk> ordered = new LinkedHashMap<>(order.size());
        for (long key : order) {
            AccurateHeightChunk chunk = chunks.get(key);
            if (chunk != null) {
                ordered.put(key, chunk);
            }
        }
        return Collections.unmodifiableMap(ordered);
    }

    private static Map<Long, AccurateHeightSample> orderedSamples(Collection<Long> order,
                                                                  Map<Long, AccurateHeightSample> samples) {
        LinkedHashMap<Long, AccurateHeightSample> ordered = new LinkedHashMap<>(order.size());
        for (long key : order) {
            AccurateHeightSample sample = samples.get(key);
            if (sample != null) {
                ordered.put(key, sample);
            }
        }
        return Collections.unmodifiableMap(ordered);
    }
}
