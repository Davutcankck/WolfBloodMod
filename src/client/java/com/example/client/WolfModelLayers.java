package com.example.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

public class WolfModelLayers {

    public static final ModelLayerLocation LYCAN_KULAK =
            new ModelLayerLocation(new ResourceLocation("wolfblood", "lycan_kulak"), "main");

    public static final ModelLayerLocation LYCAN_PENCE =
            new ModelLayerLocation(new ResourceLocation("wolfblood", "lycan_pence"), "main");

    private WolfModelLayers() {
    }
}