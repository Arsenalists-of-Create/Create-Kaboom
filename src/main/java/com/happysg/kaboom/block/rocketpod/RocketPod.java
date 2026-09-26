package com.happysg.kaboom.block.rocketpod;

import com.happysg.kaboom.items.rocket.RocketItem;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.happysg.kaboom.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.cannons.CannonContraptionProviderBlock;
import rbasamoyai.createbigcannons.cannons.InteractableCannonBlock;
import rbasamoyai.createbigcannons.crafting.casting.CannonCastShape;
import com.simibubi.create.content.contraptions.Contraption;

public class RocketPod extends DirectionalBlock
        implements CannonContraptionProviderBlock, EntityBlock, InteractableCannonBlock {

    public RocketPod(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return simpleCodec(RocketPod::new);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }




    @Override
    public AbstractMountedCannonContraption getCannonContraption() {
        return new RocketPodContraption();
    }

    @Override
    public Direction getFacing(BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    public CannonCastShape getCannonShape() {
        return CannonCastShape.VERY_SMALL;
    }

    @Override
    public boolean isComplete(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getBlock() != ModBlocks.ROCKET_POD_REAR.get()) {
            return null;
        }
        return new RocketPodBlockEntity(ModBlockEntityTypes.ROCKET_POD_REAR.get(), pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (!isRear(state) || !(stack.getItem() instanceof RocketItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof RocketPodBlockEntity rear
                && rear.insertRocket(stack, false).getCount() < stack.getCount()) {
            if (!player.isCreative()) {
                stack.shrink(1);
            }
            syncWorldRear(level, pos, state);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hit) {
        if (!isRear(state) || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof RocketPodBlockEntity rear) {
            ItemStack rocket = rear.extractNextRocket(false);
            if (!rocket.isEmpty()) {
                giveToPlayer(player, rocket);
                syncWorldRear(level, pos, state);
                level.playSound(player, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean onInteractWhileAssembled(Player player, BlockPos localPos, Direction side,
                                             InteractionHand hand, Level level, Contraption contraption,
                                             BlockEntity blockEntity, StructureBlockInfo info,
                                             PitchOrientedContraptionEntity entity) {
        if (!(contraption instanceof RocketPodContraption rocketPod)
                || !(blockEntity instanceof RocketPodBlockEntity rear)
                || !isRear(info.state())) {
            return false;
        }

        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof RocketItem) {
            if (!level.isClientSide
                    && rear.insertRocket(held, false).getCount() < held.getCount()) {
                if (!player.isCreative()) {
                    held.shrink(1);
                }
                rocketPod.syncRear(localPos, entity);
                playAssembledSound(level, entity, localPos, SoundEvents.ITEM_FRAME_ADD_ITEM);
            }
            return true;
        }

        if (held.isEmpty() && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                ItemStack rocket = rocketPod.extractNextRocket(localPos, false, entity);
                if (!rocket.isEmpty()) {
                    giveToPlayer(player, rocket);
                    playAssembledSound(level, entity, localPos, SoundEvents.ITEM_FRAME_REMOVE_ITEM);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()
                && level.getBlockEntity(pos) instanceof RocketPodBlockEntity rear) {
            for (ItemStack rocket : rear.removeAllRockets()) {
                Block.popResource(level, pos, rocket);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private static boolean isRear(BlockState state) {
        return state.getBlock() == ModBlocks.ROCKET_POD_REAR.get();
    }

    private static void syncWorldRear(Level level, BlockPos pos, BlockState state) {
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    private static void playAssembledSound(Level level, PitchOrientedContraptionEntity entity,
                                           BlockPos localPos, SoundEvent sound) {
        Vec3 soundPos = entity.toGlobalVector(Vec3.atCenterOf(localPos), 0);
        level.playSound(null, soundPos.x, soundPos.y, soundPos.z,
                sound, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    private static void giveToPlayer(Player player, ItemStack stack) {
        if (player.addItem(stack)) {
            return;
        }
        ItemEntity dropped = player.drop(stack, false);
        if (dropped != null) {
            dropped.setNoPickUpDelay();
            dropped.setTarget(player.getUUID());
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }
}
