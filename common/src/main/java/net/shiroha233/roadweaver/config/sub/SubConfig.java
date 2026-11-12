package net.shiroha233.roadweaver.config.sub;

/**
 * 子配置基础接口，定义配置校验和快照能力
 */
public interface SubConfig {
    void sanitize();

    SubConfig snapshot();
}
