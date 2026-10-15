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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Yetenek ağacı ekranı (tam ekran, tek ağaç, yukarıdan aşağı).
 *
 * Faz A - Görsel Cila & Atmosfer Geliştirmeleri:
 *  - Karanlık tuğla duvar, vinyet karartması ve animasyonlu meşaleler.
 *  - Büyütülmüş merkez Alfa Kurdu (kor kırmızı gözler, yumuşak hale).
 *  - Açık yollarda akan enerji parçacıkları; Kaos dalında zincir motifi; Temel dalda kan damlası animasyonu.
 *  - Eğimli (bevel), iç gölgeli ve köşe perçinli düğüm çerçeveleri (kare & elmas).
 *  - İsteğe bağlı doku desteği (varsa doku, yoksa yüksek kaliteli kodla çizim). 16x16 / 32x32 esnek ikon ölçekleme.
 *  - Seviye atlama / yetenek açılma animasyonu (çizgi dolumu, flaş ve kıvılcım).
 *  - Hover parlama, basma (press-down) efekti ve ekran altı hata banner'ı.
 *  - Genişletilmiş renkli ipucu kutusu, şeritli başlık plakası, özel X butonu ve Home tuşu ile merkeze odaklanma.
 */
public class WolfSkillScreen extends Screen {

    // =========================================================================
    // AYARLANABİLİR SABİTLER (BOYUTLAR, RENKLER, HIZLAR VE SÜRELER)
    // =========================================================================

    // ---- Boyutlar ve Yerleşim ----
    private static final int DUGUM_BOYUT_NORMAL = 24;            // Normal yetenek düğüm boyutu (kare/elmas)
    private static final int DUGUM_BOYUT_KOK = 30;               // En üstteki kök düğüm boyutu
    private static final int DUGUM_BOYUT_MERKEZ = 38;            // Merkez Alfa düğümü boyutu (~1.58x)
    private static final int IKON_BOYUT = 16;                    // Düğüm içi ikon çizim boyutu (ekranda)
    private static final int ARALIK_X = 56;                      // Izgara yatay birim mesafesi
    private static final int ARALIK_Y = 46;                      // Izgara dikey birim mesafesi
    private static final int KOK_EK_Y = -14;                     // Kök düğümün hafif yukarı ötelenmesi
    private static final int UST_BILGI_YUKSEKLIK = 38;           // Üst bilgi paneli yüksekliği
    private static final int GORUNUR_MARJ = 60;                  // Ağacın ekranda kalma minimum marjı
    private static final int SURUKLEME_ESIGI = 3;                // Tıklama ile sürüklemeyi ayıran piksel eşiği

    // ---- Renk Paleti (ARGB) ----
    private static final int RENK_ZEMIN = 0xFF121217;
    private static final int RENK_TUGLA_DERZI = 0xFF0A0A0D;
    private static final int RENK_METIN_BEYAZ = 0xFFFFFFFF;
    private static final int RENK_METIN_GRI = 0xFFA0A0A8;
    private static final int RENK_METIN_KIRMIZI = 0xFFFF4444;
    private static final int RENK_METIN_YESIL = 0xFF55FF55;
    private static final int RENK_METIN_SARI = 0xFFFFFF55;
    private static final int RENK_ALTIN = 0xFFFFD700;
    private static final int RENK_ALTIN_PARLAK = 0xFFFFF080;
    private static final int RENK_KIRMIZI_CANLI = 0xFFFF2222;
    private static final int RENK_KAN_KOYU = 0xFF880000;
    private static final int RENK_MOR_KAOS = 0xFFBA55D3;
    private static final int RENK_MOR_KOYU = 0xFF4A156D;
    private static final int RENK_HAT_KILITLI = 0xFF2A2A34;
    private static final int RENK_BAR_ARKA = 0xFF1C1C24;
    private static final int RENK_BAR_DOLGU = 0xFFCC2222;
    private static final int RENK_TAS_CERCEVE = 0xFF4A4A58;
    private static final int RENK_TAS_KOYU = 0xFF1A1A22;

    // ---- Animasyon Hızları ve Süreleri (Milisaniye) ----
    private static final double PULS_PERIYOT_MS = 700.0;         // Standart nabız döngüsü periyodu
    private static final double GOZ_PULS_PERIYOT_MS = 1400.0;    // Alfa gözleri yavaş nabız periyodu
    private static final double ENERJI_AKIS_HIZI_MS = 1200.0;    // Çizgilerdeki parlak nokta akış hızı
    private static final double DAMLA_PERIYOT_MS = 1800.0;       // Kan damlası damlama animasyonu periyodu
    private static final long ACILMA_ANIMASYON_SURE_MS = 900;    // Yetenek açılma flaş/dolma animasyon süresi
    private static final long HATA_MESAJI_SURE_MS = 2500;        // Ekran altı hata uyarı süresi
    private static final int MESALE_KARE_SURE_MS = 130;          // Meşale alev animasyon karesi süresi
    private static final int TOZ_PARTIKUL_SAYISI = 16;           // Yüzen ortam toz partikülü sayısı

    // ---- İsteğe Bağlı Doku Yolları (assets/wolfblood/textures/gui/...) ----
    private static final ResourceLocation DOKU_TUGLA = new ResourceLocation(ExampleMod.MOD_ID, "textures/gui/tugla.png");
    private static final ResourceLocation DOKU_BASLIK_SERIT = new ResourceLocation(ExampleMod.MOD_ID, "textures/gui/baslik_serit.png");
    private static final ResourceLocation DOKU_KAPAT = new ResourceLocation(ExampleMod.MOD_ID, "textures/gui/kapat.png");
    private static final ResourceLocation DOKU_KAPAT_HOVER = new ResourceLocation(ExampleMod.MOD_ID, "textures/gui/kapat_hover.png");
    private static final ResourceLocation DOKU_KAYDIRMA_RAY = new ResourceLocation(ExampleMod.MOD_ID, "textures/gui/kaydirma_ray.png");
    private static final ResourceLocation DOKU_KAYDIRMA_TUTAC = new ResourceLocation(ExampleMod.MOD_ID, "textures/gui/kaydirma_tutac.png");
    private static final ResourceLocation DOKU_CIZGI_ZINCIR = new ResourceLocation(ExampleMod.MOD_ID, "textures/gui/cizgi_zincir.png");
    private static final ResourceLocation DOKU_MERKEZ_GOZ = new ResourceLocation(ExampleMod.MOD_ID, "textures/gui/merkez_goz.png");

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

    // Ağaç sınırları (ızgara birimi)
    private int minGx;
    private int maxGx;
    private int minGy;
    private int maxGy;

    // Fare basma/sürükleme takibi
    private boolean basildi = false;
    private boolean surukledi = false;
    private double basX;
    private double basY;

    // Sağ dikey kaydırma çubuğu (scrollbar) takibi
    private boolean scrollbarSurukleniyor = false;

    // Kapat butonu konumu ve hover durumu
    private int kapatX;
    private int kapatY;
    private static final int KAPAT_BOYUT = 20;
    private boolean kapatHover = false;

    // Bu karede fare hangi düğümün üstünde (ipucu çizmek için)
    private WolfSkill ustundekiYetenek;
    private WolfSkill.Sonuc ustundekiSonuc;

    // Doku varlık ve boyut önbelleği
    private final Map<String, Boolean> dokuVarMi = new HashMap<>();
    private final Map<String, int[]> dokuBoyutlari = new HashMap<>();

    // Yetenek açılma animasyonu takibi
    private final Set<String> bilinenYetenekler = new HashSet<>();
    private WolfSkill animasyonYetenek = null;
    private long animasyonBaslangic = 0;

    // Hata geri bildirimi bildirimi (ekran altı)
    private String hataMesaji = null;
    private long hataBitisZamani = 0;

    public WolfSkillScreen(Screen ustEkran) {
        super(Component.literal("Yetenek Ağacı"));
        this.ustEkran = ustEkran;
    }

