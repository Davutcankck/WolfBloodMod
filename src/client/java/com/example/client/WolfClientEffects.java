package com.example.client;

import com.example.WolfNetworking;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Sunucudan "kontrol kaybı anı" paketi geldiğinde birkaç saniyeliğine ekranın kenarlarını
// kızartan (radyal vinyet texture) ve merkeze doğru uzanan damar deseni (texture) çizen HUD efekti.
public class WolfClientEffects {

    private static final ResourceLocation KRIZ_TEXTURE =
            new ResourceLocation("wolfblood", "textures/gui/kriz_layer.png");

    private static final ResourceLocation VINYET_TEXTURE =
            new ResourceLocation("wolfblood", "textures/gui/vinyet_texture.png");

    private static final Random RASTGELE = new Random();

    private static int kalanTick = 0;
    private static int toplamTick = 1;
    private static List<Damar> damarlar = new ArrayList<>();

    // ================= NABIZ (PULSE) AYARLARI =================
    // NABIZ_HIZI_HZ: saniyede kaç kalp atışı olacağı (1.4 ~= dakikada 84 atış, gergin/hızlı bir nabız).
    // NABIZ_DERINLIGI: yoğunluğun ne kadarının nabza göre dalgalanacağı (0 = sabit, 1 = tam sıfıra iner).
    private static final double NABIZ_HIZI_HZ = 1.4;
    private static final float NABIZ_DERINLIGI = 0.65f;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(WolfNetworking.KONTROL_KAYBI, (client, handler, buf, responseSender) -> {
            int sureTick = buf.readInt();
            client.execute(() -> baslat(sureTick));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (kalanTick > 0) {
                kalanTick--;
            }
        });

        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            if (kalanTick > 0) {
                ciz(graphics);
            }
        });
    }

    private static void baslat(int sureTick) {
        kalanTick = sureTick;
        toplamTick = Math.max(sureTick, 1);
        damarlar = damarlariUret();
    }

   // Damarlar artık kriz_layer dosyasıtla png olarak aktaralıdı çizimle yapılmak istersse diye duruyor
    private static List<Damar> damarlariUret() {
        List<Damar> liste = new ArrayList<>();
        int damarSayisi = 6 + RASTGELE.nextInt(4); // 6-9 arası damar

        for (int i = 0; i < damarSayisi; i++) {
            float[] baslangic = kenarNoktasiSec();
            List<float[]> noktalar = new ArrayList<>();
            noktalar.add(baslangic);

            float x = baslangic[0];
            float y = baslangic[1];
            float merkezX = 0.5f;
            float merkezY = 0.5f;

            int segmentSayisi = 2 + RASTGELE.nextInt(2); // 2-3 segment
            for (int s = 0; s < segmentSayisi; s++) {
                float oran = (s + 1f) / (segmentSayisi + 1f) * 0.55f; // merkeze doğru %55'e kadar yaklaş
                float hedefX = x + (merkezX - x) * oran + (RASTGELE.nextFloat() - 0.5f) * 0.08f;
                float hedefY = y + (merkezY - y) * oran + (RASTGELE.nextFloat() - 0.5f) * 0.08f;
                noktalar.add(new float[]{hedefX, hedefY});
                x = hedefX;
                y = hedefY;
            }

            liste.add(new Damar(noktalar));
        }
        return liste;
    }

    private static float[] kenarNoktasiSec() {
        int kenar = RASTGELE.nextInt(4); // 0=üst, 1=alt, 2=sol, 3=sağ
        float t = RASTGELE.nextFloat();
        return switch (kenar) {
            case 0 -> new float[]{t, 0f};
            case 1 -> new float[]{t, 1f};
            case 2 -> new float[]{0f, t};
            default -> new float[]{1f, t};
        };
    }

    private static void ciz(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        int genislik = client.getWindow().getGuiScaledWidth();
        int yukseklik = client.getWindow().getGuiScaledHeight();

        // 1.0 -> efekt yeni başladı, 0.0 -> efekt bitiyor (fade-in / fade-out zarfı)
        float kalanOran = kalanTick / (float) toplamTick;
        float yogunluk;
        if (kalanOran > 0.8f) {
            yogunluk = (1f - kalanOran) / 0.2f; // ilk %20'de içeri doğru belirsin (fade-in)
        } else if (kalanOran < 0.25f) {
            yogunluk = kalanOran / 0.25f; // son %25'te kaybolsun (fade-out)
        } else {
            yogunluk = 1f;
        }
        yogunluk = Math.max(0f, Math.min(1f, yogunluk));

        // ============ NABIZ (PULSE) MODÜLASYONU ============
        // Duvar saatine (System.currentTimeMillis()) bağlı bir sinüs dalgası üretiyoruz.
        // Bu, oyunun tick hızından bağımsız, gerçek zamanlı ve pürüzsüz bir salınım verir.
        double zamanSaniye = System.currentTimeMillis() / 1000.0;
        float nabiz01 = (float) ((Math.sin(zamanSaniye * NABIZ_HIZI_HZ * 2 * Math.PI) + 1.0) / 2.0);

        // "taban + derinlik * nabiz" formülüyle bir aralığa sıkıştırıyoruz, tamamen sıfıra inmesin.
        float nabizCarpani = (1f - NABIZ_DERINLIGI) + NABIZ_DERINLIGI * nabiz01;

        // Fade zarfı ile nabız çarpanını birleştiriyoruz.
        yogunluk *= nabizCarpani;

        // ============ BLEND (YUMUŞATMA) AÇ ============
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Radyal vinyet — tek texture, dikdörtgen birleşim sorunu yok
        RenderSystem.setShaderColor(1f, 1f, 1f, yogunluk);
        graphics.blit(VINYET_TEXTURE, 0, 0, genislik, yukseklik, 0f, 0f, 1920, 1080, 1920, 1080);

        // Damar texture'ı, vinyetin üstüne biniyor
        graphics.blit(KRIZ_TEXTURE, 0, 0, genislik, yukseklik, 0f, 0f, 2752, 1536, 2752, 1536);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f); // sonraki HUD çizimlerini etkilememesi için normale döndür

        // ============ BLEND (YUMUŞATMA) KAPAT ============
        RenderSystem.disableBlend();
    }

    // Rastgele açıda bir çizgi çizmek için PoseStack'i döndürüp ince bir dikdörtgen dolduruyoruz.
    // (Şu an kullanılmıyor, prosedürel damar çizimi için yedekte tutuluyor.)
    private static void cizgiCiz(GuiGraphics graphics, float x1, float y1, float x2, float y2, int renk, int kalinlik) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float uzunluk = (float) Math.sqrt(dx * dx + dy * dy);
        float aci = (float) Math.atan2(dy, dx);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x1, y1, 0);
        pose.mulPose(Axis.ZP.rotation(aci));
        graphics.fill(0, -kalinlik / 2, Math.round(uzunluk), kalinlik / 2, renk);
        pose.popPose();
    }

    private record Damar(List<float[]> noktalar) {
    }
}