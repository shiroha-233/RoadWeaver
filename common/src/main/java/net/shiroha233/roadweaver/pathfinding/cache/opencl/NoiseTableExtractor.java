package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 Minecraft 已实例化噪声对象中抽取 GPU 需要的数据。
 */
public final class NoiseTableExtractor {
    private final Map<NormalNoise, Integer> normalIndices = new IdentityHashMap<>();
    private final Map<PerlinNoise, Integer> perlinIndices = new IdentityHashMap<>();
    private final Map<ImprovedNoise, Integer> improvedIndices = new IdentityHashMap<>();
    private final List<OpenCLNormalNoise> normalNoises = new ArrayList<>();
    private final List<OpenCLPerlinNoise> perlinNoises = new ArrayList<>();
    private final List<OpenCLImprovedNoise> improvedNoises = new ArrayList<>();

    public int normalNoiseIndex(NormalNoise noise) {
        if (noise == null) {
            return -1;
        }
        return normalIndices.computeIfAbsent(noise, this::extractNormalNoise);
    }

    int perlinNoiseIndex(PerlinNoise noise) {
        if (noise == null) {
            return -1;
        }
        return perlinIndices.computeIfAbsent(noise, this::extractPerlinNoise);
    }

    int simplexNoiseIndex(SimplexNoise noise) {
        Object permutationObject = DensityGraphReflection.readField(noise, int[].class, 0);
        if (!(permutationObject instanceof int[] permutation) || permutation.length < 256) {
            throw new UnsupportedOperationException("SimplexNoise missing permutation");
        }
        byte[] compact = new byte[256];
        for (int i = 0; i < compact.length; i++) {
            compact[i] = (byte) permutation[i];
        }
        int index = improvedNoises.size();
        improvedNoises.add(new OpenCLImprovedNoise(0.0D, 0.0D, 0.0D, compact));
        return index;
    }

    public OpenCLNoiseTables toTables() {
        return new OpenCLNoiseTables(normalNoises, perlinNoises, improvedNoises);
    }

    private int extractNormalNoise(NormalNoise noise) {
        PerlinNoise first = (PerlinNoise) DensityGraphReflection.readField(noise, PerlinNoise.class, 0);
        PerlinNoise second = (PerlinNoise) DensityGraphReflection.readField(noise, PerlinNoise.class, 1);
        if (first == null || second == null) {
            throw new UnsupportedOperationException("NormalNoise missing perlin noise levels");
        }
        int firstIndex = perlinNoiseIndex(first);
        int secondIndex = perlinNoiseIndex(second);
        double valueFactor = DensityGraphReflection.readFieldDouble(noise, 0, Double.NaN);
        if (Double.isNaN(valueFactor)) {
            throw new UnsupportedOperationException("NormalNoise missing valueFactor");
        }
        int index = normalNoises.size();
        normalNoises.add(new OpenCLNormalNoise(firstIndex, secondIndex, valueFactor, noise.maxValue()));
        return index;
    }

    private int extractPerlinNoise(PerlinNoise noise) {
        Object levelsObject = DensityGraphReflection.readField(noise, ImprovedNoise[].class, 0);
        if (!(levelsObject instanceof ImprovedNoise[] levels)) {
            throw new UnsupportedOperationException("PerlinNoise missing noiseLevels");
        }
        Object amplitudesObject = DensityGraphReflection.readField(noise, DoubleList.class, 0);
        if (!(amplitudesObject instanceof DoubleList amplitudesList)) {
            throw new UnsupportedOperationException("PerlinNoise missing amplitudes");
        }

        List<Double> amplitudes = new ArrayList<>(amplitudesList.size());
        for (int i = 0; i < amplitudesList.size(); i++) {
            amplitudes.add(amplitudesList.getDouble(i));
        }

        List<Integer> improved = new ArrayList<>(levels.length);
        for (ImprovedNoise level : levels) {
            improved.add(level == null ? -1 : improvedNoiseIndex(level));
        }

        int firstOctave = DensityGraphReflection.readFieldInt(noise, 0, 0);
        double lowestFreqValueFactor = DensityGraphReflection.readFieldDouble(noise, 0, Double.NaN);
        double lowestFreqInputFactor = DensityGraphReflection.readFieldDouble(noise, 1, Double.NaN);
        double maxValue = DensityGraphReflection.readFieldDouble(noise, 2, Double.NaN);
        if (Double.isNaN(lowestFreqInputFactor) || Double.isNaN(lowestFreqValueFactor) || Double.isNaN(maxValue)) {
            throw new UnsupportedOperationException("PerlinNoise missing factors");
        }

        int index = perlinNoises.size();
        perlinNoises.add(new OpenCLPerlinNoise(
                firstOctave,
                lowestFreqInputFactor,
                lowestFreqValueFactor,
                maxValue,
                amplitudes,
                improved));
        return index;
    }

    private int improvedNoiseIndex(ImprovedNoise noise) {
        return improvedIndices.computeIfAbsent(noise, this::extractImprovedNoise);
    }

    private int extractImprovedNoise(ImprovedNoise noise) {
        Object permutationObject = DensityGraphReflection.readField(noise, byte[].class, 0);
        if (!(permutationObject instanceof byte[] permutation)) {
            throw new UnsupportedOperationException("ImprovedNoise missing permutation");
        }
        int index = improvedNoises.size();
        improvedNoises.add(new OpenCLImprovedNoise(noise.xo, noise.yo, noise.zo, permutation));
        return index;
    }
}
