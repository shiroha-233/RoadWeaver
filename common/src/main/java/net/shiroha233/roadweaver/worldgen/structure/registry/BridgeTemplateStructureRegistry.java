package net.shiroha233.roadweaver.worldgen.structure.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.shiroha233.roadweaver.worldgen.structure.types.BridgeTemplateStructure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * 桥模板结构注册中心。
 */
public final class BridgeTemplateStructureRegistry {

    private BridgeTemplateStructureRegistry() {
    }

    private static volatile List<BridgeTemplate> cache;

    public static class BridgeTemplate {
        private final Identifier id;
        private final Holder<Structure> holder;
        private final BridgeTemplateStructure structure;
        private BlockState[][][] voxelGrid = new BlockState[0][0][0];
        private Vec3i size = new Vec3i(0, 0, 0);

        public BridgeTemplate(Identifier id, Holder<Structure> holder, BridgeTemplateStructure structure,
                ServerLevel level) {
            this.id = id;
            this.holder = holder;
            this.structure = structure;

            if (level != null) {
                StructureTemplate template = level.getStructureTemplateManager()
                        .get(structure.templateId).orElse(null);
                if (template == null) {
                    return;
                }
                size = template.getSize();
                voxelGrid = new BlockState[size.getX()][size.getY()][size.getZ()];

                CompoundTag tag = template.save(new CompoundTag());
                ListTag palette = resolvePalette(tag);
                ListTag blocks = tag.getListOrEmpty(StructureTemplate.BLOCKS_TAG);

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

        public Identifier getId() {
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
                CompoundTag blockTag = paletteTag.getCompoundOrEmpty(i);
                if (blockTag.isEmpty()) {
                    continue;
                }
                palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, blockTag));
            }

            for (int i = 0; i < blocksTag.size(); i++) {
                CompoundTag blockTag = blocksTag.getCompoundOrEmpty(i);
                if (blockTag.isEmpty()) {
                    continue;
                }

                ListTag pos = blockTag.getListOrEmpty(StructureTemplate.BLOCK_TAG_POS);
                int x = pos.getIntOr(0, 0);
                int y = pos.getIntOr(1, 0);
                int z = pos.getIntOr(2, 0);

                int state = blockTag.getIntOr(StructureTemplate.BLOCK_TAG_STATE, 0);

                if (state < 0 || state >= palette.size()) {
                    continue;
                }
                if (x >= 0 && x < size.getX() && y >= 0 && y < size.getY() && z >= 0 && z < size.getZ()) {
                    voxelGrid[x][y][z] = palette.get(state);
                }
            }
        }

        /**
         * 取出模板的调色板列表。26.3 使用 {@code palettes}（多重调色板）作为主字段，
         * 仅对旧模板回退到单个 {@code palette}。
         */
        private static ListTag resolvePalette(CompoundTag tag) {
            Optional<ListTag> palettes = tag.getList(StructureTemplate.PALETTE_LIST_TAG);
            if (palettes.isPresent() && !palettes.get().isEmpty()) {
                Optional<ListTag> first = palettes.get().getList(0);
                if (first.isPresent() && !first.get().isEmpty()) {
                    return first.get();
                }
            }
            return tag.getListOrEmpty(StructureTemplate.PALETTE_TAG);
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

        Registry<Structure> structureRegistry = registryAccess.lookupOrThrow(Registries.STRUCTURE);

        for (var entry : structureRegistry.entrySet()) {
            Identifier id = entry.getKey().identifier();
            Structure structure = entry.getValue();

            if (structure instanceof BridgeTemplateStructure roadsideStructure) {
                Holder<Structure> holder = structureRegistry.getOrThrow(entry.getKey());
                result.add(new BridgeTemplate(id, holder, roadsideStructure, level));
            }
        }

        return result;
    }

    public static void clearCache() {
        cache = null;
    }
}
