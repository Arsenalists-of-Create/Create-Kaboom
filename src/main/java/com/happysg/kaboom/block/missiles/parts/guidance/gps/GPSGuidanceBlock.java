package com.happysg.kaboom.block.missiles.parts.guidance.gps;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.block.missiles.parts.MissilePartShapes;
import com.happysg.kaboom.block.missiles.parts.guidance.IGuidanceBlock;
import com.happysg.kaboom.client.ClientScreenOpener;

import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.happysg.kaboom.registry.ModBlocks;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GPSGuidanceBlock extends RotatedPillarBlock implements IBE<GPSGuidanceBlockEntity>, IGuidanceBlock, IMissileComponent {
    private final MissileSize missileSize;

    public GPSGuidanceBlock(Properties properties, MissileSize missileSize) {
        super(properties);
        this.missileSize = missileSize;
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return this == ModBlocks.GPS_GUIDANCE_SMALL.get()
                ? MissilePartShapes.small(state.getValue(AXIS))
                : MissilePartShapes.FULL;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return getShape(s, l, p, c);
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        openScreen(level, pos);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        openScreen(level, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void openScreen(Level level, BlockPos pos) {
        if (level.isClientSide) {
            ClientScreenOpener.openGpsGuidance(pos);
        }
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
    public Class<GPSGuidanceBlockEntity> getBlockEntityClass() {
        return GPSGuidanceBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GPSGuidanceBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.GPS_GUIDANCE.get();
    }

}
