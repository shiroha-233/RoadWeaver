/* 文件职责：计算可复用的世界生成持久化指纹。 */
package net.shiroha233.roadweaver.persistence;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 世界生成指纹计算服务。
 * 指纹在一次服务器会话内不变，按维度与 schemaVersion 记忆化，
 * 避免每次调用重复执行噪声设置的双重 NBT 编码与 SHA-256。
 */
public final class WorldgenFingerprintService {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String DOMAIN = "roadweaver:worldgen_fingerprint";

    private static final ConcurrentHashMap<String, WorldgenFingerprint> MEMO = new ConcurrentHashMap<>();

    private WorldgenFingerprintService() {}

    public static void clearMemo() {
        MEMO.clear();
    }

    public static WorldgenFingerprint forLevel(ServerLevel level, int schemaVersion) {
        Objects.requireNonNull(level, "level");
        String memoKey = level.dimension().location() + "|" + schemaVersion;
        return MEMO.computeIfAbsent(memoKey, key -> computeForLevel(level, schemaVersion));
    }

    public static WorldgenFingerprint forLevel(ServerLevel level) {
        return forLevel(level, CURRENT_SCHEMA_VERSION);
    }

    private static WorldgenFingerprint computeForLevel(ServerLevel level, int schemaVersion) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        Holder<NoiseGeneratorSettings> settingsHolder = generator instanceof NoiseBasedChunkGenerator noiseGenerator
                ? noiseGenerator.generatorSettings()
                : null;
        return create(
                level.dimension().location(),
                level.getSeed(),
                generatorIdentity(generator),
                level.registryAccess(),
                settingsHolder,
                SharedConstants.getCurrentVersion().getDataVersion().getVersion(),
                schemaVersion);
    }

    public static WorldgenFingerprint create(ResourceLocation dimension,
                                             long seed,
                                             String generatorIdentity,
                                             HolderLookup.Provider registries,
                                             Holder<NoiseGeneratorSettings> settingsHolder,
                                             int dataVersion,
                                             int schemaVersion) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(generatorIdentity, "generatorIdentity");
        Objects.requireNonNull(registries, "registries");

        var registryNbtOps = registries.createSerializationContext(NbtOps.INSTANCE);
        byte[] settingsIdentityBytes = settingsHolder == null
                ? new byte[0]
                : serialize(NoiseGeneratorSettings.CODEC.encodeStart(registryNbtOps, settingsHolder)
                .result()
                .orElseThrow(() -> new IllegalStateException("failed to encode noise settings identity")));
        byte[] settingsPayloadBytes = settingsHolder == null
                ? new byte[0]
                : serialize(NoiseGeneratorSettings.DIRECT_CODEC.encodeStart(registryNbtOps, settingsHolder.value())
                .result()
                .orElseThrow(() -> new IllegalStateException("failed to encode noise settings payload")));

        Hasher hasher = Hashing.sha256().newHasher();
        putString(hasher, DOMAIN);
        hasher.putInt(schemaVersion);
        hasher.putInt(dataVersion);
        putString(hasher, dimension.toString());
        hasher.putLong(seed);
        putString(hasher, generatorIdentity);
        putBytes(hasher, settingsIdentityBytes);
        putBytes(hasher, settingsPayloadBytes);
        return new WorldgenFingerprint(hasher.hash().toString(), schemaVersion, dataVersion);
    }

    public static String generatorIdentity(ChunkGenerator generator) {
        Objects.requireNonNull(generator, "generator");
        String typeKey = generator.getTypeNameForDataFixer()
                .map(key -> key.location().toString())
                .orElse("unregistered");
        return typeKey + "|" + generator.getClass().getName();
    }

    private static void putString(Hasher hasher, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        hasher.putInt(bytes.length);
        hasher.putBytes(bytes);
    }

    private static void putBytes(Hasher hasher, byte[] bytes) {
        hasher.putInt(bytes.length);
        hasher.putBytes(bytes);
    }

    private static byte[] serialize(Tag tag) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(raw)) {
                NbtIo.writeAnyTag(tag, out);
            }
            return raw.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("failed to serialize worldgen fingerprint payload", failure);
        }
    }
}
