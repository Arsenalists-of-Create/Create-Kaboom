package com.happysg.kaboom.block.missiles.parts.guidance.command;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.block.missiles.parts.HugeMissileReservations;
import com.happysg.kaboom.block.missiles.parts.MissilePartShapes;
import com.happysg.kaboom.block.missiles.parts.guidance.IGuidanceBlock;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public class CommandGuidanceBlock extends RotatedPillarBlock implements IBE<CommandGuidanceBlockEntity>, IGuidanceBlock, IMissileComponent {
    private final MissileSize missileSize;

    public CommandGuidanceBlock(Properties properties, MissileSize missileSize) {
        super(properties);
        this.missileSize = missileSize;
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CommandGuidanceBlockEntity blockEntity) {
            blockEntity.setChanged();
        }
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(this);
        level.getBlockEntity(pos, getBlockEntityType())
                .filter(CommandGuidanceBlockEntity::hasNetworkControllerPos)
                .ifPresent(blockEntity -> blockEntity.saveToItem(stack, level.registryAccess()));
        return stack;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (!(blockEntity instanceof CommandGuidanceBlockEntity commandGuidance) || !commandGuidance.hasNetworkControllerPos()) {
            return drops;
        }

        for (ItemStack drop : drops) {
            if (drop.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == this) {
                commandGuidance.saveToItem(drop, params.getLevel().registryAccess());
            }
        }

        return drops;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction.Axis axis = state.getValue(AXIS);
        return switch (missileSize) {
            case SMALL -> MissilePartShapes.small(axis);
            case LARGE -> MissilePartShapes.FULL;
            case HUGE -> MissilePartShapes.hugeBody(axis);
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
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
    public Class<CommandGuidanceBlockEntity> getBlockEntityClass() {
        return CommandGuidanceBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CommandGuidanceBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.COMMAND_GUIDANCE.get();
    }

    @Override
    public MissilePartType getPartType() {
        return MissilePartType.GUIDANCE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (missileSize == MissileSize.HUGE) {
            HugeMissileReservations.reconcileOwner(level, pos, state);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (missileSize == MissileSize.HUGE && !state.is(newState.getBlock())) {
            HugeMissileReservations.removeForOwner(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public MissileSize getMissileSize() {
        return missileSize;
    }
}
