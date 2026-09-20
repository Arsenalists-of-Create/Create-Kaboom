package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.items.AltitudeFuze;
import com.tterrag.registrate.util.entry.ItemEntry;

import static com.happysg.kaboom.CreateKaboom.REGISTRATE;

public class ModItems {

    public static final ItemEntry<AltitudeFuze> ALTITUDE_FUZE = REGISTRATE.item("altitude_fuze", AltitudeFuze::new)
            .model((ctx, prov) -> prov.generated(ctx, CreateKaboom.asResource("item/fuze_altitude")))
            .register();

    public static final ItemEntry<com.happysg.kaboom.items.MineFuze> MINE_FUZE = REGISTRATE.item("mine_fuze", com.happysg.kaboom.items.MineFuze::new)
            .model((ctx, prov) -> prov.generated(ctx, CreateKaboom.asResource("item/mine_fuze")))
            .register();

    public static void register() {
        CreateKaboom.getLogger().info("Registering Items!");
    }
}
