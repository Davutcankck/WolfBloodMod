package com.example.client;

import com.example.ExampleMod;
import com.example.InfectionData;
import com.example.WolfNetworking;
import com.example.WolfRutbe;
import com.example.WolfSkill;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Yetenek ağacı ekranı (tam ekran, tek ağaç, yukarıdan aşağı).
 *
 *  1) Sürüklenebilir kanvas: ağaç, ekranın ortasındaki kırpma alanında (scissor) offsetX/offsetY ile gezilir.
 *     Üstteki rütbe ve XP barı sabit kalır. Zoom yok.
 *  2) Düğümler 16x16 ikon sprite'ı olarak çizilir (textures/gui/skill/<id>.png). Doku yoksa kodla çizilen
 *     geçici bir simge görünür (harf/metin YOK). Bağlantı çizgisi: iki düğüm de açıksa altın, değilse koyu gri.
 *  3) Slot yok. Ağacın ortasındaki "Kaos Mührü" düğümü oyuncunun mühür bilgisini gösterir:
 *     yoksa asma kilit, varsa parlayan mühür (doku: kaos_muhru_kilitli.png / kaos_muhru_acik.png).
 *  4) Çift elmas dizilimi koordinatları WolfSkill içinde (üst elmas -> mühür -> alt elmas).
 */
public class WolfSkillScreen extends Screen {

    // ---- Yerleşim ----
    private static final int DUGUM = 24;                 // düğüm çerçevesi
    private static final int IKON = 16;                  // ikon sprite boyutu
    private static final int ARALIK = 48;                // 1 ızgara birimi = kaç piksel (x ve y AYNI, böylece çaprazlar 45 derece)
    private static final int UST_BILGI_YUKSEKLIGI = 44;
    private static final int AGAC_UST_BOSLUK = 36;       // kanvas üstünden kök düğüme mesafe
    private static final int GORUNUR_MARJ = 70;          // kaydırırken ağacın her yönde en az bu kadarı ekranda kalır
    private static final int SURUKLEME_ESIGI = 3;        // bu kadar pikselden az hareket "tıklama" sayılır

    // ---- Renkler (ARGB) ----
    private static final int RENK_ZEMIN = 0xFF0B0B10;
    private static final int RENK_TUGLA_DERZI = 0xFF14141B;
    private static final int RENK_METIN = 0xFFFFFFFF;
    private static final int RENK_GRI = 0xFFA0A0A8;
    private static final int RENK_ALTIN = 0xFFE0B040;
    private static final int RENK_ALTIN_KOYU = 0xFF7A6224;
    private static final int RENK_ALTIN_PARILTI_A = 0xFF4A3A12;
    private static final int RENK_ALTIN_PARILTI_B = 0xFF7A6224;
    private static final int RENK_CIZGI_GRI = 0xFF3A3A44;
    private static final int RENK_BAR_ARKA = 0xFF26262E;
    private static final int RENK_BAR_DOLGU = 0xFFB03030;
    private static final int RENK_CERCEVE = 0xFF4A4A55;
    private static final int RENK_DUGUM_ACIK = 0xFF2A2210;
    private static final int RENK_DUGUM_ACILABILIR = 0xFF22222A;
    private static final int RENK_DUGUM_KILITLI = 0xFF17171C;
    private static final int RENK_MOR = 0xFF9B4DDB;

    private enum Stil {
        ACIK, ACILABILIR, KILITLI
    }

    private final Screen ustEkran;

    // Ağacın kaydırma konumu (kanvas merkezine göre, piksel)
    private double offsetX = 0;
    private double offsetY = 0;

    private int kanvasX;
    private int kanvasY;
    private int kanvasGenislik;
    private int kanvasYukseklik;

    // Ağaç sınırları (ızgara birimi) - init'te hesaplanır
    private int minGx;
    private int maxGx;
    private int minGy;
    private int maxGy;

    // Fare basma/sürükleme takibi
    private boolean basildi = false;
    private boolean surukledi = false;
    private double basX;
    private double basY;

    // Bu karede fare hangi düğümün üstünde (ipucu çizmek için)
    private WolfSkill ustundekiYetenek;
    private WolfSkill.Sonuc ustundekiSonuc;

    // Doku var mı önbelleği (ekran her açıldığında yenilenir, F3+T sonrası ekranı kapat-aç)
    private final Map<String, Boolean> dokuVarMi = new HashMap<>();

