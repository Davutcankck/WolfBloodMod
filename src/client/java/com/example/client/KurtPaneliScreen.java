package com.example.client;

import com.example.InfectionData;
import com.example.WolfRutbe;
import com.example.WolfSkill;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Kurt Paneli (ana panel): rütbe, seviye, XP barı, yetenek puanı ve Kaos durumu.
 * "Yetenekler" butonu yetenek ağacı ekranını (YetenekAgaciScreen) açar.
 *
 * Çizim tamamen kodla yapılıyor (doku dosyası gerekmiyor).
 */
public class KurtPaneliScreen extends Screen {

    private static final int PANEL_GENISLIK = 240;
    private static final int PANEL_YUKSEKLIK = 140;
    private static final int KENAR_BOSLUGU = 12;

    private static final int RENK_ARKA_PLAN = 0xE0101015;
    private static final int RENK_CERCEVE = 0xFF4A4A55;
    private static final int RENK_METIN = 0xFFFFFFFF;
    private static final int RENK_GRI = 0xFFA0A0A8;
    private static final int RENK_ALTIN = 0xFFE0B040;
    private static final int RENK_BAR_ARKA = 0xFF26262E;
    private static final int RENK_BAR_DOLGU = 0xFFB03030;

    private int panelX;
    private int panelY;
    private Button yetenekButonu;

    public KurtPaneliScreen() {
        super(Component.literal("Kurt Paneli"));
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_GENISLIK) / 2;
        panelY = (this.height - PANEL_YUKSEKLIK) / 2;

        int butonGenislik = 120;
        yetenekButonu = Button.builder(Component.literal("Yetenekler"),
                        dugme -> this.minecraft.setScreen(new YetenekAgaciScreen(this)))
                .bounds(panelX + (PANEL_GENISLIK - butonGenislik) / 2, panelY + PANEL_YUKSEKLIK - 32, butonGenislik, 20)
                .build();
        this.addRenderableWidget(yetenekButonu);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // çok oyunculuda zaten durmaz, tek oyunculuda da oyun akmaya devam etsin
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.fill(panelX, panelY, panelX + PANEL_GENISLIK, panelY + PANEL_YUKSEKLIK, RENK_ARKA_PLAN);
        cerceveCiz(g, panelX, panelY, PANEL_GENISLIK, PANEL_YUKSEKLIK, RENK_CERCEVE);

        int merkezX = panelX + PANEL_GENISLIK / 2;
        g.drawCenteredString(this.font, "KURT PANELİ", merkezX, panelY + 10, RENK_METIN);

        boolean enfekte = WolfLevelClientState.enfekteMi();
        yetenekButonu.active = enfekte;

        if (!enfekte) {
            g.drawCenteredString(this.font, "Damarlarında henüz kurt kanı yok.",
                    merkezX, panelY + 56, RENK_GRI);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        int xp = WolfLevelClientState.getXp();
        int seviye = InfectionData.seviyeHesapla(xp);
        int puan = WolfSkill.kullanilabilirPuan(xp, WolfLevelClientState.getYetenekler());
        boolean kaosAcik = WolfLevelClientState.kaosKilidiAcikMi();

        int solX = panelX + KENAR_BOSLUGU;
        int sagX = panelX + PANEL_GENISLIK - KENAR_BOSLUGU;

        // Rütbe (solda) ve seviye (sağda)
        WolfRutbe rutbe = WolfRutbe.seviyeyeGore(seviye);
        String rutbeEtiketi = "Rütbe: ";
        g.drawString(this.font, rutbeEtiketi, solX, panelY + 26, RENK_GRI, false);
        g.drawString(this.font, rutbe.getAd(), solX + this.font.width(rutbeEtiketi), panelY + 26, rutbe.getRenk(), false);

        String seviyeMetni = "Seviye " + seviye;
        g.drawString(this.font, seviyeMetni, sagX - this.font.width(seviyeMetni), panelY + 26, RENK_METIN, false);

        // XP barı
        int barY = panelY + 42;
        int barGenislik = PANEL_GENISLIK - 2 * KENAR_BOSLUGU;
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

        g.fill(solX, barY, solX + barGenislik, barY + barYukseklik, RENK_BAR_ARKA);
        g.fill(solX, barY, solX + (int) (barGenislik * oran), barY + barYukseklik, RENK_BAR_DOLGU);
        cerceveCiz(g, solX, barY, barGenislik, barYukseklik, RENK_CERCEVE);
        g.drawCenteredString(this.font, xpMetni, merkezX, barY + barYukseklik + 4, RENK_GRI);

        // Yetenek puanı ve Kaos durumu
        g.drawCenteredString(this.font, "Yetenek Puanı: " + puan, merkezX, panelY + 70,
                puan > 0 ? RENK_ALTIN : RENK_GRI);

        if (kaosAcik) {
            g.drawCenteredString(this.font, "Kaos dalı: uyanık", merkezX, panelY + 84,
                    WolfSkill.Dal.KAOS.getRenk());
        } else {
            g.drawCenteredString(this.font, "Kaos dalı: kilitli", merkezX, panelY + 84, RENK_GRI);
        }

        super.render(g, mouseX, mouseY, partialTick);
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

    private static void cerceveCiz(GuiGraphics g, int x, int y, int genislik, int yukseklik, int renk) {
        g.fill(x, y, x + genislik, y + 1, renk);
        g.fill(x, y + yukseklik - 1, x + genislik, y + yukseklik, renk);
        g.fill(x, y, x + 1, y + yukseklik, renk);
        g.fill(x + genislik - 1, y, x + genislik, y + yukseklik, renk);
    }
}