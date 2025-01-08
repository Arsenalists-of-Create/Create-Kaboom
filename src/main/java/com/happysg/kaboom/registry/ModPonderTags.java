package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.simibubi.create.foundation.ponder.PonderTag;

public class ModPonderTags {

    private static PonderTag create(String id) {
        return new PonderTag(CreateKaboom.asResource(id));
    }

    public static void register() {

    }

}
