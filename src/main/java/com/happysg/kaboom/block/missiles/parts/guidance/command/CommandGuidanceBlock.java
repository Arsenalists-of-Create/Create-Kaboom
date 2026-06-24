package com.happysg.kaboom.block.missiles.parts.guidance.command;

import com.happysg.kaboom.block.missiles.assembly.IMissileComponent;
import com.happysg.kaboom.block.missiles.parts.guidance.IGuidanceBlock;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.happysg.kaboom.registry.ModBlocks;
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
    private static final VoxelShape SMALL = Block.box(3, 0, 3, 13, 16, 13);
    private static final VoxelShape FULL = Block.box(0, 0, 0, 16, 16, 16);

    public CommandGuidanceBlock(Properties properties) {
        super(properties);
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
        return this == ModBlocks.COMMAND_GUIDANCE_SMALL.get() ? SMALL : FULL;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return this == ModBlocks.COMMAND_GUIDANCE_SMALL.get() ? SMALL : FULL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
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
}