    @Override
    protected void init() {
        kanvasX = 0;
        kanvasY = UST_BILGI_YUKSEKLIK;
        kanvasGenislik = this.width;
        kanvasYukseklik = Math.max(40, this.height - UST_BILGI_YUKSEKLIK);
        dokuVarMi.clear();
        dokuBoyutlari.clear();

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

        // Kapatma (X) butonu pozisyonu
        kapatX = this.width - 26;
        kapatY = 6;

        // Ekran açılışında otomatik olarak merkez Alfa Kurdu'na (y = 2) odaklan
        offsetX = 0;
        offsetY = - (WolfSkill.ALFA_KURDU.getY() * ARALIK_Y);
        offsetSinirla();

        // Başlangıçtaki açık yetenekleri kaydet (sonradan gelenler animasyon tetikler)
        bilinenYetenekler.clear();
        bilinenYetenekler.addAll(WolfLevelClientState.getYetenekler());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(ustEkran);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =========================================================================
    // ÇİZİM DÖNGÜSÜ (RENDER)
    // =========================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long zaman = System.currentTimeMillis();
        double puls = 0.5 + 0.5 * Math.sin(zaman / PULS_PERIYOT_MS);
        double gozPuls = 0.5 + 0.5 * Math.sin(zaman / GOZ_PULS_PERIYOT_MS);

        // Sunucudan yeni açılan yetenek gelip gelmediğini kontrol et
        yeniAcilanYetenekleriKontrolEt();

        // 1. Arka plan tuğla duvarı, vinyet, meşaleler ve yüzen atmosfer parçacıkları
        zeminCiz(g, zaman);

        ustundekiYetenek = null;
        ustundekiSonuc = null;

        if (!WolfLevelClientState.enfekteMi()) {
            g.drawCenteredString(this.font, "Damarlarında henüz kurt kanı yok.",
                    this.width / 2, this.height / 2, RENK_METIN_GRI);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        int xp = WolfLevelClientState.getXp();
        Set<String> acik = WolfLevelClientState.getYetenekler();
        boolean muhurVar = WolfLevelClientState.kaosKilidiAcikMi();
        int seviye = InfectionData.seviyeHesapla(xp);
        int puan = WolfSkill.kullanilabilirPuan(xp, acik);

        // 2. Alt rünik taş süsleme plakaları (odaklanan dala göre dinamik ad)
        altRunikBantlariCiz(g, puls);

        // 3. Kırpma alanı: ağaç ve hatlar sadece kanvas içinde çizilir
        g.enableScissor(kanvasX, kanvasY, kanvasX + kanvasGenislik, kanvasY + kanvasYukseklik);
        baglantilariCiz(g, acik, muhurVar, puls, zaman);
        dugumleriCiz(g, mouseX, mouseY, xp, acik, muhurVar, puls, gozPuls, zaman);
        g.disableScissor();

        // 4. Sağ kenardaki dikey kaydırma çubuğu
        scrollbarCiz(g);

        // 5. Sabit üst bilgi banner'ı (şeritli plaka, XP barı, nabız atan puan)
        ustBilgiCiz(g, xp, seviye, puan, puls);

        // 6. Özel kapatma (X) butonu
        kapatButonuCiz(g, mouseX, mouseY);

        // 7. Hata bildirim banner'ı (ekran altı)
        hataBildirimiCiz(g, zaman);

        super.render(g, mouseX, mouseY, partialTick);

        // 8. İpucu kutusu (en üst katman)
        if (ustundekiYetenek != null) {
            ipucuCiz(g, ustundekiYetenek, ustundekiSonuc, acik, muhurVar, seviye, puan, mouseX, mouseY);
        }
    }

    // =========================================================================
    // A4: ARKA PLAN, VİNYET, MEŞALELER VE ATMOSFERİK PARÇACIKLAR
    // =========================================================================

    private void zeminCiz(GuiGraphics g, long zaman) {
        g.fill(0, 0, this.width, this.height, RENK_ZEMIN);

        // Döşenebilir tuğla dokusu varsa kullan, yoksa zengin kodla çizim
        if (dokuVarMi(DOKU_TUGLA)) {
            int[] d = dokuBoyutlariniAl(DOKU_TUGLA);
            int tw = d[0] > 0 ? d[0] : 32;
            int th = d[1] > 0 ? d[1] : 32;
            for (int x = 0; x < this.width; x += tw) {
                for (int y = 0; y < this.height; y += th) {
                    g.blit(DOKU_TUGLA, x, y, Math.min(tw, this.width - x), Math.min(th, this.height - y),
                            0, 0, Math.min(tw, this.width - x), Math.min(th, this.height - y), tw, th);
                }
            }
        } else {
            // Deterministik renk gürültülü zindan taş tuğlaları
            int tuglaBoy = 14;
            int tuglaEn = 28;
            for (int y = 0; y < this.height; y += tuglaBoy) {
                int satir = y / tuglaBoy;
                int kayma = (satir % 2 == 0) ? 0 : (tuglaEn / 2);
                for (int x = -kayma; x < this.width; x += tuglaEn) {
                    // Deterministik koordinat tabanlı gürültü / ton varyasyonu
                    int h = ((x * 374761393 + y * 668265263) ^ 0x5bf03635) & 0x7FFFFFFF;
                    int varyasyon = h % 4;
                    int renkTugla;
                    switch (varyasyon) {
                        case 0 -> renkTugla = 0xFF171720;
                        case 1 -> renkTugla = 0xFF1B1B25;
                        case 2 -> renkTugla = 0xFF14141C;
                        default -> renkTugla = 0xFF1E1E28;
                    }

                    g.fill(x + 1, y + 1, x + tuglaEn, y + tuglaBoy, renkTugla);
                    // Üst taş eğimi (kabartma ışık)
                    g.fill(x + 1, y + 1, x + tuglaEn, y + 2, 0x14FFFFFF);
                    // Alt taş gölgesi
                    g.fill(x + 1, y + tuglaBoy - 1, x + tuglaEn, y + tuglaBoy, 0x1A000000);
                }
                // Harç derz çizgisi
                g.fill(0, y, this.width, y + 1, RENK_TUGLA_DERZI);
            }
        }

        // Meşaleler (sol ve sağ duvarda hafif paralaks ile)
        int mesaleYParalaks = (int) (offsetY * 0.12);
        mesaleCiz(g, 18, kanvasY + kanvasYukseklik / 2 + mesaleYParalaks, zaman, true);
        mesaleCiz(g, this.width - 24, kanvasY + kanvasYukseklik / 2 + mesaleYParalaks, zaman, false);

        // Vinyet (kenar karartması)
        vinyetCiz(g);

        // Yüzen ince atmosferik toz/köz parçacıkları
        tozPartikulleriCiz(g, zaman);
    }

    private void vinyetCiz(GuiGraphics g) {
        // Doğal karanlık zindan kenar gölgesi (kırmızı doku yok, sadece hafif kenar karartması)
        int vBoyut = Math.min(70, this.width / 5);
        g.fillGradient(0, 0, this.width, vBoyut, 0x80000000, 0x00000000);
        g.fillGradient(0, this.height - vBoyut, this.width, this.height, 0x00000000, 0x80000000);
        g.fillGradient(0, 0, vBoyut, this.height, 0x80000000, 0x00000000);
        g.fillGradient(this.width - vBoyut, 0, this.width, this.height, 0x00000000, 0x80000000);
    }

    private void mesaleCiz(GuiGraphics g, int x, int y, long zaman, boolean solTaraf) {
        // Meşale alevi animasyon karesi (0..3)
        int kare = (int) ((zaman / MESALE_KARE_SURE_MS) % 4);
        ResourceLocation mesaleDoku = new ResourceLocation(ExampleMod.MOD_ID, "textures/gui/mesale_" + kare + ".png");

        // Yumuşak turuncu ortam ışığı (halo)
        double alevPuls = 0.85 + 0.15 * Math.sin(zaman / 180.0 + (solTaraf ? 0 : 2.5));
        int auraBoyut = (int) (48 * alevPuls);
        g.fillGradient(x - auraBoyut, y - auraBoyut, x + auraBoyut, y + auraBoyut, 0x28FF8800, 0x00FF8800);

        if (dokuVarMi(mesaleDoku)) {
            int[] d = dokuBoyutlariniAl(mesaleDoku);
            g.blit(mesaleDoku, x - 8, y - 16, 16, 32, 0, 0, d[0], d[1], d[0], d[1]);
            return;
        }

        // Kodla çizilen meşale
        // 1. Duvar demir plakası & eğik demir braket
        int braketX = solTaraf ? x - 4 : x + 4;
        g.fill(braketX - 2, y + 4, braketX + 2, y + 10, 0xFF2A2A32);
        g.fill(x - 2, y + 2, x + 2, y + 5, 0xFF353540);

        // 2. Ahşap meşale sapı
        g.fill(x - 1, y, x + 2, y + 14, 0xFF5C3317);
        g.fill(x - 1, y, x, y + 14, 0xFF7D4620); // sol açık ton

        // 3. Üst katranlı bez başlığı
        g.fill(x - 2, y - 3, x + 3, y + 1, 0xFF281D14);

        // 4. 4 Kareli animasyonlu alev çekirdeği
        int fx = x;
        int fy = y - 4;
        int titremeX = (kare == 1 ? 1 : (kare == 3 ? -1 : 0));
        int titremeY = (kare % 2 == 0 ? 0 : -1);

        // Dış kırmızı alev
        g.fill(fx - 3 + titremeX, fy - 6 + titremeY, fx + 4 + titremeX, fy, 0xDDE03010);
        // Orta turuncu alev
        g.fill(fx - 2 + titremeX, fy - 5 + titremeY, fx + 3 + titremeX, fy - 1, 0xFFFF7700);
        // İç parlak sarı çekirdek
        g.fill(fx - 1, fy - 4 + titremeY, fx + 2, fy - 2, 0xFFFFF080);
        // Alev ucundaki minik kıvılcım
        if (kare == 0 || kare == 2) {
            g.fill(fx + titremeX, fy - 8 + titremeY, fx + 1 + titremeX, fy - 7 + titremeY, 0xFFFFCC33);
        }
    }

    private void tozPartikulleriCiz(GuiGraphics g, long zaman) {
        for (int i = 0; i < TOZ_PARTIKUL_SAYISI; i++) {
            // Her partikül için deterministik sabit tohumlar
            int tohumX = (i * 97 + 13) % 1000;
            int tohumY = (i * 151 + 37) % 1000;
            int hiz = 25 + (i * 17) % 30; // piksel / saniye

            int px = (int) ((tohumX * this.width / 1000.0 + Math.sin(zaman / 1000.0 + i) * 12) % this.width);
            if (px < 0) px += this.width;

            int py = (int) ((tohumY * this.height / 1000.0 - (zaman * hiz / 1000.0)) % this.height);
            if (py < 0) py += this.height;

            double pAlfa = 0.3 + 0.3 * Math.sin(zaman / 400.0 + i);
            int alfa = (int) (pAlfa * 255);
            int pRenk = (alfa << 24) | 0x00FFCC77;
            g.fill(px, py, px + 1, py + 1, pRenk);
        }
    }

    // =========================================================================
    // A2: BAĞLANTI ÇİZGİLERİ, ENERJİ AKIŞI, KAOS ZİNCİRİ & KAN DAMLASI ANİMASYONU
    // =========================================================================

    private void baglantilariCiz(GuiGraphics g, Set<String> acik, boolean muhurVar, double puls, long zaman) {
        for (int gecis = 0; gecis < 2; gecis++) {
            boolean aktifGecis = (gecis == 1);
            for (WolfSkill yetenek : WolfSkill.values()) {
                for (WolfSkill on : yetenek.getOnKosullar()) {
                    boolean aktif = dugumAcikMi(on, acik, muhurVar) && dugumAcikMi(yetenek, acik, muhurVar);
                    if (aktif != aktifGecis) continue;

                    int x1 = dugumMerkezX(on);
                    int y1 = dugumMerkezY(on);
                    int x2 = dugumMerkezX(yetenek);
                    int y2 = dugumMerkezY(yetenek);

                    if (aktif) {
                        // Açık bağlantı yolu
                        if (yetenek.getDal() == WolfSkill.Dal.KAOS) {
                            // Kaos dalı: Mor zincir deseni
                            kaosZincirHattiCiz(g, x1, y1, x2, y2, true, puls);
                        } else {
                            // Temel dal: Altın parıltılı kalın hat
                            int dalRenk = yetenek.getDal().getRenk();
                            int parilti = renkKaristir(dalRenk, 0xFFFFFFFF, (float) (puls * 0.4));
                            cizgiCiz(g, x1, y1, x2, y2, 6, parilti & 0x60FFFFFF);
                            cizgiCiz(g, x1, y1, x2, y2, 3, dalRenk);

                            // Temel dal için süzülen kan damlası animasyonu
                            kanDamlasiAnimasyonuCiz(g, x1, y1, x2, y2, yetenek.hashCode(), zaman);
                        }

                        // Ebeveynden çocuğa akan parlak enerji parçacığı
                        akanEnerjiPartikuluCiz(g, x1, y1, x2, y2, yetenek.getDal().getRenk(), on.hashCode(), zaman);
                    } else {
                        // Kilitli bağlantı yolu
                        if (yetenek.getDal() == WolfSkill.Dal.KAOS) {
                            kaosZincirHattiCiz(g, x1, y1, x2, y2, false, puls);
                        } else {
                            cizgiCiz(g, x1, y1, x2, y2, 2, RENK_HAT_KILITLI);
                        }
                    }

                    // A5: Yetenek açılma anı çizgi dolumu animasyonu
                    if (animasyonYetenek == yetenek && (zaman - animasyonBaslangic) < (ACILMA_ANIMASYON_SURE_MS / 2)) {
                        cizgiDolmaAnimasyonuCiz(g, x1, y1, x2, y2, zaman - animasyonBaslangic);
                    }
                }
            }
        }
    }

    private void kaosZincirHattiCiz(GuiGraphics g, int x1, int y1, int x2, int y2, boolean aktif, double puls) {
        if (dokuVarMi(DOKU_CIZGI_ZINCIR)) {
            // Doku varsa basit çizgi ve doku overlay
            int renk = aktif ? RENK_MOR_KAOS : 0xFF35303D;
            cizgiCiz(g, x1, y1, x2, y2, aktif ? 4 : 2, renk);
            return;
        }

        // Kodla çizilen dönüşümlü zincir halkaları
        double mesafe = Math.hypot(x2 - x1, y2 - y1);
        if (mesafe <= 2) return;

        double dx = (x2 - x1) / mesafe;
        double dy = (y2 - y1) / mesafe;
        double px = -dy;
        double py = dx;

        int adimSayisi = (int) (mesafe / 6.0);
        int halkaRenk = aktif ? renkKaristir(0xFF9932CC, RENK_MOR_KAOS, (float) puls) : 0xFF35303D;
        int auraRenk = aktif ? (0x50BA55D3) : 0x00000000;

        for (int i = 1; i < adimSayisi; i++) {
            double cx = x1 + dx * (i * 6.0);
            double cy = y1 + dy * (i * 6.0);

            if (aktif) {
                g.fill((int) cx - 3, (int) cy - 3, (int) cx + 4, (int) cy + 4, auraRenk);
            }

            if (i % 2 == 0) {
                // Hat boyunca uzanan halka (içi boş dikdörtgen)
                int rx = (int) Math.round(cx);
                int ry = (int) Math.round(cy);
                g.fill(rx - 2, ry - 1, rx + 3, ry + 2, halkaRenk);
                g.fill(rx - 1, ry, rx + 2, ry + 1, 0xFF14141B);
            } else {
                // Çapraz ara bağlantı halkası
                int rx = (int) Math.round(cx + px);
                int ry = (int) Math.round(cy + py);
                g.fill(rx - 1, ry - 1, rx + 2, ry + 2, halkaRenk);
            }
        }
    }

    private void akanEnerjiPartikuluCiz(GuiGraphics g, int x1, int y1, int x2, int y2, int dalRenk, int tohum, long zaman) {
        double ilerleme = ((zaman + Math.abs(tohum) * 233) % (long) ENERJI_AKIS_HIZI_MS) / ENERJI_AKIS_HIZI_MS;
        int ex = (int) (x1 + (x2 - x1) * ilerleme);
        int ey = (int) (y1 + (y2 - y1) * ilerleme);

        // Dış parıltı halesi
        g.fill(ex - 2, ey - 2, ex + 3, ey + 3, dalRenk & 0x60FFFFFF);
        // Parlak merkez çekirdek
        g.fill(ex - 1, ey - 1, ex + 2, ey + 2, 0xFFFFFFFF);
    }

    private void kanDamlasiAnimasyonuCiz(GuiGraphics g, int x1, int y1, int x2, int y2, int tohum, long zaman) {
        double damlaT = ((zaman + Math.abs(tohum) * 377) % (long) DAMLA_PERIYOT_MS) / DAMLA_PERIYOT_MS;
        int midX = (x1 + x2) / 2;
        int midY = (y1 + y2) / 2;

        if (damlaT < 0.6) {
            // 1. Aşama: Damla toplanır ve aşağı doğru sarkar
            int damlaBoy = (int) (damlaT * 8);
            g.fill(midX - 1, midY + 1, midX + 2, midY + 2 + damlaBoy, RENK_KAN_KOYU);
            g.fill(midX, midY + damlaBoy, midX + 1, midY + damlaBoy + 2, RENK_KIRMIZI_CANLI);
        } else {
            // 2. Aşama: Damla hattan kopup süzülerek düşer
            double dususT = (damlaT - 0.6) / 0.4;
            int dususY = (int) (dususT * 14);
            int alfa = (int) ((1.0 - dususT) * 255);
            int renk = (alfa << 24) | (RENK_KIRMIZI_CANLI & 0x00FFFFFF);
            g.fill(midX, midY + 6 + dususY, midX + 2, midY + 8 + dususY, renk);
        }
    }

    private void cizgiDolmaAnimasyonuCiz(GuiGraphics g, int x1, int y1, int x2, int y2, long gecenMs) {
        float oran = Math.min(1.0f, gecenMs / (float) (ACILMA_ANIMASYON_SURE_MS / 2));
        int dolanX = (int) (x1 + (x2 - x1) * oran);
        int dolanY = (int) (y1 + (y2 - y1) * oran);
        // İlerleyen parlak dolum hattı
        cizgiCiz(g, x1, y1, dolanX, dolanY, 5, 0xE0FFFFFF);
        // Uçtaki flaş kafası
        g.fill(dolanX - 3, dolanY - 3, dolanX + 4, dolanY + 4, 0xFFFFFFFF);
    }

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

    // =========================================================================
    // A1, A3, A5, A6: DÜĞÜM ÇİZİMİ, ÇERÇEVELER, PARLAMA, BASTIRMA VE FLAŞ
    // =========================================================================

    private void dugumleriCiz(GuiGraphics g, int mouseX, int mouseY, int xp, Set<String> acik,
                              boolean muhurVar, double puls, double gozPuls, long zaman) {
        boolean fareKanvasta = fareKanvastaMi(mouseX, mouseY);

        for (WolfSkill yetenek : WolfSkill.values()) {
            int cx = dugumMerkezX(yetenek);
            int cy = dugumMerkezY(yetenek);
            int boyut = dugumBoyutu(yetenek);

            // Ekran dışındaki düğümleri kırp
            if (cx + boyut < kanvasX || cx - boyut > kanvasX + kanvasGenislik
                    || cy + boyut < kanvasY || cy - boyut > kanvasY + kanvasYukseklik) {
                continue;
            }

            WolfSkill.Sonuc sonuc = WolfSkill.degerlendir(yetenek, xp, acik, muhurVar);
            Stil stil;
            if (yetenek.isMuhur()) {
                stil = muhurVar ? Stil.ACIK : Stil.KILITLI;
            } else if (sonuc == WolfSkill.Sonuc.ZATEN_ACIK) {
                stil = Stil.ACIK;
            } else if (sonuc == WolfSkill.Sonuc.ACILABILIR) {
                stil = Stil.ACILABILIR;
            } else {
                stil = Stil.KILITLI;
            }

            boolean hover = fareKanvasta && fareDugumde(yetenek, mouseX, mouseY);
            // A6: Tıklamada 1 piksel bastırma efekti
            int basmaOfset = (hover && basildi && !surukledi) ? 1 : 0;
            cx += basmaOfset;
            cy += basmaOfset;
            int x = cx - boyut / 2;
            int y = cy - boyut / 2;

            if (yetenek == WolfSkill.ALFA_KURDU) {
                // A1: Merkez Alfa Kurdu
                merkezAlfaDugumuCiz(g, cx, cy, stil, gozPuls);
            } else if (yetenek == WolfSkill.VAHSI_UYANIS) {
                // Büyütülmüş kök düğüm
                kokDugumCiz(g, cx, cy, stil, puls);
            } else if (isBaklava(yetenek)) {
                // A3: Elmas/Baklava düğüm kutusu
                elmasDugumKutusuCiz(g, yetenek, cx, cy, boyut / 2 + 2, stil, puls);
                ikonCiz(g, yetenek, cx, cy, stil, muhurVar, puls);
                if (stil == Stil.KILITLI) {
                    kucukKilitCiz(g, cx + boyut / 2 - 4, cy + boyut / 2 - 5);
                }
            } else {
                // A3: Kare düğüm kutusu
                kareDugumKutusuCiz(g, yetenek, x, y, boyut, stil, puls);
                ikonCiz(g, yetenek, cx, cy, stil, muhurVar, puls);
                if (stil == Stil.KILITLI && !yetenek.isMuhur()) {
                    kucukKilitCiz(g, x + boyut - 7, y + boyut - 8);
                }
            }

            // A6: Hover parlaması
            if (hover) {
                if (isBaklava(yetenek)) {
                    elmasCiz(g, cx, cy, boyut / 2 + 3, 0x25FFFFFF);
                } else {
                    g.fill(x - 1, y - 1, x + boyut + 1, y + boyut + 1, 0x25FFFFFF);
                }
                ustundekiYetenek = yetenek;
                ustundekiSonuc = sonuc;
            }

            // A5: Yetenek açılma flaş patlaması ve kıvılcımları
            if (animasyonYetenek == yetenek) {
                yetenekAcilmaFlasiCiz(g, cx, cy, boyut, zaman - animasyonBaslangic);
            }
        }
    }

    private void merkezAlfaDugumuCiz(GuiGraphics g, int cx, int cy, Stil stil, double gozPuls) {
        int r = DUGUM_BOYUT_MERKEZ / 2;

        // Doku kontrolü: node_merkez_acik.png / node_merkez_kilitli.png
        ResourceLocation dokuMerkez = new ResourceLocation(ExampleMod.MOD_ID,
                "textures/gui/node_merkez_" + (stil != Stil.KILITLI ? "acik" : "kilitli") + ".png");

        if (dokuVarMi(dokuMerkez)) {
            int[] d = dokuBoyutlariniAl(dokuMerkez);
            g.blit(dokuMerkez, cx - r, cy - r, DUGUM_BOYUT_MERKEZ, DUGUM_BOYUT_MERKEZ, 0, 0, d[0], d[1], d[0], d[1]);
        } else {
            // Kodla yumuşak kırmızı hale ve büyük taş blok
            int cerceveRenk;
            int gozKirmizi;
            int gozParlak;

            if (stil == Stil.ACIK) {
                // Tam uyanmış Alfa: Yumuşak kırmızı katmanlı hale
                int aura = renkKaristir(0x20CC0000, 0x70FF2020, (float) gozPuls);
                g.fill(cx - r - 6, cy - r - 6, cx + r + 6, cy + r + 6, aura & 0x25FFFFFF);
                g.fill(cx - r - 4, cy - r - 4, cx + r + 4, cy + r + 4, aura);
                cerceveRenk = 0xFFFF3333;
                gozKirmizi = renkKaristir(0xFFDD1111, 0xFFFF3333, (float) gozPuls);
                gozParlak = 0xFFFFAAAA;
            } else if (stil == Stil.ACILABILIR) {
                int aura = renkKaristir(0x15FFAA00, 0x40FF3333, (float) gozPuls);
                g.fill(cx - r - 3, cy - r - 3, cx + r + 3, cy + r + 3, aura);
                cerceveRenk = renkKaristir(0xFF555566, RENK_ALTIN, (float) gozPuls);
                gozKirmizi = renkKaristir(0xFF550000, 0xFFAA2222, (float) gozPuls);
                gozParlak = 0xFFDD8888;
            } else {
                cerceveRenk = 0xFF353542;
                gozKirmizi = 0xFF1C0606;
                gozParlak = 0xFF1C0606;
            }

            // Bevel ve iç gölgeli taş çerçeve
            g.fill(cx - r, cy - r, cx + r, cy + r, RENK_TAS_KOYU);
            kutuCerceveCiz(g, cx - r, cy - r, DUGUM_BOYUT_MERKEZ, DUGUM_BOYUT_MERKEZ, cerceveRenk);
            // Köşe perçinleri
            kosePerçinleriCiz(g, cx - r, cy - r, DUGUM_BOYUT_MERKEZ, DUGUM_BOYUT_MERKEZ, cerceveRenk);
            // İç karanlık zemin
            int icR = r - 3;
            g.fill(cx - icR, cy - icR, cx + icR, cy + icR, 0xFF08080C);

            // Gözler: merkez_goz.png varsa onu çiz, yoksa parlayan kurt gözleri
            if (dokuVarMi(DOKU_MERKEZ_GOZ)) {
                int[] d = dokuBoyutlariniAl(DOKU_MERKEZ_GOZ);
                g.blit(DOKU_MERKEZ_GOZ, cx - 12, cy - 6, 24, 12, 0, 0, d[0], d[1], d[0], d[1]);
            } else {
                // Sol Göz
                int solGx = cx - 7;
                g.fill(solGx - 4, cy - 2, solGx + 3, cy + 2, gozKirmizi);
                g.fill(solGx - 2, cy - 3, solGx + 1, cy + 3, gozKirmizi);
                if (stil != Stil.KILITLI) {
                    g.fill(solGx - 1, cy - 1, solGx + 1, cy + 1, gozParlak);
                }

                // Sağ Göz
                int sagGx = cx + 7;
                g.fill(sagGx - 3, cy - 2, sagGx + 4, cy + 2, gozKirmizi);
                g.fill(sagGx - 1, cy - 3, sagGx + 2, cy + 3, gozKirmizi);
                if (stil != Stil.KILITLI) {
                    g.fill(sagGx - 1, cy - 1, sagGx + 1, cy + 1, gozParlak);
                }
            }

            if (stil == Stil.KILITLI) {
                kucukKilitCiz(g, cx + r - 7, cy + r - 8);
            }
        }
    }

    private void kokDugumCiz(GuiGraphics g, int cx, int cy, Stil stil, double puls) {
        int r = DUGUM_BOYUT_KOK / 2;
        int auraRenk = renkKaristir(0x35DD0000, 0x85FF2222, (float) puls);
        g.fill(cx - r - 4, cy - r - 4, cx + r + 4, cy + r + 4, auraRenk);
        g.fill(cx - r - 7, cy - r - 7, cx + r + 7, cy + r + 7, auraRenk & 0x35FFFFFF);

        g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF180A0E);
        kutuCerceveCiz(g, cx - r, cy - r, DUGUM_BOYUT_KOK, DUGUM_BOYUT_KOK, 0xFFFF2222);
        kutuCerceveCiz(g, cx - r + 1, cy - r + 1, DUGUM_BOYUT_KOK - 2, DUGUM_BOYUT_KOK - 2, 0xFF660B0E);
        kosePerçinleriCiz(g, cx - r, cy - r, DUGUM_BOYUT_KOK, DUGUM_BOYUT_KOK, 0xFFFF8888);

        g.fill(cx - r + 2, cy - r + 2, cx + r - 2, cy + r - 2, 0xFF100406);
        kurtKafaIkonuCiz(g, cx, cy, renkKaristir(0xFFEE2222, 0xFFFF5555, (float) puls));
    }

