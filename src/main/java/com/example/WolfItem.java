package com.example;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public class WolfItem {


    public static final Item KURT_OTU = new Item(new Item.Properties()
            .food(new FoodProperties.Builder()
                    .nutrition(1)
                    .effect(new MobEffectInstance( MobEffects.MOVEMENT_SPEED,20 * 15,1),1.0f)
                    .saturationMod(0.1f)
                    .alwaysEat()
                    .build()
            ));

    public static final Item KURT_DISI = new Item(new Item.Properties());

    public  static final Item KURT_KANI = new Item(new Item.Properties().stacksTo(1));

    public static final Item GUMUS = new Item(new Item.Properties());

    public  static  final Item PROBİS = new Item(new Item.Properties()
            .food(new FoodProperties.Builder()
                    .nutrition(3)
                    .effect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 10, 1),1.0f)
                    .saturationMod(1.0f)
                    .build()
            ));

    public static void register(){
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ExampleMod.MOD_ID, "kurt_otu"), KURT_OTU);
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ExampleMod.MOD_ID, "kurt_disi"), KURT_DISI);
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ExampleMod.MOD_ID, "kurt_kani_iksiri"), KURT_KANI);
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ExampleMod.MOD_ID, "gumus"), GUMUS);
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ExampleMod.MOD_ID, "probis"),PROBİS);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
            entries.accept(KURT_OTU);
            entries.accept(KURT_DISI);
            entries.accept(KURT_KANI);
            entries.accept(GUMUS);
            entries.accept(PROBİS);
        });
    }

}
