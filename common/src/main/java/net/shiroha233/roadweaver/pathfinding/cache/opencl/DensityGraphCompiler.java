package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft density graph 到 OpenCL 节点表的编译器。
 */
public final class DensityGraphCompiler {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private final Map<DensityFunction, Integer> seen = new IdentityHashMap<>();
    private final List<DensityGraphNode> nodes = new ArrayList<>();
    private final List<Double> constants = new ArrayList<>();
    private final NoiseTableExtractor noiseTables = new NoiseTableExtractor();
    private final List<OpenCLSpline> splines = new ArrayList<>();
    private final List<Integer> interpolatedNodes = new ArrayList<>();
    private final boolean preserveInterpolation;

    private DensityGraphCompiler(boolean preserveInterpolation) {
        this.preserveInterpolation = preserveInterpolation;
    }

    public static DensityGraphCompileResult compile(DensityFunction root) {
        if (root == null) {
            return DensityGraphCompileResult.unsupported("density root is null");
        }
        DensityGraphCompiler compiler = new DensityGraphCompiler(false);
        try {
            int rootNode = compiler.compileNode(root);
            return DensityGraphCompileResult.supported(new DensityGraphProgram(
                    rootNode,
                    compiler.nodes,
                    compiler.constants,
                    compiler.noiseTables.toTables(),
                    compiler.splines));
        } catch (TemporaryDensityGraphException e) {
            return DensityGraphCompileResult.retryable(e.getMessage());
        } catch (UnsupportedDensityGraphException e) {
            return DensityGraphCompileResult.unsupported(e.getMessage());
        } catch (Throwable t) {
            LOGGER.info("OpenCL density graph compile failed: root={} error={} message={}",
                    root.getClass().getName(),
                    t.getClass().getName(),
                    t.getMessage(),
                    t);
            return DensityGraphCompileResult.unsupported("density graph compile failed: " + t.getClass().getSimpleName());
        }
    }

    public static DensityGraphCompileResult compile(Map<DensityGraphRoot, DensityFunction> roots) {
        if (roots == null || roots.isEmpty() || roots.get(DensityGraphRoot.FINAL_DENSITY) == null) {
            return DensityGraphCompileResult.unsupported("accurate density roots are incomplete");
        }
        DensityGraphCompiler compiler = new DensityGraphCompiler(true);
        try {
            EnumMap<DensityGraphRoot, Integer> compiledRoots = new EnumMap<>(DensityGraphRoot.class);
            for (DensityGraphRoot root : DensityGraphRoot.values()) {
                DensityFunction function = roots.get(root);
                if (function != null) {
                    compiledRoots.put(root, compiler.compileNode(function));
                }
            }
            int finalRoot = compiledRoots.get(DensityGraphRoot.FINAL_DENSITY);
            return DensityGraphCompileResult.supported(new DensityGraphProgram(
                    finalRoot,
                    compiledRoots,
                    compiler.interpolatedNodes,
                    compiler.nodes,
                    compiler.constants,
                    compiler.noiseTables.toTables(),
                    compiler.splines));
        } catch (TemporaryDensityGraphException e) {
            return DensityGraphCompileResult.retryable(e.getMessage());
        } catch (UnsupportedDensityGraphException e) {
            return DensityGraphCompileResult.unsupported(e.getMessage());
        } catch (Throwable failure) {
            LOGGER.info("OpenCL accurate density graph compile failed: error={} message={}",
                    failure.getClass().getName(), failure.getMessage(), failure);
            return DensityGraphCompileResult.unsupported("accurate density graph compile failed: "
                    + failure.getClass().getSimpleName());
        }
    }

    private int compileNode(DensityFunction function) {
        Integer cached = seen.get(function);
        if (cached != null) {
            return cached;
        }

        int index = compileUncached(function);
        seen.put(function, index);
        return index;
    }

