package net.shiroha233.roadweaver.structures.roadside;

/**
 * 路边结构规模枚举
 * 
 * 定义不同规模结构的放置参数：
 * - 地形检查阈值（坡度、高度差）
 * - 托盘缓冲区大小
 * - 间距基准值
 * 
 * 添加新结构时，只需选择合适的规模即可继承这些参数。
 */
public enum StructureScale {
    
    /**
     * 小型结构（如长椅、路牌）
     * - 简单地形检查
     * - 较小托盘
     */
    SMALL(3, 5, 4, 64, 16),
    
    /**
     * 中型结构（如咖啡屋、商店）
     * - 严格坡度检查
     * - 多点采样
     * - 较大托盘
     */
    MEDIUM(6, 10, 8, 256, 48),
    
    /**
     * 大型结构（预留）
     * - 最严格的地形要求
     * - 最大托盘
     */
    LARGE(4, 12, 12, 512, 64);
    
    private final int maxSlope;          // 允许的最大坡度（底部高度差）
    private final int maxHeightDiff;     // 与道路的最大高度差
    private final int terraceBuffer;     // 托盘缓冲区宽度
    private final int defaultSpacing;    // 同类型结构默认间距
    private final int defaultSeparation; // 与其他结构默认间距
    
    StructureScale(int maxSlope, int maxHeightDiff, int terraceBuffer, 
                   int defaultSpacing, int defaultSeparation) {
        this.maxSlope = maxSlope;
        this.maxHeightDiff = maxHeightDiff;
        this.terraceBuffer = terraceBuffer;
        this.defaultSpacing = defaultSpacing;
        this.defaultSeparation = defaultSeparation;
    }
    
    /** 允许的最大坡度（结构底部区域的高度差） */
    public int maxSlope() { return maxSlope; }
    
    /** 与道路的最大高度差 */
    public int maxHeightDiff() { return maxHeightDiff; }
    
    /** 托盘缓冲区宽度 */
    public int terraceBuffer() { return terraceBuffer; }
    
    /** 同类型结构的默认间距 */
    public int defaultSpacing() { return defaultSpacing; }
    
    /** 与其他结构的默认间距 */
    public int defaultSeparation() { return defaultSeparation; }
}
