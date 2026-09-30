package com.example.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class ExampleModClient implements ClientModInitializer {

	private static KeyMapping DONUSUM_TUSU;

	@Override
	public void onInitializeClient() {
		DONUSUM_TUSU = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.wolfblood.transform",   // ayarlar menüsünde görünecek isim (dil dosyası olmadan kod olarak görünür)
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_G,             // varsayılan tuş: G (istersen değiştir)
				"category.wolfblood"          // hangi kategori altında görünsün
		));

		WolfClientEffects.register(); // kuluçka sırasındaki "kontrol kaybı" ekran efekti

		// NOT: Eğer WolfClientState.register() zaten başka bir yerde çağrılıyorsa bu satırı silme,
		// iki kez çağrılması zararsız ama gereksizdir. Yoksa mutlaka bir yerde çağrılmalı,
		// aksi halde form senkronu (ve dolayısıyla ölçek/göz/kulak animasyonu) hiç çalışmaz.
		WolfClientState.register();

		// Kulak modelinin "baked layer"ını kaydediyoruz - LycanKulakModel.createBodyLayer()
		// ilk kez kullanılacağı anda (PlayerRenderer'a eklendiğinde) bu tanıma göre inşa edilir.
		EntityModelLayerRegistry.registerModelLayer(WolfModelLayers.LYCAN_KULAK, LycanKulakModel::createBodyLayer);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (DONUSUM_TUSU.consumeClick()) {
				if (client.player != null) {
					client.player.connection.sendCommand("kurtadam");
				}
			}
		});
	}
}