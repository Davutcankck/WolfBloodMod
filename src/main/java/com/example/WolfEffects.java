package com.example;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;


public class WolfEffects {
    public static final MobEffect ENFEKSIYON = new InfectionEffect();

    public static void register() {
        Registry.register(BuiltInRegistries.MOB_EFFECT,
                new ResourceLocation(ExampleMod.MOD_ID, "enfeksiyon"), ENFEKSIYON);
    }
}