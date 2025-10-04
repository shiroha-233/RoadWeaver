package net.countered.settlementroads.fabric.persistence;

import com.mojang.serialization.Codec;
import net.countered.settlementroads.RoadWeaver;
import net.countered.settlementroads.helpers.Records;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Fabric数据附件注册
 */
public class FabricWorldDataAttachment {
    
    public static final AttachmentType<Records.StructureLocationData> STRUCTURE_LOCATIONS;
    public static final AttachmentType<List<Records.StructureConnection>> CONNECTED_STRUCTURES;
    public static final AttachmentType<List<Records.RoadData>> ROAD_DATA_LIST;
    
    static {
        // 静态初始化块确保在类加载时就注册所有附件
        STRUCTURE_LOCATIONS = AttachmentRegistry.createPersistent(
                Identifier.of(RoadWeaver.MOD_ID, "village_locations"),
                Records.StructureLocationData.CODEC
        );
        
        CONNECTED_STRUCTURES = AttachmentRegistry.createPersistent(
                Identifier.of(RoadWeaver.MOD_ID, "connected_villages"),
                Codec.list(Records.StructureConnection.CODEC)
        );
        
        ROAD_DATA_LIST = AttachmentRegistry.createPersistent(
                Identifier.of(RoadWeaver.MOD_ID, "road_chunk_data_map"),
                Codec.list(Records.RoadData.CODEC)
        );
    }
    
    public static void register() {
        // 触发静态初始化块
        RoadWeaver.getLogger().info("Registering Fabric WorldData attachments");
        RoadWeaver.getLogger().info("Registered attachments: STRUCTURE_LOCATIONS, CONNECTED_STRUCTURES, ROAD_DATA_LIST");
    }
}