    public WolfSkillScreen(Screen ustEkran) {
        super(Component.literal("Yetenek Ağacı"));
        this.ustEkran = ustEkran;
    }

    @Override
    protected void init() {
        kanvasX = 0;
        kanvasY = UST_BILGI_YUKSEKLIGI;
        kanvasGenislik = this.width;
        kanvasYukseklik = Math.max(40, this.height - UST_BILGI_YUKSEKLIGI);
        dokuVarMi.clear();

        minGx = Integer.MAX_VALUE;
        maxGx = Integer.MIN_VALUE;
        minGy = Integer.MAX_VALUE;
        maxGy = Integer.MIN_VALUE;
        for (WolfSkill yetenek : WolfSkill.values()) {
            minGx = Math.min(minGx, yetenek.getX());
            maxGx = Math.max(maxGx, yetenek.getX());
            minGy = Math.min(minGy, yetenek.getY());
            maxGy = Math.max(maxGy, yetenek.getY());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Geri"), dugme -> this.onClose())
                .bounds(this.width - 70, this.height - 24, 62, 18)
                .build());

        offsetSinirla();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(ustEkran);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================= ÇİZİM =================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        zeminCiz(g);

        ustundekiYetenek = null;
        ustundekiSonuc = null;

        if (!WolfLevelClientState.enfekteMi()) {
            g.drawCenteredString(this.font, "Damarlarında henüz kurt kanı yok.",
                    this.width / 2, this.height / 2, RENK_GRI);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        int xp = WolfLevelClientState.getXp();
        Set<String> acik = WolfLevelClientState.getYetenekler();
        boolean muhurVar = WolfLevelClientState.kaosKilidiAcikMi(); // hasChaosSeal
        int seviye = InfectionData.seviyeHesapla(xp);
        int puan = WolfSkill.kullanilabilirPuan(xp, acik);
        long zaman = System.currentTimeMillis();
        double puls = 0.5 + 0.5 * Math.sin(zaman / 350.0);

        // Kırpma alanı: ağaç üst bilginin altına taşmasın (GuiGraphics'in scissor'ı GUI ölçeğini kendisi çevirir)
        g.enableScissor(kanvasX, kanvasY, kanvasX + kanvasGenislik, kanvasY + kanvasYukseklik);
        baglantilariCiz(g, acik, muhurVar, puls);
        dugumleriCiz(g, mouseX, mouseY, xp, acik, muhurVar, puls);
        g.disableScissor();

        // Sabit üst bilgi (kaydırmadan etkilenmez)
        ustBilgiCiz(g, xp, seviye, puan);

        super.render(g, mouseX, mouseY, partialTick);

        if (ustundekiYetenek != null) {
            g.renderComponentTooltip(this.font,
                    ipucuSatirlari(ustundekiYetenek, ustundekiSonuc, acik, muhurVar), mouseX, mouseY);
        }
    }

    private void zeminCiz(GuiGraphics g) {
        g.fill(0, 0, this.width, this.height, RENK_ZEMIN);
        // Basit tuğla deseni (doku gerektirmez)
        for (int y = 0; y < this.height; y += 16) {
            g.fill(0, y, this.width, y + 1, RENK_TUGLA_DERZI);
            int kayma = ((y / 16) % 2 == 0) ? 0 : 16;
            for (int x = -kayma; x < this.width; x += 32) {
                if (x >= 0) {
                    g.fill(x, y, x + 1, y + 16, RENK_TUGLA_DERZI);
                }
            }
        }
    }

    private void ustBilgiCiz(GuiGraphics g, int xp, int seviye, int puan) {
        g.fill(0, 0, this.width, UST_BILGI_YUKSEKLIGI - 1, RENK_ZEMIN);
        g.fill(0, UST_BILGI_YUKSEKLIGI - 1, this.width, UST_BILGI_YUKSEKLIGI, RENK_CERCEVE);

        g.drawCenteredString(this.font, "YETENEK AĞACI", this.width / 2, 5, RENK_METIN);

        int barGenislik = Math.max(80, Math.min(240, this.width - 260));
        int barX = (this.width - barGenislik) / 2;
        int barY = 18;
        int barYukseklik = 8;

        float oran;
        String xpMetni;
        if (seviye >= InfectionData.MAX_SEVIYE) {
            oran = 1f;
            xpMetni = "MAKSİMUM SEVİYE";
        } else {
            int alt = InfectionData.seviyeXpEsigi(seviye);
            int ust = InfectionData.seviyeXpEsigi(seviye + 1);
            oran = Math.max(0f, Math.min(1f, (xp - alt) / (float) (ust - alt)));
            xpMetni = xp + " / " + ust + " XP";
        }

        g.fill(barX, barY, barX + barGenislik, barY + barYukseklik, RENK_BAR_ARKA);
        g.fill(barX, barY, barX + (int) (barGenislik * oran), barY + barYukseklik, RENK_BAR_DOLGU);
        kutuCerceveCiz(g, barX, barY, barGenislik, barYukseklik, RENK_CERCEVE);
        g.drawCenteredString(this.font, xpMetni, this.width / 2, barY + barYukseklik + 4, RENK_GRI);

        // Solda rütbe ve seviye, sağda yetenek puanı
        WolfRutbe rutbe = WolfRutbe.seviyeyeGore(seviye);
        String seviyeMetni = " (Sv. " + seviye + ")";
        int solGenislik = this.font.width(rutbe.getAd()) + this.font.width(seviyeMetni);
        int solX = barX - 10 - solGenislik;
        g.drawString(this.font, rutbe.getAd(), solX, barY, rutbe.getRenk(), false);
        g.drawString(this.font, seviyeMetni, solX + this.font.width(rutbe.getAd()), barY, RENK_GRI, false);

        String puanMetni = "Yetenek Puanı: " + puan;
        g.drawString(this.font, puanMetni, barX + barGenislik + 10, barY, puan > 0 ? RENK_ALTIN : RENK_GRI, false);
    }

    // ---- Bağlantı çizgileri ----

    private void baglantilariCiz(GuiGraphics g, Set<String> acik, boolean muhurVar, double puls) {
        int parilti = renkKaristir(RENK_ALTIN_PARILTI_A, RENK_ALTIN_PARILTI_B, (float) puls);

        // Önce tüm gri çizgiler, sonra altınlar (altın, gri üstüne binsin)
        for (int gecis = 0; gecis < 2; gecis++) {
            boolean altinGecisi = gecis == 1;
            for (WolfSkill yetenek : WolfSkill.values()) {
                for (WolfSkill on : yetenek.getOnKosullar()) {
                    boolean altin = dugumAcikMi(on, acik, muhurVar) && dugumAcikMi(yetenek, acik, muhurVar);
                    if (altin != altinGecisi) {
                        continue;
                    }

                    int x1 = dugumMerkezX(on);
                    int y1 = dugumMerkezY(on);
                    int x2 = dugumMerkezX(yetenek);
                    int y2 = dugumMerkezY(yetenek);

                    if (altin) {
                        cizgiCiz(g, x1, y1, x2, y2, 7, parilti);      // nabız atan parıltı
                        cizgiCiz(g, x1, y1, x2, y2, 3, RENK_ALTIN);   // çizgi
                    } else {
                        cizgiCiz(g, x1, y1, x2, y2, 3, RENK_CIZGI_GRI);
                    }
                }
            }
        }
    }

    // Yatay/dikey ise tek dikdörtgen, çapraz ise 1 piksellik adımlarla (opak renk kullan, üst üste binmeler sorun olmasın)
    private static void cizgiCiz(GuiGraphics g, int x1, int y1, int x2, int y2, int kalinlik, int renk) {
        int yari = kalinlik / 2;
        if (x1 == x2 || y1 == y2) {
            g.fill(Math.min(x1, x2) - yari, Math.min(y1, y2) - yari,
                    Math.max(x1, x2) - yari + kalinlik, Math.max(y1, y2) - yari + kalinlik, renk);
            return;
        }
        int adim = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= adim; i++) {
            int x = x1 + Math.round((float) (x2 - x1) * i / adim);
            int y = y1 + Math.round((float) (y2 - y1) * i / adim);
            g.fill(x - yari, y - yari, x - yari + kalinlik, y - yari + kalinlik, renk);
        }
    }

