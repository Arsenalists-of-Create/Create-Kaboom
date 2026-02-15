package com.happysg.kaboom.block.aerialBombs.baseTypes;

import com.happysg.kaboom.compat.vs2.VS2Utils;
import com.happysg.kaboom.registry.ModProjectiles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;


public class AerialBombBlockEntity extends FuzedBlockEntity {

    public AerialBombBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
    public void activate() {
        if (level == null || level.isClientSide) return;

        BlockState state = getBlockState();
        AerialBombProjectile projectile = ModProjectiles.AERIAL_BOMB_PROJECTILE.create(level);
        if (projectile == null) return;

        projectile.setPos(VS2Utils.getWorldPos(this).below().getCenter());
        projectile.setState(state);

        ItemStack fuzeStack = getFuze();
        projectile.setFuzeStack(fuzeStack.isEmpty() ? ItemStack.EMPTY : fuzeStack.copy());

        int type = state.getValue(AerialBombBlock.TYPE);
        int count = state.getValue(AerialBombBlock.COUNT);

        projectile.setSize(state.getValue(AerialBombBlock.SIZE));
        projectile.setBombType(switch (type) {
            case 1 -> AerialBombProjectile.BombType.HE;
            case 2 -> AerialBombProjectile.BombType.AP;
            case 3 -> AerialBombProjectile.BombType.FRAG;
            case 4 -> AerialBombProjectile.BombType.INCENDIARY;
            case 7 -> AerialBombProjectile.BombType.CLUSTER;
            default -> AerialBombProjectile.BombType.HE;
        });

        level.addFreshEntity(projectile);

        if (count > 1) {
            level.setBlock(worldPosition, state.setValue(AerialBombBlock.COUNT, count - 1), 3);
        } else {
            level.destroyBlock(worldPosition, false);
        }
    }
    private ItemStack fuzeStack = ItemStack.EMPTY;
    private int fuzeTicksRemaining = 0;


}

