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

        // Pull constants from the placed block instance (plan parity)
        AerialBombProjectile.BombType bombType = AerialBombProjectile.BombType.HE;
        int bombSize = 1;

        if (state.getBlock() instanceof AerialBombBlock bomb) {
            bombType = bomb.getBombType();
            bombSize = bomb.getBombSize();
        }

        projectile.setBombType(bombType);
        projectile.setSize(bombSize);

        level.addFreshEntity(projectile);

        // COUNT still lives in blockstate (ammo/stacking)
        int count = state.getValue(AerialBombBlock.COUNT);
        if (count > 1) {
            level.setBlock(worldPosition, state.setValue(AerialBombBlock.COUNT, count - 1), 3);
        } else {
            level.destroyBlock(worldPosition, false);
        }
    }

    // (These aren't used in the snippet; safe to delete unless you plan to use them soon)
    private ItemStack fuzeStack = ItemStack.EMPTY;
    private int fuzeTicksRemaining = 0;
}