    private int compileUncached(DensityFunction function) {
        String typeId = densityTypeId(function);
        return switch (typeId) {
            case "constant", "Constant" -> constant(function);
            case "blend_alpha", "BlendAlpha" -> constant(1.0D);
            case "blend_offset", "BlendOffset", "beardifier", "BeardifierMarker" -> constant(0.0D);
            case "blend_density", "BlendDensity" -> compileNode(recordFunction(function, 0, "blend_density input"));
            case "add", "mul", "min", "max" -> twoArgument(function, typeId);
            case "Ap2", "MulOrAdd" -> twoArgument(function, null);
            case "clamp", "Clamp" -> clamp(function);
            case "clamp_to_nearest_unit", "ClampToNearestUnit" -> clampToNearestUnit(function);
            case "abs", "square", "cube", "half_negative", "quarter_negative", "squeeze" -> mapped(function, typeId);
            case "Mapped" -> mapped(function, null);
            case "y_clamped_gradient", "YClampedGradient" -> yClampedGradient(function);
            case "range_choice", "RangeChoice" -> rangeChoice(function);
            case "marker", "Marker" -> marker(function);
            case "holder", "HolderHolder" -> holder(function);
            case "noise", "Noise" -> noise(function);
            case "shifted_noise", "ShiftedNoise" -> shiftedNoise(function);
            case "shift_a", "ShiftA" -> shift(function, DensityGraphNodeType.SHIFT_A);
            case "shift_b", "ShiftB" -> shift(function, DensityGraphNodeType.SHIFT_B);
            case "shift", "Shift" -> shift(function, DensityGraphNodeType.SHIFT);
            case "weird_scaled_sampler", "WeirdScaledSampler" -> weirdScaledSampler(function);
            case "spline", "Spline" -> spline(function);
            case "old_blended_noise", "BlendedNoise" -> blendedNoise(function);
            case "end_islands", "EndIslandDensityFunction" -> endIsland(function);
            default -> unsupported("unsupported density node: " + function.getClass().getName());
        };
    }

    private static String densityTypeId(DensityFunction function) {
        if (function instanceof DensityFunctions.HolderHolder) {
            return "holder";
        }
        if (function instanceof DensityFunctions.MarkerOrMarked) {
            return "marker";
        }
        try {
            ResourceLocation id = BuiltInRegistries.DENSITY_FUNCTION_TYPE.getKey(function.codec().codec());
            if (id != null) {
                return id.getPath();
            }
        } catch (RuntimeException ignored) {
        }
        return function.getClass().getSimpleName();
    }

    private int constant(DensityFunction function) {
        double value = function.minValue();
        if (Double.isNaN(value)) {
            throw new UnsupportedDensityGraphException("constant node missing value");
        }
        return constant(value);
    }

    private int constant(double value) {
        constants.add(value);
        return add(new DensityGraphNode(DensityGraphNodeType.CONSTANT, -1, -1, constants.size() - 1, -1, value, 0.0D, 0.0D, 0.0D));
    }

    private int twoArgument(DensityFunction function, String operation) {
        Object firstInput = DensityGraphReflection.readRecord(function, 1);
        Object secondInput = DensityGraphReflection.readRecord(function, 2);
        if (!(firstInput instanceof DensityFunction firstFunction)) {
            throw new UnsupportedDensityGraphException("two-argument node missing input");
        }
        int left;
        int right;
        if (secondInput instanceof DensityFunction secondFunction) {
            left = compileNode(firstFunction);
            right = compileNode(secondFunction);
        } else {
            double argument = DensityGraphReflection.readRecordDouble(function, 4, Double.NaN);
            if (Double.isNaN(argument)) {
                throw new UnsupportedDensityGraphException("two-argument node missing constant input");
            }
            left = constant(argument);
            right = compileNode(firstFunction);
        }
        String op = operation == null ? serializedName(DensityGraphReflection.readRecord(function, 0)) : operation;
        DensityGraphNodeType nodeType = switch (op == null ? "" : op.toLowerCase()) {
            case "add" -> DensityGraphNodeType.ADD;
            case "mul" -> DensityGraphNodeType.MUL;
            case "min" -> DensityGraphNodeType.MIN;
            case "max" -> DensityGraphNodeType.MAX;
            default -> throw new UnsupportedDensityGraphException("unsupported two-argument op: " + op);
        };
        return add(new DensityGraphNode(nodeType, left, right, -1, -1, 0.0D, 0.0D, 0.0D, 0.0D));
    }

