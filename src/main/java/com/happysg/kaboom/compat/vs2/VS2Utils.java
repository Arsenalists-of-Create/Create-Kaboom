package com.happysg.kaboom.compat.vs2;

import com.happysg.kaboom.compat.Mods;
import dev.architectury.platform.Mod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.impl.shadow.Bl;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

public class VS2Utils {

    public static BlockPos getWorldPos(Level level, BlockPos pos) {
        if (!Mods.VALKYRIENSKIES.isLoaded())
            return pos;
        if (VSGameUtilsKt.getShipObjectManagingPos(level, pos) != null) {
            final LoadedShip loadedShip = VSGameUtilsKt.getShipObjectManagingPos(level, pos);
            final Vector3d vec = loadedShip.getShipToWorld().transformPosition(new Vector3d(pos.getX(), pos.getY(), pos.getZ()));
            VectorConversionsMCKt.toMinecraft(vec);
            final BlockPos newPos = new BlockPos((int) vec.x(), (int) vec.y(), (int) vec.z());
            return newPos;
        }
        return pos;
    }

    public static BlockPos getWorldPos(BlockEntity blockEntity) {
        return getWorldPos(blockEntity.getLevel(), blockEntity.getBlockPos());
    }
    public static Vector3dc getVelocity(Level level, BlockPos pos){
        if(!Mods.VALKYRIENSKIES.isLoaded())
                return null;
        if (VSGameUtilsKt.getShipObjectManagingPos(level, pos) != null) {
            final LoadedShip loadedShip = VSGameUtilsKt.getShipObjectManagingPos(level, pos);
            if(loadedShip == null) return null;
            return loadedShip.getVelocity();
        }
        return null;
    }

}
