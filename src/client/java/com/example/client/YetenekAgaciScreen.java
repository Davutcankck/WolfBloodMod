package com.example.client;

import com.example.ExampleMod;
import com.example.InfectionData;
import com.example.WolfNetworking;
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
 * Yetenek ağacı ekranı (tam ekran).
 *
 *  - Ağaç YUKARIDAN AŞAĞI açılır. Her dal bir sekmedir (Temel / Kaos), koordinatlar WolfSkill'den gelir.
 *  - Bağlantı çizgileri: Temel sekmede KIRMIZI, Kaos sekmesinde MOR (renkler WolfSkill.Dal içinde).
 *  - Ağaç fare ile sürüklenerek kaydırılır (pan). Zoom YOK.
 *  - Düğüme tıklama, fare bırakılınca çalışır; sürükleme sırasında yanlışlıkla yetenek açılmaz.
 *  - Düğüm ikonu: textures/gui/skill/<yetenek_id>.png varsa o çizilir, yoksa baş harf (doku isteğe bağlı).
 */
public class YetenekAgaciScreen extends Screen {

    // ---- Yerleşim ----
    private static final int DUGUM = 24;
    private static final int ARALIK_X = 56;
    private static final int ARALIK_Y = 52;
    private static final int AGAC_UST_BOSLUK = 34;   // kanvasın üstünden ilk satıra mesafe
    private static final int GORUNUR_MARJ = 60;      // kaydırırken ağacın en az bu kadarı ekranda kalır

    private static final int UST_BILGI_YUKSEKLIGI = 62;
    private static final int SEKME_GENISLIK = 90;
    private static final int SEKME_YUKSEKLIK = 18;
    private static final int SEKME_Y = 38;
    private static final int SEKME_ARALIK = 6;

    // true: kilitli Kaos sekmesi gri "önizleme" olarak açılır, false: kilitliyken sekmeye girilemez
    private static final boolean KILITLI_SEKME_ONIZLEME = true;

    // ---- Renkler (ARGB) ----
    private static final int RENK_ZEMIN = 0xFF0B0B10;
    private static final int RENK_TUGLA_DERZI = 0xFF14141B;
    private static final int RENK_METIN = 0xFFFFFFFF;
    private static final int RENK_GRI = 0xFFA0A0A8;
    private static final int RENK_ALTIN = 0xFFE0B040;
    private static final int RENK_BAR_ARKA = 0xFF26262E;
    private static final int RENK_BAR_DOLGU = 0xFFB03030;
    private static final int RENK_CERCEVE = 0xFF4A4A55;
    private static final int RENK_DUGUM_KILITLI = 0xFF17171C;
    private static final int RENK_DUGUM_ALINABILIR = 0xFF22222A;
    private static final int RENK_CERCEVE_KILITLI = 0xFF3A3A44;
    private static final int RENK_CIZGI_KILITLI = 0xFF2A2A31;
    private static final int RENK_CIZGI_SEKME_KILITLI = 0xFF3A2F45;

    private final Screen ustEkran;

    private WolfSkill.Dal aktifSekme = WolfSkill.Dal.TEMEL;
    // Her sekmenin kendi kaydırma konumu (sekme değişince kaybolmasın)
    private final double[] panX = new double[WolfSkill.Dal.values().length];
    private final double[] panY = new double[WolfSkill.Dal.values().length];

    private int kanvasX;
    private int kanvasY;
    private int kanvasGenislik;
    private int kanvasYukseklik;

    // Fare basma/sürükleme takibi
    private boolean basildi = false;
    private boolean surukledi = false;
    private double basX;
    private double basY;

    // Bu karede fare hangi düğümün üstünde (ipucu çizmek için)
    private WolfSkill ustundekiYetenek;
    private WolfSkill.Sonuc ustundekiSonuc;

    // Düğüm ikon dokusunun var olup olmadığı önbelleği (ekran her açıldığında yenilenir)
    private final Map<String, Boolean> ikonVarMi = new HashMap<>();

    public YetenekAgaciScreen(Screen ustEkran) {
        super(Component.literal("Yetenek Ağacı"));
        this.ustEkran = ustEkran;
    }

