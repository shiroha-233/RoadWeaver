package net.countered.settlementroads.features.decoration;

import net.countered.settlementroads.features.decoration.util.BiomeWoodAware;
import net.countered.settlementroads.helpers.Records;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;

public class DistanceSignDecoration extends OrientedDecoration implements BiomeWoodAware {
    private final boolean isEnd;
    private final String signText;
    private Records.WoodAssets wood;

    public DistanceSignDecoration(BlockPos pos, Vec3i direction, WorldGenLevel world, Boolean isEnd, String distanceText) {
        super(pos, direction, world);
        this.isEnd = isEnd;
        this.signText = distanceText;
    }

    @Override
    public void place() {
        if (!placeAllowed()) return;

        int rotation = getCardinalRotationFromVector(getOrthogonalVector(), isEnd);
        DirectionProperties props = getDirectionProperties(rotation);

        BlockPos basePos = this.getPos();
        WorldGenLevel world = this.getWorld();

        BlockPos signPos = basePos.above(2).relative(props.offsetDirection.getOpposite());
        this.setBlock(signPos,
            wood.hangingSign().defaultBlockState()
                .setValue(BlockStateProperties.ROTATION_16, (int) rotation)
                .setValue(BlockStateProperties.ATTACHED, true));

        updateSignText(world, signPos);
        placeFenceStructure(basePos, props);
    }

    private void placeFenceStructure(BlockPos pos, DirectionProperties props) {
        WorldGenLevel world = this.getWorld();

        this.setBlock(pos.above(3).relative(props.offsetDirection.getOpposite()),
            wood.fence().defaultBlockState().setValue(props.directionProperty, true));
        this.setBlock(pos.above(0), wood.fence().defaultBlockState());
        this.setBlock(pos.above(1), wood.fence().defaultBlockState());
        this.setBlock(pos.above(2), wood.fence().defaultBlockState());
        this.setBlock(pos.above(3), wood.fence().defaultBlockState().setValue(props.reverseDirectionProperty, true));
    }

    private void updateSignText(WorldGenLevel world, BlockPos signPos) {
        // 检查方块是否为悬挂标志 - 使用Forge兼容的方法
        net.minecraft.world.level.block.Block block = world.getBlockState(signPos).getBlock();
        if (!block.asItem().toString().contains("hanging_sign") &&
            !block.getDescriptionId().contains("hanging_sign")) {
            return;
        }

        // 更新标志文本（需要在服务器端执行）
        if (world.getServer() != null) {
            world.getServer().execute(() -> {
                BlockEntity blockEntity = world.getBlockEntity(signPos);

                if (blockEntity instanceof HangingSignBlockEntity hangingSign) {
                    // 设置正面文本
                    SignText frontText = hangingSign.getText(true);
                    frontText = frontText.setMessage(0, Component.literal("----------"));
                    frontText = frontText.setMessage(1, Component.literal("Next Village"));
                    frontText = frontText.setMessage(2, Component.literal(signText + "m"));
                    frontText = frontText.setMessage(3, Component.literal("----------"));
                    hangingSign.setText(frontText, true);

                    // 设置背面文本
                    SignText backText = hangingSign.getText(false);
                    backText = backText.setMessage(0, Component.literal("----------"));
                    backText = backText.setMessage(1, Component.literal("Welcome"));
                    backText = backText.setMessage(2, Component.literal("traveller"));
                    backText = backText.setMessage(3, Component.literal("----------"));
                    hangingSign.setText(backText, false);

                    hangingSign.setChanged();
                }
            });
        }
    }

    @Override
    public void setWoodType(Records.WoodAssets assets) {
        this.wood = assets;
    }
}
