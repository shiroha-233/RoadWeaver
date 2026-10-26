package net.shiroha233.roadweaver;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.shiroha233.roadweaver.datagen.ModWorldGenerator;

/**
 * Fabric 数据生成器入口
 */
public class RoadWeaverDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
        pack.addProvider(ModWorldGenerator::new);
    }
}
