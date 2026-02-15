package com.happysg.kaboom.block.missiles;

import com.simibubi.create.api.contraption.ContraptionType;

import com.simibubi.create.content.contraptions.Contraption;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import net.minecraft.world.level.Level;


public class MissileContraption extends Contraption {

    private BlockPos cursor;




    @Override
    public boolean canBeStabilized(Direction facing, BlockPos localPos) {
        return false;
    }

    @Override
    public ContraptionType getType() {
        return null;
    }
    @Override
    public boolean assemble(Level level, BlockPos base) {
        if(level.isClientSide) return false;
        if(!(base instanceof IMissileComponent.IThrusterBlock)) return false;
        ///  add to contraption
        cursor = cursor.above();
        while (true){

        }




    }










}