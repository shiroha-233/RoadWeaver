package net.countered.settlementroads.config;

import eu.midnightdust.lib.config.MidnightConfig;

public class ModConfig extends MidnightConfig {

    @Entry(category = "structures", name = "Maximum Number of structures to locate")
    public static int maxLocatingCount = 100;

    @Entry(category = "structures", name = "Structure to locate")
    public static String structureToLocate = "#minecraft:village";

    @Entry(category = "pre-generation", name = "Number of structures to locate on world load")
    public static int initialLocatingCount = 7;

    @Entry(category = "roads", name = "Distance between buoys")
    public static int distanceBetweenBuoys = 25;

    @Entry(category = "roads", name = "Artificial road averaging")
    public static int averagingRadius = 1;

    @Entry(category = "roads", name = "Allow artificial roads")
    public static boolean allowArtificial = true;

    @Entry(category = "roads", name = "Allow natural roads")
    public static boolean allowNatural = true;

    @Entry(category = "roads", name = "Place waypoints instead of roads")
    public static boolean placeWaypoints = false;
}