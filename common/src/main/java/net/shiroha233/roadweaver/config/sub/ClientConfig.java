package net.shiroha233.roadweaver.config.sub;

import net.shiroha233.roadweaver.config.SubConfig;

/**
 * 客户端显示配置
 */
public final class ClientConfig implements SubConfig {
    private boolean loadingTipsEnabled = true;
    private boolean loadingProgressEnabled = true;
    private boolean loadingProgressDebugEnabled = false;

    @Override
    public void sanitize() {
        // 布尔字段无需校验
    }

    @Override
    public ClientConfig snapshot() {
        ClientConfig copy = new ClientConfig();
        copy.loadingTipsEnabled = this.loadingTipsEnabled;
        copy.loadingProgressEnabled = this.loadingProgressEnabled;
        copy.loadingProgressDebugEnabled = this.loadingProgressDebugEnabled;
        return copy;
    }

    public boolean loadingTipsEnabled() { return loadingTipsEnabled; }
    public void setLoadingTipsEnabled(boolean v) { this.loadingTipsEnabled = v; }
    public boolean loadingProgressEnabled() { return loadingProgressEnabled; }
    public void setLoadingProgressEnabled(boolean v) { this.loadingProgressEnabled = v; }
    public boolean loadingProgressDebugEnabled() { return loadingProgressDebugEnabled; }
    public void setLoadingProgressDebugEnabled(boolean v) { this.loadingProgressDebugEnabled = v; }
}
