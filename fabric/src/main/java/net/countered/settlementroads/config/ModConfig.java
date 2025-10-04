package net.countered.settlementroads.config;

import eu.midnightdust.lib.config.MidnightConfig;

public class ModConfig extends MidnightConfig {

    @Entry(category = "structures")
    public static String structureToLocate = "#minecraft:village";

    @Entry(category = "structures", min = 50, max = 200)
    public static int structureSearchRadius = 100;

    @Entry(category = "pre-generation")
    public static int initialLocatingCount = 7;

    @Entry(category = "roads")
    public static int averagingRadius = 1;

    @Entry(category = "roads")
    public static boolean allowArtificial = true;

    @Entry(category = "roads")
    public static boolean allowNatural = true;

    @Entry(category = "roads")
    public static boolean placeWaypoints = false;

    @Entry(category = "roads")
    public static boolean placeRoadFences = true;

    @Entry(category = "roads")
    public static boolean placeSwings = true;

    @Entry(category = "roads")
    public static boolean placeBenches = true;

    @Entry(category = "roads")
    public static boolean placeGloriettes = true;

    @Entry(category = "roads", min = 3, max = 8)
    public static int structureDistanceFromRoad = 4;

    @Entry(category = "roads", min = 3, max = 10)
    public static int maxHeightDifference = 5;

    @Entry(category = "roads", min = 2, max = 10)
    public static int maxTerrainStability = 4;

    @Entry(category = "pre-generation", min = 1, max = 10)
    public static int maxConcurrentRoadGeneration = 3;
}