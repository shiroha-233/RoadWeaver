package net.countered.settlementroads.persistence.attachments;

import com.mojang.serialization.Codec;
import net.countered.settlementroads.SettlementRoads;
import net.countered.settlementroads.helpers.Records;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Forge 等效的世界数据存储实现。使用 {@link SavedData} 保存结构位置、结构连接以及道路数据。
 */
public final class WorldDataHelper {

    private WorldDataHelper() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SettlementRoads.MOD_ID);

    public static StructureLocationsData structureLocations(ServerLevel level) {
        return StructureLocationsData.get(level);
    }

    public static StructureConnectionsData structureConnections(ServerLevel level) {
        return StructureConnectionsData.get(level);
    }

    public static RoadDataStorage roadData(ServerLevel level) {
        return RoadDataStorage.get(level);
    }

    private static <T> void encodeList(CompoundTag tag, String key, Codec<List<T>> codec, List<T> value) {
        codec.encodeStart(NbtOps.INSTANCE, value).resultOrPartial(message ->
                LOGGER.error("Failed to encode {} for {}: {}", key, SettlementRoads.MOD_ID, message)
        ).ifPresent(encodedTag -> {
            if (encodedTag instanceof ListTag listTag) {
                tag.put(key, listTag);
            } else {
                LOGGER.error("Encoded tag for {} is not ListTag ({}).", key, encodedTag.getClass());
            }
        });
    }

    private static <T> List<T> decodeList(CompoundTag tag, String key, Codec<List<T>> codec) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            return new ArrayList<>();
        }
        return codec.parse(NbtOps.INSTANCE, tag.get(key)).resultOrPartial(message ->
                LOGGER.error("Failed to decode {} for {}: {}", key, SettlementRoads.MOD_ID, message)
        ).orElseGet(ArrayList::new);
    }

    /**
     * 保存结构位置数据。
     */
    public static class StructureLocationsData extends SavedData {
        private static final String STORAGE_KEY = "roadweaver_structure_locations";
        private static final String TAG_LOCATIONS = "locations";
        private static final Codec<List<BlockPos>> LOCATIONS_CODEC = BlockPos.CODEC.listOf();

        private final List<BlockPos> locations = new ArrayList<>();

        private StructureLocationsData() {
        }

        private StructureLocationsData(List<BlockPos> stored) {
            locations.addAll(stored);
        }

        public static StructureLocationsData get(ServerLevel level) {
            DimensionDataStorage storage = level.getDataStorage();
            return storage.computeIfAbsent(StructureLocationsData::load, StructureLocationsData::new, STORAGE_KEY);
        }

        private static StructureLocationsData load(CompoundTag tag) {
            return new StructureLocationsData(decodeList(tag, TAG_LOCATIONS, LOCATIONS_CODEC));
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            encodeList(tag, TAG_LOCATIONS, LOCATIONS_CODEC, locations);
            return tag;
        }

        public List<BlockPos> getLocations() {
            return locations;
        }

        public void add(BlockPos pos) {
            locations.add(pos);
            setDirty();
        }

        public void setLocations(List<BlockPos> newLocations) {
            locations.clear();
            locations.addAll(newLocations);
            setDirty();
        }
    }

    /**
     * 保存结构连接数据。
     */
    public static class StructureConnectionsData extends SavedData {
        private static final String STORAGE_KEY = "roadweaver_structure_connections";
        private static final String TAG_CONNECTIONS = "connections";
        private static final Codec<List<Records.StructureConnection>> CONNECTION_CODEC = Records.StructureConnection.CODEC.listOf();

        private final List<Records.StructureConnection> connections = new ArrayList<>();

        private StructureConnectionsData() {
        }

        private StructureConnectionsData(List<Records.StructureConnection> stored) {
            connections.addAll(stored);
        }

        public static StructureConnectionsData get(ServerLevel level) {
            DimensionDataStorage storage = level.getDataStorage();
            return storage.computeIfAbsent(StructureConnectionsData::load, StructureConnectionsData::new, STORAGE_KEY);
        }

        private static StructureConnectionsData load(CompoundTag tag) {
            return new StructureConnectionsData(decodeList(tag, TAG_CONNECTIONS, CONNECTION_CODEC));
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            encodeList(tag, TAG_CONNECTIONS, CONNECTION_CODEC, connections);
            return tag;
        }

        public List<Records.StructureConnection> getConnections() {
            return connections;
        }

        public void setConnections(List<Records.StructureConnection> newConnections) {
            connections.clear();
            connections.addAll(newConnections);
            setDirty();
        }

        public void addConnection(Records.StructureConnection connection) {
            connections.add(connection);
            setDirty();
        }
    }

    /**
     * 保存道路数据。
     */
    public static class RoadDataStorage extends SavedData {
        private static final String STORAGE_KEY = "roadweaver_road_data";
        private static final String TAG_ROADS = "roads";
        private static final Codec<List<Records.RoadData>> ROAD_CODEC = Records.RoadData.CODEC.listOf();

        private final List<Records.RoadData> roadData = new ArrayList<>();

        private RoadDataStorage() {
        }

        private RoadDataStorage(List<Records.RoadData> stored) {
            roadData.addAll(stored);
        }

        public static RoadDataStorage get(ServerLevel level) {
            DimensionDataStorage storage = level.getDataStorage();
            return storage.computeIfAbsent(RoadDataStorage::load, RoadDataStorage::new, STORAGE_KEY);
        }

        private static RoadDataStorage load(CompoundTag tag) {
            return new RoadDataStorage(decodeList(tag, TAG_ROADS, ROAD_CODEC));
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            encodeList(tag, TAG_ROADS, ROAD_CODEC, roadData);
            return tag;
        }

        public List<Records.RoadData> getRoadData() {
            return roadData;
        }

        public void setRoadData(List<Records.RoadData> newData) {
            roadData.clear();
            roadData.addAll(newData);
            setDirty();
        }

        public void addRoad(Records.RoadData data) {
            roadData.add(data);
            setDirty();
        }
    }
}
