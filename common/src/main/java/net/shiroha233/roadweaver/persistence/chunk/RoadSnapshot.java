package net.shiroha233.roadweaver.persistence.chunk;

import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 查询线程只读此对象；写入时构造全新实例并通过 AtomicReference 发布。 */
public final class RoadSnapshot {
    private static final RoadSnapshot EMPTY = new RoadSnapshot(new LinkedHashMap<>());

    private final Map<Long, Entry> entries;
    private final Map<Long, long[]> chunkReferences;

    private RoadSnapshot(LinkedHashMap<Long, Entry> source) {
        LinkedHashMap<Long, Entry> copiedEntries = new LinkedHashMap<>(source.size());
        for (Map.Entry<Long, Entry> entry : source.entrySet()) {
            copiedEntries.put(entry.getKey(), entry.getValue());
        }
        this.entries = Collections.unmodifiableMap(copiedEntries);

        LinkedHashMap<Long, LongArrayBuilder> mutableChunks = new LinkedHashMap<>();
        for (Entry entry : copiedEntries.values()) {
            for (long chunkKey : entry.footprint().chunkKeys()) {
                mutableChunks.computeIfAbsent(chunkKey, ignored -> new LongArrayBuilder()).add(entry.fingerprint());
            }
        }
        LinkedHashMap<Long, long[]> frozenChunks = new LinkedHashMap<>(mutableChunks.size());
        for (Map.Entry<Long, LongArrayBuilder> entry : mutableChunks.entrySet()) {
            frozenChunks.put(entry.getKey(), entry.getValue().toArray());
        }
        this.chunkReferences = Collections.unmodifiableMap(frozenChunks);
    }

    public static RoadSnapshot empty() {
        return EMPTY;
    }

    public static RoadSnapshot from(Collection<RoadData> roads) {
        LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>();
        if (roads != null) {
            for (RoadData road : roads) {
                if (road == null) continue;
                RoadData frozen = freezeForStorage(road);
                long fingerprint = RoadFingerprint.compute(frozen);
                entries.put(fingerprint, new Entry(fingerprint, frozen, RoadFootprint.from(frozen)));
            }
        }
        return entries.isEmpty() ? EMPTY : new RoadSnapshot(entries);
    }

    public static RoadSnapshot fromEntries(Map<Long, RoadData> roads) {
        if (roads == null || roads.isEmpty()) return EMPTY;
        LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>();
        for (Map.Entry<Long, RoadData> source : roads.entrySet()) {
            RoadData road = source.getValue();
            if (road == null) continue;
            RoadData frozen = freezeForStorage(road);
            long fingerprint = source.getKey() == null ? RoadFingerprint.compute(frozen) : source.getKey();
            entries.put(fingerprint, new Entry(fingerprint, frozen, RoadFootprint.from(frozen)));
        }
        return entries.isEmpty() ? EMPTY : new RoadSnapshot(entries);
    }

