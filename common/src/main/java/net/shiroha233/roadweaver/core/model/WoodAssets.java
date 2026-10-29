package net.shiroha233.roadweaver.core.model;

import net.minecraft.world.level.block.Block;

/**
 * 木材资源组合（栅栏、悬挂告示牌、木板）
 */
public record WoodAssets(Block fence, Block hangingSign, Block planks) {}