    // ---- Düğümler ----

    private void dugumleriCiz(GuiGraphics g, int mouseX, int mouseY, int xp, Set<String> acik,
                              boolean muhurVar, double puls) {
        boolean fareKanvasta = fareKanvastaMi(mouseX, mouseY);

        for (WolfSkill yetenek : WolfSkill.values()) {
            int cx = dugumMerkezX(yetenek);
            int cy = dugumMerkezY(yetenek);

            // Ekran dışındaki düğümü çizme
            if (cx + DUGUM < kanvasX || cx - DUGUM > kanvasX + kanvasGenislik
                    || cy + DUGUM < kanvasY || cy - DUGUM > kanvasY + kanvasYukseklik) {
                continue;
            }

            WolfSkill.Sonuc sonuc = WolfSkill.degerlendir(yetenek, xp, acik, muhurVar);
            Stil stil;
            if (yetenek.isMuhur()) {
                stil = muhurVar ? Stil.ACIK : Stil.KILITLI; // mühür düğümü satın alınmaz, mühür bilgisine bağlı
            } else if (sonuc == WolfSkill.Sonuc.ZATEN_ACIK) {
                stil = Stil.ACIK;
            } else if (sonuc == WolfSkill.Sonuc.ACILABILIR) {
                stil = Stil.ACILABILIR;
            } else {
                stil = Stil.KILITLI;
            }

            int x = cx - DUGUM / 2;
            int y = cy - DUGUM / 2;

            int ic;
            int cerceve;
            boolean kalin = false;
            switch (stil) {
                case ACIK -> {
                    ic = RENK_DUGUM_ACIK;
                    cerceve = yetenek.isMuhur() ? RENK_MOR : RENK_ALTIN;
                    kalin = true;
                }
                case ACILABILIR -> {
                    ic = RENK_DUGUM_ACILABILIR;
                    cerceve = renkKaristir(RENK_ALTIN_KOYU, RENK_ALTIN, (float) puls); // nabız atan altın çerçeve
                    kalin = true;
                }
                default -> {
                    ic = RENK_DUGUM_KILITLI;
                    cerceve = RENK_CIZGI_GRI;
                }
            }

            g.fill(x, y, x + DUGUM, y + DUGUM, ic);
            kutuCerceveCiz(g, x, y, DUGUM, DUGUM, cerceve);
            if (kalin) {
                kutuCerceveCiz(g, x + 1, y + 1, DUGUM - 2, DUGUM - 2, cerceve);
            }

            ikonCiz(g, yetenek, cx, cy, stil, muhurVar, puls);

            if (fareKanvasta && fareDugumde(yetenek, mouseX, mouseY)) {
                g.fill(x, y, x + DUGUM, y + DUGUM, 0x30FFFFFF); // hover vurgusu
                ustundekiYetenek = yetenek;
                ustundekiSonuc = sonuc;
            }
        }
    }