    @Override
    protected void init() {
        kanvasX = 0;
        kanvasY = UST_BILGI_YUKSEKLIGI;
        kanvasGenislik = this.width;
        kanvasYukseklik = Math.max(40, this.height - UST_BILGI_YUKSEKLIGI);
        ikonVarMi.clear();

        this.addRenderableWidget(Button.builder(Component.literal("Geri"), dugme -> this.onClose())
                .bounds(this.width - 70, this.height - 24, 62, 18)
                .build());

        for (WolfSkill.Dal sekme : WolfSkill.Dal.values()) {
            panSinirla(sekme);
        }
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
        boolean kaosAcik = WolfLevelClientState.kaosKilidiAcikMi();
        int seviye = InfectionData.seviyeHesapla(xp);
        int puan = WolfSkill.kullanilabilirPuan(xp, acik);

        ustBilgiCiz(g, xp, seviye, puan);
        sekmeleriCiz(g, kaosAcik);

        boolean sekmeKilitli = !aktifSekme.kilitAcikMi(kaosAcik);
        long zaman = System.currentTimeMillis();

        // Ağaç sadece kanvas alanında görünsün (üst bilginin altına taşmasın)
        g.enableScissor(kanvasX, kanvasY, kanvasX + kanvasGenislik, kanvasY + kanvasYukseklik);
        baglantilariCiz(g, acik, sekmeKilitli, zaman);
        dugumleriCiz(g, mouseX, mouseY, xp, acik, kaosAcik, zaman);
        g.disableScissor();

        if (sekmeKilitli) {
            String bilgi = WolfSkill.ALFA_KURDU.acikMi(acik)
                    ? "Kaos dalı kilitli: Kaos İksiri'ni iç."
                    : "Önce Alfa Kurdu'nu aç, sonra Kaos İksiri'ni iç.";
            g.drawCenteredString(this.font, bilgi, this.width / 2, kanvasY + 8, WolfSkill.Dal.KAOS.getRenk());
        }

        super.render(g, mouseX, mouseY, partialTick);

        // İpucu en son çizilir ki her şeyin üstünde kalsın
        if (ustundekiYetenek != null) {
            g.renderComponentTooltip(this.font, ipucuSatirlari(ustundekiYetenek, ustundekiSonuc, acik), mouseX, mouseY);
        }
    }

    private void zeminCiz(GuiGraphics g) {
        g.fill(0, 0, this.width, this.height, RENK_ZEMIN);
        // Basit tuğla deseni (doku gerektirmez): yatay derzler + her sırada kaydırılmış dikey derzler
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
        g.drawCenteredString(this.font, "YETENEK AĞACI", this.width / 2, 6, RENK_METIN);

        int barGenislik = Math.min(240, this.width - 220);
        barGenislik = Math.max(80, barGenislik);
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
        g.drawCenteredString(this.font, xpMetni, this.width / 2, barY + barYukseklik + 3, RENK_GRI);

        String seviyeMetni = "Seviye " + seviye;
        g.drawString(this.font, seviyeMetni, barX - 8 - this.font.width(seviyeMetni), barY, RENK_METIN, false);

        String puanMetni = "Yetenek Puanı: " + puan;
        g.drawString(this.font, puanMetni, barX + barGenislik + 8, barY, puan > 0 ? RENK_ALTIN : RENK_GRI, false);
    }

    private void sekmeleriCiz(GuiGraphics g, boolean kaosAcik) {
        WolfSkill.Dal[] sekmeler = WolfSkill.Dal.values();
        for (int i = 0; i < sekmeler.length; i++) {
            WolfSkill.Dal sekme = sekmeler[i];
            int x = sekmeX(i);
            boolean secili = sekme == aktifSekme;
            boolean kilitli = !sekme.kilitAcikMi(kaosAcik);

            g.fill(x, SEKME_Y, x + SEKME_GENISLIK, SEKME_Y + SEKME_YUKSEKLIK, secili ? 0xFF1E1E26 : 0xFF121217);
            kutuCerceveCiz(g, x, SEKME_Y, SEKME_GENISLIK, SEKME_YUKSEKLIK, RENK_CERCEVE);
            if (secili) {
                g.fill(x, SEKME_Y + SEKME_YUKSEKLIK - 2, x + SEKME_GENISLIK, SEKME_Y + SEKME_YUKSEKLIK, sekme.getCizgiRengi());
            }

            int yaziRengi = kilitli ? RENK_GRI : (secili ? sekme.getRenk() : RENK_METIN);
            g.drawCenteredString(this.font, sekme.getAd(), x + SEKME_GENISLIK / 2 + (kilitli ? 5 : 0),
                    SEKME_Y + (SEKME_YUKSEKLIK - 8) / 2, yaziRengi);
            if (kilitli) {
                kilitCiz(g, x + 14, SEKME_Y + 5, RENK_GRI);
            }
        }
    }

