package net.shiroha233.roadweaver.structures.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.shiroha233.roadweaver.structures.types.RoadsideStructure;

import java.util.ArrayList;
import java.util.List;

/**
 * 路边结构注册中心
 */
public final class RoadsideStructureRegistry {
    private RoadsideStructureRegistry() {}
    
    private static volatile List<RoadsideStructureEntry> cache;
    
    public record RoadsideStructureEntry(
        ResourceLocation id,
        Holder<Structure> holder,
        RoadsideStructure structure
    ) {}
    
    public static List<RoadsideStructureEntry> getAll(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return List.of();
        List<RoadsideStructureEntry> current = cache;
        if (current != null) return current;
        synchronized (RoadsideStructureRegistry.class) {
            if (cache == null) cache = List.copyOf(loadFromRegistry(level.registryAccess()));
            return cache;
        }
    }
    
    private static List<RoadsideStructureEntry> loadFromRegistry(RegistryAccess registryAccess) {
        List<RoadsideStructureEntry> result = new ArrayList<>();
        
        Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        
        for (var entry : structureRegistry.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            Structure structure = entry.getValue();
            
            if (structure instanceof RoadsideStructure roadsideStructure) {
                Holder<Structure> holder = structureRegistry.getHolderOrThrow(entry.getKey());
                result.add(new RoadsideStructureEntry(id, holder, roadsideStructure));
            }
        }
        
        return result;
    }
    
    public static void clearCache() {
        cache = null;
    }
}
