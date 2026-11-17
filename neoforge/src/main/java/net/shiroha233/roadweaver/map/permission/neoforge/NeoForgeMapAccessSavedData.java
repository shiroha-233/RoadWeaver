package net.shiroha233.roadweaver.map.permission.neoforge;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.map.permission.MapAccessPolicy;

public final class NeoForgeMapAccessSavedData extends SavedData {
    private static final String DATA_NAME = "roadweaver_map_access";
    private static final String KEY_POLICY = "policy";

    public static final Codec<NeoForgeMapAccessSavedData> CODEC =
            CompoundTag.CODEC.xmap(NeoForgeMapAccessSavedData::readTag, NeoForgeMapAccessSavedData::writeTag);
    public static final SavedDataType<NeoForgeMapAccessSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(RoadWeaver.MOD_ID, DATA_NAME),
            NeoForgeMapAccessSavedData::new,
            CODEC,
            DataFixTypes.LEVEL);

    private MapAccessPolicy policy = MapAccessPolicy.DEFAULT;

    private static NeoForgeMapAccessSavedData readTag(CompoundTag tag) {
        NeoForgeMapAccessSavedData data = new NeoForgeMapAccessSavedData();
        if (tag.contains(KEY_POLICY)) {
            MapAccessPolicy.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get(KEY_POLICY))
                    .result()
                    .ifPresent(value -> data.policy = value);
        }
        return data;
    }

    private CompoundTag writeTag() {
        CompoundTag tag = new CompoundTag();
        MapAccessPolicy.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, policy)
                .result()
                .ifPresent(encoded -> tag.put(KEY_POLICY, encoded));
        return tag;
    }

    public static NeoForgeMapAccessSavedData get(MinecraftServer server) {
        ServerLevel level = server != null ? server.getLevel(Level.OVERWORLD) : null;
        if (level == null) {
            return new NeoForgeMapAccessSavedData();
        }
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public MapAccessPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(MapAccessPolicy policy) {
        this.policy = policy != null ? policy : MapAccessPolicy.DEFAULT;
        setDirty();
    }
}
