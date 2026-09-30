package com.example;

import net.fabricmc.api.ModInitializer;


import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
	public static final String MOD_ID = "wolfblood";


	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		WolfItem.register();
		WolfTransform.register();
		WolfEffects.register();

		LOGGER.info("WOLF BLOOD modu yükleniyor...");
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
