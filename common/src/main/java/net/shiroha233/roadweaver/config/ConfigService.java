package net.shiroha233.roadweaver.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import net.shiroha233.roadweaver.persistence.files.FileStorageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置文件加载、保存、校验服务
 */
public final class ConfigService {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BASE_DIR = "roadweaver";
    private static final String FILE_NAME = "roadweaver.json";

    private static volatile ModConfig INSTANCE = new ModConfig();

    private ConfigService() {}

    public static synchronized void load() {
        Path baseDir = Platform.getConfigFolder().resolve(BASE_DIR);
        Path file = baseDir.resolve(FILE_NAME);
        ensureDirectory(baseDir);

        if (Files.exists(file)) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) INSTANCE = loaded;
            } catch (Exception e) {
                LOGGER.warn("配置文件读取失败，使用默认值: {}", file, e);
            }
        } else {
            save();
        }

        try {
            INSTANCE.sanitize();
        } catch (Throwable t) {
            LOGGER.warn("配置校验失败，继续使用原始值", t);
        }
        LOGGER.info("配置已加载 (predictRadius={}, predictionEnabled={})",
                INSTANCE.structurePrediction().predictRadiusChunks(),
                INSTANCE.structurePrediction().enabled());
    }

    public static synchronized void save() {
        Path baseDir = Platform.getConfigFolder().resolve(BASE_DIR);
        Path file = baseDir.resolve(FILE_NAME);
        ensureDirectory(baseDir);

        try {
            FileStorageIO.writeStringAtomic(file, GSON.toJson(INSTANCE));
        } catch (Exception e) {
            LOGGER.warn("配置文件写入失败: {}", file, e);
        }
    }

    /**
     * 无锁读取配置。世界生成最内层循环（Beardifier/Feature mixin）高频调用，
     * 写入侧由 synchronized 保护，读取侧依赖 volatile 保证可见性。
     */
    public static ModConfig get() {
        return INSTANCE;
    }

    /**
     * 修改配置后自动保存
     */
    public static synchronized void markDirty() {
        save();
    }

    private static void ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.warn("创建配置目录失败: {}", dir, e);
        }
    }
}
