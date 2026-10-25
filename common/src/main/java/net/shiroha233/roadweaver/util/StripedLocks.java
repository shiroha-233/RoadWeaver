package net.shiroha233.roadweaver.util;

import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

/*
  基于键哈希的固定容量分片锁。内存占用恒定，适合只需互斥、不需按键区分的场合。
 */
public final class StripedLocks {
    public static final int NS_COORDINATE = 0;
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

    public static Object lockForPath(Path path) {
        return lockFor(NS_FILE_WRITE, path == null ? 0 : path.hashCode());
    }

    private static int namespacedIndex(int namespace, int hashKey) {
        return namespace * STRIPES_PER_NAMESPACE + (spread(hashKey) & STRIPE_MASK);
    }

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