    private int clamp(DensityFunction function) {
        DensityFunction input = recordFunction(function, 0, "clamp input");
        int child = compileNode(input);
        double min = function.minValue();
        double max = function.maxValue();
        return add(new DensityGraphNode(DensityGraphNodeType.CLAMP, child, -1, -1, -1, min, max, 0.0D, 0.0D));
    }

    private int clampToNearestUnit(DensityFunction function) {
        DensityFunction input = (DensityFunction) DensityGraphReflection.read(function, "function");
        if (input == null) {
            throw new UnsupportedDensityGraphException("clamp_to_nearest_unit node missing function");
        }
        int child = compileNode(input);
        int resolution = DensityGraphReflection.readInt(function, "resolution", Integer.MIN_VALUE);
        if (resolution == Integer.MIN_VALUE) {
            throw new UnsupportedDensityGraphException("clamp_to_nearest_unit node missing resolution");
        }
        return add(new DensityGraphNode(DensityGraphNodeType.CLAMP_TO_NEAREST_UNIT, child, -1, resolution, -1, 0.0D, 0.0D, 0.0D, 0.0D));
    }

    private int mapped(DensityFunction function, String operation) {
        DensityFunction input = recordFunction(function, 1, "mapped input");
        int child = compileNode(input);
        String op = operation == null ? serializedName(DensityGraphReflection.readRecord(function, 0)) : operation;
        DensityGraphNodeType nodeType = switch (op == null ? "" : op.toLowerCase()) {
            case "abs" -> DensityGraphNodeType.ABS;
            case "square" -> DensityGraphNodeType.SQUARE;
            case "cube" -> DensityGraphNodeType.CUBE;
            case "half_negative" -> DensityGraphNodeType.HALF_NEGATIVE;
            case "quarter_negative" -> DensityGraphNodeType.QUARTER_NEGATIVE;
            case "squeeze" -> DensityGraphNodeType.SQUEEZE;
            case "invert" -> DensityGraphNodeType.INVERT;
            default -> throw new UnsupportedDensityGraphException("unsupported mapped op: " + op);
        };
        return add(new DensityGraphNode(nodeType, child, -1, -1, -1, 0.0D, 0.0D, 0.0D, 0.0D));
    }

    private int yClampedGradient(DensityFunction function) {
        int fromY = DensityGraphReflection.readRecordInt(function, 0, Integer.MIN_VALUE);
        int toY = DensityGraphReflection.readRecordInt(function, 1, Integer.MIN_VALUE);
        double fromValue = DensityGraphReflection.readRecordDouble(function, 2, Double.NaN);
        double toValue = DensityGraphReflection.readRecordDouble(function, 3, Double.NaN);
        if (fromY == Integer.MIN_VALUE || toY == Integer.MIN_VALUE || Double.isNaN(fromValue) || Double.isNaN(toValue)) {
            throw new UnsupportedDensityGraphException("y_clamped_gradient node missing values");
        }
        return add(new DensityGraphNode(DensityGraphNodeType.Y_CLAMPED_GRADIENT, -1, -1, fromY, toY, fromValue, toValue, 0.0D, 0.0D));
    }

