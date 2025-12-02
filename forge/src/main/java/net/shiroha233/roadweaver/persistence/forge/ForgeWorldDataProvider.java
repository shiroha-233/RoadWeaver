package net.shiroha233.roadweaver.persistence.forge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.structures.model.StructureInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;

/**
 * Forge 端世界数据提供者实现，使用 SavedData 在 ServerLevel 持久化存储。
 */
public class ForgeWorldDataProvider extends WorldDataProvider {

    private static final String DATA_NAME = "roadweaver_world_data";

    /**
     * 实际持久化的数据容器。
     * 保存结构位置、结构连接。
     */
    public static class Data extends SavedData {
        private Records.StructureLocationData structureLocations = new Records.StructureLocationData(new ArrayList<>());
        private List<Records.StructureConnection> connections = new ArrayList<>();
        private List<StructureInstance> structureInstances = new ArrayList<>();
        private Set<Long> plannedTileKeys = new HashSet<>();
        private Map<Long, Long> plannedTileCenters = new HashMap<>();

        // NBT 字段名
        private static final String KEY_LOCATIONS = "structure_locations";
        private static final String KEY_CONNECTIONS = "connections";
        private static final String KEY_INSTANCES = "structure_instances";
        private static final String KEY_PLANNED_TILES = "planned_tiles";
        private static final String KEY_PLANNED_TILE_CENTERS = "planned_tile_centers";

        public Data() {}

        public static Data load(CompoundTag tag) {
            Data data = new Data();
            DynamicOps<Tag> ops = NbtOps.INSTANCE;

            // 结构位置（从 CompoundTag 读取）
            if (tag.contains(KEY_LOCATIONS)) {
                Tag locTag = tag.get(KEY_LOCATIONS);
                DataResult<Records.StructureLocationData> res = Records.StructureLocationData.CODEC.parse(new Dynamic<>(ops, locTag));
                res.result().ifPresent(val -> data.structureLocations = val);
            }

            // 结构连接（从 ListTag 读取）
            if (tag.contains(KEY_CONNECTIONS)) {
                Tag conTag = tag.get(KEY_CONNECTIONS);
                DataResult<List<Records.StructureConnection>> res = Codec.list(Records.StructureConnection.CODEC).parse(new Dynamic<>(ops, conTag));
                res.result().ifPresent(val -> data.connections = val);
            }

            // 结构实例（从 ListTag 读取）
            if (tag.contains(KEY_INSTANCES)) {
                Tag instTag = tag.get(KEY_INSTANCES);
                DataResult<List<StructureInstance>> res = StructureInstance.CODEC.listOf().parse(new Dynamic<>(ops, instTag));
                res.result().ifPresent(val -> data.structureInstances = val);
            }

            // 遗留道路数据列表不再加载

            if (tag.contains(KEY_PLANNED_TILES)) {
                Tag t = tag.get(KEY_PLANNED_TILES);
                DataResult<List<Long>> res = Codec.list(Codec.LONG).parse(new Dynamic<>(ops, t));
                res.result().ifPresent(list -> data.plannedTileKeys = new HashSet<>(list));
            }

            if (tag.contains(KEY_PLANNED_TILE_CENTERS)) {
                Tag t = tag.get(KEY_PLANNED_TILE_CENTERS);
                DataResult<Map<Long, Long>> res = Codec.unboundedMap(Codec.LONG, Codec.LONG).parse(new Dynamic<>(ops, t));
                res.result().ifPresent(map -> data.plannedTileCenters = map);
            }

            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            Objects.requireNonNull(tag);
            DynamicOps<Tag> ops = NbtOps.INSTANCE;

            // 结构位置（Record 编码为 CompoundTag）
            Records.StructureLocationData.CODEC.encodeStart(ops, structureLocations)
                    .result()
                    .ifPresent(nbt -> tag.put(KEY_LOCATIONS, Objects.requireNonNull(nbt)));

            // 结构连接（List 编码为 ListTag）
            Codec.list(Records.StructureConnection.CODEC).encodeStart(ops, connections)
                    .result()
                    .ifPresent(nbt -> tag.put(KEY_CONNECTIONS, Objects.requireNonNull(nbt)));

            // 结构实例列表
            StructureInstance.CODEC.listOf().encodeStart(ops, structureInstances)
                    .result()
                    .ifPresent(nbt -> tag.put(KEY_INSTANCES, Objects.requireNonNull(nbt)));

            // 遗留道路数据列表不再保存

            Codec.list(Codec.LONG).encodeStart(ops, new java.util.ArrayList<>(plannedTileKeys))
                    .result()
                    .ifPresent(nbt -> tag.put(KEY_PLANNED_TILES, Objects.requireNonNull(nbt)));

            Codec.unboundedMap(Codec.LONG, Codec.LONG).encodeStart(ops, plannedTileCenters)
                    .result()
                    .ifPresent(nbt -> tag.put(KEY_PLANNED_TILE_CENTERS, Objects.requireNonNull(nbt)));

            return tag;
        }

