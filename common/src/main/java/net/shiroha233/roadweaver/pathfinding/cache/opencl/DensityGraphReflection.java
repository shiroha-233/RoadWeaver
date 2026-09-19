package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;

final class DensityGraphReflection {
    private DensityGraphReflection() {}

    static Object read(Object owner, String name) {
        if (owner == null || name == null || name.isBlank()) {
            return null;
        }
        Object value = readExact(owner, name);
        if (value != null) {
            return value;
        }
        for (String alias : aliases(owner, name)) {
            value = readExact(owner, alias);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    static Object readRecord(Object owner, int componentIndex) {
        if (owner == null || componentIndex < 0) {
            return null;
        }
        if (owner.getClass().isRecord()) {
            RecordComponent[] components = owner.getClass().getRecordComponents();
            if (componentIndex >= components.length) {
                return null;
            }
            try {
                Method accessor = components[componentIndex].getAccessor();
                accessor.setAccessible(true);
                return accessor.invoke(owner);
            } catch (Throwable ignored) {
                // Forge's production remapper can make a record accessor inaccessible.
                // First use the component's runtime name, then fall back to field order.
                Object value = readExact(owner, components[componentIndex].getName());
                if (value != null) {
                    return value;
                }
            }
        }
        return readInstanceField(owner, componentIndex);
    }

    static double readRecordDouble(Object owner, int componentIndex, double fallback) {
        Object value = readRecord(owner, componentIndex);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    static int readRecordInt(Object owner, int componentIndex, int fallback) {
        Object value = readRecord(owner, componentIndex);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    static Object readField(Object owner, Class<?> expectedType, int ordinal) {
        if (owner == null || expectedType == null || ordinal < 0) {
            return null;
        }
        int match = 0;
        Class<?> type = owner.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                Class<?> fieldType = field.getType();
                boolean compatible = expectedType.isPrimitive()
                        ? expectedType == fieldType
                        : expectedType.isAssignableFrom(fieldType);
                if (!compatible) {
                    continue;
                }
                if (match++ != ordinal) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    return field.get(owner);
                } catch (Throwable ignored) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    static double readFieldDouble(Object owner, int ordinal, double fallback) {
        Object value = readField(owner, double.class, ordinal);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    static int readFieldInt(Object owner, int ordinal, int fallback) {
        Object value = readField(owner, int.class, ordinal);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Object readInstanceField(Object owner, int ordinal) {
        int match = 0;
        Class<?> type = owner.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (match++ != ordinal) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    return field.get(owner);
                } catch (Throwable ignored) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Object readExact(Object owner, String name) {
        Object value = readAccessor(owner, name);
        if (value != null) {
            return value;
        }
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static String[] aliases(Object owner, String name) {
        String ownerName = owner.getClass().getName();
        return switch (ownerName) {
            case "net.minecraft.world.level.levelgen.DensityFunctions$HolderHolder" -> switch (name) {
                case "function" -> new String[]{"f_208636_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunctions$Constant" -> switch (name) {
                case "value" -> new String[]{"f_208607_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunctions$BlendDensity" -> switch (name) {
                case "input" -> new String[]{"f_208546_", "m_207189_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunctions$Ap2" -> switch (name) {
                case "type" -> new String[]{"f_208397_", "m_207119_"};
                case "argument1" -> new String[]{"f_208398_", "m_207185_"};
                case "argument2" -> new String[]{"f_208399_", "m_207190_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunctions$MulOrAdd" -> switch (name) {
                case "specificType" -> new String[]{"f_208746_"};
                case "input" -> new String[]{"f_208747_", "m_207305_"};
                case "argument" -> new String[]{"f_208750_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunctions$Clamp" -> switch (name) {
                case "input" -> new String[]{"f_208584_", "m_207305_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunctions$Mapped" -> switch (name) {
                case "type" -> new String[]{"f_208654_"};
                case "input" -> new String[]{"f_208655_", "m_207305_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunctions$YClampedGradient" -> switch (name) {
                case "fromY" -> new String[]{"f_208481_"};
                case "toY" -> new String[]{"f_208482_"};
                case "fromValue" -> new String[]{"f_208483_"};
                case "toValue" -> new String[]{"f_208484_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunctions$RangeChoice" -> switch (name) {
                case "input" -> new String[]{"f_208823_"};
                case "minInclusive" -> new String[]{"f_208824_"};
                case "maxExclusive" -> new String[]{"f_208825_"};
                case "whenInRange" -> new String[]{"f_208826_"};
                case "whenOutOfRange" -> new String[]{"f_208827_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunctions$Marker" -> switch (name) {
                case "wrapped" -> new String[]{"f_208706_", "m_207056_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunctions$Noise" -> switch (name) {
                case "noise" -> new String[]{"f_208787_"};
                case "xzScale" -> new String[]{"f_208788_"};
                case "yScale" -> new String[]{"f_208789_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunctions$ShiftedNoise" -> switch (name) {
                case "shiftX" -> new String[]{"f_208924_"};
                case "shiftY" -> new String[]{"f_208925_"};
                case "shiftZ" -> new String[]{"f_208926_"};
                case "xzScale" -> new String[]{"f_208927_"};
                case "yScale" -> new String[]{"f_208928_"};
                case "noise" -> new String[]{"f_208930_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunctions$ShiftA" -> switch (name) {
                case "offsetNoise" -> new String[]{"f_208877_", "m_214040_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunctions$ShiftB" -> switch (name) {
                case "offsetNoise" -> new String[]{"f_208897_", "m_214040_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunctions$Shift" -> switch (name) {
                case "offsetNoise" -> new String[]{"f_208857_", "m_214040_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunctions$WeirdScaledSampler" -> switch (name) {
                case "input" -> new String[]{"f_208425_", "m_207189_"};
                case "noise" -> new String[]{"f_208427_"};
                case "rarityValueMapper" -> new String[]{"f_208428_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunctions$Spline" -> switch (name) {
                case "spline" -> new String[]{"f_211702_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunctions$Spline$Coordinate" -> switch (name) {
                case "function" -> new String[]{"f_224122_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.DensityFunction$NoiseHolder" -> switch (name) {
                case "noise" -> new String[]{"f_223998_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.synth.NormalNoise" -> switch (name) {
                case "valueFactor" -> new String[]{"f_75373_"};
                case "first" -> new String[]{"f_75374_"};
                case "second" -> new String[]{"f_75375_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.synth.PerlinNoise" -> switch (name) {
                case "noiseLevels" -> new String[]{"f_75390_"};
                case "amplitudes" -> new String[]{"f_75391_"};
                case "lowestFreqInputFactor" -> new String[]{"f_75393_"};
                case "lowestFreqValueFactor" -> new String[]{"f_75392_"};
                case "firstOctave" -> new String[]{"f_192867_"};
                case "maxValue" -> new String[]{"f_210641_"};
                default -> empty();
            };
            case "net.minecraft.world.level.levelgen.synth.ImprovedNoise" -> switch (name) {
                case "p" -> new String[]{"f_75324_"};
                default -> empty();
            };
            case "net.minecraft.util.CubicSpline$Constant" -> switch (name) {
                case "value" -> new String[]{"f_184308_"};
                default -> empty();
            };
            case "net.minecraft.util.CubicSpline$Multipoint" -> switch (name) {
                case "coordinate" -> new String[]{"f_184319_"};
                case "locations" -> new String[]{"f_184320_"};
                case "values" -> new String[]{"f_184321_"};
                case "derivatives" -> new String[]{"f_184322_"};
                default -> empty();
            };
            default -> empty();
        };
    }

    private static String[] empty() {
        return new String[0];
    }

    private static Object readAccessor(Object owner, String name) {
        try {
            Method method = owner.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(owner);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static double readDouble(Object owner, String name, double fallback) {
        Object value = read(owner, name);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    static int readInt(Object owner, String name, int fallback) {
        Object value = read(owner, name);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