    static RoadSnapshot fromEntryMap(LinkedHashMap<Long, Entry> entries) {
        return entries == null || entries.isEmpty() ? EMPTY : new RoadSnapshot(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<RoadData> all() {
        if (entries.isEmpty()) return List.of();
        ArrayList<RoadData> roads = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) roads.add(entry.road());
        return Collections.unmodifiableList(roads);
    }

    public RoadData byFingerprint(long fingerprint) {
        Entry entry = entries.get(fingerprint);
        return entry == null ? null : entry.road();
    }

    public List<RoadData> queryChunk(int chunkX, int chunkZ) {
        return queryChunk(RoadChunkKey.pack(chunkX, chunkZ));
    }

    public List<RoadData> queryChunk(long chunkKey) {
        long[] references = chunkReferences.get(chunkKey);
        if (references == null || references.length == 0) return List.of();
        ArrayList<RoadData> roads = new ArrayList<>(references.length);
        for (long fingerprint : references) {
            Entry entry = entries.get(fingerprint);
            if (entry != null) roads.add(entry.road());
        }
        return roads.isEmpty() ? List.of() : Collections.unmodifiableList(roads);
    }

    public List<RoadData> queryRect(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        if (entries.isEmpty()) return List.of();
        int minX = Math.min(minBlockX, maxBlockX);
        int maxX = Math.max(minBlockX, maxBlockX);
        int minZ = Math.min(minBlockZ, maxBlockZ);
        int maxZ = Math.max(minBlockZ, maxBlockZ);
        int firstChunkX = Math.floorDiv(minX, 16);
        int lastChunkX = Math.floorDiv(maxX, 16);
        int firstChunkZ = Math.floorDiv(minZ, 16);
        int lastChunkZ = Math.floorDiv(maxZ, 16);

        LinkedHashSet<Long> candidateFingerprints = new LinkedHashSet<>();
        for (long cx = firstChunkX; cx <= (long) lastChunkX; cx++) {
            for (long cz = firstChunkZ; cz <= (long) lastChunkZ; cz++) {
                long[] references = chunkReferences.get(RoadChunkKey.pack((int) cx, (int) cz));
                if (references == null) continue;
                for (long fingerprint : references) candidateFingerprints.add(fingerprint);
            }
        }
        if (candidateFingerprints.isEmpty()) return List.of();

        ArrayList<RoadData> roads = new ArrayList<>(candidateFingerprints.size());
        for (long fingerprint : candidateFingerprints) {
            Entry entry = entries.get(fingerprint);
            if (entry != null && entry.footprint().intersectsRect(minX, minZ, maxX, maxZ)) {
                roads.add(entry.road());
            }
        }
        return roads.isEmpty() ? List.of() : Collections.unmodifiableList(roads);
    }

    public boolean hasRoadInRect(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        if (entries.isEmpty()) return false;
        int minX = Math.min(minBlockX, maxBlockX);
        int maxX = Math.max(minBlockX, maxBlockX);
        int minZ = Math.min(minBlockZ, maxBlockZ);
        int maxZ = Math.max(minBlockZ, maxBlockZ);
        int firstChunkX = Math.floorDiv(minX, 16);
        int lastChunkX = Math.floorDiv(maxX, 16);
        int firstChunkZ = Math.floorDiv(minZ, 16);
        int lastChunkZ = Math.floorDiv(maxZ, 16);
        HashSet<Long> visited = new HashSet<>();
        for (long chunkX = firstChunkX; chunkX <= (long) lastChunkX; chunkX++) {
            for (long chunkZ = firstChunkZ; chunkZ <= (long) lastChunkZ; chunkZ++) {
                long[] references = chunkReferences.get(RoadChunkKey.pack((int) chunkX, (int) chunkZ));
                if (references == null) continue;
                for (long fingerprint : references) {
                    if (!visited.add(fingerprint)) continue;
                    Entry entry = entries.get(fingerprint);
                    if (entry != null && entry.footprint().intersectsRect(minX, minZ, maxX, maxZ)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Map<Long, Entry> entries() {
        return entries;
    }

    Map<Long, long[]> chunkReferences() {
        return chunkReferences;
    }

    public static final class Entry {
        private final long fingerprint;
        private final RoadData road;
        private final RoadFootprint footprint;
        private final String fileName;

        public Entry(long fingerprint, RoadData road, RoadFootprint footprint) {
            this(fingerprint, road, footprint, Long.toUnsignedString(fingerprint) + ".nbt");
        }

        public Entry(long fingerprint, RoadData road, RoadFootprint footprint, String fileName) {
            this.fingerprint = fingerprint;
            this.road = road;
            this.footprint = footprint;
            this.fileName = fileName == null || fileName.isBlank()
                    ? Long.toUnsignedString(fingerprint) + ".nbt"
                    : fileName;
        }

        public long fingerprint() {
            return fingerprint;
        }

        public RoadData road() {
            return road;
        }

        public RoadFootprint footprint() {
            return footprint;
        }

        public String fileName() {
            return fileName;
        }
    }

    static RoadData freezeForStorage(RoadData source) {
        List<RoadSegmentPlacement> sourceSegments = source.roadSegmentList();
        ArrayList<RoadSegmentPlacement> segments = new ArrayList<>(sourceSegments == null ? 0 : sourceSegments.size());
        if (sourceSegments != null) {
            for (RoadSegmentPlacement segment : sourceSegments) {
                if (segment == null) {
                    segments.add(null);
                } else {
                    segments.add(new RoadSegmentPlacement(segment.middlePos(), copyList(segment.positions())));
                }
            }
        }
        return new RoadData(
                source.width(),
                source.roadType(),
                copyList(source.materials()),
                copyList(source.slabMaterials()),
                Collections.unmodifiableList(segments),
                copyList(source.spans()),
                copyList(source.targetY()),
                source.ownerA2dKey(),
                source.ownerB2dKey());
    }

    private static <T> List<T> copyList(List<T> source) {
        if (source == null || source.isEmpty()) return List.of();
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static final class LongArrayBuilder {
        private long[] values = new long[4];
        private int size;

        void add(long value) {
            if (size == values.length) values = Arrays.copyOf(values, values.length * 2);
            values[size++] = value;
        }

        long[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
