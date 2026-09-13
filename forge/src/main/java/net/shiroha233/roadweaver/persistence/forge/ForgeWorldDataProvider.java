package net.shiroha233.roadweaver.persistence.forge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ForgeWorldDataProvider extends WorldDataProvider {
    private static final String DATA_NAME = "roadweaver_world_data";

    public static class Data extends SavedData {
        private static final String KEY_LOCATIONS = "structure_locations";
        private static final String KEY_CONNECTIONS = "connections";
        private static final String KEY_PLANNED_TILES = "planned_tiles";
        private static final String KEY_PLANNED_TILE_CENTERS = "planned_tile_centers";
        private static final Codec<Map<Long, Long>> LONG_KEY_MAP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG)
                .xmap(Data::parseLongKeyMap, Data::stringifyLongKeyMap);

        private StructureLocationData structureLocations = emptyStructureLocations();
        private List<StructureConnection> connections = new ArrayList<>();
        private Set<Long> plannedTileKeys = new HashSet<>();
        private Map<Long, Long> plannedTileCenters = new HashMap<>();

        public Data() {
        }

        public static Data load(CompoundTag tag) {
            Data data = new Data();
            DynamicOps<Tag> ops = NbtOps.INSTANCE;

            if (tag.contains(KEY_LOCATIONS)) {
                DataResult<StructureLocationData> result = StructureLocationData.CODEC.parse(
                        new Dynamic<>(ops, tag.get(KEY_LOCATIONS)));
                result.result().ifPresent(value -> data.structureLocations = value);
            }
            if (tag.contains(KEY_CONNECTIONS)) {
                DataResult<List<StructureConnection>> result = Codec.list(StructureConnection.CODEC).parse(
                        new Dynamic<>(ops, tag.get(KEY_CONNECTIONS)));
                result.result().ifPresent(value -> data.connections = value);
            }
            if (tag.contains(KEY_PLANNED_TILES)) {
                DataResult<List<Long>> result = Codec.list(Codec.LONG).parse(
                        new Dynamic<>(ops, tag.get(KEY_PLANNED_TILES)));
                result.result().ifPresent(value -> data.plannedTileKeys = new HashSet<>(value));
            }
            if (tag.contains(KEY_PLANNED_TILE_CENTERS)) {
                DataResult<Map<Long, Long>> result = LONG_KEY_MAP_CODEC.parse(
                        new Dynamic<>(ops, tag.get(KEY_PLANNED_TILE_CENTERS)));
                result.result().ifPresent(value -> data.plannedTileCenters = value);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            StructureLocationData.CODEC.encodeStart(NbtOps.INSTANCE, structureLocations)
                    .result()
                    .ifPresent(value -> tag.put(KEY_LOCATIONS, value));
            Codec.list(StructureConnection.CODEC).encodeStart(NbtOps.INSTANCE, connections)
                    .result()
                    .ifPresent(value -> tag.put(KEY_CONNECTIONS, value));
            Codec.list(Codec.LONG).encodeStart(NbtOps.INSTANCE, new ArrayList<>(plannedTileKeys))
                    .result()
                    .ifPresent(value -> tag.put(KEY_PLANNED_TILES, value));
            LONG_KEY_MAP_CODEC.encodeStart(NbtOps.INSTANCE, plannedTileCenters)
                    .result()
                    .ifPresent(value -> tag.put(KEY_PLANNED_TILE_CENTERS, value));
            return tag;
        }

        public StructureLocationData getStructureLocations() {
            return copyStructureLocations(structureLocations);
        }

        public void setStructureLocations(StructureLocationData data) {
            structureLocations = copyStructureLocations(Objects.requireNonNullElseGet(data,
                    ForgeWorldDataProvider::emptyStructureLocations));
            setDirty();
        }

        public List<StructureConnection> getConnections() {
            return new ArrayList<>(connections);
        }

        public Set<Long> getPlannedTileKeys() {
            return new HashSet<>(plannedTileKeys);
        }

        public Map<Long, Long> getPlannedTileCenters() {
            return new HashMap<>(plannedTileCenters);
        }

        private static Map<Long, Long> parseLongKeyMap(Map<String, Long> values) {
            Map<Long, Long> result = new HashMap<>();
            for (Map.Entry<String, Long> entry : values.entrySet()) {
                try {
                    result.put(Long.parseLong(entry.getKey()), entry.getValue());
                } catch (NumberFormatException ignored) {
                }
            }
            return result;
        }

        private static Map<String, Long> stringifyLongKeyMap(Map<Long, Long> values) {
            Map<String, Long> result = new HashMap<>();
            values.forEach((key, value) -> result.put(Long.toString(key), value));
            return result;
        }
    }

    private Data getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Data::load, Data::new, DATA_NAME);
    }

    @Override
    public StructureLocationData getStructureLocations(ServerLevel level) {
        StructureLocationData current = StructureFileStorage.getStructureLocations(level);
        if (hasStructureLocations(current)) {
            return current;
        }
        StructureLocationData legacy = getOrCreate(level).getStructureLocations();
        if (hasStructureLocations(legacy)) {
            StructureFileStorage.setStructureLocations(level, legacy);
            return StructureFileStorage.getStructureLocations(level);
        }
        return current;
    }

    @Override
    public void setStructureLocations(ServerLevel level, StructureLocationData data) {
        StructureFileStorage.setStructureLocations(level, data);
    }

    @Override
    public List<StructureConnection> getStructureConnections(ServerLevel level) {
        List<StructureConnection> current = StructureFileStorage.getStructureConnections(level);
        if (!current.isEmpty()) {
            return current;
        }
        List<StructureConnection> legacy = getOrCreate(level).getConnections();
        if (!legacy.isEmpty()) {
            StructureFileStorage.setStructureConnections(level, legacy);
            return StructureFileStorage.getStructureConnections(level);
        }
        return current;
    }

    @Override
    public void setStructureConnections(ServerLevel level, List<StructureConnection> connections) {
        StructureFileStorage.setStructureConnections(level, connections);
    }

    @Override
    public Set<Long> getPlannedTileKeys(ServerLevel level) {
        Set<Long> current = StructureFileStorage.getPlannedTileKeys(level);
        if (!current.isEmpty()) {
            return current;
        }
        Set<Long> legacy = getOrCreate(level).getPlannedTileKeys();
        if (!legacy.isEmpty()) {
            StructureFileStorage.setPlannedTileKeys(level, legacy);
            return StructureFileStorage.getPlannedTileKeys(level);
        }
        return current;
    }

    @Override
    public void setPlannedTileKeys(ServerLevel level, Set<Long> keys) {
        StructureFileStorage.setPlannedTileKeys(level, keys);
    }

    @Override
    public Map<Long, Long> getPlannedTileCenters(ServerLevel level) {
        Map<Long, Long> current = StructureFileStorage.getPlannedTileCenters(level);
        if (!current.isEmpty()) {
            return current;
        }
        Map<Long, Long> legacy = getOrCreate(level).getPlannedTileCenters();
        if (!legacy.isEmpty()) {
            StructureFileStorage.setPlannedTileCenters(level, legacy);
            return StructureFileStorage.getPlannedTileCenters(level);
        }
        return current;
    }

    @Override
    public void setPlannedTileCenters(ServerLevel level, Map<Long, Long> centers) {
        StructureFileStorage.setPlannedTileCenters(level, centers);
    }

    private static StructureLocationData emptyStructureLocations() {
        return new StructureLocationData(new ArrayList<>(), new ArrayList<>());
    }

    private static StructureLocationData copyStructureLocations(StructureLocationData data) {
        return new StructureLocationData(data.structureLocations(), data.structureInfos());
    }

    private static boolean hasStructureLocations(StructureLocationData data) {
        return data != null && (!data.structureLocations().isEmpty() || !data.structureInfos().isEmpty());
    }
}
