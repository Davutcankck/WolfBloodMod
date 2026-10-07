package com.example.client;

import com.example.InfectionData;
import com.example.WolfNetworking;
import com.example.WolfRutbe;
import com.example.WolfSkill;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Kurt Paneli: rütbe, seviye, XP barı, yetenek puanı ve yetenek ağacı.
 *
 * Ağaç düğümleri ve koordinatları WolfSkill'den okunur; panel boyutu ağacın sınırlarına göre
 * otomatik hesaplanır. Yani ağaca düğüm/dal eklemek bu dosyaya dokunmayı gerektirmez.
 * Tüm çizim kodla yapılıyor (doku dosyası gerekmiyor).
 */
public class KurtPaneliScreen extends Screen {

    // ---- Yerleşim ----
    private static final int DUGUM_BOYUT = 20;
    private static final int ARALIK = 28;               // 1 ızgara birimi = kaç piksel
    private static final int KENAR_BOSLUGU = 12;
    private static final int AYIRAC_Y = 84;
    private static final int UST_BILGI_YUKSEKLIGI = 90; // ayraç dahil, ağaç bunun altında başlar
    private static final int AGAC_BOSLUGU = 14;
    private static final int MIN_PANEL_GENISLIK = 240;

    // ---- Renkler (ARGB) ----
    private static final int RENK_ARKA_PLAN = 0xE0101015;
    private static final int RENK_CERCEVE = 0xFF4A4A55;
    private static final int RENK_METIN = 0xFFFFFFFF;
    private static final int RENK_GRI = 0xFFA0A0A8;
    private static final int RENK_ALTIN = 0xFFE0B040;
    private static final int RENK_BAR_ARKA = 0xFF26262E;
    private static final int RENK_BAR_DOLGU = 0xFFB03030;
    private static final int RENK_DUGUM_ALINABILIR = 0xFF2A2A33;
    private static final int RENK_DUGUM_KILITLI = 0xFF16161B;
    private static final int RENK_CIZGI_KILITLI = 0xFF33333B;

    private int panelX;
    private int panelY;
    private int panelGenislik;
    private int panelYukseklik;
    private int agacX; // ağacın sol üst köşesi (ilk düğümün sol üstü)
    private int agacY;
    private int minGridX;
    private int minGridY;

    // Bu karede fare hangi düğümün üstünde (ipucu çizmek için)
    private WolfSkill ustundekiYetenek;
    private WolfSkill.Sonuc ustundekiSonuc;

    public KurtPaneliScreen() {
        super(Component.literal("Kurt Paneli"));
    }

    @Override
    protected void init() {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (WolfSkill yetenek : WolfSkill.values()) {
            minX = Math.min(minX, yetenek.getX());
            maxX = Math.max(maxX, yetenek.getX());
            minY = Math.min(minY, yetenek.getY());
            maxY = Math.max(maxY, yetenek.getY());
        }
        minGridX = minX;
        minGridY = minY;

        int agacGenislik = (maxX - minX) * ARALIK + DUGUM_BOYUT;
        int agacYukseklik = (maxY - minY) * ARALIK + DUGUM_BOYUT;

        panelGenislik = Math.max(MIN_PANEL_GENISLIK, agacGenislik + 2 * KENAR_BOSLUGU);
        panelYukseklik = UST_BILGI_YUKSEKLIGI + AGAC_BOSLUGU + agacYukseklik + AGAC_BOSLUGU;

        panelX = (this.width - panelGenislik) / 2;
        panelY = (this.height - panelYukseklik) / 2;

        agacX = panelX + (panelGenislik - agacGenislik) / 2;
        agacY = panelY + UST_BILGI_YUKSEKLIGI + AGAC_BOSLUGU;
    }

    @Override
    public boolean isPauseScreen() {
        return false; // çok oyunculuda zaten durmaz, tek oyunculuda da oyun akmaya devam etsin
    }

    // ================= ÇİZİM =================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        kutuCiz(g, panelX, panelY, panelGenislik, panelYukseklik);

        int merkezX = panelX + panelGenislik / 2;
        g.drawCenteredString(this.font, "KURT PANELİ", merkezX, panelY + 10, RENK_METIN);

        ustundekiYetenek = null;
        ustundekiSonuc = null;

        if (!WolfLevelClientState.enfekteMi()) {
            g.drawCenteredString(this.font, "Damarlarında henüz kurt kanı yok.",
                    merkezX, panelY + panelYukseklik / 2, RENK_GRI);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        int xp = WolfLevelClientState.getXp();
        Set<String> acik = WolfLevelClientState.getYetenekler();
        boolean kaosAcik = WolfLevelClientState.kaosKilidiAcikMi();
        int seviye = InfectionData.seviyeHesapla(xp);
        int puan = WolfSkill.kullanilabilirPuan(xp, acik);

        ustBilgiCiz(g, xp, seviye, puan);
        agacCiz(g, mouseX, mouseY, xp, acik, kaosAcik);
        altBilgiCiz(g, kaosAcik);

        super.render(g, mouseX, mouseY, partialTick);

        // İpucu en son çizilir ki her şeyin üstünde kalsın
        if (ustundekiYetenek != null) {
            g.renderComponentTooltip(this.font, ipucuSatirlari(ustundekiYetenek, ustundekiSonuc, acik), mouseX, mouseY);
        }
    }