    // 16x16 ikon sprite'ı. Doku yoksa kodla çizilen geçici simge (harf/metin yok).
    private void ikonCiz(GuiGraphics g, WolfSkill yetenek, int cx, int cy, Stil stil, boolean muhurVar, double puls) {
        ResourceLocation doku = ikonDokusu(yetenek, muhurVar);

        if (dokuVarMi(doku)) {
            g.blit(doku, cx - IKON / 2, cy - IKON / 2, 0, 0, IKON, IKON, IKON, IKON);
            if (stil == Stil.KILITLI && !yetenek.isMuhur()) {
                g.fill(cx - IKON / 2, cy - IKON / 2, cx + IKON / 2, cy + IKON / 2, 0xA0000000); // kilitli ikonu karart
            }
            return;
        }

        if (yetenek.isMuhur()) {
            if (muhurVar) {
                muhurGeciciCiz(g, cx, cy, puls);
            } else {
                kilitCiz(g, cx, cy, RENK_GRI);
            }
        } else {
            int renk = switch (stil) {
                case ACIK -> RENK_ALTIN;
                case ACILABILIR -> renkKaristir(RENK_ALTIN_KOYU, RENK_ALTIN, (float) puls);
                case KILITLI -> RENK_CIZGI_GRI;
            };
            elmasCiz(g, cx, cy, 6, renk);
        }
    }

    private ResourceLocation ikonDokusu(WolfSkill yetenek, boolean muhurVar) {
        String ad = yetenek.getId();
        if (yetenek.isMuhur()) {
            ad = muhurVar ? "kaos_muhru_acik" : "kaos_muhru_kilitli";
        }
        return new ResourceLocation(ExampleMod.MOD_ID, "textures/gui/skill/" + ad + ".png");
    }

    private boolean dokuVarMi(ResourceLocation doku) {
        return dokuVarMi.computeIfAbsent(doku.toString(),
                anahtar -> Minecraft.getInstance().getResourceManager().getResource(doku).isPresent());
    }

