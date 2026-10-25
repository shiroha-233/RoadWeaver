package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * OpenCL density evaluator 的静态输入。
 */
record OpenCLDensityProgramPayload(
        int[] nodeInts,
        double[] nodeValues,
        int[] normalInts,
        double[] normalValues,
        int[] perlinInts,
        double[] perlinValues,
        double[] amplitudes,
        int[] improvedIndices,
        double[] improvedValues,
        int[] permutations,
        int[] splineInts,
        double[] splineLocations,
        int[] splineValueNodes,
        double[] splineDerivatives,
        int[] interpolatedNodes
) {
    static OpenCLDensityProgramPayload from(DensityGraphProgram program) {
        int nodeCount = program.nodes().size();
        int[] usageMasks = nodeUsageMasks(program);
        int[] nodeInts = new int[Math.multiplyExact(nodeCount, 6)];
        double[] nodeValues = new double[Math.multiplyExact(nodeCount, 4)];
        for (int i = 0; i < nodeCount; i++) {
            DensityGraphNode node = program.nodes().get(i);
            int intBase = i * 6;
            nodeInts[intBase] = node.type().ordinal();
            nodeInts[intBase + 1] = node.left();
            nodeInts[intBase + 2] = node.right();
            nodeInts[intBase + 3] = node.extraA();
            nodeInts[intBase + 4] = node.extraB();
            nodeInts[intBase + 5] = usageMasks[i];
            int valueBase = i * 4;
            nodeValues[valueBase] = node.valueA();
            nodeValues[valueBase + 1] = node.valueB();
            nodeValues[valueBase + 2] = node.valueC();
            nodeValues[valueBase + 3] = node.valueD();
        }

        OpenCLNoiseTables noiseTables = program.noiseTables();
        int[] normalInts = new int[Math.multiplyExact(noiseTables.normalNoises().size(), 2)];
        double[] normalValues = new double[Math.multiplyExact(noiseTables.normalNoises().size(), 2)];
        for (int i = 0; i < noiseTables.normalNoises().size(); i++) {
            OpenCLNormalNoise noise = noiseTables.normalNoises().get(i);
            normalInts[i * 2] = noise.firstPerlinIndex();
            normalInts[i * 2 + 1] = noise.secondPerlinIndex();
            normalValues[i * 2] = noise.valueFactor();
            normalValues[i * 2 + 1] = noise.maxValue();
        }

        List<Double> amplitudeValues = new ArrayList<>();
        List<Integer> improvedIndexValues = new ArrayList<>();
        int[] perlinInts = new int[Math.multiplyExact(noiseTables.perlinNoises().size(), 4)];
        double[] perlinValues = new double[Math.multiplyExact(noiseTables.perlinNoises().size(), 3)];
        for (int i = 0; i < noiseTables.perlinNoises().size(); i++) {
            OpenCLPerlinNoise noise = noiseTables.perlinNoises().get(i);
            int amplitudeOffset = amplitudeValues.size();
            amplitudeValues.addAll(noise.amplitudes());
            int improvedOffset = improvedIndexValues.size();
            improvedIndexValues.addAll(noise.improvedNoiseIndices());
            int intBase = i * 4;
            perlinInts[intBase] = noise.firstOctave();
            perlinInts[intBase + 1] = noise.amplitudes().size();
            perlinInts[intBase + 2] = amplitudeOffset;
            perlinInts[intBase + 3] = improvedOffset;
            int valueBase = i * 3;
            perlinValues[valueBase] = noise.lowestFreqInputFactor();
            perlinValues[valueBase + 1] = noise.lowestFreqValueFactor();
            perlinValues[valueBase + 2] = noise.maxValue();
        }

        int[] improvedIndices = toIntArray(improvedIndexValues);
        double[] amplitudes = toDoubleArray(amplitudeValues);
        int[] permutations = new int[Math.multiplyExact(noiseTables.improvedNoises().size(), 256)];
        double[] improvedValues = new double[Math.multiplyExact(noiseTables.improvedNoises().size(), 3)];
        for (int i = 0; i < noiseTables.improvedNoises().size(); i++) {
            OpenCLImprovedNoise noise = noiseTables.improvedNoises().get(i);
            improvedValues[i * 3] = noise.xo();
            improvedValues[i * 3 + 1] = noise.yo();
            improvedValues[i * 3 + 2] = noise.zo();
            byte[] permutation = noise.permutation();
            if (permutation.length != 256) {
                throw new IllegalStateException("ImprovedNoise permutation size must be 256");
            }
            for (int p = 0; p < 256; p++) {
                permutations[i * 256 + p] = permutation[p] & 0xFF;
            }
        }

        List<Double> splineLocationValues = new ArrayList<>();
        List<Integer> splineValueNodeValues = new ArrayList<>();
        List<Double> splineDerivativeValues = new ArrayList<>();
        int[] splineInts = new int[Math.multiplyExact(program.splines().size(), 3)];
        for (int i = 0; i < program.splines().size(); i++) {
            OpenCLSpline spline = program.splines().get(i);
            int pointOffset = splineLocationValues.size();
            splineLocationValues.addAll(spline.locations());
            splineValueNodeValues.addAll(spline.valueNodes());
            splineDerivativeValues.addAll(spline.derivatives());
            int intBase = i * 3;
            splineInts[intBase] = spline.coordinateNode();
            splineInts[intBase + 1] = pointOffset;
            splineInts[intBase + 2] = spline.pointCount();
        }

        return new OpenCLDensityProgramPayload(
                nodeInts,
                nodeValues,
                normalInts,
                normalValues,
                perlinInts,
                perlinValues,
                amplitudes,
                improvedIndices,
                improvedValues,
                permutations,
                splineInts,
                toDoubleArray(splineLocationValues),
                toIntArray(splineValueNodeValues),
                toDoubleArray(splineDerivativeValues),
                toIntArray(program.interpolatedNodes()));
    }

    private static int[] nodeUsageMasks(DensityGraphProgram program) {
        int[] masks = new int[program.nodes().size()];
        if (program.roots().isEmpty()) {
            Arrays.fill(masks, 1);
            return masks;
        }
        for (DensityGraphRoot root : DensityGraphRoot.values()) {
            if (program.hasRoot(root)) {
                markDependencies(program, masks, program.root(root), 1 << root.ordinal());
            }
        }
        int latticeMask = 1 << DensityGraphRoot.values().length;
        for (int node : program.interpolatedNodes()) {
            markDependencies(program, masks, node, latticeMask);
        }
        return masks;
    }

    private static void markDependencies(DensityGraphProgram program,
                                         int[] masks,
                                         int nodeIndex,
                                         int usageMask) {
        if (nodeIndex < 0 || nodeIndex >= masks.length || (masks[nodeIndex] & usageMask) != 0) {
            return;
        }
        masks[nodeIndex] |= usageMask;
        DensityGraphNode node = program.nodes().get(nodeIndex);
        if (node.type() == DensityGraphNodeType.BLENDED_NOISE) {
            markDependencies(program, masks, node.extraB(), usageMask);
            return;
        }
        markDependencies(program, masks, node.left(), usageMask);
        markDependencies(program, masks, node.right(), usageMask);
        if (node.type() == DensityGraphNodeType.RANGE_CHOICE
                || node.type() == DensityGraphNodeType.SHIFTED_NOISE) {
            markDependencies(program, masks, node.extraA(), usageMask);
        } else if (node.type() == DensityGraphNodeType.SPLINE) {
            OpenCLSpline spline = program.splines().get(node.extraA());
            markDependencies(program, masks, spline.coordinateNode(), usageMask);
            for (int valueNode : spline.valueNodes()) {
                markDependencies(program, masks, valueNode, usageMask);
            }
        }
    }

    private static double[] toDoubleArray(List<Double> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}
