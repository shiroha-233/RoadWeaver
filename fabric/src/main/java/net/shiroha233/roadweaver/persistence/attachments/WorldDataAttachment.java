package net.shiroha233.roadweaver.persistence.attachments;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.map.permission.MapAccessPolicy;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Fabric 世界数据附件注册
 */
public class WorldDataAttachment {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoadWeaver.MOD_ID);

    public static final AttachmentType<List<StructureConnection>> CONNECTED_STRUCTURES = AttachmentRegistry.createPersistent(
            new ResourceLocation(RoadWeaver.MOD_ID, "connected_villages"),
            Codec.list(StructureConnection.CODEC)
    );

    public static final AttachmentType<StructureLocationData> STRUCTURE_LOCATIONS = AttachmentRegistry.createPersistent(
            new ResourceLocation(RoadWeaver.MOD_ID, "village_locations"),
            StructureLocationData.CODEC
    );

    public static final AttachmentType<java.util.Set<Long>> PLANNED_TILE_KEYS = AttachmentRegistry.createPersistent(
            new ResourceLocation(RoadWeaver.MOD_ID, "planned_tiles"),
            Codec.list(Codec.LONG).xmap(HashSet::new, ArrayList::new)
    );

    public static final AttachmentType<java.util.Map<Long, Long>> PLANNED_TILE_CENTERS = AttachmentRegistry.createPersistent(
            new ResourceLocation(RoadWeaver.MOD_ID, "planned_tile_centers"),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).xmap(
                    m -> {
                        java.util.HashMap<Long, Long> out = new java.util.HashMap<>();
                        for (java.util.Map.Entry<String, Long> e : m.entrySet()) {
                            try {
                                out.put(Long.parseLong(e.getKey()), e.getValue());
                            } catch (NumberFormatException ignored) {}
                        }
                        return out;
                    },
                    m -> {
                        java.util.HashMap<String, Long> out = new java.util.HashMap<>();
                        for (java.util.Map.Entry<Long, Long> e : m.entrySet()) {
                            out.put(Long.toString(e.getKey()), e.getValue());
                        }
                        return out;
                    }
            )
    );

    public static final AttachmentType<MapAccessPolicy> MAP_ACCESS_POLICY = AttachmentRegistry.createPersistent(
            new ResourceLocation(RoadWeaver.MOD_ID, "map_access_policy"),
            MapAccessPolicy.CODEC
    );

    public static void registerWorldDataAttachment() {
        LOGGER.info("Registering WorldData attachment");
    }
}