    private List<Component> ipucuSatirlari(WolfSkill yetenek, WolfSkill.Sonuc sonuc, Set<String> acik, boolean muhurVar) {
        List<Component> satirlar = new ArrayList<>();
        satirlar.add(Component.literal(yetenek.getAd()).withStyle(ChatFormatting.GOLD));
        satirlar.add(Component.literal(yetenek.getAciklama()).withStyle(ChatFormatting.GRAY));

        if (yetenek.isMuhur()) {
            if (muhurVar) {
                satirlar.add(Component.literal("Mühür işlendi").withStyle(ChatFormatting.LIGHT_PURPLE));
            } else {
                satirlar.add(Component.literal("Mühür henüz işlenmedi").withStyle(ChatFormatting.RED));
                satirlar.add(Component.literal(WolfSkill.ALFA_KURDU.acikMi(acik)
                                ? "Kaos Mührü eşyasını kullan."
                                : "Önce Alfa Kurdu'nu aç.")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            return satirlar;
        }

        if (yetenek.getMinSeviye() > 1) {
            satirlar.add(Component.literal("Gereken seviye: " + yetenek.getMinSeviye()).withStyle(ChatFormatting.DARK_GRAY));
        }

        switch (sonuc) {
            case ZATEN_ACIK -> satirlar.add(Component.literal("Açıldı").withStyle(ChatFormatting.GREEN));
            case ACILABILIR -> satirlar.add(Component.literal("Tıkla: 1 yetenek puanı harca").withStyle(ChatFormatting.YELLOW));
            case KATALIZOR_GEREKLI -> satirlar.add(Component.literal("Kaos Mührü gerekli").withStyle(ChatFormatting.DARK_PURPLE));
            case ON_KOSUL_EKSIK -> {
                for (WolfSkill on : yetenek.getOnKosullar()) {
                    if (!dugumAcikMi(on, acik, muhurVar)) {
                        satirlar.add(Component.literal("Önce: " + on.getAd()).withStyle(ChatFormatting.RED));
                    }
                }
            }
            case SEVIYE_YETERSIZ -> satirlar.add(Component.literal("Seviyen yetersiz").withStyle(ChatFormatting.RED));
            case PUAN_YOK -> satirlar.add(Component.literal("Yetenek puanın yok").withStyle(ChatFormatting.RED));
        }
        return satirlar;
    }

    // ================= ETKİLEŞİM =================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ÖNCE widget'lar (Geri butonu). Kanvas alt alanı butonun üstünü de kapladığı için sıra önemli:
        // eskiden kanvas tıklamayı yutuyor ve Geri butonu hiç çalışmıyordu.
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0 && WolfLevelClientState.enfekteMi() && fareKanvastaMi(mouseX, mouseY)) {
            basildi = true;
            surukledi = false;
            basX = mouseX;
            basY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && basildi) {
            if (!surukledi && Math.hypot(mouseX - basX, mouseY - basY) > SURUKLEME_ESIGI) {
                surukledi = true; // küçük titremeler sürükleme sayılmaz
            }
            if (surukledi) {
                offsetX += dragX;
                offsetY += dragY;
                offsetSinirla();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && basildi) {
            boolean tiklamaydi = !surukledi;
            basildi = false;
            surukledi = false;
            if (tiklamaydi) {
                dugumTikla(mouseX, mouseY);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Panel tuşu her şeyi kapatır (ESC ise bir önceki ekrana, yani panele döner)
        if (ExampleModClient.panelTusuEslesirMi(keyCode, scanCode)) {
            this.minecraft.setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void dugumTikla(double mouseX, double mouseY) {
        int xp = WolfLevelClientState.getXp();
        Set<String> acik = WolfLevelClientState.getYetenekler();
        boolean muhurVar = WolfLevelClientState.kaosKilidiAcikMi();

        for (WolfSkill yetenek : WolfSkill.values()) {
            if (yetenek.isMuhur()) {
                continue; // mühür düğümü tıklanarak açılmaz
            }
            if (fareDugumde(yetenek, mouseX, mouseY)
                    && WolfSkill.degerlendir(yetenek, xp, acik, muhurVar) == WolfSkill.Sonuc.ACILABILIR) {
                FriendlyByteBuf buf = PacketByteBufs.create();
                buf.writeUtf(yetenek.getId(), 32);
                ClientPlayNetworking.send(WolfNetworking.YETENEK_AC, buf);
                // Durum güncellemesi sunucunun LEVEL_SENKRON cevabıyla gelir; burada tahmini güncelleme yapmıyoruz.
                return;
            }
        }
    }

    // ================= KONUM / KAYDIRMA =================

    private int dugumMerkezX(WolfSkill yetenek) {
        return (int) Math.round(kanvasX + kanvasGenislik / 2.0 + offsetX + yetenek.getX() * ARALIK);
    }

    private int dugumMerkezY(WolfSkill yetenek) {
        return (int) Math.round(kanvasY + AGAC_UST_BOSLUK + offsetY + yetenek.getY() * ARALIK);
    }

    private boolean fareDugumde(WolfSkill yetenek, double mouseX, double mouseY) {
        int x = dugumMerkezX(yetenek) - DUGUM / 2;
        int y = dugumMerkezY(yetenek) - DUGUM / 2;
        return mouseX >= x && mouseX < x + DUGUM && mouseY >= y && mouseY < y + DUGUM;
    }

    private boolean fareKanvastaMi(double mouseX, double mouseY) {
        return mouseX >= kanvasX && mouseX < kanvasX + kanvasGenislik
                && mouseY >= kanvasY && mouseY < kanvasY + kanvasYukseklik;
    }

    // Ağacı tamamen ekran dışına kaydırıp kaybetmemek için: her yönde en az GORUNUR_MARJ kadarı ekranda kalır
    private void offsetSinirla() {
        double enAzX = GORUNUR_MARJ - kanvasGenislik / 2.0 - maxGx * ARALIK - DUGUM / 2.0;
        double enCokX = kanvasGenislik - GORUNUR_MARJ - kanvasGenislik / 2.0 - minGx * ARALIK + DUGUM / 2.0;
        offsetX = Math.max(enAzX, Math.min(enCokX, offsetX));

        double enAzY = GORUNUR_MARJ - AGAC_UST_BOSLUK - maxGy * ARALIK - DUGUM / 2.0;
        double enCokY = kanvasYukseklik - GORUNUR_MARJ - AGAC_UST_BOSLUK - minGy * ARALIK + DUGUM / 2.0;
        offsetY = Math.max(enAzY, Math.min(enCokY, offsetY));
    }

    // Düğüm açık mı? Mühür düğümü yetenek listesine değil, oyuncunun mühür bilgisine bakar.
    private static boolean dugumAcikMi(WolfSkill yetenek, Set<String> acik, boolean muhurVar) {
        return yetenek.isMuhur() ? muhurVar : yetenek.acikMi(acik);
    }

    // ================= GEÇİCİ SİMGELER (doku yokken, harf/metin kullanmadan) =================

    // Dolu elmas (baklava) şekli
    private static void elmasCiz(GuiGraphics g, int cx, int cy, int yaricap, int renk) {
        for (int dy = -yaricap; dy <= yaricap; dy++) {
            int w = yaricap - Math.abs(dy);
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, renk);
        }
    }

    // Asma kilit (yaklaşık 10x14)
    private static void kilitCiz(GuiGraphics g, int cx, int cy, int renk) {
        g.fill(cx - 3, cy - 6, cx + 3, cy - 5, renk);       // kement üstü
        g.fill(cx - 3, cy - 5, cx - 2, cy - 1, renk);       // kement sol
        g.fill(cx + 2, cy - 5, cx + 3, cy - 1, renk);       // kement sağ
        g.fill(cx - 5, cy - 1, cx + 5, cy + 7, renk);       // gövde
        g.fill(cx - 1, cy + 1, cx + 1, cy + 4, 0xFF000000); // anahtar deliği
    }

    // Parlayan mühür (geçici): iç içe nabız atan mor elmaslar
    private static void muhurGeciciCiz(GuiGraphics g, int cx, int cy, double puls) {
        elmasCiz(g, cx, cy, 7, renkKaristir(0xFF3A1F5A, 0xFF6A35A0, (float) puls));
        elmasCiz(g, cx, cy, 5, renkKaristir(0xFF7A3FC0, RENK_MOR, (float) puls));
        elmasCiz(g, cx, cy, 2, 0xFFFFFFFF);
    }

    // ================= YARDIMCILAR =================

    // İki opak rengi karıştırır (t: 0 -> a, 1 -> b)
    private static int renkKaristir(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int gr = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | bl;
    }

    private static void kutuCerceveCiz(GuiGraphics g, int x, int y, int genislik, int yukseklik, int renk) {
        g.fill(x, y, x + genislik, y + 1, renk);
        g.fill(x, y + yukseklik - 1, x + genislik, y + yukseklik, renk);
        g.fill(x, y, x + 1, y + yukseklik, renk);
        g.fill(x + genislik - 1, y, x + genislik, y + yukseklik, renk);
    }
}