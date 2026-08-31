package net.shiroha233.roadweaver.runtime;

import net.minecraft.server.MinecraftServer;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 共享优先级工作池管理器。
 */
public final class ThreadPoolManager {
    private ThreadPoolManager() {}

    public enum TaskRole {
        INITIAL(0),
        PLANNING(10),
        COARSE(10),
        GENERATION(20),
        MAP(30),
        POSTPROCESS(30),
        IDLE(40);

        private final int priority;

        TaskRole(int priority) {
            this.priority = priority;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    private static final AtomicReference<ThreadPoolExecutor> INITIAL_WORKERS = new AtomicReference<>();
    private static final AtomicReference<ThreadPoolExecutor> GENERATION_WORKERS = new AtomicReference<>();
    private static final AtomicReference<ThreadPoolExecutor> SHARED_EXEC = new AtomicReference<>();
    private static final AtomicReference<ThreadPoolExecutor> MAP_WORKERS = new AtomicReference<>();
    private static final AtomicLong EPOCH = new AtomicLong(0L);
    private static final AtomicLong TASK_SEQ = new AtomicLong(0L);

    private static final ExecutorService INITIAL_EXEC = new RoleExecutorService(TaskRole.INITIAL);
    private static final ExecutorService PLANNING_EXEC = new RoleExecutorService(TaskRole.PLANNING);
    private static final ExecutorService COARSE_EXEC = new RoleExecutorService(TaskRole.COARSE);
    private static final ExecutorService GENERATION_EXEC = new RoleExecutorService(TaskRole.GENERATION);
    private static final ExecutorService MAP_EXEC = new RoleExecutorService(TaskRole.MAP);
    private static final ExecutorService POSTPROCESS_EXEC = new RoleExecutorService(TaskRole.POSTPROCESS);
    private static final ExecutorService IDLE_EXEC = new RoleExecutorService(TaskRole.IDLE);

    private static final ThreadLocal<Long> WORK_START = ThreadLocal.withInitial(System::currentTimeMillis);

    // ==================== 生命周期 ====================

    public static synchronized void onServerStarted(MinecraftServer server) {
        EPOCH.incrementAndGet();
        rebuildInitialPool(resolveInitialGenerationThreads());
        rebuildGenerationPool(resolveGenerationThreads());
        rebuildSharedPool(resolveSharedWorkerThreads());
        rebuildMapPool(resolveMapWorkerThreads());
        LOGGER.debug("ThreadPoolManager: 工作池已启动 (epoch={}, initialThreads={}, generationThreads={}, sharedThreads={}, mapThreads={})",
                EPOCH.get(), resolveInitialGenerationThreads(), resolveGenerationThreads(),
                resolveSharedWorkerThreads(), resolveMapWorkerThreads());
    }

    public static synchronized void onServerStopping() {
        EPOCH.incrementAndGet();
        shutdownQuietly(INITIAL_WORKERS.get());
        shutdownQuietly(GENERATION_WORKERS.get());
        shutdownQuietly(SHARED_EXEC.get());
        shutdownQuietly(MAP_WORKERS.get());
        INITIAL_WORKERS.set(null);
        GENERATION_WORKERS.set(null);
        SHARED_EXEC.set(null);
        MAP_WORKERS.set(null);
        LOGGER.debug("ThreadPoolManager: 工作池已关闭 (epoch={})", EPOCH.get());
    }

    // ==================== 运行时重建 ====================

    public static synchronized void resizeInitialPool(int threads) {
        int resolved = threads <= 0 ? resolveInitialGenerationThreads() : Math.max(1, threads);
        rebuildInitialPool(resolved);
    }

    public static synchronized void resizeSharedPool(int threads) {
        int resolved = threads <= 0 ? resolveSharedWorkerThreads() : Math.max(1, threads);
        rebuildSharedPool(resolved);
    }

    public static synchronized void resizeGenerationPool(int threads) {
        int resolved = threads <= 0 ? resolveGenerationThreads() : Math.max(1, threads);
        rebuildGenerationPool(resolved);
    }

    public static synchronized void resizeComputePool(int threads) {
        resizeSharedPool(threads);
    }

    // ==================== Executor 访问 ====================

    public static ExecutorService computeExecutor() {
        return roleExecutor(TaskRole.PLANNING);
    }

    public static ExecutorService generationExecutor() {
        return roleExecutor(TaskRole.GENERATION);
    }

    public static ExecutorService roleExecutor(TaskRole role) {
        return switch (role == null ? TaskRole.PLANNING : role) {
            case INITIAL -> INITIAL_EXEC;
            case PLANNING -> PLANNING_EXEC;
            case COARSE -> COARSE_EXEC;
            case GENERATION -> GENERATION_EXEC;
            case MAP -> MAP_EXEC;
            case POSTPROCESS -> POSTPROCESS_EXEC;
            case IDLE -> IDLE_EXEC;
        };
    }

    public static void execute(TaskRole role, Runnable runnable) {
        roleExecutor(role).execute(runnable);
    }

    public static java.util.concurrent.Future<?> submit(TaskRole role, Runnable runnable) {
        return roleExecutor(role).submit(runnable);
    }

    public static <T> java.util.concurrent.Future<T> submit(TaskRole role, java.util.concurrent.Callable<T> callable) {
        return roleExecutor(role).submit(callable);
    }

    public static CompletableFuture<Void> runAsync(TaskRole role, Runnable runnable) {
        return CompletableFuture.runAsync(runnable, roleExecutor(role));
    }

    public static <T> CompletableFuture<T> supplyAsync(TaskRole role, Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, roleExecutor(role));
    }