    private void kareDugumKutusuCiz(GuiGraphics g, WolfSkill yetenek, int x, int y, int boyut, Stil stil, double puls) {
        String durumStr = (stil == Stil.ACIK) ? "acik" : (stil == Stil.ACILABILIR ? "acilabilir" : "kilitli");
        ResourceLocation dokuKare = new ResourceLocation(ExampleMod.MOD_ID, "textures/gui/node_kare_" + durumStr + ".png");

        if (dokuVarMi(dokuKare)) {
            int[] d = dokuBoyutlariniAl(dokuKare);
            g.blit(dokuKare, x, y, boyut, boyut, 0, 0, d[0], d[1], d[0], d[1]);
            return;
        }

        int dalRenk = yetenek.getDal().getRenk();
        int zemin;
        int cerceve;

        switch (stil) {
            case ACIK -> {
                zemin = 0xE01A1820;
                cerceve = dalRenk;
                g.fill(x - 2, y - 2, x + boyut + 2, y + boyut + 2, (dalRenk & 0x40FFFFFF));
            }
            case ACILABILIR -> {
                zemin = 0xE022202A;
                cerceve = renkKaristir(0xFF555566, dalRenk, (float) puls);
                g.fill(x - 1, y - 1, x + boyut + 1, y + boyut + 1, (dalRenk & 0x25FFFFFF));
            }
            default -> {
                zemin = 0xE014141A;
                cerceve = 0xFF353542;
            }
        }

        // Zemin dolgusu
        g.fill(x, y, x + boyut, y + boyut, zemin);
        // Bevel ve iç gölge efektleri
        g.fill(x + 1, y + 1, x + boyut - 1, y + 2, 0x25FFFFFF); // üst parlaklık
        g.fill(x + 1, y + 1, x + 2, y + boyut - 1, 0x25FFFFFF); // sol parlaklık
        g.fill(x + 1, y + boyut - 2, x + boyut - 1, y + boyut - 1, 0x30000000); // alt gölge
        g.fill(x + boyut - 2, y + 1, x + boyut - 1, y + boyut - 1, 0x30000000); // sağ gölge

        // Ana çerçeve ve perçinler
        kutuCerceveCiz(g, x, y, boyut, boyut, cerceve);
        kosePerçinleriCiz(g, x, y, boyut, boyut, cerceve);
    }

