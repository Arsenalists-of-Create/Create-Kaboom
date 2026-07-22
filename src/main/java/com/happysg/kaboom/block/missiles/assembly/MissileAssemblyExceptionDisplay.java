package com.happysg.kaboom.block.missiles.assembly;

import com.happysg.kaboom.block.missiles.parts.thrust.ThrusterBlockEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Delegates a missile component's overlay diagnostic to its controller thruster. */
public interface MissileAssemblyExceptionDisplay extends IDisplayAssemblyExceptions {
    @Override
    @Nullable
    default AssemblyException getLastAssemblyException() {
        if (!((Object) this instanceof BlockEntity blockEntity)) {
            return null;
        }
        Level level = blockEntity.getLevel();
        if (level == null) {
            return null;
        }
        BlockPos controllerPos = MissileAssembler.findControllerFromComponent(level, blockEntity.getBlockPos());
        if (controllerPos == null) {
            return null;
        }
        return level.getBlockEntity(controllerPos) instanceof ThrusterBlockEntity thruster
                ? thruster.getStoredAssemblyException()
                : null;
    }
}
