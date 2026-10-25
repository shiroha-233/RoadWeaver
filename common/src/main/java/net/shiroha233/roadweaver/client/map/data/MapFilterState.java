package net.shiroha233.roadweaver.client.map.data;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.map.search.MapStructureSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MapFilterState {
    private final EnumSet<ConnectionStatus> statuses = EnumSet.allOf(ConnectionStatus.class);
    private final EnumSet<MapStructureSource> sources = EnumSet.allOf(MapStructureSource.class);
    private final LinkedHashSet<String> structureTypes = new LinkedHashSet<>();
    private int revision;

    public int revision() {
        return revision;
    }

    public boolean acceptsStatus(ConnectionStatus status) {
        return status == null || statuses.contains(status);
    }

    public boolean acceptsSource(MapStructureSource source) {
        return sources.contains(source == null ? MapStructureSource.UNKNOWN : source);
    }

    public boolean acceptsType(String type) {
        return structureTypes.isEmpty() || structureTypes.contains(normalizeType(type));
    }

    public boolean isStatusSelected(ConnectionStatus status) {
        return statuses.contains(status);
    }

    public boolean isSourceSelected(MapStructureSource source) {
        return sources.contains(source);
    }

    public boolean isTypeSelected(String type, Set<String> availableTypes) {
        return structureTypes.isEmpty() || structureTypes.contains(normalizeType(type));
    }

    public void toggleStatus(ConnectionStatus status) {
        if (status == null) return;
        toggle(statuses, status, ConnectionStatus.values().length);
    }

    public void toggleSource(MapStructureSource source) {
        if (source == null) return;
        toggle(sources, source, MapStructureSource.values().length);
    }

    public void toggleType(String type, Set<String> availableTypes) {
        if (type == null || availableTypes == null || availableTypes.isEmpty()) return;
        String normalized = normalizeType(type);
        if (structureTypes.isEmpty()) structureTypes.addAll(availableTypes);
        if (!structureTypes.remove(normalized)) structureTypes.add(normalized);
        if (structureTypes.size() >= availableTypes.size()) structureTypes.clear();
        revision++;
    }

    public void reset() {
        statuses.clear();
        statuses.addAll(EnumSet.allOf(ConnectionStatus.class));
        sources.clear();
        sources.addAll(EnumSet.allOf(MapStructureSource.class));
        structureTypes.clear();
        revision++;
    }

    public Set<String> availableTypes(MapSnapshot snapshot) {
        LinkedHashSet<String> types = new LinkedHashSet<>();
        if (snapshot == null) return types;
        for (BlockPos pos : snapshot.structures()) {
            types.add(normalizeType(snapshot.structureName(pos)));
        }
        return types;
    }

    public MapSnapshot apply(MapSnapshot snapshot) {
        if (snapshot == null) return MapSnapshot.empty();
        List<BlockPos> structures = new ArrayList<>();
        List<StructureInfo> infos = new ArrayList<>();
        java.util.Map<BlockPos, Integer> sourceMap = new java.util.HashMap<>();
        HashSet<Long> allStructureKeys = new HashSet<>();
        HashSet<Long> visibleStructureKeys = new HashSet<>();
        for (BlockPos pos : snapshot.structures()) {
            long key = positionKey(pos);
            allStructureKeys.add(key);
            if (!acceptsType(snapshot.structureName(pos))
                    || !acceptsSource(snapshot.structureSourceType(pos))) continue;
            structures.add(pos);
            visibleStructureKeys.add(key);
            String name = snapshot.structureName(pos);
            if (name != null) infos.add(new StructureInfo(pos, name));
            sourceMap.put(pos, snapshot.structureSource(pos));
        }
        List<StructureConnection> connections = snapshot.connections().stream()
                .filter(connection -> acceptsStatus(connection.status()))
                .filter(connection -> endpointVisible(connection.from(), allStructureKeys, visibleStructureKeys)
                        && endpointVisible(connection.to(), allStructureKeys, visibleStructureKeys))
                .toList();
        List<List<BlockPos>> roads = acceptsStatus(ConnectionStatus.COMPLETED)
                ? snapshot.roadPolylines()
                : List.of();
        return new MapSnapshot(structures, connections, infos, roads, sourceMap);
    }

    private static <E extends Enum<E>> void toggle(EnumSet<E> values, E value, int allCount) {
        if (!values.remove(value)) values.add(value);
        if (values.isEmpty()) values.add(value);
    }

    private static String normalizeType(String type) {
        return type == null || type.isBlank() ? "unknown" : type;
    }

    private static boolean endpointVisible(BlockPos pos, Set<Long> all, Set<Long> visible) {
        long key = positionKey(pos);
        return !all.contains(key) || visible.contains(key);
    }

    private static long positionKey(BlockPos pos) {
        return (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL);
    }
}
