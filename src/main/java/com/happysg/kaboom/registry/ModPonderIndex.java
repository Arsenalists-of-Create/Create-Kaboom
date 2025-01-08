package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.simibubi.create.foundation.ponder.PonderRegistrationHelper;

public class ModPonderIndex {
    static final PonderRegistrationHelper HELPER = new PonderRegistrationHelper(CreateKaboom.MODID);

    public static void register() {
        CreateKaboom.getLogger().info("Registering Ponder!");

    }
}
