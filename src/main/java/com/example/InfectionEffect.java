package com.example;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class InfectionEffect extends MobEffect {
    protected InfectionEffect() {
        // HARMFUL = zararlı görünsün (ekranda kırmızımsı çerçeve çıkar)
        // 0x0D0D0D = neredeyse siyah renk, hem ikon hem partiküller bu renkte olacak
        super(MobEffectCategory.HARMFUL, 0x0D0D0D);
    }
}