package net.shiroha233.roadweaver.structures.registry;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.shiroha233.roadweaver.structures.types.BridgeTemplateStructure;

import java.util.*;

/**
 * 桥模板结构注册中心。
 */
public final class BridgeTemplateStructureRegistry {

    private BridgeTemplateStructureRegistry() {
    }

    private static volatile List<BridgeTemplate> cache;

    public static class BridgeTemplate {
        private final ResourceLocation id;
        private final Holder<Structure> holder;
        private final BridgeTemplateStructure structure;
        private BlockState[][][] voxelGrid = new BlockState[0][0][0];
        private Vec3i size = new Vec3i(0, 0, 0);

        public BridgeTemplate(ResourceLocation id, Holder<Structure> holder, BridgeTemplateStructure structure,
                ServerLevel level) {
            this.id = id;
            this.holder = holder;
            this.structure = structure;

            if (level != null) {
                StructureTemplate template = level.getStructureManager().get(structure.templateId).orElse(null);
                if (template == null) {
                    return;
                }
                size = template.getSize();
                voxelGrid = new BlockState[size.getX()][size.getY()][size.getZ()];

                CompoundTag tag = template.save(new CompoundTag());
                ListTag palette = tag.getList("palette", 10);
                ListTag blocks = tag.getList("blocks", 10);

                parseVoxel(palette, blocks);
            }
        }

        public boolean isInVoxelGrid(double x, double y, double z) {
            int originalX = (int) Math.floor(x);
            int originalY = (int) Math.floor(y + structure.heightOffset + 1);
            int originalZ = (int) Math.floor(z + 0.01 + size.getZ() / 2.0);

            return originalX >= 0 && originalX < size.getX() && originalY < size.getY() && originalZ >= 0
                    && originalZ < size.getZ();
        }

        public BlockState getBlock(double x, double y, double z) {
            int originalX = (int) Math.floor(x);
            int originalY = (int) Math.floor(y + structure.heightOffset + 1);
            int originalZ = (int) Math.floor(z + 0.01 + size.getZ() / 2.0);

            if (originalX < 0 || originalX >= size.getX() || originalY >= size.getY() || originalZ < 0
                    || originalZ >= size.getZ()) {
                return null;
            }

            if (originalY < 0) {
                originalY = 0;
            }

            return voxelGrid[originalX][originalY][originalZ];
        }

        public int getStartLength() {
            return structure.bridgeDeckStart;
        }

        public int getEndLength() {
            return size.getX() - structure.bridgeDeckEnd;
        }

        public int getDeckLength() {
            return structure.bridgeDeckEnd - structure.bridgeDeckStart + 1;
        }

        public int getTotalLength() {
            return size.getX();
        }

        public ResourceLocation getId() {
            return id;
        }

        public Holder<Structure> getHolder() {
            return holder;
        }

        public BridgeTemplateStructure getStructure() {
            return structure;
        }

        private void parseVoxel(ListTag paletteTag, ListTag blocksTag) {
            List<BlockState> palette = new ArrayList<>();

            for (int i = 0; i < paletteTag.size(); i++) {
                CompoundTag blockTag = paletteTag.getCompound(i);
                BlockState blockState = readBlockState(blockTag);
                palette.add(blockState);
            }

            for (int i = 0; i < blocksTag.size(); i++) {
                CompoundTag blockTag = blocksTag.getCompound(i);

                ListTag pos = blockTag.getList("pos", 3);
                int x = pos.getInt(0);
                int y = pos.getInt(1);
                int z = pos.getInt(2);

                int state = blockTag.getInt("state");

                if (x >= 0 && x < size.getX() && y >= 0 && y < size.getY() && z >= 0 && z < size.getZ()) {
                    voxelGrid[x][y][z] = palette.get(state);
                }
            }
        }

        public static BlockState readBlockState(CompoundTag pTag) {
            if (!pTag.contains("Name", 8)) {
                return Blocks.AIR.defaultBlockState();
            } else {
                ResourceLocation resourcelocation = new ResourceLocation(pTag.getString("Name"));
                Optional<? extends Holder<Block>> optional = BuiltInRegistries.BLOCK.asLookup().get(ResourceKey.create(Registries.BLOCK, resourcelocation));
                if (optional.isEmpty()) {
                    return Blocks.AIR.defaultBlockState();
                } else {
                    Block block = optional.get().value();
                    BlockState blockstate = block.defaultBlockState();
                    if (pTag.contains("Properties", 10)) {
                        CompoundTag compoundtag = pTag.getCompound("Properties");
                        StateDefinition<Block, BlockState> statedefinition = block.getStateDefinition();

                        for(String s : compoundtag.getAllKeys()) {
                            Property<?> property = statedefinition.getProperty(s);
                            if (property != null) {
                                blockstate = setValueHelper(blockstate, property, s, compoundtag, pTag);
                            }
                        }
                    }

                    return blockstate;
                }
            }
        }

        private static <S extends StateHolder<?, S>, T extends Comparable<T>> S setValueHelper(S pStateHolder, Property<T> pProperty, String pPropertyName, CompoundTag pPropertiesTag, CompoundTag pBlockStateTag) {
            Optional<T> optional = pProperty.getValue(pPropertiesTag.getString(pPropertyName));
            return optional.map(t -> pStateHolder.setValue(pProperty, t)).orElse(pStateHolder);
        }
    }

    public static List<BridgeTemplate> getAll(ServerLevel level) {
        if (level == null || !net.minecraft.world.level.Level.OVERWORLD.equals(level.dimension())) return List.of();
        List<BridgeTemplate> current = cache;
        if (current != null) return current;
        synchronized (BridgeTemplateStructureRegistry.class) {
            if (cache == null) cache = List.copyOf(loadFromRegistry(level));
            return cache;
        }
    }

    public static BridgeTemplate choose(ServerLevel level, int seed) {
        return choose(level, seed, null);
    }

    public static BridgeTemplate choose(ServerLevel level, int seed, Holder<Biome> biome) {
        List<BridgeTemplate> all = getAll(level);
        if (all == null || all.isEmpty()) {
            return null;
        }

        List<BridgeTemplate> candidates = filterByBiome(all, biome);
        if (candidates.isEmpty()) {
            return null;
        }

        return chooseFromList(candidates, seed);
    }

    private static List<BridgeTemplate> filterByBiome(List<BridgeTemplate> all, Holder<Biome> biome) {
        if (biome == null) {
            return all;
        }
        List<BridgeTemplate> result = new ArrayList<>();
        for (BridgeTemplate template : all) {
            if (template.structure.biomes().contains(biome)) {
                result.add(template);
            }
        }
        return result;
    }

    private static BridgeTemplate chooseFromList(List<BridgeTemplate> candidates, int seed) {
        Random random = new Random(seed * 10000L + 2025 + 1225);
        int randomIndex = random.nextInt(candidates.size());

        candidates.sort(Comparator.comparingInt(o -> o.id.getPath().hashCode()));

        return candidates.get(randomIndex);
    }

    private static List<BridgeTemplate> loadFromRegistry(ServerLevel level) {
        List<BridgeTemplate> result = new ArrayList<>();
        RegistryAccess registryAccess = level.registryAccess();

        Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);

        for (var entry : structureRegistry.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            Structure structure = entry.getValue();

            if (structure instanceof BridgeTemplateStructure roadsideStructure) {
                Holder<Structure> holder = structureRegistry.getHolderOrThrow(entry.getKey());
                result.add(new BridgeTemplate(id, holder, roadsideStructure, level));
            }
        }

        return result;
    }

    public static void clearCache() {
        cache = null;
    }
}