    private void baglantilariCiz(GuiGraphics g, Set<String> acik, boolean sekmeKilitli, long zaman) {
        int cizgiRengi = aktifSekme.getCizgiRengi();
        double puls = 0.5 + 0.5 * Math.sin(zaman / 350.0);

        for (WolfSkill yetenek : WolfSkill.values()) {
            if (yetenek.getDal() != aktifSekme) {
                continue;
            }
            for (WolfSkill on : yetenek.getOnKosullar()) {
                if (on.getDal() != aktifSekme) {
                    continue; // sekmeler arası bağlantı çizilmez
                }

                int ustX = dugumMerkezX(on);
                int ustY = dugumMerkezY(on) + DUGUM / 2;
                int altX = dugumMerkezX(yetenek);
                int altY = dugumMerkezY(yetenek) - DUGUM / 2;
                int ortaY = ustY + Math.max(0, altY - ustY) / 2; // "gövde" çizgisi iki satırın tam ortasında

                boolean ustAcik = on.acikMi(acik);
                boolean altAcik = yetenek.acikMi(acik);

                if (sekmeKilitli) {
                    rotaCiz(g, ustX, ustY, altX, altY, ortaY, 2, RENK_CIZGI_SEKME_KILITLI);
                } else if (ustAcik && altAcik) {
                    // Açılmış yol: nabız atan parıltı + parlak çizgi
                    rotaCiz(g, ustX, ustY, altX, altY, ortaY, 6, alfa(cizgiRengi, (int) (25 + 45 * puls)));
                    rotaCiz(g, ustX, ustY, altX, altY, ortaY, 3, cizgiRengi);
                } else if (ustAcik) {
                    // Sıradaki açılabilir yol: sönük
                    rotaCiz(g, ustX, ustY, altX, altY, ortaY, 2, koyulastir(cizgiRengi, 0.5f));
                } else {
                    rotaCiz(g, ustX, ustY, altX, altY, ortaY, 2, RENK_CIZGI_KILITLI);
                }
            }
        }
    }

    // Üstteki düğümden alttakine: aşağı in, yatay git, aşağı in
    private void rotaCiz(GuiGraphics g, int ustX, int ustY, int altX, int altY, int ortaY, int kalinlik, int renk) {
        parcaCiz(g, ustX, ustY, ustX, ortaY, kalinlik, renk);
        parcaCiz(g, ustX, ortaY, altX, ortaY, kalinlik, renk);
        parcaCiz(g, altX, ortaY, altX, altY, kalinlik, renk);
    }

    // Sadece yatay veya dikey çizgi parçası
    private void parcaCiz(GuiGraphics g, int x1, int y1, int x2, int y2, int kalinlik, int renk) {
        int yari = kalinlik / 2;
        g.fill(Math.min(x1, x2) - yari, Math.min(y1, y2) - yari,
                Math.max(x1, x2) - yari + kalinlik, Math.max(y1, y2) - yari + kalinlik, renk);
    }

