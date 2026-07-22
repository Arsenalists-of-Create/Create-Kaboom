package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.items.alt_fuze.AltitudeFuze;
import com.happysg.kaboom.items.rocket.RocketItem;
import com.tterrag.registrate.util.entry.ItemEntry;

import static com.happysg.kaboom.CreateKaboom.REGISTRATE;

public class ModItems {

    public static final ItemEntry<RocketItem> ROCKET = REGISTRATE.item("rocket", RocketItem::new)
            .model((ctx, prov) -> {})
            .register();

    public static final ItemEntry<AltitudeFuze> ALTITUDE_FUZE = REGISTRATE.item("altitude_fuze", AltitudeFuze::new)
            .model((ctx, prov) -> prov.generated(ctx, CreateKaboom.asResource("item/fuze_altitude")))
            .register();

    public static void register() {
        CreateKaboom.getLogger().info("Registering Items!");
    }
}
