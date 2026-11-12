package net.shiroha233.roadweaver.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 包结构守卫：以源码文本为输入校验分层方向、包级无环、服务端不依赖客户端、包说明齐备。
 */
class PackageStructureTest {

    private static final String ROOT_PACKAGE = "net.shiroha233.roadweaver";

    private static final String CLIENT_LAYER = "client";

    private static final String PACKAGE_FILE = "package-info.java";

    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^import\\s+(?:static\\s+)?([a-zA-Z0-9_.]+(?:\\.\\*)?);",
                    Pattern.MULTILINE);

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^package\\s+([a-zA-Z0-9_.]+);", Pattern.MULTILINE);

    private static final Pattern IMPORT_LINE_PATTERN =
            Pattern.compile("^import\\s+.*$", Pattern.MULTILINE);

    private static final Map<String, Integer> LAYERS = buildLayers();

    private static Path sourceRoot;

    private static List<Path> sourceFiles;

    @Test
    void everyPackageHasPackageInfo() {
        List<String> missing = new ArrayList<>();
        for (Path file : sourceFiles()) {
            if (PACKAGE_FILE.equals(file.getFileName().toString())) {
                continue;
            }
            Path packageInfo = file.getParent().resolve(PACKAGE_FILE);
            if (!Files.isRegularFile(packageInfo)) {
                missing.add(relative(file.getParent()));
            }
        }
        assertTrue(missing.isEmpty(), "以下目录缺少 package-info.java：" + missing);
    }

    @Test
    void packageGraphIsAcyclic() {
        Map<String, Set<String>> edges = packageEdges();
        List<String> cycles = findCycles(edges);
        assertTrue(cycles.isEmpty(), "存在包级循环依赖：" + cycles);
    }

    @Test
    void serverSideDoesNotDependOnClient() {
        String prefix = ROOT_PACKAGE + "." + CLIENT_LAYER + ".";
        List<String> violations = new ArrayList<>();
        for (Path file : sourceFiles()) {
            String packageName = packageOf(file);
            if (CLIENT_LAYER.equals(topLevelOf(packageName))) {
                continue;
            }
            for (String imported : importsOf(file)) {
                if ((imported + ".").startsWith(prefix)) {
                    violations.add(relative(file) + " -> " + imported);
                }
            }
        }
        assertTrue(violations.isEmpty(), "服务端代码不得依赖客户端包：" + violations);
    }

    @Test
    void layerOrderIsRespected() {
        List<String> violations = new ArrayList<>();
        for (Path file : sourceFiles()) {
            String from = packageOf(file);
            Integer fromLayer = LAYERS.get(topLevelOf(from));
            if (fromLayer == null) {
                continue;
            }
            for (String imported : importsOf(file)) {
                Integer toLayer = LAYERS.get(topLevelOf(imported));
                if (toLayer != null && toLayer < fromLayer) {
                    violations.add(relative(file) + " : " + from + " -> " + imported);
                }
            }
        }
        assertTrue(violations.isEmpty(), "依赖方向必须自上而下，下层不得反向 import 上层：" + violations);
    }

    @Test
    void noWildcardImports() {
        List<String> violations = new ArrayList<>();
        for (Path file : sourceFiles()) {
            for (String imported : importsOf(file)) {
                if (imported.endsWith(".*")) {
                    violations.add(relative(file) + " -> " + imported);
                }
            }
        }
        assertTrue(violations.isEmpty(), "禁止使用通配导入，必须逐条显式导入：" + violations);
    }

    @Test
    void noUnusedImports() {
        List<String> violations = new ArrayList<>();
        for (Path file : sourceFiles()) {
            String text = read(file);
            String body = IMPORT_LINE_PATTERN.matcher(text).replaceAll("");
            for (String imported : importsOf(file)) {
                if (imported.endsWith(".*")) {
                    continue;
                }
                String simple = imported.substring(imported.lastIndexOf('.') + 1);
                if (!Pattern.compile("(?<![A-Za-z0-9_.])" + Pattern.quote(simple) + "(?![A-Za-z0-9_])")
                        .matcher(body).find()) {
                    violations.add(relative(file) + " -> " + imported);
                }
            }
        }
        assertTrue(violations.isEmpty(), "存在未被使用的导入：" + violations);
    }

    private static Map<String, Integer> buildLayers() {
        Map<String, Integer> layers = new HashMap<>();
        registerLayer(layers, 1, "api", "command", "client", "lifecycle");
        registerLayer(layers, 2, "generation", "planning", "map", "network", "worldgen", "postprocess");
        registerLayer(layers, 3, "pathfinding", "persistence", "terrain", "runtime");
        registerLayer(layers, 4, "core", "config", "util", "progress");
        return Map.copyOf(layers);
    }

    private static void registerLayer(Map<String, Integer> layers, int layer, String... names) {
        for (String name : names) {
            layers.put(name, layer);
        }
    }

    private static List<Path> sourceFiles() {
        if (sourceFiles != null) {
            return sourceFiles;
        }
        Path root = sourceRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            sourceFiles = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sourceFiles;
    }

    private static Path sourceRoot() {
        if (sourceRoot != null) {
            return sourceRoot;
        }
        Path candidate = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 4 && candidate != null; depth++) {
            Path root = candidate.resolve("src/main/java/net/shiroha233/roadweaver");
            if (Files.isDirectory(root)) {
                sourceRoot = root;
                return root;
            }
            root = candidate.resolve("common/src/main/java/net/shiroha233/roadweaver");
            if (Files.isDirectory(root)) {
                sourceRoot = root;
                return root;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("未找到 Java 源码根目录");
    }

    private static Map<String, Set<String>> packageEdges() {
        Map<String, Set<String>> edges = new TreeMap<>();
        for (Path file : sourceFiles()) {
            String from = packageOf(file);
            for (String imported : importsOf(file)) {
                String to = packageOfImport(imported);
                if (to == null || to.equals(from)) {
                    continue;
                }
                edges.computeIfAbsent(from, key -> new TreeSet<>()).add(to);
            }
            edges.computeIfAbsent(from, key -> new TreeSet<>());
        }
        return edges;
    }

    private static String packageOfImport(String importedClassName) {
        String prefix = ROOT_PACKAGE + ".";
        if (!importedClassName.startsWith(prefix)) {
            return null;
        }
        String rest = importedClassName.substring(prefix.length());
        int split = rest.lastIndexOf('.');
        if (split <= 0) {
            return ROOT_PACKAGE;
        }
        return prefix + rest.substring(0, split);
    }

    private static List<String> findCycles(Map<String, Set<String>> edges) {
        List<String> cycles = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Set<String> inStack = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        for (String node : edges.keySet()) {
            if (!visited.contains(node)) {
                walk(node, edges, visited, inStack, stack, cycles);
            }
        }
        return cycles;
    }

    private static void walk(String node,
                             Map<String, Set<String>> edges,
                             Set<String> visited,
                             Set<String> inStack,
                             Deque<String> path,
                             List<String> cycles) {
        visited.add(node);
        inStack.add(node);
        path.addLast(node);
        for (String next : edges.getOrDefault(node, Set.of())) {
            if (!visited.contains(next)) {
                walk(next, edges, visited, inStack, path, cycles);
            } else if (inStack.contains(next)) {
                cycles.add(renderCycle(path, next));
            }
        }
        path.removeLast();
        inStack.remove(node);
    }

    private static String renderCycle(Deque<String> path, String start) {
        StringBuilder builder = new StringBuilder();
        boolean started = false;
        for (String node : path) {
            if (!started && node.equals(start)) {
                started = true;
            }
            if (started) {
                builder.append(shortName(node)).append(" -> ");
            }
        }
        return builder.append(shortName(start)).toString();
    }

    private static String shortName(String packageName) {
        return packageName.startsWith(ROOT_PACKAGE + ".")
                ? packageName.substring(ROOT_PACKAGE.length() + 1)
                : packageName;
    }

    private static String packageOf(Path file) {
        Matcher matcher = PACKAGE_PATTERN.matcher(read(file));
        return matcher.find() ? matcher.group(1) : "";
    }

    private static List<String> importsOf(Path file) {
        List<String> imports = new ArrayList<>();
        Matcher matcher = IMPORT_PATTERN.matcher(read(file));
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }
        return imports;
    }

    private static String topLevelOf(String packageName) {
        String prefix = ROOT_PACKAGE + ".";
        String rest = packageName.startsWith(prefix) ? packageName.substring(prefix.length()) : packageName;
        int split = rest.indexOf('.');
        return split < 0 ? rest : rest.substring(0, split);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String relative(Path path) {
        return sourceRoot().relativize(path).toString().replace('\\', '/');
    }
}
