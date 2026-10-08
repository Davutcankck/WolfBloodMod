package com.example;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

public class WolfItem {


    public static final Item KURT_OTU = new Item(new Item.Properties()
            .food(new FoodProperties.Builder()
                    .nutrition(1)
                    .effect(new MobEffectInstance( MobEffects.MOVEMENT_SPEED,20 * 15,1),1.0f)
                    .saturationMod(0.1f)
                    .alwaysEat()
                    .build()
            ));

    public static final Item KAOS_IKSIRI = new KaosIksiriItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));




    public static final Item KURT_DISI = new Item(new Item.Properties());

    public  static final Item KURT_KANI = new Item(new Item.Properties().stacksTo(1));

    public static final Item GUMUS = new Item(new Item.Properties());

    public  static  final Item PROBIS = new Item(new Item.Properties()
            .food(new FoodProperties.Builder()
                    .nutrition(3)
                    .effect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 10, 1),1.0f)
                    .saturationMod(1.0f)
                    .build()
            ));

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ExampleMod.MOD_ID, "kurt_otu"), KURT_OTU);
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ExampleMod.MOD_ID, "kurt_disi"), KURT_DISI);
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ExampleMod.MOD_ID, "kurt_kani_iksiri"), KURT_KANI);
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ExampleMod.MOD_ID, "gumus"), GUMUS);
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ExampleMod.MOD_ID, "probis"), PROBIS);
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(ExampleMod.MOD_ID, "kaos_iksiri"), KAOS_IKSIRI);

        CreativeModeTab wolfTab = FabricItemGroup.builder()
                .title(Component.translatable("WolfBlood"))
                .icon(() -> new ItemStack(KURT_DISI))
                .displayItems((parameters, output) -> {
                    output.accept(KURT_OTU);
                    output.accept(KURT_DISI);
                    output.accept(KURT_KANI);
                    output.accept(GUMUS);
                    output.accept(PROBIS);
                    output.accept(KAOS_IKSIRI);
                })
                .build();

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
                new ResourceLocation(ExampleMod.MOD_ID, "main"), wolfTab);
    }

}