    private int rangeChoice(DensityFunction function) {
        DensityFunction input = recordFunction(function, 0, "range_choice input");
        Object inRangeValue = DensityGraphReflection.readRecord(function, 3);
        Object outOfRangeValue = DensityGraphReflection.readRecord(function, 4);
        DensityFunction whenInRange = inRangeValue instanceof DensityFunction value ? value : null;
        DensityFunction whenOutOfRange = outOfRangeValue instanceof DensityFunction value ? value : null;
        if (input == null || whenInRange == null || whenOutOfRange == null) {
            throw new UnsupportedDensityGraphException("range_choice node missing input");
        }
        int condition = compileNode(input);
        int inRange = compileNode(whenInRange);
        int outOfRange = compileNode(whenOutOfRange);
        double min = DensityGraphReflection.readRecordDouble(function, 1, Double.NaN);
        double max = DensityGraphReflection.readRecordDouble(function, 2, Double.NaN);
        if (Double.isNaN(min) || Double.isNaN(max)) {
            throw new UnsupportedDensityGraphException("range_choice node missing bounds");
        }
        return add(new DensityGraphNode(DensityGraphNodeType.RANGE_CHOICE, condition, inRange, outOfRange, -1, min, max, 0.0D, 0.0D));
    }

    private int marker(DensityFunction function) {
        if (!(function instanceof DensityFunctions.MarkerOrMarked marker)) {
            throw new UnsupportedDensityGraphException("marker node missing wrapped function");
        }
        int wrapped = compileNode(marker.wrapped());
        Object markerType = marker.type();
        String markerName = markerType instanceof StringRepresentable named
                ? named.getSerializedName()
                : String.valueOf(markerType);
        if (!preserveInterpolation || !"interpolated".equalsIgnoreCase(markerName)) {
            return wrapped;
        }
        int slot = interpolatedNodes.size();
        int node = add(new DensityGraphNode(
                DensityGraphNodeType.INTERPOLATED,
                wrapped,
                -1,
                slot,
                -1,
                0.0D,
                0.0D,
                0.0D,
                0.0D));
        interpolatedNodes.add(node);
        return node;
    }

    private int holder(DensityFunction function) {
        if (!(function instanceof DensityFunctions.HolderHolder holderHolder)) {
            throw new TemporaryDensityGraphException("holder node is not bound");
        }
        DensityFunction nested = holderHolder.function().value();
        if (nested == null) {
            throw new TemporaryDensityGraphException("holder node is not bound");
        }
        return compileNode(nested);
    }

    private int noise(DensityFunction function) {
        int noiseIndex = normalNoiseIndex(DensityGraphReflection.readRecord(function, 0));
        double xzScale = DensityGraphReflection.readRecordDouble(function, 1, Double.NaN);
        double yScale = DensityGraphReflection.readRecordDouble(function, 2, Double.NaN);
        if (Double.isNaN(xzScale) || Double.isNaN(yScale)) {
            throw new UnsupportedDensityGraphException("noise node missing scale");
        }
        return add(new DensityGraphNode(DensityGraphNodeType.NOISE, -1, -1, noiseIndex, -1, xzScale, yScale, 0.0D, 0.0D));
    }

    private int shiftedNoise(DensityFunction function) {
        int x = compileNode(recordFunction(function, 0, "shifted_noise shiftX"));
        int y = compileNode(recordFunction(function, 1, "shifted_noise shiftY"));
        int z = compileNode(recordFunction(function, 2, "shifted_noise shiftZ"));
        int noiseIndex = normalNoiseIndex(DensityGraphReflection.readRecord(function, 5));
        double xzScale = DensityGraphReflection.readRecordDouble(function, 3, Double.NaN);
        double yScale = DensityGraphReflection.readRecordDouble(function, 4, Double.NaN);
        if (Double.isNaN(xzScale) || Double.isNaN(yScale)) {
            throw new UnsupportedDensityGraphException("shifted_noise node missing scale");
        }
        return add(new DensityGraphNode(DensityGraphNodeType.SHIFTED_NOISE, x, y, z, noiseIndex, xzScale, yScale, 0.0D, 0.0D));
    }

