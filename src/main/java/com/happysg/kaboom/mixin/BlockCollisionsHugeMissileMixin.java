package com.happysg.kaboom.mixin;

import com.happysg.kaboom.block.missiles.parts.HugeMissileOverlap;
import net.minecraft.core.Cursor3D;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BlockCollisions.class, remap = false)
public abstract class BlockCollisionsHugeMissileMixin {
    /**
     * Vanilla skips corner cursor cells before reading their block state. Route them through the
     * edge path so the block-state redirect below can admit Huge missile parts only.
     */
    @Redirect(
            method = "computeNext",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/Cursor3D;getNextType()I",
                    remap = false
            )
    )
    private int createKaboom$inspectCornerCells(Cursor3D cursor) {
        int type = cursor.getNextType();
        return type == Cursor3D.TYPE_CORNER ? Cursor3D.TYPE_EDGE : type;
    }

    /**
     * Edge cells normally accept moving pistons only. Huge missile shapes can extend diagonally
     * into edge and corner cells, so allow those parts through to the normal shape intersection.
     */
    @Redirect(
            method = "computeNext",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z",
                    remap = false
            )
    )
    private boolean createKaboom$includeHugeMissileEdges(BlockState state, Block block) {
        return state.is(block) || HugeMissileOverlap.isHugeMissilePart(state);
    }
}
