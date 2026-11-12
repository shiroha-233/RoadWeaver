package net.shiroha233.roadweaver.client.config;

import net.shiroha233.roadweaver.config.structure.StructureEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 结构路径节点 - 树形数据结构
 * 将结构 ID 按分隔符解析成树形结构，支持折叠显示
 */
public final class StructurePathNode {
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[/_]");
    private static final int FOLDER_THRESHOLD = 3;
    
    private final String name;
    private final String fullPath;
    private final int depth;
    private final Map<String, StructurePathNode> children = new LinkedHashMap<>();
    private final List<StructureEntry> structures = new ArrayList<>();
    
    public StructurePathNode(String name, String fullPath, int depth) {
        this.name = name;
        this.fullPath = fullPath;
        this.depth = depth;
    }
    
    public static StructurePathNode buildTree(List<StructureEntry> structures, String namespace) {
        StructurePathNode root = new StructurePathNode("", namespace + ":", 0);
        
        for (StructureEntry structure : structures) {
            String path = structure.path();
            String[] parts = SEPARATOR_PATTERN.split(path);
            
            if (parts.length <= 1) {
                root.structures.add(structure);
            } else {
                StructurePathNode current = root;
                StringBuilder pathBuilder = new StringBuilder(namespace).append(":");
                
                for (int i = 0; i < parts.length - 1; i++) {
                    String part = parts[i];
                    if (part.isEmpty()) continue;
                    
                    if (pathBuilder.length() > namespace.length() + 1) {
                        pathBuilder.append("/");
                    }
                    pathBuilder.append(part);
                    
                    String childPath = pathBuilder.toString();
                    final int currentDepth = current.depth;
                    current = current.children.computeIfAbsent(part, 
                            k -> new StructurePathNode(part, childPath, currentDepth + 1));
                }
                
                current.structures.add(structure);
            }
        }
        
        sortRecursively(root);
        return root;
    }
    
    private static void sortRecursively(StructurePathNode node) {
        node.structures.sort(Comparator.comparing(s -> s.path().toLowerCase(Locale.ROOT)));
        for (StructurePathNode child : node.children.values()) {
            sortRecursively(child);
        }
    }
    
    public String name() {
        return name;
    }
    
    public String fullPath() {
        return fullPath;
    }
    
    public int depth() {
        return depth;
    }
    
    public Collection<StructurePathNode> children() {
        return children.values();
    }
    
    public List<StructureEntry> structures() {
        return Collections.unmodifiableList(structures);
    }
    
    public boolean hasContent() {
        return !children.isEmpty() || !structures.isEmpty();
    }
    
    public boolean isLeafFolder() {
        return children.isEmpty() && !structures.isEmpty();
    }
    
    public List<StructureEntry> getAllStructures() {
        List<StructureEntry> result = new ArrayList<>(structures);
        for (StructurePathNode child : children.values()) {
            result.addAll(child.getAllStructures());
        }
        return result;
    }
    
    public Set<String> getAllStructureIds() {
        Set<String> result = new LinkedHashSet<>();
        for (StructureEntry structure : structures) {
            result.add(structure.id().toString());
        }
        for (StructurePathNode child : children.values()) {
            result.addAll(child.getAllStructureIds());
        }
        return result;
    }
    
    public int getTotalStructureCount() {
        int count = structures.size();
        for (StructurePathNode child : children.values()) {
            count += child.getTotalStructureCount();
        }
        return count;
    }
    
    public boolean shouldShowAsFolder() {
        if (!children.isEmpty()) {
            return true;
        }
        return structures.size() >= FOLDER_THRESHOLD;
    }
    
    public static boolean hasPathSeparator(String path) {
        return path.contains("/") || path.contains("_");
    }
    
    public static String getLeafName(StructureEntry structure) {
        String path = structure.path();
        String[] parts = SEPARATOR_PATTERN.split(path);
        return parts.length > 0 ? parts[parts.length - 1] : path;
    }
}
