package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.items.AltitudeFuze;
import com.tterrag.registrate.util.entry.ItemEntry;

import static com.happysg.kaboom.CreateKaboom.REGISTRATE;

public class ModItems {

    public static final ItemEntry<AltitudeFuze> ALTITUDE_FUZE = REGISTRATE.item("altitude_fuze", AltitudeFuze::new)
            .register();




    public static void register() {
        CreateKaboom.getLogger().info("Registering Items!");
    }
}
