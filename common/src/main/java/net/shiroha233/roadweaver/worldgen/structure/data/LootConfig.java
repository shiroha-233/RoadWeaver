package net.shiroha233.roadweaver.worldgen.structure.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;

/**
 * 结构战利品配置。
 */
public record LootConfig(
    Identifier lootTable,
    Vec3i offset,
    float chance
) {
    
    public static final Codec<LootConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Identifier.CODEC
                .fieldOf("loot_table")
                .forGetter(LootConfig::lootTable),
            Vec3i.CODEC.optionalFieldOf("offset", Vec3i.ZERO)
                .forGetter(LootConfig::offset),
            Codec.FLOAT.optionalFieldOf("chance", 1.0f)
                .forGetter(LootConfig::chance)
        ).apply(instance, LootConfig::new)
    );
    
    public static final Codec<List<LootConfig>> LIST_CODEC = CODEC.listOf();
    
    public boolean apply(ServerLevelAccessor level, BlockPos anchorPos, RandomSource random) {
        if (chance < 1.0f && random.nextFloat() > chance) {
            return false;
        }
        
        BlockPos containerPos = anchorPos.offset(offset.getX(), offset.getY(), offset.getZ());
        
        BlockEntity blockEntity = level.getBlockEntity(containerPos);
        if (blockEntity == null) {
            return false;
        }
        
        if (blockEntity instanceof RandomizableContainerBlockEntity container) {
            ResourceKey<LootTable> lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTable);
            container.setLootTable(lootTableKey, random.nextLong());
            return true;
        }
        
        return false;
    }
    
    public boolean placeChestWithLoot(ServerLevelAccessor level, BlockPos anchorPos, RandomSource random) {
        if (chance < 1.0f && random.nextFloat() > chance) {
            return false;
        }
        
        BlockPos containerPos = anchorPos.offset(offset.getX(), offset.getY(), offset.getZ());
        
        level.setBlock(containerPos, Blocks.CHEST.defaultBlockState(), 2);
        
        BlockEntity blockEntity = level.getBlockEntity(containerPos);
        if (blockEntity instanceof RandomizableContainerBlockEntity container) {
            ResourceKey<LootTable> lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTable);
            container.setLootTable(lootTableKey, random.nextLong());
            return true;
        }
        
        return false;
    }
    
    public static final LootConfig ROADSIDE_SUPPLIES = new LootConfig(
        Identifier.fromNamespaceAndPath("roadweaver", "chests/roadside_supplies"),
        new Vec3i(0, 1, 0),
        1.0f
    );
    
    public static final LootConfig CABIN_CHEST = new LootConfig(
        Identifier.fromNamespaceAndPath("roadweaver", "chests/cabin_chest"),
        new Vec3i(0, 1, 0),
        1.0f
    );
    
    public static final LootConfig RARE_LOOT = new LootConfig(
        Identifier.fromNamespaceAndPath("roadweaver", "chests/rare_loot"),
        new Vec3i(0, 1, 0),
        0.2f
    );
}
