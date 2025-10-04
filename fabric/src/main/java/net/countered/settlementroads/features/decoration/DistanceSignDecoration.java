package net.countered.settlementroads.features.decoration;

import net.countered.settlementroads.features.decoration.util.BiomeWoodAware;
import net.countered.settlementroads.helpers.Records;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HangingSignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.StructureWorldAccess;

import java.util.Objects;

public class DistanceSignDecoration extends OrientedDecoration implements BiomeWoodAware {
    private final boolean isStart;
    private final String signText;
    private Records.WoodAssets wood;

    public DistanceSignDecoration(BlockPos pos, Vec3i direction, StructureWorldAccess world, Boolean isStart, String distanceText) {
        super(pos, direction, world);
        this.isStart = isStart;
        this.signText = distanceText;
    }

    @Override
    public void place() {
        if (!placeAllowed()) return;

        int rotation = getCardinalRotationFromVector(getOrthogonalVector(), isStart);
        DirectionProperties props = getDirectionProperties(rotation);

        BlockPos basePos = this.getPos();
        StructureWorldAccess world = this.getWorld();

        BlockPos signPos = basePos.up(2).offset(props.offsetDirection.getOpposite());
        world.setBlockState(signPos, wood.hangingSign().getDefaultState()
                .with(Properties.ROTATION, rotation)
                .with(Properties.ATTACHED, true), 3);
        updateSigns(world, signPos, signText);

        placeFenceStructure(basePos, props);
    }

    private void placeFenceStructure(BlockPos pos, DirectionProperties props) {
        StructureWorldAccess world = this.getWorld();

        world.setBlockState(pos.up(3).offset(props.offsetDirection.getOpposite()), wood.fence().getDefaultState().with(props.directionProperty, true), 3);
        world.setBlockState(pos.up(0), wood.fence().getDefaultState(), 3);
        world.setBlockState(pos.up(1), wood.fence().getDefaultState(), 3);
        world.setBlockState(pos.up(2), wood.fence().getDefaultState(), 3);
        world.setBlockState(pos.up(3), wood.fence().getDefaultState().with(props.reverseDirectionProperty, true), 3);
    }

    private void updateSigns(StructureWorldAccess structureWorldAccess, BlockPos surfacePos, String text) {
        Objects.requireNonNull(structureWorldAccess.getServer()).execute( () -> {
            BlockEntity signEntity = structureWorldAccess.getBlockEntity(surfacePos);
            if (signEntity instanceof HangingSignBlockEntity signBlockEntity) {
                signBlockEntity.setWorld(structureWorldAccess.toServerWorld());
                SignText signText = signBlockEntity.getText(true);
                signText = (signText.withMessage(0, Text.literal("----------")));
                signText = (signText.withMessage(1, Text.literal("Next Village")));
                signText = (signText.withMessage(2, Text.literal(text + "m")));
                signText = (signText.withMessage(3, Text.literal("----------")));
                signBlockEntity.setText(signText, true);

                SignText signTextBack = signBlockEntity.getText(false);
                signTextBack = signTextBack.withMessage(0, Text.of("----------"));
                signTextBack = signTextBack.withMessage(1, Text.of("Welcome"));
                signTextBack = signTextBack.withMessage(2, Text.of("traveller"));
                signTextBack = signTextBack.withMessage(3, Text.of("----------"));
                signBlockEntity.setText(signTextBack, false);

                signBlockEntity.markDirty();
            }
        });
    }

    @Override
    public void setWoodType(Records.WoodAssets assets) {
        this.wood = assets;
    }
}