        // getters/setters
        public Records.StructureLocationData getStructureLocations() {
            return structureLocations;
        }

        public void setStructureLocations(Records.StructureLocationData data) {
            this.structureLocations = Objects.requireNonNullElseGet(data, () -> new Records.StructureLocationData(new ArrayList<>()));
            setDirty();
        }

        public List<Records.StructureConnection> getConnections() {
            return connections;
        }

        public void setConnections(List<Records.StructureConnection> connections) {
            this.connections = Objects.requireNonNullElseGet(connections, ArrayList::new);
            setDirty();
        }

        public List<StructureInstance> getStructureInstances() {
            return structureInstances;
        }

        public void setStructureInstances(List<StructureInstance> instances) {
            this.structureInstances = Objects.requireNonNullElseGet(instances, ArrayList::new);
            setDirty();
        }

        // 遗留道路数据列表访问器已移除

        public Set<Long> getPlannedTileKeys() {
            return plannedTileKeys;
        }

        public void setPlannedTileKeys(Set<Long> keys) {
            this.plannedTileKeys = Objects.requireNonNullElseGet(keys, HashSet::new);
            setDirty();
        }

        public Map<Long, Long> getPlannedTileCenters() {
            return plannedTileCenters;
        }

        public void setPlannedTileCenters(Map<Long, Long> centers) {
            this.plannedTileCenters = Objects.requireNonNullElseGet(centers, HashMap::new);
            setDirty();
        }
    }

    private Data getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Data::load, Data::new, DATA_NAME);
    }

    @Override
    public Records.StructureLocationData getStructureLocations(ServerLevel level) {
        return getOrCreate(level).getStructureLocations();
    }

    @Override
    public void setStructureLocations(ServerLevel level, Records.StructureLocationData data) {
        getOrCreate(level).setStructureLocations(data);
    }

    @Override
    public List<Records.StructureConnection> getStructureConnections(ServerLevel level) {
        return getOrCreate(level).getConnections();
    }

    @Override
    public void setStructureConnections(ServerLevel level, List<Records.StructureConnection> connections) {
        getOrCreate(level).setConnections(connections);
    }

    // 遗留道路数据列表重载已移除

    @Override
    public Set<Long> getPlannedTileKeys(ServerLevel level) {
        return getOrCreate(level).getPlannedTileKeys();
    }

    @Override
    public void setPlannedTileKeys(ServerLevel level, Set<Long> keys) {
        getOrCreate(level).setPlannedTileKeys(keys);
    }

    @Override
    public Map<Long, Long> getPlannedTileCenters(ServerLevel level) {
        return getOrCreate(level).getPlannedTileCenters();
    }

    @Override
    public void setPlannedTileCenters(ServerLevel level, Map<Long, Long> centers) {
        getOrCreate(level).setPlannedTileCenters(centers);
    }

    @Override
    public List<StructureInstance> getStructureInstances(ServerLevel level) {
        return getOrCreate(level).getStructureInstances();
    }

    @Override
    public void setStructureInstances(ServerLevel level, List<StructureInstance> instances) {
        getOrCreate(level).setStructureInstances(instances);
    }
}