package net.shiroha233.roadweaver.worldgen.road.decoration.system;

import net.shiroha233.roadweaver.worldgen.road.decoration.base.Decoration;
import net.shiroha233.roadweaver.worldgen.road.decoration.material.BiomeWoodAware;
import net.shiroha233.roadweaver.worldgen.road.decoration.material.WoodSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Set;

/**
 * 装饰执行器
 */
public final class DecorationExecutor {
    private DecorationExecutor() {}
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    public static void tryPlaceDecorations(Set<Decoration> positions) {
        if (positions.isEmpty()) return;
        Iterator<Decoration> it = positions.iterator();
        while (it.hasNext()) {
            Decoration dec = it.next();
            if (dec == null) { it.remove(); continue; }
            try {
                if (dec instanceof BiomeWoodAware aware) {
                    aware.setWoodType(WoodSelector.forBiome(dec.getWorld(), dec.getPos()));
                }
                dec.place();
            } catch (Throwable t) {
                LOGGER.warn("Decoration placement failed: {}", dec.getClass().getSimpleName(), t);
            } finally {
                it.remove();
            }
        }
    }
}