    // ==================== Epoch ====================

    public static long currentEpoch() {
        return EPOCH.get();
    }

    public static boolean isEpoch(long epoch) {
        return EPOCH.get() == epoch;
    }

    // ==================== 占空比节流 ====================

    public static void throttle(int duty) {
        if (duty >= RoadConstants.DUTY_CYCLE_MAX) return;
        if (duty <= 0) duty = RoadConstants.DEFAULT_DUTY_CYCLE;

        long elapsed = System.currentTimeMillis() - WORK_START.get();
        if (elapsed >= RoadConstants.WORK_PERIOD_MS) {
            long sleepMs = (long) (RoadConstants.WORK_PERIOD_MS * (100.0 - duty) / duty);
            if (sleepMs > 0) {
                try {
                    Thread.sleep(Math.min(sleepMs, RoadConstants.MAX_SLEEP_MS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            WORK_START.set(System.currentTimeMillis());
        }
    }

    public static void throttle() {
        try {
            throttle(ConfigService.get().performance().threadDutyCycle());
        } catch (Throwable ignored) {}
    }

    public static void resetThrottle() {
        WORK_START.set(System.currentTimeMillis());
    }

    public static void clearThrottle() {
        WORK_START.remove();
    }

    // ==================== 内部方法 ====================

    private static ThreadPoolExecutor executorFor(TaskRole role) {
        return switch (role == null ? TaskRole.PLANNING : role) {
            case INITIAL -> initialExecutor();
            case GENERATION -> generationPoolExecutor();
            case MAP -> mapExecutor();
            default -> sharedExecutor();
        };
    }

    /**
     * 共享池当前积压任务数，供提交端做背压判断。
     */
    public static int sharedBacklog() {
        ThreadPoolExecutor e = SHARED_EXEC.get();
        return e == null ? 0 : e.getQueue().size();
    }

    private static ThreadPoolExecutor initialExecutor() {
        ThreadPoolExecutor e = INITIAL_WORKERS.get();
        if (e == null || e.isShutdown()) {
            synchronized (ThreadPoolManager.class) {
                e = INITIAL_WORKERS.get();
                if (e == null || e.isShutdown()) {
                    rebuildInitialPool(resolveInitialGenerationThreads());
                    e = INITIAL_WORKERS.get();
                }
            }
        }
        return e;
    }

    private static ThreadPoolExecutor sharedExecutor() {
        ThreadPoolExecutor e = SHARED_EXEC.get();
        if (e == null || e.isShutdown()) {
            synchronized (ThreadPoolManager.class) {
                e = SHARED_EXEC.get();
                if (e == null || e.isShutdown()) {
                    rebuildSharedPool(resolveSharedWorkerThreads());
                    e = SHARED_EXEC.get();
                }
            }
        }
        return e;
    }

    private static ThreadPoolExecutor mapExecutor() {
        ThreadPoolExecutor e = MAP_WORKERS.get();
        if (e == null || e.isShutdown()) {
            synchronized (ThreadPoolManager.class) {
                e = MAP_WORKERS.get();
                if (e == null || e.isShutdown()) {
                    rebuildMapPool(resolveMapWorkerThreads());
                    e = MAP_WORKERS.get();
                }
            }
        }
        return e;
    }

    private static ThreadPoolExecutor generationPoolExecutor() {
        ThreadPoolExecutor e = GENERATION_WORKERS.get();
        if (e == null || e.isShutdown()) {
            synchronized (ThreadPoolManager.class) {
                e = GENERATION_WORKERS.get();
                if (e == null || e.isShutdown()) {
                    rebuildGenerationPool(resolveGenerationThreads());
                    e = GENERATION_WORKERS.get();
                }
            }
        }
        return e;
    }

    private static void rebuildInitialPool(int threads) {
        shutdownQuietly(INITIAL_WORKERS.get());
        INITIAL_WORKERS.set(new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>(),
                namedFactory("RW-Initial")
        ));
    }

    private static void rebuildGenerationPool(int threads) {
        shutdownQuietly(GENERATION_WORKERS.get());
        GENERATION_WORKERS.set(new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>(),
                namedFactory("RW-Gen")
        ));
    }

    private static void rebuildSharedPool(int threads) {
        shutdownQuietly(SHARED_EXEC.get());
        SHARED_EXEC.set(new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>(),
                namedFactory("RW-Worker")
        ));
    }

    private static void rebuildMapPool(int threads) {
        shutdownQuietly(MAP_WORKERS.get());
        MAP_WORKERS.set(new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>(),
                namedFactory("RW-Map")
        ));
    }

