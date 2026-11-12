package net.shiroha233.roadweaver.worldgen.structure.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * 结构规模枚举
 */
public enum StructureScale implements StringRepresentable {
    
    SMALL("small", 3, 5, 4, 64, 16),
    MEDIUM("medium", 6, 10, 8, 256, 48),
    LARGE("large", 4, 12, 12, 512, 64);
    
    public static final Codec<StructureScale> CODEC = StringRepresentable.fromEnum(StructureScale::values);
    
    private final String name;
    private final int maxSlope;
    private final int maxHeightDiff;
    private final int terraceBuffer;
    private final int defaultSpacing;
    private final int defaultSeparation;
    
    StructureScale(String name, int maxSlope, int maxHeightDiff, int terraceBuffer, 
                   int defaultSpacing, int defaultSeparation) {
        this.name = name;
        this.maxSlope = maxSlope;
        this.maxHeightDiff = maxHeightDiff;
        this.terraceBuffer = terraceBuffer;
        this.defaultSpacing = defaultSpacing;
        this.defaultSeparation = defaultSeparation;
    }
    
    @Override
    public String getSerializedName() {
        return name;
    }
    
    public int maxSlope() { return maxSlope; }
    
    public int maxHeightDiff() { return maxHeightDiff; }
    
    public int terraceBuffer() { return terraceBuffer; }
    
    public int defaultSpacing() { return defaultSpacing; }
    
    public int defaultSeparation() { return defaultSeparation; }
}