    private void dugumleriCiz(GuiGraphics g, int mouseX, int mouseY, int xp, Set<String> acik,
                              boolean kaosAcik, long zaman) {
        double puls = 0.5 + 0.5 * Math.sin(zaman / 350.0);
        boolean fareKanvasta = fareKanvastaMi(mouseX, mouseY);

        for (WolfSkill yetenek : WolfSkill.values()) {
            if (yetenek.getDal() != aktifSekme) {
                continue;
            }

            WolfSkill.Sonuc sonuc = WolfSkill.degerlendir(yetenek, xp, acik, kaosAcik);
            int x = dugumMerkezX(yetenek) - DUGUM / 2;
            int y = dugumMerkezY(yetenek) - DUGUM / 2;
            int vurgu = yetenek.getDal().getRenk();

            int ic;
            int cerceve;
            boolean kalinCerceve = false;
            switch (sonuc) {
                case ZATEN_ACIK -> {
                    ic = koyulastir(vurgu, 0.35f);
                    cerceve = vurgu;
                    kalinCerceve = true;
                }
                case ACILABILIR -> {
                    ic = RENK_DUGUM_ALINABILIR;
                    cerceve = alfa(RENK_ALTIN, (int) (140 + 115 * puls)); // nabız atan altın çerçeve
                    kalinCerceve = true;
                }
                default -> {
                    ic = RENK_DUGUM_KILITLI;
                    cerceve = RENK_CERCEVE_KILITLI;
                }
            }

            g.fill(x, y, x + DUGUM, y + DUGUM, ic);
            kutuCerceveCiz(g, x, y, DUGUM, DUGUM, cerceve);
            if (kalinCerceve) {
                kutuCerceveCiz(g, x + 1, y + 1, DUGUM - 2, DUGUM - 2, cerceve);
            }

            boolean soluk = sonuc != WolfSkill.Sonuc.ZATEN_ACIK && sonuc != WolfSkill.Sonuc.ACILABILIR;
            ikonCiz(g, yetenek, x, y, soluk);

            if (sonuc == WolfSkill.Sonuc.KATALIZOR_GEREKLI) {
                kilitCiz(g, x + DUGUM - 9, y - 3, RENK_GRI); // köşede asma kilit
            }

            if (fareKanvasta && fareDugumde(yetenek, mouseX, mouseY)) {
                g.fill(x, y, x + DUGUM, y + DUGUM, 0x30FFFFFF); // hover vurgusu
                ustundekiYetenek = yetenek;
                ustundekiSonuc = sonuc;
            }
        }
    }