    private void ustBilgiCiz(GuiGraphics g, int xp, int seviye, int puan) {
        int solX = panelX + KENAR_BOSLUGU;
        int sagX = panelX + panelGenislik - KENAR_BOSLUGU;

        // Rütbe (solda) ve seviye (sağda)
        WolfRutbe rutbe = WolfRutbe.seviyeyeGore(seviye);
        String rutbeEtiketi = "Rütbe: ";
        g.drawString(this.font, rutbeEtiketi, solX, panelY + 26, RENK_GRI, false);
        g.drawString(this.font, rutbe.getAd(), solX + this.font.width(rutbeEtiketi), panelY + 26, rutbe.getRenk(), false);

        String seviyeMetni = "Seviye " + seviye;
        g.drawString(this.font, seviyeMetni, sagX - this.font.width(seviyeMetni), panelY + 26, RENK_METIN, false);

        // XP barı
        int barX = solX;
        int barY = panelY + 42;
        int barGenislik = panelGenislik - 2 * KENAR_BOSLUGU;
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
        cerceveCiz(g, barX, barY, barGenislik, barYukseklik, RENK_CERCEVE);
        g.drawCenteredString(this.font, xpMetni, panelX + panelGenislik / 2, barY + barYukseklik + 4, RENK_GRI);

        // Yetenek puanı
        g.drawCenteredString(this.font, "Yetenek Puanı: " + puan,
                panelX + panelGenislik / 2, panelY + 68, puan > 0 ? RENK_ALTIN : RENK_GRI);

        // Ayraç
        g.fill(solX, panelY + AYIRAC_Y, sagX, panelY + AYIRAC_Y + 1, RENK_CERCEVE);
    }

    private void altBilgiCiz(GuiGraphics g, boolean kaosAcik) {
        if (!kaosAcik) {
            g.drawCenteredString(this.font, "Kaos dalı kilitli: Alfa Kurdu ol ve Kaos İksiri'ni iç.",
                    panelX + panelGenislik / 2, panelY + panelYukseklik - 11, WolfSkill.Dal.KAOS.getRenk());
        }
    }

    private void agacCiz(GuiGraphics g, int mouseX, int mouseY, int xp, Set<String> acik, boolean kaosAcik) {
        // 1) Bağlantı çizgileri (düğümlerin altında kalsın). Önkoşuldan düğüme: önce yatay, sonra dikey.
        for (WolfSkill yetenek : WolfSkill.values()) {
            for (WolfSkill on : yetenek.getOnKosullar()) {
                int ox = dugumMerkezX(on);
                int oy = dugumMerkezY(on);
                int hx = dugumMerkezX(yetenek);
                int hy = dugumMerkezY(yetenek);
                int renk = on.acikMi(acik) ? koyulastir(yetenek.getDal().getRenk(), 0.8f) : RENK_CIZGI_KILITLI;
                cizgiCiz(g, ox, oy, hx, oy, renk);
                cizgiCiz(g, hx, oy, hx, hy, renk);
            }
        }

        // 2) Düğümler
        for (WolfSkill yetenek : WolfSkill.values()) {
            WolfSkill.Sonuc sonuc = WolfSkill.degerlendir(yetenek, xp, acik, kaosAcik);
            int x = dugumMerkezX(yetenek) - DUGUM_BOYUT / 2;
            int y = dugumMerkezY(yetenek) - DUGUM_BOYUT / 2;
            int dalRengi = yetenek.getDal().getRenk();

            int dolgu;
            int cerceve;
            if (sonuc == WolfSkill.Sonuc.ZATEN_ACIK) {
                dolgu = koyulastir(dalRengi, 0.55f);
                cerceve = dalRengi;
            } else if (sonuc == WolfSkill.Sonuc.ACILABILIR) {
                dolgu = RENK_DUGUM_ALINABILIR;
                cerceve = RENK_ALTIN;
            } else {
                dolgu = RENK_DUGUM_KILITLI;
                cerceve = RENK_CIZGI_KILITLI;
            }

            g.fill(x, y, x + DUGUM_BOYUT, y + DUGUM_BOYUT, dolgu);
            cerceveCiz(g, x, y, DUGUM_BOYUT, DUGUM_BOYUT, cerceve);

            boolean soluk = sonuc != WolfSkill.Sonuc.ZATEN_ACIK && sonuc != WolfSkill.Sonuc.ACILABILIR;
            String simge = yetenek.getAd().substring(0, 1).toUpperCase();
            g.drawCenteredString(this.font, simge, x + DUGUM_BOYUT / 2, y + (DUGUM_BOYUT - 8) / 2,
                    soluk ? RENK_CIZGI_KILITLI : RENK_METIN);

            if (fareDugumde(yetenek, mouseX, mouseY)) {
                g.fill(x, y, x + DUGUM_BOYUT, y + DUGUM_BOYUT, 0x30FFFFFF); // hover vurgusu
                ustundekiYetenek = yetenek;
                ustundekiSonuc = sonuc;
            }
        }
    }

