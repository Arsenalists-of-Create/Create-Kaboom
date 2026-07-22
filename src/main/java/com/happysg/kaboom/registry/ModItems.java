package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.items.alt_fuze.AltitudeFuze;
import com.happysg.kaboom.items.rocket.GuidedRocketItem;
import com.happysg.kaboom.items.rocket.UnguidedRocketItem;
import com.happysg.kaboom.items.tracer.ColoredTracerTipItem;
import com.happysg.kaboom.items.tracer.TracerColor;
import com.tterrag.registrate.util.entry.ItemEntry;

import static com.happysg.kaboom.CreateKaboom.REGISTRATE;

public class ModItems {

    public static final ItemEntry<UnguidedRocketItem> ROCKET = REGISTRATE.item("rocket", UnguidedRocketItem::new)
            .model((ctx, prov) -> {})
            .register();

    public static final ItemEntry<GuidedRocketItem> GUIDED_ROCKET =
            REGISTRATE.item("guided_rocket", GuidedRocketItem::new)
            .model((ctx, prov) -> {})
            .register();

    public static final ItemEntry<ColoredTracerTipItem> BLUE_TRACER_TIP =
            tracerTip("blue_tracer_tip", TracerColor.BLUE);
    public static final ItemEntry<ColoredTracerTipItem> RED_TRACER_TIP =
            tracerTip("red_tracer_tip", TracerColor.RED);
    public static final ItemEntry<ColoredTracerTipItem> GREEN_TRACER_TIP =
            tracerTip("green_tracer_tip", TracerColor.GREEN);
    public static final ItemEntry<ColoredTracerTipItem> PINK_TRACER_TIP =
            tracerTip("pink_tracer_tip", TracerColor.PINK);
    public static final ItemEntry<ColoredTracerTipItem> WHITE_TRACER_TIP =
            tracerTip("white_tracer_tip", TracerColor.WHITE);
    public static final ItemEntry<ColoredTracerTipItem> ORANGE_TRACER_TIP =
            tracerTip("orange_tracer_tip", TracerColor.ORANGE);

    public static final ItemEntry<AltitudeFuze> ALTITUDE_FUZE = REGISTRATE.item("altitude_fuze", AltitudeFuze::new)
            .model((ctx, prov) -> prov.generated(ctx, CreateKaboom.asResource("item/fuze_altitude")))
            .register();

    private static ItemEntry<ColoredTracerTipItem> tracerTip(String name, TracerColor color) {
        return REGISTRATE.item(name, properties -> new ColoredTracerTipItem(properties, color))
                .model((ctx, prov) -> prov.generated(ctx, CreateKaboom.asResource("item/" + name)))
                .register();
    }

    public static void register() {
        CreateKaboom.getLogger().info("Registering Items!");
    }
}
