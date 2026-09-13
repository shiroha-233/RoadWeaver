package net.shiroha233.roadweaver.structures.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;
import net.shiroha233.roadweaver.structures.precompute.PendingStructureStorage;
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure;

/**
 * 初始小屋放置器。
 */
public final class SpawnCabinPlacer {
    private SpawnCabinPlacer() {}
    
    private static final ResourceLocation STRUCTURE_ID = new ResourceLocation("roadweaver", "spawn_cabin");
    
    public static boolean ensurePlaced(ServerLevel level) {
        if (level == null) return false;
        
        BlockPos spawn = level.getSharedSpawnPos();
        
        var provider = WorldDataProvider.getInstance();
        var locs = provider.getStructureLocations(level);
        if (locs != null && locs.structureLocations() != null && !locs.structureLocations().isEmpty()) {
            return false;
        }
        
        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure structure = structureRegistry.get(STRUCTURE_ID);
        
        if (!(structure instanceof SpawnCabinStructure spawnCabin)) {
            return false;
        }
        
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        BlockPos anchor = new BlockPos(spawn.getX(), y, spawn.getZ());
        Vec3i sizeHint = spawnCabin.sizeHint();
        
        PendingStructureStorage.addPendingStructure(
            level,
            STRUCTURE_ID,
            anchor,
            Rotation.NONE,
            sizeHint.getX(),
            sizeHint.getY(),
            sizeHint.getZ()
        );
        
        provider.addStructureLocation(level, anchor);

        StructureFileStorage.addStructures(
                level,
                java.util.List.of(new StructureInfo(new BlockPos(anchor.getX(), 0, anchor.getZ()), STRUCTURE_ID.toString())),
                StructureFileStorage.SOURCE_MANUAL
        );
        
        return true;
    }
}
