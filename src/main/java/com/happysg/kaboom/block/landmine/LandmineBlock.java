package com.happysg.kaboom.block.landmine;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.happysg.kaboom.items.MineFuze;

import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;

public class LandmineBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {

    public static final BooleanProperty FUZED = BooleanProperty.create("fuzed");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    protected static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 3.0D, 14.0D);

    public LandmineBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FUZED, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FUZED, WATERLOGGED);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof LandmineBlockEntity landmineBE) {
            if (landmineBE.getCamouflage() != null && landmineBE.getCamouflage().getBlock() instanceof com.simibubi.create.content.decoration.copycat.CopycatBlock) {
                return Block.box(0.0D, 0.0D, 0.0D, 16.0D, 3.0D, 16.0D);
            }
        }
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return com.happysg.kaboom.registry.ModBlockEntityTypes.LANDMINE_BE.get().create(pos, state);
    }

    public void explodeMine(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof LandmineBlockEntity landmineBE) {
            landmineBE.setCamouflage(null);
        }
        level.removeBlock(pos, false);
        com.happysg.kaboom.compat.CBCExplosionHelper.createShellExplosion(
                level, null, null,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                3.0f, false,
                rbasamoyai.createbigcannons.config.CBCConfigs.server().munitions.damageRestriction.get().explosiveInteraction()
        );
    }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide ? null : (l, p, s, be) -> {
            if (be instanceof LandmineBlockEntity landmineBE) {
                landmineBE.tick();
            }
        };
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof LandmineBlockEntity landmineBE) {
                BlockState camouflage = landmineBE.getCamouflage();
                if (camouflage != null) {
                    if (camouflage.getBlock() instanceof com.simibubi.create.content.decoration.copycat.CopycatBlock) {
                        Block.popResource(level, pos, new ItemStack(com.simibubi.create.AllBlocks.COPYCAT_PANEL.get()));
                        if (landmineBE.getCopycatMaterial() != null) {
                            ItemStack dropped = landmineBE.getCopycatConsumedItem();
                            if (!dropped.isEmpty()) {
                                Block.popResource(level, pos, dropped.copy());
                            } else {
                                Block.popResource(level, pos, new ItemStack(landmineBE.getCopycatMaterial().getBlock()));
                            }
                        }
                    } else {
                        Block.popResource(level, pos, new ItemStack(camouflage.getBlock()));
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public net.minecraft.world.level.block.state.BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        net.minecraft.world.level.material.FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState().setValue(WATERLOGGED, fluidstate.getType() == Fluids.WATER);
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide) {
            explodeMine(level, pos);
        }
        super.onBlockExploded(state, level, pos, explosion);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(FUZED) && !player.isCreative()) {
            explodeMine(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof LandmineBlockEntity landmineBE) {
            if (player.isShiftKeyDown() && (stack.isEmpty() || com.simibubi.create.AllItems.WRENCH.isIn(stack))) {
                if (landmineBE.getCamouflage() != null) {
                    if (landmineBE.getCamouflage().getBlock() instanceof com.simibubi.create.content.decoration.copycat.CopycatBlock) {
                        if (landmineBE.getCopycatMaterial() != null) {
                            if (!level.isClientSide) {
                                ItemStack dropped = landmineBE.getCopycatConsumedItem();
                                if (dropped.isEmpty()) {
                                    dropped = new ItemStack(landmineBE.getCopycatMaterial().getBlock());
                                } else {
                                    dropped = dropped.copy();
                                }
                                landmineBE.setCopycatMaterial(null, ItemStack.EMPTY);
                                if (!player.addItem(dropped)) {
                                    Block.popResource(level, pos, dropped);
                                }
                                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
                            } else {
                                landmineBE.setCopycatMaterial(null, ItemStack.EMPTY);
                            }
                            return ItemInteractionResult.sidedSuccess(level.isClientSide);
                        } else {
                            if (!level.isClientSide) {
                                ItemStack panelStack = new ItemStack(com.simibubi.create.AllBlocks.COPYCAT_PANEL.get());
                                landmineBE.setCamouflage(null);
                                if (!player.addItem(panelStack)) {
                                    Block.popResource(level, pos, panelStack);
                                }
                                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
                            } else {
                                landmineBE.setCamouflage(null);
                            }
                            return ItemInteractionResult.sidedSuccess(level.isClientSide);
                        }
                    } else {
                        if (!level.isClientSide) {
                            ItemStack bushStack = new ItemStack(landmineBE.getCamouflage().getBlock());
                            landmineBE.setCamouflage(null);
                            if (!player.addItem(bushStack)) {
                                Block.popResource(level, pos, bushStack);
                            }
                            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.GRASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
                        } else {
                            landmineBE.setCamouflage(null);
                        }
                        return ItemInteractionResult.sidedSuccess(level.isClientSide);
                    }
                }
            }

            if (!player.isShiftKeyDown() && landmineBE.getCamouflage() != null && landmineBE.getCamouflage().getBlock() instanceof com.simibubi.create.content.decoration.copycat.CopycatPanelBlock copycatPanel) {
                if (stack.getItem() instanceof net.minecraft.world.item.BlockItem) {
                    BlockState accepted = copycatPanel.getAcceptedBlockState(level, pos, stack, hitResult.getDirection());
                    if (accepted != null) {
                        accepted = copycatPanel.prepareMaterial(level, pos, landmineBE.getCamouflage(), player, hand, hitResult, accepted);
                    }
                    if (accepted != null) {
                        if (!level.isClientSide) {
                            if (landmineBE.getCopycatMaterial() != null) {
                                ItemStack dropped = landmineBE.getCopycatConsumedItem();
                                if (dropped.isEmpty()) {
                                    dropped = new ItemStack(landmineBE.getCopycatMaterial().getBlock());
                                } else {
                                    dropped = dropped.copy();
                                }
                                if (!player.addItem(dropped)) {
                                    Block.popResource(level, pos, dropped);
                                }
                            }
                            ItemStack consumed = stack.copyWithCount(1);
                            landmineBE.setCopycatMaterial(accepted, consumed);
                            net.minecraft.world.level.block.SoundType sound = accepted.getSoundType(level, pos, player);
                            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
                            if (!player.isCreative()) {
                                stack.shrink(1);
                            }
                        } else {
                            landmineBE.setCopycatMaterial(accepted, stack.copyWithCount(1));
                        }
                        return ItemInteractionResult.sidedSuccess(level.isClientSide);
                    }
                }
            }

            if (!player.isShiftKeyDown() && landmineBE.getCamouflage() == null && stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                Block block = blockItem.getBlock();
                if (block instanceof com.simibubi.create.content.decoration.copycat.CopycatPanelBlock) {
                    BlockState panelState = com.simibubi.create.AllBlocks.COPYCAT_PANEL.getDefaultState().setValue(com.simibubi.create.content.decoration.copycat.CopycatPanelBlock.FACING, Direction.UP);
                    if (!level.isClientSide) {
                        landmineBE.setCamouflage(panelState);
                        net.minecraft.world.level.block.SoundType sound = panelState.getSoundType(level, pos, player);
                        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                        if (!player.isCreative()) {
                            stack.shrink(1);
                        }
                    } else {
                        landmineBE.setCamouflage(panelState);
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                } else if (block instanceof net.minecraft.world.level.block.BushBlock) {
                    BlockState stateBelow = level.getBlockState(pos.below());
                    if (stateBelow.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) || stateBelow.is(net.minecraft.world.level.block.Blocks.DIRT)) {
                        if (!level.isClientSide) {
                            landmineBE.setCamouflage(block.defaultBlockState());
                            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                            if (!player.isCreative()) {
                                stack.shrink(1);
                            }
                        } else {
                            landmineBE.setCamouflage(block.defaultBlockState());
                        }
                        return ItemInteractionResult.sidedSuccess(level.isClientSide);
                    }
                }
            }
        }

        if (state.getValue(FUZED) && stack.getItem() instanceof net.minecraft.world.item.ShovelItem) {
            boolean hasSilkTouch = false;
            net.minecraft.world.item.enchantment.ItemEnchantments enchantments = stack.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
            if (enchantments != null) {
                for (net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder : enchantments.keySet()) {
                    if (holder.is(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH)) {
                        hasSilkTouch = true;
                        break;
                    }
                }
            }
            if (hasSilkTouch) {
                if (!level.isClientSide) {
                    double explodeChance = 0.20;
                    String mat = stack.getItem().toString().toLowerCase();
                    if (mat.contains("wood")) explodeChance = 0.40;
                    else if (mat.contains("stone")) explodeChance = 0.30;
                    else if (mat.contains("iron")) explodeChance = 0.20;
                    else if (mat.contains("gold")) explodeChance = 0.45;
                    else if (mat.contains("diamond")) explodeChance = 0.10;
                    else if (mat.contains("netherite")) explodeChance = 0.03;

                    if (level.random.nextDouble() < explodeChance) {
                        explodeMine(level, pos);
                    } else {
                        if (be instanceof LandmineBlockEntity landmineBE) {
                            ItemStack fuze = landmineBE.getFuze();
                            if (!fuze.isEmpty()) {
                                Block.popResource(level, pos, fuze);
                            }
                            landmineBE.setFuze(ItemStack.EMPTY);
                        }
                        level.setBlock(pos, state.setValue(FUZED, false), 3);
                        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        } else if (!state.getValue(FUZED) && stack.getItem() instanceof MineFuze) {
            if (!level.isClientSide) {
                if (be instanceof LandmineBlockEntity landmineBE) {
                    landmineBE.setFuze(stack.copyWithCount(1));
                    level.setBlock(pos, state.setValue(FUZED, true), 3);
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return false;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && state.getValue(FUZED)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof LandmineBlockEntity landmineBE) {
                ItemStack fuzeStack = landmineBE.getFuze();
                if (fuzeStack.getItem() instanceof MineFuze) {
                    int sensitivity = MineFuze.getSensitivity(fuzeStack);
                    
                    String entityName = entity.getType().getDescriptionId().toLowerCase();
                    boolean isMassiveEntity = entityName.contains("sable") ||
                                              entityName.contains("ship") ||
                                              entityName.contains("contraption");

                    double centerX = pos.getX() + 0.5;
                    double centerZ = pos.getZ() + 0.5;
                    double dx = Math.abs(entity.getX() - centerX);
                    double dz = Math.abs(entity.getZ() - centerZ);
                    
                    if (isMassiveEntity || (dx < 0.35 && dz < 0.35)) {
                        float weight = -1.0f;
                        if (isMassiveEntity) {
                            weight = 1000.0f;
                        } else if (entity instanceof LivingEntity living) {
                            float width = living.getBbWidth();
                            float height = living.getBbHeight();
                            weight = width * width * height * 100.0f;
                        }
                        
                        if (weight >= sensitivity) {
                            explodeMine(level, pos);
                        }
                    }
                }
            }
        }
        super.entityInside(state, level, pos, entity);
    }
}