    private static void shutdownQuietly(ExecutorService exec) {
        if (exec != null && !exec.isShutdown()) {
            try { exec.shutdownNow(); } catch (Throwable ignored) {}
        }
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicLong next = new AtomicLong(1L);
        return r -> {
            Thread t = new Thread(r, prefix + "-" + next.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
    }

    private static int resolveInitialGenerationThreads() {
        try {
            int configured = ConfigService.get().performance().initialGenerationThreads();
            if (configured > 0) return Math.max(1, configured);
        } catch (Throwable ignored) {}
        return Math.max(1, Math.min(RoadConstants.COMPUTE_THREADS_MAX, Runtime.getRuntime().availableProcessors()));
    }

    private static int resolveSharedWorkerThreads() {
        try {
            int configured = ConfigService.get().performance().sharedWorkerThreads();
            if (configured > 0) return Math.max(1, configured);
        } catch (Throwable ignored) {}
        int available = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(4, available - 1));
    }

    /**
     * 生成池独立于共享池，线程数跟随最大并发生成配置，
     * 避免道路生成与规划/地图任务争抢同一组工作线程。
     */
    private static int resolveGenerationThreads() {
        try {
            int concurrent = ConfigService.get().performance().maxConcurrentGenerations();
            if (concurrent > 0) return Math.max(2, Math.min(concurrent, RoadConstants.COMPUTE_THREADS_MAX));
        } catch (Throwable ignored) {}
        return Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
    }

    private static int resolveMapWorkerThreads() {
        int available = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(2, available - 1));
    }

    private static final class RoleExecutorService extends AbstractExecutorService {
        private final TaskRole role;

        private RoleExecutorService(TaskRole role) {
            this.role = role;
        }

        @Override
        public void shutdown() {
            ThreadPoolManager.onServerStopping();
        }

        @Override
        public List<Runnable> shutdownNow() {
            ThreadPoolManager.onServerStopping();
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            ThreadPoolExecutor e = executorFor(role);
            return e == null || e.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            ThreadPoolExecutor e = executorFor(role);
            return e == null || e.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            ThreadPoolExecutor e = executorFor(role);
            return e == null || e.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            if (command == null) throw new NullPointerException("command");
            executorFor(role).execute(new PrioritizedRunnable(role, command));
        }
    }

    private static final class PrioritizedRunnable implements Runnable, Comparable<PrioritizedRunnable> {
        private final TaskRole role;
        private final Runnable delegate;
        private final long sequence;

        private PrioritizedRunnable(TaskRole role, Runnable delegate) {
            this.role = role == null ? TaskRole.PLANNING : role;
            this.delegate = delegate;
            this.sequence = TASK_SEQ.getAndIncrement();
        }

        @Override
        public void run() {
            delegate.run();
        }

        @Override
        public int compareTo(PrioritizedRunnable other) {
            int byPriority = Integer.compare(this.role.priority, other.role.priority);
            if (byPriority != 0) return byPriority;
            return Long.compare(this.sequence, other.sequence);
        }
    }
}
