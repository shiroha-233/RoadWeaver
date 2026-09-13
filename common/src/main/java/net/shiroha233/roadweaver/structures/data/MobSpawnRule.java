package net.shiroha233.roadweaver.structures.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 结构生物生成规则
 */
public record MobSpawnRule(
    ResourceLocation entityId,
    int countMin,
    int countMax,
    Vec3i offset,
    float chance
) {

    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver/MobSpawnRule");

    private static final Set<ResourceLocation> MISSING_ENTITY_LOGGED = ConcurrentHashMap.newKeySet();
    
    public static final Codec<MobSpawnRule> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceLocation.CODEC
                .fieldOf("entity")
                .forGetter(MobSpawnRule::entityId),
            Codec.INT.optionalFieldOf("count_min", 1)
                .forGetter(MobSpawnRule::countMin),
            Codec.INT.optionalFieldOf("count_max", 1)
                .forGetter(MobSpawnRule::countMax),
            Vec3i.CODEC.optionalFieldOf("offset", Vec3i.ZERO)
                .forGetter(MobSpawnRule::offset),
            Codec.FLOAT.optionalFieldOf("chance", 1.0f)
                .forGetter(MobSpawnRule::chance)
        ).apply(instance, MobSpawnRule::new)
    );
    
    public static final Codec<List<MobSpawnRule>> LIST_CODEC = CODEC.listOf();

    public Optional<EntityType<?>> resolveEntityType() {
        Optional<EntityType<?>> opt = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId);
        if (opt.isEmpty()) {
            if (MISSING_ENTITY_LOGGED.add(entityId)) {
                LOGGER.warn("Missing entity type {}, skipping spawn rule", entityId);
            }
        }
        return opt;
    }
    
    public int spawn(ServerLevelAccessor level, BlockPos anchorPos, RandomSource random) {
        if (chance < 1.0f && random.nextFloat() > chance) {
            return 0;
        }

        EntityType<?> resolvedType = resolveEntityType().orElse(null);
        if (resolvedType == null) {
            return 0;
        }
        
        int count = countMin;
        if (countMax > countMin) {
            count = countMin + random.nextInt(countMax - countMin + 1);
        }
        
        BlockPos spawnPos = anchorPos.offset(offset.getX(), offset.getY(), offset.getZ());
        
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double x = spawnPos.getX() + 0.5 + (random.nextDouble() - 0.5) * 2;
            double y = spawnPos.getY();
            double z = spawnPos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 2;
            
            Entity entity = resolvedType.create(level.getLevel());
            if (entity == null) {
                continue;
            }
            
            entity.moveTo(x, y, z, random.nextFloat() * 360.0f, 0.0f);
            
            if (entity instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), 
                    MobSpawnType.STRUCTURE, null, null);
                mob.setPersistenceRequired();
            }
            
            if (level.addFreshEntity(entity)) {
                spawned++;
            }
        }
        
        return spawned;
    }
    
    public static final MobSpawnRule SINGLE_VILLAGER = new MobSpawnRule(
        new ResourceLocation("minecraft", "villager"), 1, 1, new Vec3i(0, 1, 0), 1.0f
    );
    
    public static final MobSpawnRule VILLAGERS = new MobSpawnRule(
        new ResourceLocation("minecraft", "villager"), 1, 2, new Vec3i(0, 1, 0), 1.0f
    );
    
    public static final MobSpawnRule CAT = new MobSpawnRule(
        new ResourceLocation("minecraft", "cat"), 1, 1, new Vec3i(0, 1, 0), 0.5f
    );
    
    public static final MobSpawnRule IRON_GOLEM = new MobSpawnRule(
        new ResourceLocation("minecraft", "iron_golem"), 1, 1, new Vec3i(0, 1, 0), 0.3f
    );
}
