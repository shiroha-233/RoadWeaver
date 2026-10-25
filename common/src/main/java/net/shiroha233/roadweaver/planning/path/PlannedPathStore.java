package net.shiroha233.roadweaver.planning.path;

import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 待生成路径存储边界。
 */
public interface PlannedPathStore {
    Optional<List<BlockPos>> load(PlannedPathKey key, String fingerprint) throws IOException;

    void save(PlannedPathKey key, String fingerprint, List<BlockPos> path) throws IOException;

    void delete(PlannedPathKey key) throws IOException;
}