    private List<Component> ipucuSatirlari(WolfSkill yetenek, WolfSkill.Sonuc sonuc, Set<String> acik) {
        List<Component> satirlar = new ArrayList<>();
        satirlar.add(Component.literal(yetenek.getAd()).withStyle(ChatFormatting.GOLD));
        satirlar.add(Component.literal(yetenek.getAciklama()).withStyle(ChatFormatting.GRAY));
        if (yetenek.getMinSeviye() > 1) {
            satirlar.add(Component.literal("Gereken seviye: " + yetenek.getMinSeviye()).withStyle(ChatFormatting.DARK_GRAY));
        }

        switch (sonuc) {
            case ZATEN_ACIK -> satirlar.add(Component.literal("Açıldı").withStyle(ChatFormatting.GREEN));
            case ACILABILIR -> satirlar.add(Component.literal("Tıkla: 1 yetenek puanı harca").withStyle(ChatFormatting.YELLOW));
            case KATALIZOR_GEREKLI -> satirlar.add(Component.literal("Kaos İksiri gerekli").withStyle(ChatFormatting.DARK_PURPLE));
            case ON_KOSUL_EKSIK -> {
                for (WolfSkill on : yetenek.getOnKosullar()) {
                    if (!on.acikMi(acik)) {
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
        if (button == 0 && WolfLevelClientState.enfekteMi()) {
            int xp = WolfLevelClientState.getXp();
            Set<String> acik = WolfLevelClientState.getYetenekler();
            boolean kaosAcik = WolfLevelClientState.kaosKilidiAcikMi();

            for (WolfSkill yetenek : WolfSkill.values()) {
                if (fareDugumde(yetenek, mouseX, mouseY)
                        && WolfSkill.degerlendir(yetenek, xp, acik, kaosAcik) == WolfSkill.Sonuc.ACILABILIR) {
                    yetenekAcIstegiGonder(yetenek);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Panel tuşuna tekrar basınca kapansın (ESC zaten Screen tarafından işleniyor)
        if (ExampleModClient.panelTusuEslesirMi(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void yetenekAcIstegiGonder(WolfSkill yetenek) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(yetenek.getId(), 32);
        ClientPlayNetworking.send(WolfNetworking.YETENEK_AC, buf);
        // Durum güncellemesi sunucunun LEVEL_SENKRON cevabıyla gelir; burada tahmini güncelleme yapmıyoruz.
    }

    // ================= YARDIMCILAR =================

    private int dugumMerkezX(WolfSkill yetenek) {
        return agacX + (yetenek.getX() - minGridX) * ARALIK + DUGUM_BOYUT / 2;
    }

    private int dugumMerkezY(WolfSkill yetenek) {
        return agacY + (yetenek.getY() - minGridY) * ARALIK + DUGUM_BOYUT / 2;
    }

    private boolean fareDugumde(WolfSkill yetenek, double mouseX, double mouseY) {
        int x = dugumMerkezX(yetenek) - DUGUM_BOYUT / 2;
        int y = dugumMerkezY(yetenek) - DUGUM_BOYUT / 2;
        return mouseX >= x && mouseX < x + DUGUM_BOYUT && mouseY >= y && mouseY < y + DUGUM_BOYUT;
    }

    private static int koyulastir(int argb, float oran) {
        int r = (int) (((argb >> 16) & 0xFF) * oran);
        int gr = (int) (((argb >> 8) & 0xFF) * oran);
        int b = (int) ((argb & 0xFF) * oran);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    // Sadece yatay veya dikey çizgi (2 piksel kalınlık)
    private static void cizgiCiz(GuiGraphics g, int x1, int y1, int x2, int y2, int renk) {
        g.fill(Math.min(x1, x2) - 1, Math.min(y1, y2) - 1, Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, renk);
    }

    private static void kutuCiz(GuiGraphics g, int x, int y, int genislik, int yukseklik) {
        g.fill(x, y, x + genislik, y + yukseklik, RENK_ARKA_PLAN);
        cerceveCiz(g, x, y, genislik, yukseklik, RENK_CERCEVE);
    }

    private static void cerceveCiz(GuiGraphics g, int x, int y, int genislik, int yukseklik, int renk) {
        g.fill(x, y, x + genislik, y + 1, renk);
        g.fill(x, y + yukseklik - 1, x + genislik, y + yukseklik, renk);
        g.fill(x, y, x + 1, y + yukseklik, renk);
        g.fill(x + genislik - 1, y, x + genislik, y + yukseklik, renk);
    }
}