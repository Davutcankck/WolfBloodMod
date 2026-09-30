package com.example.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

// Vanilla olmayan (özel) modellerin "baked layer" konumlarını tek yerde topluyoruz.
// İleride pençe modeli de eklersen buraya yeni bir sabit eklemen yeterli.
public class WolfModelLayers {

    public static final ModelLayerLocation LYCAN_KULAK =
            new ModelLayerLocation(new ResourceLocation("wolfblood", "lycan_kulak"), "main");

    private WolfModelLayers() {
    }
}
