package com.happysg.kaboom.block.missiles.parts.warhead;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombProjectile;
import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.block.missiles.parts.HugeMissileReservations;
import com.happysg.kaboom.block.missiles.parts.MissilePartShapes;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.happysg.kaboom.registry.ModProjectiles;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedProjectileBlock;

import javax.annotation.Nullable;
import java.util.List;

public class AbstractMissileWarhead extends FuzedProjectileBlock<MissileWarheadBlockEntity, MissileWarheadProjectile> {
    private final AerialBombProjectile.BombType bombType;
    private final int bombSize;
    private final MissileSize missileSize;

    public AbstractMissileWarhead(Properties properties, AerialBombProjectile.BombType bombType, int bombSize,
                                  MissileSize missileSize) {
        super(properties);
        this.bombType = bombType;
        this.bombSize = Math.max(1, bombSize);
        this.missileSize = missileSize;
    }

    @Override
    protected MapCodec<? extends AbstractMissileWarhead> codec() {
        return simpleCodec(properties -> new AbstractMissileWarhead(properties, bombType, bombSize, missileSize));
    }

    public AerialBombProjectile.BombType getBombType() {
        return bombType;
    }

    public int getBombSize() {
        return bombSize;
    }

    public MissileSize getMissileSize() {
        return missileSize;
    }

    public boolean isFluidWarhead() {
        return bombType == AerialBombProjectile.BombType.FLUID;
    }

    public int getFluidCapacityMb() {
        return isFluidWarhead() ? 12_000 / bombSize : 0;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placed = super.getStateForPlacement(context);
        return placed == null
                ? null
                : placed.setValue(FACING, context.getNearestLookingDirection().getOpposite());
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return missileSize == MissileSize.HUGE
                ? MissilePartShapes.hugeWarhead(state.getValue(FACING))
                : super.getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public boolean isBaseFuze() {
        return false;
    }

    @Override
    public boolean canBeLoaded(BlockState state, Direction.Axis axis) {
        return false;
    }

    @Override
    public EntityType<? extends MissileWarheadProjectile> getAssociatedEntityType() {
        return ModProjectiles.MISSILE_WARHEAD_PROJECTILE.get();
    }

    @Override
    public MissileWarheadProjectile getProjectile(Level level, List<StructureBlockInfo> blocks) {
        MissileWarheadProjectile projectile = (MissileWarheadProjectile) super.getProjectile(level, blocks);
        StructureBlockInfo info = blocks.isEmpty() ? null : blocks.getFirst();
        BlockState state = info == null ? defaultBlockState() : info.state();
        FluidStack payload = info == null
                ? FluidStack.EMPTY
                : MissileWarheadBlockEntity.readFluidPayload(info.nbt(), level.registryAccess(), getFluidCapacityMb());
        return configure(projectile, state, payload);
    }

    @Override
    public MissileWarheadProjectile getProjectile(Level level, ItemStack stack) {
        MissileWarheadProjectile projectile = (MissileWarheadProjectile) super.getProjectile(level, stack);
        return configure(projectile, defaultBlockState(), FluidStack.EMPTY);
    }

    @Override
    public MissileWarheadProjectile getProjectile(Level level, BlockPos pos, BlockState state) {
        MissileWarheadProjectile projectile = (MissileWarheadProjectile) super.getProjectile(level, pos, state);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        FluidStack payload = blockEntity instanceof MissileWarheadBlockEntity warhead
                ? warhead.getContainedFluidCopy()
                : FluidStack.EMPTY;
        return configure(projectile, state, payload);
    }

    private MissileWarheadProjectile configure(@Nullable MissileWarheadProjectile projectile,
                                                BlockState state,
                                                FluidStack payload) {
        if (projectile != null) {
            projectile.configure(state, bombType, bombSize, payload);
        }
        return projectile;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hit) {
        if (isFluidWarhead()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MissileWarheadBlockEntity warhead) {
                boolean canEmpty = GenericItemEmptying.canItemBeEmptied(level, held);
                boolean canFill = GenericItemFilling.canItemBeFilled(level, held);

                if (canEmpty && warhead.tryEmptyItemIntoTank(level, player, hand, held)) {
                    if (!level.isClientSide) {
                        player.swing(hand, true);
                        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS,
                                1.0F, 0.9F + level.random.nextFloat() * 0.2F);
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }

                if (canFill && warhead.tryFillItemFromTank(level, player, hand, held)) {
                    if (!level.isClientSide) {
                        player.swing(hand, true);
                        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS,
                                1.0F, 0.9F + level.random.nextFloat() * 0.2F);
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        }

        return super.useItemOn(held, state, level, pos, player, hand, hit);
    }

    @Override
    public Class<MissileWarheadBlockEntity> getBlockEntityClass() {
        return MissileWarheadBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MissileWarheadBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.MISSILE_WARHEAD.get();
    }

    @Override
    public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state, BlockEntityType<S> type) {
        return createTickerHelper(type, ModBlockEntityTypes.MISSILE_WARHEAD.get(),
                (tickLevel, pos, tickState, blockEntity) -> blockEntity.tick());
    }
}
