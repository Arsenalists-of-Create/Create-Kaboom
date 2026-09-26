package com.happysg.kaboom.client;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlockEntity;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombFuzeLayout;
import com.happysg.kaboom.block.aerialBombs.small.FluidSmallAerialBombBlock;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;

public class FuzeSelectionHandler {
    private static final int HIGHLIGHT = 0xFFFF55;
    private final Object outlineSlot = new Object();

    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AerialBombBlock bomb)
                || !(level.getBlockEntity(pos) instanceof AerialBombBlockEntity blockEntity)) {
            return;
        }

        int slot = AerialBombFuzeLayout.selectedSlot(state, pos, hit);
        if (slot < 0) {
            return;
        }

        ItemStack held = player.getMainHandItem();
        boolean fuzed = !blockEntity.getFuze(slot).isEmpty();
        if (!((held.getItem() instanceof FuzeItem && !fuzed) || (held.isEmpty() && fuzed))) {
            return;
        }

        AerialBombFuzeLayout.SlotCenter center = AerialBombFuzeLayout.activeCenters(state)[slot];
        Direction facing = state.getValue(AerialBombBlock.FACING);
        HighlightDimensions dimensions = dimensionsFor(state, bomb);
        Vec3 boxCenter = Vec3.atCenterOf(pos)
                .add(AerialBombFuzeLayout.offsetFromBlockCenter(facing, center, dimensions.forward()));

        double halfX = facing.getAxis() == Direction.Axis.X ? dimensions.depth() : dimensions.radius();
        double halfZ = facing.getAxis() == Direction.Axis.X ? dimensions.radius() : dimensions.depth();
        AABB box = new AABB(boxCenter, boxCenter).inflate(halfX, dimensions.radius(), halfZ);

        Outliner.getInstance()
                .showAABB(outlineSlot, box)
                .colored(HIGHLIGHT)
                .clearTextures()
                .disableLineNormals()
                .lineWidth(1 / 32f);
    }

    private static HighlightDimensions dimensionsFor(BlockState state, AerialBombBlock bomb) {
        if (state.getBlock() instanceof FluidSmallAerialBombBlock) {
            return new HighlightDimensions(7 / 16.0, 2 / 16.0, 4 / 16.0);
        }
        if (bomb.getBombSize() <= 1) {
            return new HighlightDimensions(7 / 16.0, 2 / 16.0, 5 / 16.0);
        }
        if (bomb.getBombSize() == 2) {
            return new HighlightDimensions(7 / 16.0, 1.3 / 16.0, 3.5 / 16.0);
        }
        return new HighlightDimensions(7.3 / 16.0, 1 / 16.0, 2.5 / 16.0);
    }

    private record HighlightDimensions(double forward, double depth, double radius) {
    }
}
