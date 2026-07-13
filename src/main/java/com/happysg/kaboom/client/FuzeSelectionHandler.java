package com.happysg.kaboom.client;




import com.happysg.kaboom.block.aerialBombs.baseTypes.FluidAerialBombBlock;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedProjectileBlock;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlockEntity;
import com.happysg.kaboom.block.aerialBombs.heavy.HeavyAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.small.FluidSmallAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.small.SmallAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.tiny.TinyAerialBombBlock;

public class FuzeSelectionHandler {


    private static final int HIGHLIGHT = 16777045;

    private final Object bbOutlineSlot = new Object();


    public void tick() {


        Minecraft mc = Minecraft.getInstance();


        LocalPlayer player = mc.player;

        ClientLevel level = mc.level;


        if (player == null || level == null)
            return;



        BlockPos hovered = null;


        HitResult hitResult = mc.hitResult;


        if (hitResult != null &&
                hitResult.getType() == HitResult.Type.BLOCK) {

            hovered = ((BlockHitResult) hitResult).getBlockPos();

        }


        if (hovered == null)
            return;



        BlockState state = level.getBlockState(hovered);


        boolean isKaboomFluid =
                state.getBlock() instanceof FluidAerialBombBlock;


        boolean isKaboom =
                state.getBlock() instanceof AerialBombBlock;



        if (!isKaboom && !isKaboomFluid)
            return;




        ItemStack hand = player.getMainHandItem();

        boolean isTiny = state.getBlock() instanceof TinyAerialBombBlock;
        boolean isSmall = state.getBlock() instanceof SmallAerialBombBlock || state.getBlock() instanceof FluidSmallAerialBombBlock;

        int globalIndex = 0;
        double hOffset = 0.0;
        double vOffset = 0.0;
        boolean hasOffset = false;

        Direction dir;
        if (isKaboom) {
            dir = state.getValue(AerialBombBlock.FACING);
        } else {
            dir = state.getValue(FuzedProjectileBlock.FACING);
        }

        if (isKaboom && (isTiny || isSmall)) {
            if (hitResult instanceof BlockHitResult blockHitResult) {
                Vec3 hitVec = blockHitResult.getLocation();
                double localX = hitVec.x - hovered.getX();
                double localY = hitVec.y - hovered.getY();
                double localZ = hitVec.z - hovered.getZ();

                double u = localX;
                double v = localY;

                switch (dir) {
                    case NORTH:
                        u = localX;
                        break;
                    case SOUTH:
                        u = 1.0 - localX;
                        break;
                    case EAST:
                        u = localZ;
                        break;
                    case WEST:
                        u = 1.0 - localZ;
                        break;
                }

                int count = state.hasProperty(AerialBombBlock.COUNT) ? state.getValue(AerialBombBlock.COUNT) : 1;
                double[][] activeCenters = AerialBombBlock.getActiveCenters(isTiny, count);
                double minDistanceSq = Double.MAX_VALUE;
                int activeIndex = 0;
                for (int i = 0; i < activeCenters.length; i++) {
                    double cx = activeCenters[i][0];
                    double cy = activeCenters[i][1];
                    double dx = u * 16.0 - cx;
                    double dy = v * 16.0 - cy;
                    double distSq = dx * dx + dy * dy;
                    if (distSq < minDistanceSq) {
                        minDistanceSq = distSq;
                        activeIndex = i;
                    }
                }
                globalIndex = AerialBombBlock.getGlobalIndex(isTiny, count, activeIndex);
                double cx = activeCenters[activeIndex][0];
                double cy = activeCenters[activeIndex][1];
                hOffset = (cx - 8.0) / 16.0;
                vOffset = (cy - 8.0) / 16.0;
                hasOffset = true;
            }
        }

        boolean fuzed;
        var be = level.getBlockEntity(hovered);
        if (be instanceof AerialBombBlockEntity aerialBomb) {
            fuzed = !aerialBomb.getFuze(globalIndex).isEmpty();
        } else if (be instanceof rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity shellBE) {
            fuzed = shellBE.hasFuze();
        } else {
            return;
        }

        if (!(
                (hand.getItem() instanceof FuzeItem && !fuzed)
                        ||
                        (hand.isEmpty() && fuzed)
        ))
            return;

        double offset = 7 / 16f;
        double yOffset = 0;
        double dx = 2 / 16d;
        double dy = 5 / 16d;
        double dz = 5 / 16d;

        if (state.getBlock() instanceof FluidSmallAerialBombBlock) {
            dx = 2 / 16d;
            dy = 4 / 16d;
            dz = 4 / 16d;
            offset = 7 / 16f;
            yOffset = 4 / 16f;
        }
        else if (state.getBlock() instanceof HeavyAerialBombBlock) {
            dx = 2 / 16d;
            dy = 5 / 16d;
            dz = 5 / 16d;
            offset = 7 / 16f;
        }
        else if (state.getBlock() instanceof SmallAerialBombBlock) {
            dx = 1.3 / 16d;
            dy = 3.5 / 16d;
            dz = 3.5 / 16d;
            offset = 7 / 16f;
            yOffset = 4 / 16f;
        }
        else if (state.getBlock() instanceof TinyAerialBombBlock) {
            dx = 1 / 16d;
            dy = 2.5 / 16d;
            dz = 2.5 / 16d;
            offset = 7.3 / 16f;
            yOffset = -5.5 / 16f;
        }

        Vec3 center;
        if (hasOffset) {
            Vec3 right = new Vec3(dir.getClockWise().step());
            center = Vec3.atCenterOf(hovered)
                    .add(new Vec3(dir.step()).scale(offset))
                    .add(right.scale(hOffset))
                    .add(0, vOffset, 0);
        } else {
            center = Vec3.atCenterOf(hovered)
                    .add(new Vec3(dir.step()).scale(offset))
                    .add(0, yOffset, 0);
        }

        double finalDx = dir.getAxis() == Direction.Axis.X ? dx : dz;
        double finalDz = dir.getAxis() == Direction.Axis.X ? dz : dx;
        AABB box =
                new AABB(center, center)
                        .inflate(finalDx, dy, finalDz);




        Outliner.getInstance()

                .showAABB(bbOutlineSlot, box)

                .colored(HIGHLIGHT)

                .clearTextures()

                .disableLineNormals()

                .lineWidth(1 / 32f);

    }

}