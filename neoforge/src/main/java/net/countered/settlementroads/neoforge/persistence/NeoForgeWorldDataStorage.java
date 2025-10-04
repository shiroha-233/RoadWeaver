package net.countered.settlementroads.neoforge.persistence;

import com.mojang.serialization.Codec;
import net.countered.settlementroads.RoadWeaver;
import net.countered.settlementroads.helpers.Records;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge世界数据存储实现
 * 使用SavedData系统进行持久化
 */
public class NeoForgeWorldDataStorage {
    
    private static final String STRUCTURE_LOCATIONS_KEY = RoadWeaver.MOD_ID + "_structure_locations";
    private static final String STRUCTURE_CONNECTIONS_KEY = RoadWeaver.MOD_ID + "_structure_connections";
    private static final String ROAD_DATA_LIST_KEY = RoadWeaver.MOD_ID + "_road_data_list";
    
    public static void register() {
        RoadWeaver.getLogger().info("Registering NeoForge WorldData storage");
    }
    
    public static Records.StructureLocationData getStructureLocations(ServerLevel world) {
        StructureLocationsSavedData data = getOrCreateData(world, STRUCTURE_LOCATIONS_KEY, 
                StructureLocationsSavedData::new, StructureLocationsSavedData.class);
        return data.data;
    }
    
    public static void setStructureLocations(ServerLevel world, Records.StructureLocationData data) {
        StructureLocationsSavedData savedData = getOrCreateData(world, STRUCTURE_LOCATIONS_KEY,
                StructureLocationsSavedData::new, StructureLocationsSavedData.class);
        savedData.data = data;
        savedData.setDirty();
    }
    
    public static List<Records.StructureConnection> getStructureConnections(ServerLevel world) {
        StructureConnectionsSavedData data = getOrCreateData(world, STRUCTURE_CONNECTIONS_KEY,
                StructureConnectionsSavedData::new, StructureConnectionsSavedData.class);
        return data.connections;
    }
    
    public static void setStructureConnections(ServerLevel world, List<Records.StructureConnection> connections) {
        StructureConnectionsSavedData savedData = getOrCreateData(world, STRUCTURE_CONNECTIONS_KEY,
                StructureConnectionsSavedData::new, StructureConnectionsSavedData.class);
        savedData.connections = new ArrayList<>(connections);
        savedData.setDirty();
    }
    
    public static List<Records.RoadData> getRoadDataList(ServerLevel world) {
        RoadDataListSavedData data = getOrCreateData(world, ROAD_DATA_LIST_KEY,
                RoadDataListSavedData::new, RoadDataListSavedData.class);
        return data.roadDataList;
    }
    
    public static void setRoadDataList(ServerLevel world, List<Records.RoadData> roadDataList) {
        RoadDataListSavedData savedData = getOrCreateData(world, ROAD_DATA_LIST_KEY,
                RoadDataListSavedData::new, RoadDataListSavedData.class);
        savedData.roadDataList = new ArrayList<>(roadDataList);
        savedData.setDirty();
    }
    
    @SuppressWarnings("unchecked")
    private static <T extends SavedData> T getOrCreateData(ServerLevel level, String key, 
                                                            SavedData.Factory<T> factory, Class<T> clazz) {
        return level.getDataStorage().computeIfAbsent(factory, key);
    }
    
    // SavedData实现类
    public static class StructureLocationsSavedData extends SavedData {
        public Records.StructureLocationData data = new Records.StructureLocationData(new ArrayList<>());
        
        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
            Records.StructureLocationData.CODEC.encodeStart(NbtOps.INSTANCE, data)
                    .resultOrPartial(RoadWeaver.getLogger()::error)
                    .ifPresent(nbt -> tag.put("data", nbt));
            return tag;
        }
        
        public static StructureLocationsSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
            StructureLocationsSavedData data = new StructureLocationsSavedData();
            if (tag.contains("data")) {
                Records.StructureLocationData.CODEC.parse(NbtOps.INSTANCE, tag.get("data"))
                        .resultOrPartial(RoadWeaver.getLogger()::error)
                        .ifPresent(parsed -> data.data = parsed);
            }
            return data;
        }
    }
    
    public static class StructureConnectionsSavedData extends SavedData {
        public List<Records.StructureConnection> connections = new ArrayList<>();
        
        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
            Codec.list(Records.StructureConnection.CODEC).encodeStart(NbtOps.INSTANCE, connections)
                    .resultOrPartial(RoadWeaver.getLogger()::error)
                    .ifPresent(nbt -> tag.put("connections", nbt));
            return tag;
        }
        
        public static StructureConnectionsSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
            StructureConnectionsSavedData data = new StructureConnectionsSavedData();
            if (tag.contains("connections")) {
                Codec.list(Records.StructureConnection.CODEC).parse(NbtOps.INSTANCE, tag.get("connections"))
                        .resultOrPartial(RoadWeaver.getLogger()::error)
                        .ifPresent(parsed -> data.connections = new ArrayList<>(parsed));
            }
            return data;
        }
    }
    
    public static class RoadDataListSavedData extends SavedData {
        public List<Records.RoadData> roadDataList = new ArrayList<>();
        
        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
            Codec.list(Records.RoadData.CODEC).encodeStart(NbtOps.INSTANCE, roadDataList)
                    .resultOrPartial(RoadWeaver.getLogger()::error)
                    .ifPresent(nbt -> tag.put("roadDataList", nbt));
            return tag;
        }
        
        public static RoadDataListSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
            RoadDataListSavedData data = new RoadDataListSavedData();
            if (tag.contains("roadDataList")) {
                Codec.list(Records.RoadData.CODEC).parse(NbtOps.INSTANCE, tag.get("roadDataList"))
                        .resultOrPartial(RoadWeaver.getLogger()::error)
                        .ifPresent(parsed -> data.roadDataList = new ArrayList<>(parsed));
            }
            return data;
        }
    }
}
