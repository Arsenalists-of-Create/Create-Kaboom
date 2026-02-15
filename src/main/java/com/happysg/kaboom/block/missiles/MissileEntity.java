package com.happysg.kaboom.block.missiles;

import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class MissileEntity extends OrientedContraptionEntity {

    public MissileEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();

    }
}