package net.shiroha233.roadweaver.structures.pieces;

import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.LootTable;
import net.shiroha233.roadweaver.structures.data.LootConfig;
import net.shiroha233.roadweaver.structures.data.MobSpawnRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单模板结构片段
 */
public class SimpleTemplatePiece extends TemplateStructurePiece {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver/SimpleTemplatePiece");
    
    private final ResourceLocation templateId;
    private final List<MobSpawnRule> mobSpawns;
    private final List<LootConfig> lootConfigs;
    
    private boolean[] lootApplied;
    private int[] lootApplyAttempts;
    private boolean mobsSpawned = false;
    
    public SimpleTemplatePiece(StructureTemplateManager manager,
                               ResourceLocation templateId,
                               BlockPos pos,
                               Rotation rotation,
                               Mirror mirror) {
        this(manager, templateId, pos, rotation, mirror, List.of(), List.of());
    }
    
    public SimpleTemplatePiece(StructureTemplateManager manager,
                               ResourceLocation templateId,
                               BlockPos pos,
                               Rotation rotation,
                               Mirror mirror,
                               List<MobSpawnRule> mobSpawns,
                               List<LootConfig> lootConfigs) {
        super(ModStructurePieceTypes.SIMPLE_TEMPLATE, 
              0, 
              manager, 
              templateId, 
              templateId.toString(), 
              createPlaceSettings(rotation, mirror), 
              pos);
        this.templateId = templateId;
        this.mobSpawns = mobSpawns != null ? mobSpawns : List.of();
        this.lootConfigs = lootConfigs != null ? lootConfigs : List.of();
    }
    
    public SimpleTemplatePiece(StructureTemplateManager manager, CompoundTag tag) {
        super(ModStructurePieceTypes.SIMPLE_TEMPLATE, 
              tag, 
              manager, 
              id -> createPlaceSettings(
                  Rotation.valueOf(tag.getString("Rot")),
                  Mirror.valueOf(tag.getString("Mir"))
              ));
        this.templateId = ResourceLocation.tryParse(tag.getString("Template"));
        this.mobSpawns = deserializeMobSpawns(tag);
        this.lootConfigs = deserializeLootConfigs(tag);
    }
    
    private static StructurePlaceSettings createPlaceSettings(Rotation rotation, Mirror mirror) {
        return new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(mirror)
                .setIgnoreEntities(false)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }
    
    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Template", templateId.toString());
        tag.putString("Rot", placeSettings.getRotation().name());
        tag.putString("Mir", placeSettings.getMirror().name());
        