    // İkon dokusu varsa onu, yoksa baş harfi çizer
    private void ikonCiz(GuiGraphics g, WolfSkill yetenek, int x, int y, boolean soluk) {
        ResourceLocation doku = new ResourceLocation(ExampleMod.MOD_ID, "textures/gui/skill/" + yetenek.getId() + ".png");
        boolean dokuVar = ikonVarMi.computeIfAbsent(yetenek.getId(),
                id -> Minecraft.getInstance().getResourceManager().getResource(doku).isPresent());

        if (dokuVar) {
            int ikonBoyut = 16;
            int ix = x + (DUGUM - ikonBoyut) / 2;
            int iy = y + (DUGUM - ikonBoyut) / 2;
            g.blit(doku, ix, iy, 0, 0, ikonBoyut, ikonBoyut, ikonBoyut, ikonBoyut);
            if (soluk) {
                g.fill(ix, iy, ix + ikonBoyut, iy + ikonBoyut, 0xA0000000); // kilitli ikonu karart
            }
        } else {
            String simge = yetenek.getAd().substring(0, 1).toUpperCase();
            g.drawCenteredString(this.font, simge, x + DUGUM / 2, y + (DUGUM - 8) / 2,
                    soluk ? RENK_CERCEVE_KILITLI : RENK_METIN);
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
            // Sekme tıklaması anında çalışır
            WolfSkill.Dal[] sekmeler = WolfSkill.Dal.values();
            for (int i = 0; i < sekmeler.length; i++) {
                int x = sekmeX(i);
                if (mouseX >= x && mouseX < x + SEKME_GENISLIK && mouseY >= SEKME_Y && mouseY < SEKME_Y + SEKME_YUKSEKLIK) {
                    boolean kilitli = !sekmeler[i].kilitAcikMi(WolfLevelClientState.kaosKilidiAcikMi());
                    if (!kilitli || KILITLI_SEKME_ONIZLEME) {
                        aktifSekme = sekmeler[i];
                        panSinirla(aktifSekme);
                    }
                    return true;
                }
            }

            // Kanvas: basmayı kaydet, düğüm tıklaması fare bırakılınca değerlendirilir
            if (fareKanvastaMi(mouseX, mouseY)) {
                basildi = true;
                surukledi = false;
                basX = mouseX;
                basY = mouseY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && basildi) {
            if (!surukledi && Math.hypot(mouseX - basX, mouseY - basY) > 3) {
                surukledi = true; // küçük titremeler sürükleme sayılmaz
            }
            if (surukledi) {
                int i = aktifSekme.ordinal();
                panX[i] += dragX;
                panY[i] += dragY;
                panSinirla(aktifSekme);
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
        boolean kaosAcik = WolfLevelClientState.kaosKilidiAcikMi();

        for (WolfSkill yetenek : WolfSkill.values()) {
            if (yetenek.getDal() != aktifSekme) {
                continue;
            }
            if (fareDugumde(yetenek, mouseX, mouseY)
                    && WolfSkill.degerlendir(yetenek, xp, acik, kaosAcik) == WolfSkill.Sonuc.ACILABILIR) {
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
        return (int) Math.round(kanvasX + kanvasGenislik / 2.0 + panX[yetenek.getDal().ordinal()]
                + yetenek.getX() * ARALIK_X);
    }

    private int dugumMerkezY(WolfSkill yetenek) {
        return (int) Math.round(kanvasY + AGAC_UST_BOSLUK + panY[yetenek.getDal().ordinal()]
                + yetenek.getY() * ARALIK_Y);
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
    private void panSinirla(WolfSkill.Dal sekme) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (WolfSkill yetenek : WolfSkill.values()) {
            if (yetenek.getDal() == sekme) {
                minX = Math.min(minX, yetenek.getX());
                maxX = Math.max(maxX, yetenek.getX());
                minY = Math.min(minY, yetenek.getY());
                maxY = Math.max(maxY, yetenek.getY());
            }
        }
        if (minX == Integer.MAX_VALUE) {
            return;
        }

        int i = sekme.ordinal();

        // Yatay: sağ kenar >= MARJ ve sol kenar <= genişlik - MARJ
        double enAzX = GORUNUR_MARJ - kanvasGenislik / 2.0 - maxX * ARALIK_X - DUGUM / 2.0;
        double enCokX = kanvasGenislik - GORUNUR_MARJ - kanvasGenislik / 2.0 - minX * ARALIK_X + DUGUM / 2.0;
        panX[i] = Math.max(enAzX, Math.min(enCokX, panX[i]));

        // Dikey: alt kenar >= MARJ ve üst kenar <= yükseklik - MARJ (kanvas içi koordinatlarda)
        double enAzY = GORUNUR_MARJ - AGAC_UST_BOSLUK - maxY * ARALIK_Y - DUGUM / 2.0;
        double enCokY = kanvasYukseklik - GORUNUR_MARJ - AGAC_UST_BOSLUK - minY * ARALIK_Y + DUGUM / 2.0;
        panY[i] = Math.max(enAzY, Math.min(enCokY, panY[i]));
    }

    private int sekmeX(int sira) {
        int adet = WolfSkill.Dal.values().length;
        int toplam = adet * SEKME_GENISLIK + (adet - 1) * SEKME_ARALIK;
        return this.width / 2 - toplam / 2 + sira * (SEKME_GENISLIK + SEKME_ARALIK);
    }

    // ================= YARDIMCILAR =================

    private static int alfa(int argb, int alfa) {
        return (Math.max(0, Math.min(255, alfa)) << 24) | (argb & 0x00FFFFFF);
    }

    private static int koyulastir(int argb, float oran) {
        int r = (int) (((argb >> 16) & 0xFF) * oran);
        int gr = (int) (((argb >> 8) & 0xFF) * oran);
        int b = (int) ((argb & 0xFF) * oran);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    // Küçük asma kilit (8x8): gövde + kement. (x,y) sol üst köşe.
    private static void kilitCiz(GuiGraphics g, int x, int y, int renk) {
        g.fill(x + 2, y, x + 6, y + 1, renk);       // kement üstü
        g.fill(x + 2, y + 1, x + 3, y + 4, renk);   // kement sol
        g.fill(x + 5, y + 1, x + 6, y + 4, renk);   // kement sağ
        g.fill(x, y + 4, x + 8, y + 9, renk);       // gövde
        g.fill(x + 3, y + 5, x + 5, y + 7, 0xFF000000); // anahtar deliği
    }

    private static void kutuCerceveCiz(GuiGraphics g, int x, int y, int genislik, int yukseklik, int renk) {
        g.fill(x, y, x + genislik, y + 1, renk);
        g.fill(x, y + yukseklik - 1, x + genislik, y + yukseklik, renk);
        g.fill(x, y, x + 1, y + yukseklik, renk);
        g.fill(x + genislik - 1, y, x + genislik, y + yukseklik, renk);
    }
}