    private void elmasDugumKutusuCiz(GuiGraphics g, WolfSkill yetenek, int cx, int cy, int yaricap, Stil stil, double puls) {
        String durumStr = (stil == Stil.ACIK) ? "acik" : (stil == Stil.ACILABILIR ? "acilabilir" : "kilitli");
        ResourceLocation dokuElmas = new ResourceLocation(ExampleMod.MOD_ID, "textures/gui/node_elmas_" + durumStr + ".png");

        if (dokuVarMi(dokuElmas)) {
            int[] d = dokuBoyutlariniAl(dokuElmas);
            int kutuBoyut = yaricap * 2;
            g.blit(dokuElmas, cx - yaricap, cy - yaricap, kutuBoyut, kutuBoyut, 0, 0, d[0], d[1], d[0], d[1]);
            return;
        }

        int dalRenk = yetenek.getDal().getRenk();
        int zemin;
        int cerceve;

        switch (stil) {
            case ACIK -> {
                zemin = 0xE01A1820;
                cerceve = dalRenk;
                elmasCiz(g, cx, cy, yaricap + 2, dalRenk & 0x40FFFFFF);
            }
            case ACILABILIR -> {
                zemin = 0xE022202A;
                cerceve = renkKaristir(0xFF555566, dalRenk, (float) puls);
                elmasCiz(g, cx, cy, yaricap + 1, dalRenk & 0x25FFFFFF);
            }
            default -> {
                zemin = 0xE016161E;
                cerceve = 0xFF353542;
            }
        }

        elmasCiz(g, cx, cy, yaricap, zemin);
        elmasCerceveCiz(g, cx, cy, yaricap, cerceve);
        if (stil != Stil.KILITLI) {
            elmasCerceveCiz(g, cx, cy, yaricap - 1, cerceve);
        }
    }

