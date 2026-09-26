package com.happysg.kaboom.mixin;

import com.happysg.kaboom.block.missiles.parts.HugeMissileOverlap;
import com.happysg.kaboom.block.missiles.parts.HugeMissileReservations;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockItem.class, remap = false)
public abstract class BlockItemHugeMissilePlacementMixin {
    /** Reject Huge parts before either side mutates the world, preventing placement flicker. */
    @Inject(method = "canPlace", at = @At("RETURN"), cancellable = true, remap = false)
    private void createKaboom$checkHugeReservation(BlockPlaceContext context, BlockState state,
                                                   CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ() || !HugeMissileOverlap.isHugeMissilePart(state)) {
            return;
        }
        BlockPos pos = context.getClickedPos().immutable();
        if (!HugeMissileReservations.canReservePlacements(
                context.getLevel(), Map.of(pos, state))) {
            callback.setReturnValue(false);
        }
    }
}