        serializeMobSpawns(tag);
        serializeLootConfigs(tag);
    }
    
    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, 
                                    RandomSource random, BoundingBox box) {
    }
    
    @Override
    public void postProcess(WorldGenLevel level, 
                           StructureManager structureManager,
                           ChunkGenerator generator,
                           RandomSource random,
                           BoundingBox box,
                           ChunkPos chunkPos,
                           BlockPos pivot) {
        super.postProcess(level, structureManager, generator, random, box, chunkPos, pivot);
        
        BlockPos anchorPos = this.templatePosition;
        Rotation rotation = this.placeSettings.getRotation();
 
        initPostProcessState();
 
        boolean allLootApplied = true;
         
        for (int i = 0; i < lootConfigs.size(); i++) {
            if (lootApplied[i]) {
                continue;
            }
 
            LootConfig lootConfig = lootConfigs.get(i);
            try {
                BlockPos rotatedOffset = transformOffset(lootConfig.offset(), rotation);
                BlockPos containerPos = anchorPos.offset(rotatedOffset);
 
                lootApplyAttempts[i]++;
                boolean applied = applyLootTable(level, containerPos, lootConfig, random);
                if (applied) {
                    lootApplied[i] = true;
                } else {
                    allLootApplied = false;
                }
            } catch (Exception e) {
                allLootApplied = false;
                LOGGER.warn("设置战利品表失败 at {}: {}", anchorPos, e.getMessage());
            }
        }
 
        if (lootConfigs.isEmpty()) {
            allLootApplied = true;
        }
         
        if (!mobsSpawned && allLootApplied) {
            for (MobSpawnRule spawnRule : mobSpawns) {
                try {
                    BlockPos rotatedOffset = transformOffset(spawnRule.offset(), rotation);
                    BlockPos spawnPos = anchorPos.offset(rotatedOffset);
                    spawnMob(level, spawnPos, spawnRule, random);
                } catch (Exception e) {
                    LOGGER.warn("生成生物失败 at {}: {}", anchorPos, e.getMessage());
                }
            }
            mobsSpawned = true;
        }
    }
 
    private void initPostProcessState() {
        if (lootApplied == null || lootApplied.length != lootConfigs.size()) {
            lootApplied = new boolean[lootConfigs.size()];
            lootApplyAttempts = new int[lootConfigs.size()];
        }
    }
     
    private BlockPos transformOffset(Vec3i offset, Rotation rotation) {
        return switch (rotation) {
            case NONE -> new BlockPos(offset.getX(), offset.getY(), offset.getZ());
            case CLOCKWISE_90 -> new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
            case CLOCKWISE_180 -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(offset.getZ(), offset.getY(), -offset.getX());
        };
    }
    
     private boolean applyLootTable(WorldGenLevel level, BlockPos containerPos, LootConfig config, RandomSource random) {
         if (config.chance() < 1.0f && random.nextFloat() > config.chance()) {
             return true;
         }
         
         BlockEntity blockEntity = level.getBlockEntity(containerPos);
         if (blockEntity instanceof RandomizableContainerBlockEntity container) {
            container.setLootTable(config.lootTable(), random.nextLong());
             LOGGER.debug("设置战利品表 {} at {}", config.lootTable(), containerPos);
             return true;
         } else {
             LOGGER.debug("位置 {} 没有找到容器方块实体（可能该区块还未放置到这里）", containerPos);
             return false;
         }
     }
    
     private void spawnMob(WorldGenLevel level, BlockPos spawnPos, MobSpawnRule rule, RandomSource random) {
        if (rule.chance() < 1.0f && random.nextFloat() > rule.chance()) {
            return;
        }

        EntityType<?> resolvedType = rule.resolveEntityType().orElse(null);
        if (resolvedType == null) {
            return;
        }
        
        int count = rule.countMin();
        if (rule.countMax() > rule.countMin()) {
            count = rule.countMin() + random.nextInt(rule.countMax() - rule.countMin() + 1);
        }
        
         for (int i = 0; i < count; i++) {
             double spread = 0.3;
             double x = spawnPos.getX() + 0.5 + (random.nextDouble() - 0.5) * spread * 2;
             double y = spawnPos.getY();
             double z = spawnPos.getZ() + 0.5 + (random.nextDouble() - 0.5) * spread * 2;
             
            
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
                 LOGGER.debug("生成生物 {} at ({}, {}, {})", rule.entityId(), x, y, z);
             }
         }
     }
    
    public ResourceLocation getTemplateId() {
        return templateId;
    }
    
    @Override
    public StructurePieceType getType() {
        return ModStructurePieceTypes.SIMPLE_TEMPLATE;
    }
    
    private void serializeMobSpawns(CompoundTag tag) {
        if (mobSpawns.isEmpty()) return;
        
        ListTag listTag = new ListTag();
        for (MobSpawnRule rule : mobSpawns) {
            DataResult<Tag> result = MobSpawnRule.CODEC.encodeStart(NbtOps.INSTANCE, rule);
            result.result().ifPresent(listTag::add);
        }
        if (!listTag.isEmpty()) {
            tag.put("MobSpawns", listTag);
        }
    }
    
    private void serializeLootConfigs(CompoundTag tag) {
        if (lootConfigs.isEmpty()) return;
        
        ListTag listTag = new ListTag();
        for (LootConfig config : lootConfigs) {
            DataResult<Tag> result = LootConfig.CODEC.encodeStart(NbtOps.INSTANCE, config);
            result.result().ifPresent(listTag::add);
        }
        if (!listTag.isEmpty()) {
            tag.put("LootConfigs", listTag);
        }
    }
    
    private static List<MobSpawnRule> deserializeMobSpawns(CompoundTag tag) {
        if (!tag.contains("MobSpawns")) return List.of();
        
        List<MobSpawnRule> result = new ArrayList<>();
        ListTag listTag = tag.getList("MobSpawns", Tag.TAG_COMPOUND);
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag ruleTag = listTag.getCompound(i);
            DataResult<MobSpawnRule> parseResult = MobSpawnRule.CODEC.parse(NbtOps.INSTANCE, ruleTag);
            parseResult.result().ifPresent(result::add);
        }
        return result;
    }
    
    private static List<LootConfig> deserializeLootConfigs(CompoundTag tag) {
        if (!tag.contains("LootConfigs")) return List.of();
        
        List<LootConfig> result = new ArrayList<>();
        ListTag listTag = tag.getList("LootConfigs", Tag.TAG_COMPOUND);
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag configTag = listTag.getCompound(i);
            DataResult<LootConfig> parseResult = LootConfig.CODEC.parse(NbtOps.INSTANCE, configTag);
            parseResult.result().ifPresent(result::add);
        }
        return result;
    }
}
