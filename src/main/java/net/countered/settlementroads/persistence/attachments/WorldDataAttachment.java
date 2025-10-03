package net.countered.settlementroads.persistence.attachments;

import com.mojang.serialization.Codec;
import net.countered.settlementroads.SettlementRoads;
import net.countered.settlementroads.helpers.Records;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class WorldDataAttachment {
    private static final Logger LOGGER = LoggerFactory.getLogger(SettlementRoads.MOD_ID);

    public static final AttachmentType<List<Records.StructureConnection>> CONNECTED_STRUCTURES = AttachmentRegistry.createPersistent(
            Identifier.of(SettlementRoads.MOD_ID, "connected_villages"),
            Codec.list(Records.StructureConnection.CODEC)
    );


    public static final AttachmentType<Records.StructureLocationData> STRUCTURE_LOCATIONS = AttachmentRegistry.createPersistent(
            Identifier.of(SettlementRoads.MOD_ID, "village_locations"),
            Records.StructureLocationData.CODEC
    );

    public static final AttachmentType<List<Records.RoadData>> ROAD_DATA_LIST = AttachmentRegistry.createPersistent(
            Identifier.of(SettlementRoads.MOD_ID, "road_chunk_data_map"),
            Codec.list(Records.RoadData.CODEC)
    );

    public static void registerWorldDataAttachment() {
        LOGGER.info("Registering WorldData attachment");
    }
}
