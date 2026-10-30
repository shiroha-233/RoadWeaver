package net.shiroha233.roadweaver.runtime;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * 异步计算服务，封装共享工作池的提交操作
 */
public final class ComputeService {

    private ComputeService() {}

    public static Executor executor() {
        return ThreadPoolManager.roleExecutor(ThreadPoolManager.TaskRole.PLANNING);
    }

    public static Executor executor(ThreadPoolManager.TaskRole role) {
        return ThreadPoolManager.roleExecutor(role);
    }

    public static Executor mapExecutor() {
        return ThreadPoolManager.roleExecutor(ThreadPoolManager.TaskRole.MAP);
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return supplyAsync(ThreadPoolManager.TaskRole.PLANNING, supplier);
    }

    public static <T> CompletableFuture<T> supplyAsync(ThreadPoolManager.TaskRole role, Supplier<T> supplier) {
        return ThreadPoolManager.supplyAsync(role, supplier);
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        return runAsync(ThreadPoolManager.TaskRole.PLANNING, runnable);
    }

    public static CompletableFuture<Void> runAsync(ThreadPoolManager.TaskRole role, Runnable runnable) {
        return ThreadPoolManager.runAsync(role, runnable);
    }

    public static CompletableFuture<Void> runMapAsync(Runnable runnable) {
        return runAsync(ThreadPoolManager.TaskRole.MAP, runnable);
    }

    public static void shutdownNow() {
        ThreadPoolManager.onServerStopping();
    }
}