    private static void kosePerçinleriCiz(GuiGraphics g, int x, int y, int w, int h, int cerceveRenk) {
        int percin = renkKaristir(cerceveRenk, 0xFFFFFFFF, 0.4f);
        g.fill(x + 1, y + 1, x + 2, y + 2, percin);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, percin);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, percin);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, percin);
    }

    private static void elmasCerceveCiz(GuiGraphics g, int cx, int cy, int yaricap, int renk) {
        for (int dy = -yaricap; dy <= yaricap; dy++) {
            int w = yaricap - Math.abs(dy);
            g.fill(cx - w, cy + dy, cx - w + 1, cy + dy + 1, renk);
            g.fill(cx + w, cy + dy, cx + w + 1, cy + dy + 1, renk);
        }
    }

    private void yetenekAcilmaFlasiCiz(GuiGraphics g, int cx, int cy, int boyut, long gecenMs) {
        if (gecenMs >= ACILMA_ANIMASYON_SURE_MS) {
            animasyonYetenek = null;
            return;
        }

        float oran = gecenMs / (float) ACILMA_ANIMASYON_SURE_MS;
        int alfa = (int) ((1.0f - oran) * 200);

        // Flaş patlaması
        int flaşBoyut = boyut + (int) (oran * 16);
        g.fill(cx - flaşBoyut / 2, cy - flaşBoyut / 2, cx + flaşBoyut / 2, cy + flaşBoyut / 2, (alfa << 24) | 0x00FFFFFF);

        // Etrafa saçılan 8 kıvılcım
        for (int i = 0; i < 8; i++) {
            double aci = i * (Math.PI / 4.0);
            double dist = (oran * 22.0);
            int kx = (int) (cx + Math.cos(aci) * dist);
            int ky = (int) (cy + Math.sin(aci) * dist);
            g.fill(kx, ky, kx + 2, ky + 2, (alfa << 24) | 0x00FFF080);
        }
    }

    private void yeniAcilanYetenekleriKontrolEt() {
        Set<String> simdiki = WolfLevelClientState.getYetenekler();
        for (String id : simdiki) {
            if (!bilinenYetenekler.contains(id)) {
                bilinenYetenekler.add(id);
                WolfSkill acilan = WolfSkill.idIle(id);
                if (acilan != null) {
                    animasyonYetenek = acilan;
                    animasyonBaslangic = System.currentTimeMillis();
                    // Açılma ses efekti
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.playSound(SoundEvents.PLAYER_LEVELUP, 0.7f, 1.3f);
                    }
                }
            }
        }
    }

    // =========================================================================
    // A3: İKON ÇİZİMİ (16x16 / 32x32 ÇÖZÜNÜRLÜK ESNEKLİĞİ)
    // =========================================================================

    private void ikonCiz(GuiGraphics g, WolfSkill yetenek, int cx, int cy, Stil stil, boolean muhurVar, double puls) {
        ResourceLocation doku = ikonDokusu(yetenek, muhurVar);

        if (dokuVarMi(doku)) {
            // 16x16 veya 32x32 doku boyutu esnekliği
            int[] d = dokuBoyutlariniAl(doku);
            int tw = d[0] > 0 ? d[0] : 16;
            int th = d[1] > 0 ? d[1] : 16;
            g.blit(doku, cx - IKON_BOYUT / 2, cy - IKON_BOYUT / 2, IKON_BOYUT, IKON_BOYUT, 0, 0, tw, th, tw, th);

            if (stil == Stil.KILITLI && !yetenek.isMuhur()) {
                g.fill(cx - IKON_BOYUT / 2, cy - IKON_BOYUT / 2, cx + IKON_BOYUT / 2, cy + IKON_BOYUT / 2, 0x90000000);
            }
            return;
        }

        int ikonRenk = (stil == Stil.KILITLI) ? 0xFF666675 : yetenek.getDal().getRenk();
        if (stil == Stil.ACILABILIR) {
            ikonRenk = renkKaristir(0xFF777788, yetenek.getDal().getRenk(), (float) puls);
        }

        if (yetenek.isMuhur()) {
            if (muhurVar) {
                muhurGeciciCiz(g, cx, cy, puls);
            } else {
                kilitCiz(g, cx, cy, ikonRenk);
            }
            return;
        }

        // Kodla çizilen tematik ikonlar
        switch (yetenek) {
            case VAHSI_UYANIS -> kurtKafaIkonuCiz(g, cx, cy, ikonRenk);
            case HAYVANI_CEVIKLIK -> kanatIkonuCiz(g, cx, cy, ikonRenk);
            case YIRTICI_ICGUDU -> penceIkonuCiz(g, cx, cy, ikonRenk);
            case GOLGE_ADIM -> kanatIkonuCiz(g, cx, cy, ikonRenk);
            case AVCININ_ZIYAFETI -> penceIkonuCiz(g, cx, cy, ikonRenk);
            case HICLIK_PENCESI -> penceIkonuCiz(g, cx, cy, ikonRenk);
            case KAOS_PATLAMASI -> zincirIkonuCiz(g, cx, cy, ikonRenk);
            case KAOSUN_ENKARNASYONU -> {
                zincirIkonuCiz(g, cx, cy, ikonRenk);
                elmasCiz(g, cx, cy, 3, renkKaristir(RENK_MOR_KAOS, 0xFFFFFFFF, (float) puls));
            }
            default -> kurtKafaIkonuCiz(g, cx, cy, ikonRenk);
        }
    }

    private int[] dokuBoyutlariniAl(ResourceLocation doku) {
        return dokuBoyutlari.computeIfAbsent(doku.toString(), k -> {
            try {
                var res = Minecraft.getInstance().getResourceManager().getResource(doku);
                if (res.isPresent()) {
                    try (var is = res.get().open()) {
                        BufferedImage img = ImageIO.read(is);
                        if (img != null) {
                            return new int[]{img.getWidth(), img.getHeight()};
                        }
                    }
                }
            } catch (Exception ignored) {}
            return new int[]{16, 16};
        });
    }

    private ResourceLocation ikonDokusu(WolfSkill yetenek, boolean muhurVar) {
        String ad = yetenek.getId();
        if (yetenek.isMuhur()) {
            ad = muhurVar ? "kaos_muhru_acik" : "kaos_muhru_kilitli";
        }
        return new ResourceLocation(ExampleMod.MOD_ID, "textures/gui/skill/" + ad + ".png");
    }

    private boolean dokuVarMi(ResourceLocation doku) {
        return dokuVarMi.computeIfAbsent(doku.toString(), anahtar ->
                Minecraft.getInstance().getResourceManager().getResource(doku).isPresent());
    }

    // =========================================================================
    // A8, A9: ÜST BİLGİ PLAKASI, XP BARI, ALT RÜNİK PLAKALAR & X BUTONU
    // =========================================================================

    private void ustBilgiCiz(GuiGraphics g, int xp, int seviye, int puan, double puls) {
        int bannerY = 4;
        int bannerGenislik = Math.min(320, this.width - 70);
        int bannerX = (this.width - bannerGenislik) / 2;

        if (dokuVarMi(DOKU_BASLIK_SERIT)) {
            int[] d = dokuBoyutlariniAl(DOKU_BASLIK_SERIT);
            g.blit(DOKU_BASLIK_SERIT, bannerX, bannerY, bannerGenislik, 28, 0, 0, d[0], d[1], d[0], d[1]);
        } else {
            // Kodla şerit kuyrukları (ribbon tails / V çentikler) ve süslemeler
            // Sol şerit kuyruğu
            g.fill(bannerX - 12, bannerY + 4, bannerX, bannerY + 24, 0xCC1A1A22);
            kutuCerceveCiz(g, bannerX - 12, bannerY + 4, 12, 20, RENK_TAS_CERCEVE);
            // Sol çentik boşluğu
            g.fill(bannerX - 12, bannerY + 11, bannerX - 9, bannerY + 17, RENK_ZEMIN);

            // Sağ şerit kuyruğu
            g.fill(bannerX + bannerGenislik, bannerY + 4, bannerX + bannerGenislik + 12, bannerY + 24, 0xCC1A1A22);
            kutuCerceveCiz(g, bannerX + bannerGenislik, bannerY + 4, 12, 20, RENK_TAS_CERCEVE);
            // Sağ çentik boşluğu
            g.fill(bannerX + bannerGenislik + 9, bannerY + 11, bannerX + bannerGenislik + 12, bannerY + 17, RENK_ZEMIN);

            // Ana taş banner
            g.fill(bannerX, bannerY, bannerX + bannerGenislik, bannerY + 28, 0xE01C1C24);
            kutuCerceveCiz(g, bannerX, bannerY, bannerGenislik, 28, RENK_TAS_CERCEVE);
            kutuCerceveCiz(g, bannerX + 1, bannerY + 1, bannerGenislik - 2, 26, 0x25FFFFFF);
        }

        // Başlık
        g.drawCenteredString(this.font, "◆ KURT ADAM YETENEK AĞACI ◆", this.width / 2, bannerY + 4, RENK_METIN_BEYAZ);

        // XP Barı ve Seviye
        int barGenislik = Math.min(180, bannerGenislik - 80);
        int barX = (this.width - barGenislik) / 2;
        int barY = bannerY + 16;
        int barYukseklik = 6;

        float oran;
        String xpMetni;
        if (seviye >= InfectionData.MAX_SEVIYE) {
            oran = 1f;
            xpMetni = "MAKSİMUM SEVİYE";
        } else {
            int alt = InfectionData.seviyeXpEsigi(seviye);
            int ust = InfectionData.seviyeXpEsigi(seviye + 1);
            oran = Math.max(0f, Math.min(1f, (xp - alt) / (float) (ust - alt)));
            xpMetni = xp + "/" + ust;
        }

        g.fill(barX, barY, barX + barGenislik, barY + barYukseklik, RENK_BAR_ARKA);
        g.fill(barX, barY, barX + (int) (barGenislik * oran), barY + barYukseklik, RENK_BAR_DOLGU);
        kutuCerceveCiz(g, barX, barY, barGenislik, barYukseklik, 0xFF3A3A46);

        // Sol: Rütbe & Seviye
        WolfRutbe rutbe = WolfRutbe.seviyeyeGore(seviye);
        String rMetin = rutbe.getAd() + " (Sv." + seviye + ")";
        g.drawString(this.font, rMetin, bannerX + 6, bannerY + 15, rutbe.getRenk(), false);

        // Sağ: Yetenek Puanı (Harcanabilir puan varsa nabız atar)
        String pMetin = (puan > 0 ? "✦ Puan: " : "Puan: ") + puan;
        int pW = this.font.width(pMetin);
        int pRenk = (puan > 0) ? renkKaristir(RENK_ALTIN, RENK_ALTIN_PARLAK, (float) puls) : RENK_METIN_GRI;
        g.drawString(this.font, pMetin, bannerX + bannerGenislik - pW - 6, bannerY + 15, pRenk, false);
    }

    private void kapatButonuCiz(GuiGraphics g, int mouseX, int mouseY) {
        kapatHover = mouseX >= kapatX && mouseX < kapatX + KAPAT_BOYUT && mouseY >= kapatY && mouseY < kapatY + KAPAT_BOYUT;

        ResourceLocation dokuKapat = kapatHover ? DOKU_KAPAT_HOVER : DOKU_KAPAT;
        if (dokuVarMi(dokuKapat)) {
            int[] d = dokuBoyutlariniAl(dokuKapat);
            g.blit(dokuKapat, kapatX, kapatY, KAPAT_BOYUT, KAPAT_BOYUT, 0, 0, d[0], d[1], d[0], d[1]);
            return;
        }

        // Kodla taş kapatma butonu
        int zemin = kapatHover ? 0xE0281216 : 0xE01A1A22;
        int cerceve = kapatHover ? 0xFFFF3333 : RENK_TAS_CERCEVE;
        int xRenk = kapatHover ? 0xFFFF6666 : RENK_METIN_GRI;

        if (kapatHover) {
            g.fill(kapatX - 2, kapatY - 2, kapatX + KAPAT_BOYUT + 2, kapatY + KAPAT_BOYUT + 2, 0x40FF2222);
        }

        g.fill(kapatX, kapatY, kapatX + KAPAT_BOYUT, kapatY + KAPAT_BOYUT, zemin);
        kutuCerceveCiz(g, kapatX, kapatY, KAPAT_BOYUT, KAPAT_BOYUT, cerceve);
        kosePerçinleriCiz(g, kapatX, kapatY, KAPAT_BOYUT, KAPAT_BOYUT, cerceve);
        g.drawCenteredString(this.font, "✕", kapatX + KAPAT_BOYUT / 2, kapatY + 5, xRenk);
    }

    private void scrollbarCiz(GuiGraphics g) {
        int sbX = this.width - 12;
        int rayY = kanvasY + 8;
        int rayYukseklik = kanvasYukseklik - 16;
        if (rayYukseklik <= 20) return;

        // Doku varsa çiz
        if (dokuVarMi(DOKU_KAYDIRMA_RAY)) {
            int[] d = dokuBoyutlariniAl(DOKU_KAYDIRMA_RAY);
            g.blit(DOKU_KAYDIRMA_RAY, sbX, rayY, 4, rayYukseklik, 0, 0, d[0], d[1], d[0], d[1]);
        } else {
            // Kodla taş ray
            g.fill(sbX, rayY, sbX + 4, rayY + rayYukseklik, 0xFF14141B);
            kutuCerceveCiz(g, sbX, rayY, 4, rayYukseklik, 0xFF2A2A35);
            // Üst ve alt piramit uç süsleri
            g.fill(sbX - 1, rayY - 3, sbX + 5, rayY, 0xFF3E3E4C);
            g.fill(sbX - 1, rayY + rayYukseklik, sbX + 5, rayY + rayYukseklik + 3, 0xFF3E3E4C);
        }

        // Kaydırıcı gösterge (Thumb)
        int thumbBoyut = Math.max(16, rayYukseklik / 4);
        double enAzY = GORUNUR_MARJ - kanvasYukseklik / 2.0 - maxGy * ARALIK_Y - DUGUM_BOYUT_MERKEZ / 2.0;
        double enCokY = kanvasYukseklik - GORUNUR_MARJ - kanvasYukseklik / 2.0 - minGy * ARALIK_Y + DUGUM_BOYUT_MERKEZ / 2.0;
        double oran = (enCokY - enAzY != 0) ? (offsetY - enCokY) / (enAzY - enCokY) : 0;
        oran = Math.max(0.0, Math.min(1.0, oran));

        int thumbY = rayY + (int) ((rayYukseklik - thumbBoyut) * oran);

        if (dokuVarMi(DOKU_KAYDIRMA_TUTAC)) {
            int[] d = dokuBoyutlariniAl(DOKU_KAYDIRMA_TUTAC);
            g.blit(DOKU_KAYDIRMA_TUTAC, sbX - 1, thumbY, 6, thumbBoyut, 0, 0, d[0], d[1], d[0], d[1]);
        } else {
            // Kodla taş tutamaç ve tutuş çizgileri (grip lines)
            g.fill(sbX - 1, thumbY, sbX + 5, thumbY + thumbBoyut, 0xFF6E6E7C);
            kutuCerceveCiz(g, sbX - 1, thumbY, 6, thumbBoyut, 0xFF9A9AA8);
            g.fill(sbX, thumbY + thumbBoyut / 2 - 2, sbX + 4, thumbY + thumbBoyut / 2 - 1, 0xFF33333E);
            g.fill(sbX, thumbY + thumbBoyut / 2 + 1, sbX + 4, thumbY + thumbBoyut / 2 + 2, 0xFF33333E);
        }
    }

    private void altRunikBantlariCiz(GuiGraphics g, double puls) {
        int y = this.height - 18;
        int genislik = Math.min(130, this.width / 4);
        if (genislik < 40) return;

        // A9: Odaklanan dala göre dinamik ad belirleme
        boolean kaosOdakli = offsetY < - (WolfSkill.ALFA_KURDU.getY() * ARALIK_Y + 30);
        String solMetin = kaosOdakli ? "• K A O S •" : "• T E M E L •";
        String sagMetin = kaosOdakli ? "• M Ü H R Ü •" : "• D A L I •";
        int metinRenk = kaosOdakli ? renkKaristir(0xFF7A3FC0, RENK_MOR_KAOS, (float) puls)
                                   : renkKaristir(0xFF888899, RENK_ALTIN, (float) puls);

        // Sol Plaka
        int solX = 14;
        g.fill(solX, y, solX + genislik, y + 8, 0xC01A1A24);
        kutuCerceveCiz(g, solX, y, genislik, 8, 0xFF3A3A48);
        g.drawCenteredString(this.font, solMetin, solX + genislik / 2, y, metinRenk);

        // Sağ Plaka
        int sagX = this.width - genislik - 14;
        g.fill(sagX, y, sagX + genislik, y + 8, 0xC01A1A24);
        kutuCerceveCiz(g, sagX, y, genislik, 8, 0xFF3A3A48);
        g.drawCenteredString(this.font, sagMetin, sagX + genislik / 2, y, metinRenk);
    }

    // =========================================================================
    // A6: HATA BİLDİRİM BANNER'I
    // =========================================================================

    private void hataBildirimiCiz(GuiGraphics g, long zaman) {
        if (hataMesaji == null || zaman >= hataBitisZamani) {
            hataMesaji = null;
            return;
        }

        long kalanMs = hataBitisZamani - zaman;
        float alfa = Math.min(1.0f, kalanMs / 400.0f);
        int arkaAlfa = (int) (alfa * 230);
        int kenarAlfa = (int) (alfa * 255);

        int metinW = this.font.width(hataMesaji) + 24;
        int bannerX = (this.width - metinW) / 2;
        int bannerY = this.height - 38;

        g.fill(bannerX, bannerY, bannerX + metinW, bannerY + 16, (arkaAlfa << 24) | 0x001A0606);
        kutuCerceveCiz(g, bannerX, bannerY, metinW, 16, (kenarAlfa << 24) | 0x00FF3333);
        g.drawCenteredString(this.font, "⚠ " + hataMesaji, this.width / 2, bannerY + 4, (kenarAlfa << 24) | 0x00FF8888);
    }

    private void hataBildir(String mesaj) {
        this.hataMesaji = mesaj;
        this.hataBitisZamani = System.currentTimeMillis() + HATA_MESAJI_SURE_MS;
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(SoundEvents.VILLAGER_NO, 0.7f, 1.0f);
        }
    }

    // =========================================================================
    // A7: GELİŞMİŞ VE RENKLİ İPUCU KUTUSU (TOOLTIP)
    // =========================================================================

    private void ipucuCiz(GuiGraphics g, WolfSkill yetenek, WolfSkill.Sonuc sonuc,
                          Set<String> acik, boolean muhurVar, int seviye, int puan,
                          int mouseX, int mouseY) {
        List<Component> satirlar = new ArrayList<>();

        // 1. Yetenek Adı
        satirlar.add(Component.literal(yetenek.getAd()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        // 2. Açıklama
        satirlar.add(Component.literal(yetenek.getAciklama()).withStyle(ChatFormatting.GRAY));

        // 3. Mühür düğümü özel açıklaması
        if (yetenek.isMuhur()) {
            if (muhurVar) {
                satirlar.add(Component.literal("✔ Kaos Mührü işlendi.").withStyle(ChatFormatting.LIGHT_PURPLE));
            } else {
                satirlar.add(Component.literal("✖ Mühür henüz işlenmedi.").withStyle(ChatFormatting.RED));
                satirlar.add(Component.literal(WolfSkill.ALFA_KURDU.acikMi(acik)
                                ? "Kaos Mührü eşyasını kullanarak formu uyandır."
                                : "Önce Alfa Kurdu'nu açmalısın.")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            g.renderComponentTooltip(this.font, satirlar, mouseX, mouseY);
            return;
        }

        // 4. Maliyet Satırı
        if (yetenek.getMaliyet() > 0) {
            boolean puanYeterli = puan >= yetenek.getMaliyet();
            satirlar.add(Component.literal("Maliyet: " + yetenek.getMaliyet() + " Yetenek Puanı")
                    .withStyle(puanYeterli ? ChatFormatting.GREEN : ChatFormatting.RED));
        }

        // 5. Seviye Gereksinimi
        if (yetenek.getMinSeviye() > 1) {
            boolean seviyeYeterli = seviye >= yetenek.getMinSeviye();
            satirlar.add(Component.literal("Gereken Seviye: " + yetenek.getMinSeviye() + " (Mevcut: " + seviye + ")")
                    .withStyle(seviyeYeterli ? ChatFormatting.GREEN : ChatFormatting.RED));
        }

        // 6. Ön Koşullar
        for (WolfSkill on : yetenek.getOnKosullar()) {
            boolean onAcik = dugumAcikMi(on, acik, muhurVar);
            String isaret = onAcik ? "✔ " : "✖ ";
            satirlar.add(Component.literal(isaret + "Ön Koşul: " + on.getAd())
                    .withStyle(onAcik ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED));
        }

        // 7. Kaos Mührü Kilidi
        if (yetenek.getDal() == WolfSkill.Dal.KAOS) {
            satirlar.add(Component.literal(muhurVar ? "✔ Kaos Mührü Aktif" : "✖ Kaos Mührü Gerekli")
                    .withStyle(muhurVar ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_PURPLE));
        }

        // 8. Durum İpucu
        switch (sonuc) {
            case ZATEN_ACIK -> satirlar.add(Component.literal("● Açıldı").withStyle(ChatFormatting.GREEN));
            case ACILABILIR -> satirlar.add(Component.literal("● Tıkla: Yeteneği Öğren").withStyle(ChatFormatting.YELLOW));
            case SEVIYE_YETERSIZ -> satirlar.add(Component.literal("● Seviyen Yetersiz").withStyle(ChatFormatting.RED));
            case PUAN_YOK -> satirlar.add(Component.literal("● Yetenek Puanın Yok").withStyle(ChatFormatting.RED));
            case ON_KOSUL_EKSIK -> satirlar.add(Component.literal("● Ön Koşullar Eksik").withStyle(ChatFormatting.RED));
            case KATALIZOR_GEREKLI -> satirlar.add(Component.literal("● Kaos Mührü İşlenmeli").withStyle(ChatFormatting.DARK_PURPLE));
        }

        g.renderComponentTooltip(this.font, satirlar, mouseX, mouseY);
    }

    // =========================================================================
    // ETKİLEŞİM VE GİRDİ YÖNETİMİ
    // =========================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Kapat (X) butonu
            if (mouseX >= kapatX && mouseX <= kapatX + KAPAT_BOYUT && mouseY >= kapatY && mouseY <= kapatY + KAPAT_BOYUT) {
                this.onClose();
                return true;
            }

            if (WolfLevelClientState.enfekteMi()) {
                // Sağ scrollbar rayına tıklama
                int sbX = this.width - 12;
                int rayY = kanvasY + 8;
                int rayYukseklik = kanvasYukseklik - 16;
                if (mouseX >= sbX - 4 && mouseX <= sbX + 8 && mouseY >= rayY && mouseY <= rayY + rayYukseklik) {
                    scrollbarSurukleniyor = true;
                    kaydiriciOraniniAyarla(mouseY, rayY, rayYukseklik);
                    return true;
                }

                // Kanvas basma takibi
                if (fareKanvastaMi(mouseX, mouseY)) {
                    basildi = true;
                    surukledi = false;
                    basX = mouseX;
                    basY = mouseY;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && scrollbarSurukleniyor) {
            int rayY = kanvasY + 8;
            int rayYukseklik = kanvasYukseklik - 16;
            kaydiriciOraniniAyarla(mouseY, rayY, rayYukseklik);
            return true;
        }

        if (button == 0 && basildi) {
            if (!surukledi && Math.hypot(mouseX - basX, mouseY - basY) > SURUKLEME_ESIGI) {
                surukledi = true;
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
        if (button == 0 && scrollbarSurukleniyor) {
            scrollbarSurukleniyor = false;
            return true;
        }

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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (fareKanvastaMi(mouseX, mouseY)) {
            offsetY += delta * 24.0;
            offsetSinirla();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Home tuşu: görünümü merkeze (Alfa Kurdu) döndürür
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            merkezeOdaklan();
            return true;
        }

        // Panel tuşu veya ESC kapatır
        if (ExampleModClient.panelTusuEslesirMi(keyCode, scanCode)) {
            this.minecraft.setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void merkezeOdaklan() {
        offsetX = 0;
        offsetY = - (WolfSkill.ALFA_KURDU.getY() * ARALIK_Y);
        offsetSinirla();
    }

    private void dugumTikla(double mouseX, double mouseY) {
        int xp = WolfLevelClientState.getXp();
        Set<String> acik = WolfLevelClientState.getYetenekler();
        boolean muhurVar = WolfLevelClientState.kaosKilidiAcikMi();

        for (WolfSkill yetenek : WolfSkill.values()) {
            if (yetenek.isMuhur()) continue;

            if (fareDugumde(yetenek, mouseX, mouseY)) {
                WolfSkill.Sonuc sonuc = WolfSkill.degerlendir(yetenek, xp, acik, muhurVar);
                if (sonuc == WolfSkill.Sonuc.ACILABILIR) {
                    FriendlyByteBuf buf = PacketByteBufs.create();
                    buf.writeUtf(yetenek.getId(), 32);
                    ClientPlayNetworking.send(WolfNetworking.YETENEK_AC, buf);
                    return;
                } else if (sonuc == WolfSkill.Sonuc.PUAN_YOK) {
                    hataBildir("Yetenek puanın yetersiz!");
                } else if (sonuc == WolfSkill.Sonuc.SEVIYE_YETERSIZ) {
                    hataBildir("Seviyen yetersiz! (Gereken: Sv." + yetenek.getMinSeviye() + ")");
                } else if (sonuc == WolfSkill.Sonuc.ON_KOSUL_EKSIK) {
                    hataBildir("Önceki gereksinimleri açmalısın!");
                } else if (sonuc == WolfSkill.Sonuc.KATALIZOR_GEREKLI) {
                    hataBildir("Bu dal için Kaos Mührü işlenmelidir!");
                }
                return;
            }
        }
    }

    private void kaydiriciOraniniAyarla(double mouseY, int rayY, int rayYukseklik) {
        int thumbBoyut = Math.max(16, rayYukseklik / 4);
        double enAzY = GORUNUR_MARJ - kanvasYukseklik / 2.0 - maxGy * ARALIK_Y - DUGUM_BOYUT_MERKEZ / 2.0;
        double enCokY = kanvasYukseklik - GORUNUR_MARJ - kanvasYukseklik / 2.0 - minGy * ARALIK_Y + DUGUM_BOYUT_MERKEZ / 2.0;
        double oran = (mouseY - rayY - thumbBoyut / 2.0) / (rayYukseklik - thumbBoyut);
        oran = Math.max(0.0, Math.min(1.0, oran));
        offsetY = enCokY + oran * (enAzY - enCokY);
        offsetSinirla();
    }

    // =========================================================================
    // KONUM VE YARDIMCI HESAPLAMALAR
    // =========================================================================

    public static int dugumBoyutu(WolfSkill yetenek) {
        if (yetenek == WolfSkill.ALFA_KURDU) return DUGUM_BOYUT_MERKEZ;
        if (yetenek == WolfSkill.VAHSI_UYANIS) return DUGUM_BOYUT_KOK;
        return DUGUM_BOYUT_NORMAL;
    }

    public static boolean isBaklava(WolfSkill yetenek) {
        return yetenek == WolfSkill.HAYVANI_CEVIKLIK
                || yetenek == WolfSkill.YIRTICI_ICGUDU
                || yetenek == WolfSkill.GOLGE_ADIM
                || yetenek == WolfSkill.HICLIK_PENCESI;
    }

    private int dugumMerkezX(WolfSkill yetenek) {
        return (int) Math.round(kanvasX + kanvasGenislik / 2.0 + offsetX + yetenek.getX() * ARALIK_X);
    }

    private int dugumMerkezY(WolfSkill yetenek) {
        int ekY = (yetenek == WolfSkill.VAHSI_UYANIS) ? KOK_EK_Y : 0;
        return (int) Math.round(kanvasY + kanvasYukseklik / 2.0 + offsetY + yetenek.getY() * ARALIK_Y + ekY);
    }

    private boolean fareDugumde(WolfSkill yetenek, double mouseX, double mouseY) {
        int boyut = dugumBoyutu(yetenek);
        int cx = dugumMerkezX(yetenek);
        int cy = dugumMerkezY(yetenek);

        if (isBaklava(yetenek)) {
            int r = boyut / 2 + 3;
            return Math.abs(mouseX - cx) + Math.abs(mouseY - cy) <= r;
        }

        int x = cx - boyut / 2;
        int y = cy - boyut / 2;
        return mouseX >= x && mouseX < x + boyut && mouseY >= y && mouseY < y + boyut;
    }

    private boolean fareKanvastaMi(double mouseX, double mouseY) {
        return mouseX >= kanvasX && mouseX < kanvasX + kanvasGenislik - 14
                && mouseY >= kanvasY && mouseY < kanvasY + kanvasYukseklik;
    }

    private void offsetSinirla() {
        double enAzX = GORUNUR_MARJ - kanvasGenislik / 2.0 - maxGx * ARALIK_X - DUGUM_BOYUT_MERKEZ / 2.0;
        double enCokX = kanvasGenislik - GORUNUR_MARJ - kanvasGenislik / 2.0 - minGx * ARALIK_X + DUGUM_BOYUT_MERKEZ / 2.0;
        offsetX = Math.max(enAzX, Math.min(enCokX, offsetX));

        double enAzY = GORUNUR_MARJ - kanvasYukseklik / 2.0 - maxGy * ARALIK_Y - DUGUM_BOYUT_MERKEZ / 2.0;
        double enCokY = kanvasYukseklik - GORUNUR_MARJ - kanvasYukseklik / 2.0 - minGy * ARALIK_Y + DUGUM_BOYUT_MERKEZ / 2.0;
        offsetY = Math.max(enAzY, Math.min(enCokY, offsetY));
    }

    private static boolean dugumAcikMi(WolfSkill yetenek, Set<String> acik, boolean muhurVar) {
        return yetenek.isMuhur() ? muhurVar : yetenek.acikMi(acik);
    }

    // =========================================================================
    // GEÇİCİ SİMGELER VE ŞEKİL ÇİZİCİLER (FALLBACK)
    // =========================================================================

    private static void elmasCiz(GuiGraphics g, int cx, int cy, int yaricap, int renk) {
        for (int dy = -yaricap; dy <= yaricap; dy++) {
            int w = yaricap - Math.abs(dy);
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, renk);
        }
    }

    private static void kucukKilitCiz(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y, x + 4, y + 2, 0xFFB0B0B8);
        g.fill(x, y + 2, x + 5, y + 6, 0xFFE0B040);
        g.fill(x + 2, y + 3, x + 3, y + 5, 0xFF000000);
    }

    private static void kilitCiz(GuiGraphics g, int cx, int cy, int renk) {
        g.fill(cx - 3, cy - 6, cx + 3, cy - 5, renk);
        g.fill(cx - 3, cy - 5, cx - 2, cy - 1, renk);
        g.fill(cx + 2, cy - 5, cx + 3, cy - 1, renk);
        g.fill(cx - 5, cy - 1, cx + 5, cy + 7, renk);
        g.fill(cx - 1, cy + 1, cx + 1, cy + 4, 0xFF000000);
    }

    private static void muhurGeciciCiz(GuiGraphics g, int cx, int cy, double puls) {
        elmasCiz(g, cx, cy, 7, renkKaristir(0xFF3A1F5A, 0xFF6A35A0, (float) puls));
        elmasCiz(g, cx, cy, 5, renkKaristir(0xFF7A3FC0, RENK_MOR_KAOS, (float) puls));
        elmasCiz(g, cx, cy, 2, 0xFFFFFFFF);
    }

    private static void kanatIkonuCiz(GuiGraphics g, int cx, int cy, int renk) {
        g.fill(cx - 5, cy - 1, cx - 1, cy + 3, renk);
        g.fill(cx + 1, cy - 1, cx + 5, cy + 3, renk);
        g.fill(cx - 7, cy - 3, cx - 3, cy, renk);
        g.fill(cx + 3, cy - 3, cx + 7, cy, renk);
        g.fill(cx - 1, cy, cx + 1, cy + 4, renk);
    }

    private static void zincirIkonuCiz(GuiGraphics g, int cx, int cy, int renk) {
        kutuCerceveCiz(g, cx - 4, cy - 5, 8, 5, renk);
        kutuCerceveCiz(g, cx - 4, cy + 1, 8, 5, renk);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 2, renk);
    }

    private static void penceIkonuCiz(GuiGraphics g, int cx, int cy, int renk) {
        g.fill(cx - 5, cy - 5, cx - 3, cy + 2, renk);
        g.fill(cx - 1, cy - 6, cx + 1, cy + 3, renk);
        g.fill(cx + 3, cy - 4, cx + 5, cy + 4, renk);
    }

    private static void kurtKafaIkonuCiz(GuiGraphics g, int cx, int cy, int renk) {
        g.fill(cx - 3, cy - 1, cx + 3, cy + 4, renk);
        g.fill(cx - 4, cy - 5, cx - 2, cy - 1, renk);
        g.fill(cx + 2, cy - 5, cx + 4, cy - 1, renk);
        g.fill(cx - 2, cy + 3, cx + 2, cy + 6, renk);
    }

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