package com.happysg.kaboom.block.missiles.parts.guidance.arad;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.block.missiles.parts.MissilePartShapes;
import com.happysg.kaboom.block.missiles.parts.guidance.IGuidanceBlock;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.happysg.kaboom.registry.ModBlocks;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ARADGuidanceBlock extends RotatedPillarBlock implements IBE<ARADGuidanceBlockEntity>, IGuidanceBlock, IMissileComponent {
    private final MissileSize missileSize;

    public ARADGuidanceBlock(Properties properties, MissileSize missileSize) {
        super(properties);
        this.missileSize = missileSize;
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this == ModBlocks.ARAD_GUIDANCE_SMALL.get()
                ? MissilePartShapes.small(state.getValue(AXIS))
                : MissilePartShapes.FULL;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getNearestLookingDirection().getAxis());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ARADGuidanceBlockEntity guidance) {
            ARADTargetAcquisitionMode mode = guidance.toggleAcquisitionMode();
            player.displayClientMessage(Component.translatable(mode.translationKey()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public MissilePartType getPartType() {
        return MissilePartType.GUIDANCE;
    }

    @Override
    public MissileSize getMissileSize() {
        return missileSize;
    }

    @Override
    public Class<ARADGuidanceBlockEntity> getBlockEntityClass() {
        return ARADGuidanceBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ARADGuidanceBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.ARAD_GUIDANCE.get();
    }
}
