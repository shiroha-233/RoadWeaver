/* 文件职责：提供固定容量的分片互斥锁，用于键数量无界的细粒度互斥场景。 */
package net.shiroha233.roadweaver.util;

import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于键哈希的固定容量分片锁。内存占用恒定，适合只需互斥、不需按键区分的场合。
 *
 * <p>不同键可能落到同一条锁上，这只降低并发度、不影响正确性。
 *
 * <p>互斥域按命名空间切分：不同命名空间各占一段独立的锁，不同子系统之间不会
 * 产生无关争用。新增子系统时分配新的命名空间常量并同步扩展
 * {@link #NAMESPACE_COUNT}。
 */
public final class StripedLocks {
    /** 通用坐标键互斥，例如采样瓦片。 */
    public static final int NS_COORDINATE = 0;
    /** 按文件路径互斥，例如瓦片 PNG 写入、存档迁移标记。 */
    public static final int NS_FILE_WRITE = 1;

    private static final int STRIPES_PER_NAMESPACE = 128;
    private static final int NAMESPACE_COUNT = 2;
    private static final int STRIPE_MASK = STRIPES_PER_NAMESPACE - 1;
    private static final ReentrantLock[] LOCKS = new ReentrantLock[STRIPES_PER_NAMESPACE * NAMESPACE_COUNT];

    static {
        for (int i = 0; i < LOCKS.length; i++) {
            LOCKS[i] = new ReentrantLock();
        }
    }

    private StripedLocks() {}

    /** 同一命名空间内，同一键在进程生命周期内恒定返回同一实例。 */
    public static Object lockFor(int namespace, int hashKey) {
        if (namespace < 0 || namespace >= NAMESPACE_COUNT) {
            throw new IllegalArgumentException("unknown lock namespace: " + namespace);
        }
        return LOCKS[namespacedIndex(namespace, hashKey)];
    }

    public static Object lockFor(int hashKey) {
        return lockFor(NS_COORDINATE, hashKey);
    }

    public static Object lockFor(String hashKey) {
        return lockFor(hashKey == null ? 0 : hashKey.hashCode());
    }

    /** 直接用 Path 自身的稳定哈希，无需物化字符串。 */
    public static Object lockForPath(Path path) {
        return lockFor(NS_FILE_WRITE, path == null ? 0 : path.hashCode());
    }

    private static int namespacedIndex(int namespace, int hashKey) {
        return namespace * STRIPES_PER_NAMESPACE + (spread(hashKey) & STRIPE_MASK);
    }

    /**
     * 混淆低位熵，避免分布集中。
     *
     * <p>{@link Path#hashCode()} 按规范由路径的字符串形式派生，因此在进程生命周期内
     * 稳定；本方法依赖这一契约，不另行物化字符串。
     */
    private static int spread(int value) {
        int h = value;
        h ^= (h >>> 16);
        h *= 0x7feb352d;
        h ^= (h >>> 15);
        h *= 0x846ca68b;
        h ^= (h >>> 16);
        return h;
    }
}