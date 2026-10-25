package net.shiroha233.roadweaver.config.structure;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * 结构标签条目，表示一个标签及其包含的所有结构
 */
public final class StructureTagEntry implements Comparable<StructureTagEntry> {
    private final ResourceLocation tagId;
    private final String displayName;
    private final List<StructureEntry> structures;
    private final boolean isVanilla;

    public StructureTagEntry(ResourceLocation tagId, String displayName, List<StructureEntry> structures) {
        this.tagId = tagId;
        this.displayName = displayName;
        this.structures = new ArrayList<>(structures);
        Collections.sort(this.structures);
        this.isVanilla = "minecraft".equals(tagId.getNamespace());
    }

    public ResourceLocation tagId() { return tagId; }
    public String displayName() { return displayName; }
    public List<StructureEntry> structures() { return Collections.unmodifiableList(structures); }
    public boolean isVanilla() { return isVanilla; }
    public String namespace() { return tagId.getNamespace(); }

    public String tagString() { return "#" + tagId.toString(); }

    public Set<String> getAllStructureIds() {
        Set<String> ids = new HashSet<>();
        for (StructureEntry entry : structures) {
            ids.add(entry.id().toString());
        }
        return ids;
    }

    @Override
    public int compareTo(StructureTagEntry other) {
        if (this.isVanilla != other.isVanilla) {
            return this.isVanilla ? -1 : 1;
        }
        return this.tagId.compareTo(other.tagId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StructureTagEntry that)) return false;
        return Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() { return Objects.hash(tagId); }
}