    private int shift(DensityFunction function, DensityGraphNodeType nodeType) {
        int noiseIndex = normalNoiseIndex(DensityGraphReflection.readRecord(function, 0));
        return add(new DensityGraphNode(nodeType, -1, -1, noiseIndex, -1, 0.25D, 4.0D, 0.0D, 0.0D));
    }

    private int weirdScaledSampler(DensityFunction function) {
        int mapperType = rarityMapperType(DensityGraphReflection.readRecord(function, 2));
        int child = compileNode(recordFunction(function, 0, "weird_scaled_sampler input"));
        int noiseIndex = normalNoiseIndex(DensityGraphReflection.readRecord(function, 1));
        return add(new DensityGraphNode(DensityGraphNodeType.WEIRD_SCALED_SAMPLER, child, -1, noiseIndex, mapperType, 0.0D, 0.0D, 0.0D, 0.0D));
    }

    private int blendedNoise(DensityFunction function) {
        if (!(function instanceof BlendedNoise blendedNoise)) {
            throw new UnsupportedDensityGraphException("blended noise node has unexpected implementation");
        }
        PerlinNoise minLimit = (PerlinNoise) DensityGraphReflection.read(blendedNoise, "minLimitNoise");
        PerlinNoise maxLimit = (PerlinNoise) DensityGraphReflection.read(blendedNoise, "maxLimitNoise");
        PerlinNoise main = (PerlinNoise) DensityGraphReflection.read(blendedNoise, "mainNoise");
        double xzMultiplier = DensityGraphReflection.readDouble(blendedNoise, "xzMultiplier", Double.NaN);
        double yMultiplier = DensityGraphReflection.readDouble(blendedNoise, "yMultiplier", Double.NaN);
        double xzFactor = DensityGraphReflection.readDouble(blendedNoise, "xzFactor", Double.NaN);
        double yFactor = DensityGraphReflection.readDouble(blendedNoise, "yFactor", Double.NaN);
        double smearScale = DensityGraphReflection.readDouble(blendedNoise, "smearScaleMultiplier", Double.NaN);
        if (minLimit == null || maxLimit == null || main == null
                || Double.isNaN(xzMultiplier) || Double.isNaN(yMultiplier)
                || Double.isNaN(xzFactor) || Double.isNaN(yFactor) || Double.isNaN(smearScale)) {
            throw new UnsupportedDensityGraphException("blended noise node is missing runtime state");
        }
        int smearNode = constant(smearScale);
        return add(new DensityGraphNode(
                DensityGraphNodeType.BLENDED_NOISE,
                noiseTables.perlinNoiseIndex(minLimit),
                noiseTables.perlinNoiseIndex(maxLimit),
                noiseTables.perlinNoiseIndex(main),
                smearNode,
                xzMultiplier,
                yMultiplier,
                xzFactor,
                yFactor));
    }

    private int endIsland(DensityFunction function) {
        Object simplexObject = DensityGraphReflection.read(function, "islandNoise");
        if (!(simplexObject instanceof SimplexNoise simplexNoise)) {
            throw new UnsupportedDensityGraphException("end island node is missing simplex noise");
        }
        return add(new DensityGraphNode(
                DensityGraphNodeType.END_ISLAND,
                -1,
                -1,
                noiseTables.simplexNoiseIndex(simplexNoise),
                -1,
                0.0D,
                0.0D,
                0.0D,
                0.0D));
    }

    private int spline(DensityFunction function) {
        if (!(function instanceof DensityFunctions.Spline spline)) {
            throw new UnsupportedDensityGraphException("spline node has unexpected implementation");
        }
        return compileSplineValueNode(spline.spline());
    }

