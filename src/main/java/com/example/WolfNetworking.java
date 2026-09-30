package com.example;

import net.minecraft.resources.ResourceLocation;

// Sunucu ile istemci arasında "kontrol kaybı anı" bildirimi için kullanılan paket kanalı.
// Ortak (common) pakette olduğu için hem sunucu hem istemci tarafından erişilebilir.
public class WolfNetworking {

    public static final ResourceLocation KONTROL_KAYBI = new ResourceLocation(ExampleMod.MOD_ID, "kontrol_kaybi");

    public static final ResourceLocation FORM_SENKRON = new ResourceLocation(ExampleMod.MOD_ID, "form_senkron");

}