    private int compileSplineValueNode(Object spline) {
        if (spline == null) {
            throw new UnsupportedDensityGraphException("spline node missing spline");
        }
        if (spline instanceof CubicSpline.Constant<?, ?> constantSpline) {
            return constant(constantSpline.value());
        }
        if (!(spline instanceof CubicSpline.Multipoint<?, ?>)) {
            throw new UnsupportedDensityGraphException("unsupported spline type: " + spline.getClass().getName());
        }
        int splineIndex = compileSplineTable(spline);
        return add(new DensityGraphNode(DensityGraphNodeType.SPLINE, -1, -1, splineIndex, -1, 0.0D, 0.0D, 0.0D, 0.0D));
    }

    private int compileSplineTable(Object spline) {
        if (spline == null) {
            throw new UnsupportedDensityGraphException("spline node missing spline");
        }

        if (!(spline instanceof CubicSpline.Multipoint<?, ?> multipoint)
                || !(multipoint.coordinate() instanceof DensityFunctions.Spline.Coordinate coordinate)) {
            throw new UnsupportedDensityGraphException("spline coordinate is not a density function");
        }
        int coordinateNode = compileNode(coordinate.function().value());

        float[] locations = multipoint.locations();
        List<?> values = multipoint.values();
        float[] derivatives = multipoint.derivatives();
        if (locations.length != values.size() || locations.length != derivatives.length) {
            throw new UnsupportedDensityGraphException("spline table size mismatch");
        }

        List<Double> locationList = new ArrayList<>(locations.length);
        List<Integer> valueNodes = new ArrayList<>(locations.length);
        List<Double> derivativeList = new ArrayList<>(locations.length);
        for (int i = 0; i < locations.length; i++) {
            locationList.add((double) locations[i]);
            valueNodes.add(compileSplineValueNode(values.get(i)));
            derivativeList.add((double) derivatives[i]);
        }

        int index = splines.size();
        splines.add(new OpenCLSpline(coordinateNode, locationList, valueNodes, derivativeList));
        return index;
    }

    private int normalNoiseIndex(Object noiseHolder) {
        if (!(noiseHolder instanceof DensityFunction.NoiseHolder holder) || holder.noise() == null) {
            throw new UnsupportedDensityGraphException("noise holder has no NormalNoise instance");
        }
        try {
            return noiseTables.normalNoiseIndex(holder.noise());
        } catch (Throwable t) {
            throw new UnsupportedDensityGraphException("noise table extraction failed: " + t.getClass().getSimpleName());
        }
    }

    private static DensityFunction recordFunction(Object owner, int componentIndex, String description) {
        Object value = DensityGraphReflection.readRecord(owner, componentIndex);
        if (!(value instanceof DensityFunction function)) {
            throw new UnsupportedDensityGraphException(description + " missing");
        }
        return function;
    }

    private static int rarityMapperType(Object mapper) {
        if (mapper == null) {
            throw new UnsupportedDensityGraphException("weird_scaled_sampler missing rarity mapper");
        }
        String serializedName = serializedName(mapper);
        if ("type_1".equals(serializedName)) {
            return 1;
        }
        if ("type_2".equals(serializedName)) {
            return 2;
        }
        throw new UnsupportedDensityGraphException("unsupported weird_scaled_sampler mapper: " + mapper);
    }

    private int add(DensityGraphNode node) {
        nodes.add(node);
        return nodes.size() - 1;
    }

    private static String serializedName(Object value) {
        if (value instanceof StringRepresentable representable) {
            return representable.getSerializedName();
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return value == null ? null : String.valueOf(value);
    }

    private static int unsupported(String reason) {
        throw new UnsupportedDensityGraphException(reason);
    }

    private static final class TemporaryDensityGraphException extends RuntimeException {
        private TemporaryDensityGraphException(String message) {
            super(message);
        }
    }

    private static final class UnsupportedDensityGraphException extends RuntimeException {
        private UnsupportedDensityGraphException(String message) {
            super(message);
        }
    }
}
