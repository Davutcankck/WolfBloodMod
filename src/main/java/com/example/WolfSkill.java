package com.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Yetenek ağacı tanımı (veri odaklı). Panel (WolfSkillScreen), sunucu doğrulaması ve puan hesabı BURADAN okur.
 *
 * TEK AĞAÇ, yukarıdan aşağı. Koordinatlar ızgara birimidir (piksel değil): x sağa, y AŞAĞI artar,
 * (0,0) en üstteki kök düğümdür. Çaprazlar 1 birim sağ/sol + 1 birim aşağı olduğu için tam 45 derecedir.
 *
 *            Vahşi Uyanış (0,0)
 *            /                \
 *   Hayvani Çeviklik (-1,1)  Yırtıcı İçgüdü (1,1)
 *        |                        |                      ÜST ELMAS (Temel)
 *   Keskin Pençeler (-1,2)   Kalın Post (1,2)
 *            \                /
 *             Alfa Kurdu (0,3)
 *                   |
 *                   |   (iki birimlik uzun hat)
 *                   |
 *             Kaos Mührü (0,5)   <- mühür kontrol düğümü (satın alınmaz)
 *            /                \
 *   Gölge Adım (-1,6)     Hiçlik Pençesi (1,6)
 *        |                      |                        ALT ELMAS (Kaos)
 *   Avcının Ziyafeti (-1,7)  Kaos Patlaması (1,7)
 *            \                /
 *       Kaosun Enkarnasyonu (0,8)
 *
 * Kurallar:
 *   - id: diske yazılır. Yayına çıktıktan sonra değiştirme (açılmış yetenekler id ile saklanır).
 *     Ağaçtan silinen / yeniden adlandırılan id'ler "bilinmeyen" sayılır ve puana yansımaz (puan iade olur).
 *   - onKosulIdleri: listedeki HEPSİ açık olmalı (VE mantığı).
 *   - maliyet 0 olan düğüm satın alınmaz, diske yazılmaz (kök ve mühür düğümü gibi).
 *   - Mühür düğümü (isMuhur): açık/kapalı durumu yetenek listesinden değil, oyuncunun mühür bilgisinden
 *     (InfectionData.isChaosUnlocked, istemcide WolfLevelClientState.kaosKilidiAcikMi) gelir.
 *     Kaos dalındaki düğümler mühür işlenmeden açılamaz (Dal.KAOS kilidi).
 *   - minSeviye: düğümü açmak için gereken en düşük seviye.
 *   - Puan: Lv2'den itibaren her level 1 puan verir (toplam MAX_SEVIYE - 1).
 */
public enum WolfSkill {

    // ================= ÜST ELMAS (normal kurt adam) =================
    //            id                    dal         x   y  mal  minLv  ad                    açıklama                                          ön koşullar
    VAHSI_UYANIS ("root_node",          Dal.TEMEL,  0,  0, 0,   1,  "Vahşi Uyanış",       "Kurt kanının ilk uyanışı. Ağacın kökü."),
    HAYVANI_CEVIKLIK("agility_node",    Dal.TEMEL, -1,  1, 1,   2,  "Hayvani Çeviklik",   "Taslak: hareket hızı ve çeviklik bonusu.",        "root_node"),
    YIRTICI_ICGUDU("instinct_node",     Dal.TEMEL,  1,  1, 1,   2,  "Yırtıcı İçgüdü",     "Taslak: saldırı gücü ve algı bonusu.",            "root_node"),
    KESKIN_PENCELER("sharp_claws_node", Dal.TEMEL, -1,  2, 1,   3,  "Keskin Pençeler",    "Taslak: pençe hasarı bonusu.",                    "agility_node"),
    KALIN_POST   ("thick_skin_node",    Dal.TEMEL,  1,  2, 1,   3,  "Kalın Post",         "Taslak: hasar azaltma bonusu.",                   "instinct_node"),
    ALFA_KURDU   ("alpha_node",         Dal.TEMEL,  0,  3, 1,   7,  "Alfa Kurdu",         "Sürünün lideri. Gelişmiş formların kapısı.",      "sharp_claws_node", "thick_skin_node"),

    // ================= DARBOĞAZ: MÜHÜR DÜĞÜMÜ =================
    KAOS_MUHRU   ("chaos_seal_node",    Dal.TEMEL,  0,  5, 0,   1,  "Kaos Mührü",         "Boşluğun mührü. İşlenince Kaos yolu açılır.",     "alpha_node"),

    // ================= ALT ELMAS (Kaos) =================
    GOLGE_ADIM   ("shadow_step_node",   Dal.KAOS,  -1,  6, 1,   8,  "Gölge Adım",         "Taslak: hasardan kaçınma şansı (i-frames).",      "chaos_seal_node"),
    HICLIK_PENCESI("void_claw_node",    Dal.KAOS,   1,  6, 1,   8,  "Hiçlik Pençesi",     "Taslak: vuruşlar zırhın bir kısmını yok sayar.",  "chaos_seal_node"),
    AVCININ_ZIYAFETI("hunters_feast_node", Dal.KAOS, -1, 7, 1,  9,  "Avcının Ziyafeti",   "Taslak: öldürdükçe can yenilenir.",               "shadow_step_node"),
    KAOS_PATLAMASI("chaos_burst_node",  Dal.KAOS,   1,  7, 1,   9,  "Kaos Patlaması",     "Taslak: patlayıcı hasar.",                        "void_claw_node"),
    KAOSUN_ENKARNASYONU("chaos_ultimate_node", Dal.KAOS, 0, 8, 1, 10, "Kaosun Enkarnasyonu", "Zirve düğüm: Kaos formunun kilidi.",            "hunters_feast_node", "chaos_burst_node");

    // ---------------------------------------------------------------------------------

    /** Dal: düğümlerin hangi kilide bağlı olduğunu söyler (görsel sekme değil, mantıksal gruplama). */
    public enum Dal {
        //     ad       mühür gerekli  vurgu rengi
        TEMEL("Temel",  false,         0xFFD9A441),
        KAOS ("Kaos",   true,          0xFF9B4DDB);

        private final String ad;
        private final boolean muhurGerekli;
        private final int renk; // ARGB

        Dal(String ad, boolean muhurGerekli, int renk) {
            this.ad = ad;
            this.muhurGerekli = muhurGerekli;
            this.renk = renk;
        }

        public String getAd() {
            return ad;
        }

        public int getRenk() {
            return renk;
        }

        /** Dal bir mühür gerektiriyorsa, mührün işlenmiş olup olmadığı. Şimdilik tek mühür Kaos Mührü. */
        public boolean kilitAcikMi(boolean kaosMuhruVar) {
            return !muhurGerekli || kaosMuhruVar;
        }
    }

    /** Bir yeteneğin o anki durumunun değerlendirmesi. Sunucu ve istemci AYNI sonucu üretir. */
    public enum Sonuc {
        ACILABILIR,
        ZATEN_ACIK,
        KATALIZOR_GEREKLI, // mühür gerekli (ad eski, sunucu kodu bu ada bağlı)
        ON_KOSUL_EKSIK,
        SEVIYE_YETERSIZ,
        PUAN_YOK
    }

    private static final Map<String, WolfSkill> ID_HARITASI = new HashMap<>();

    static {
        for (WolfSkill yetenek : values()) {
            ID_HARITASI.put(yetenek.id, yetenek);
        }
        // Ön koşul id'lerinde yazım hatası varsa oyun açılırken hemen anlaşılsın
        for (WolfSkill yetenek : values()) {
            for (String onId : yetenek.onKosulIdleri) {
                if (!ID_HARITASI.containsKey(onId)) {
                    throw new IllegalStateException("WolfSkill '" + yetenek.id + "': bilinmeyen ön koşul '" + onId + "'");
                }
            }
        }
    }

    private final String id;
    private final Dal dal;
    private final int x;
    private final int y;
    private final int maliyet;
    private final int minSeviye;
    private final String ad;
    private final String aciklama;
    private final String[] onKosulIdleri;

    WolfSkill(String id, Dal dal, int x, int y, int maliyet, int minSeviye,
              String ad, String aciklama, String... onKosulIdleri) {
        this.id = id;
        this.dal = dal;
        this.x = x;
        this.y = y;
        this.maliyet = maliyet;
        this.minSeviye = minSeviye;
        this.ad = ad;
        this.aciklama = aciklama;
        this.onKosulIdleri = onKosulIdleri;
    }

    public String getId() {
        return id;
    }

    public Dal getDal() {
        return dal;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getMaliyet() {
        return maliyet;
    }

    public int getMinSeviye() {
        return minSeviye;
    }

    public String getAd() {
        return ad;
    }

    public String getAciklama() {
        return aciklama;
    }

    /** Mühür kontrol düğümü mü? Bu düğüm satın alınmaz, durumu oyuncunun mühür bilgisinden gelir. */
    public boolean isMuhur() {
        return this == KAOS_MUHRU;
    }

    public List<WolfSkill> getOnKosullar() {
        List<WolfSkill> liste = new ArrayList<>(onKosulIdleri.length);
        for (String onId : onKosulIdleri) {
            liste.add(ID_HARITASI.get(onId)); // static blokta doğrulandı, null olamaz
        }
        return liste;
    }

    /** Açık mı? Maliyeti 0 olan düğümler (kök, mühür düğümü) yetenek listesine bakılmadan açık sayılır. */
    public boolean acikMi(Set<String> acilanlar) {
        return maliyet == 0 || acilanlar.contains(id);
    }

    /** Bilinmeyen id için null döner (eski kayıttan kalmış / silinmiş yetenek olabilir). */
    public static WolfSkill idIle(String id) {
        return ID_HARITASI.get(id);
    }

    /**
     * Harcanmamış yetenek puanı. Ağaçta artık olmayan (bilinmeyen) id'ler harcanan puana sayılmaz.
     */
    public static int kullanilabilirPuan(int toplamXp, Set<String> acilanlar) {
        int harcanan = 0;
        for (String id : acilanlar) {
            WolfSkill yetenek = ID_HARITASI.get(id);
            if (yetenek != null) {
                harcanan += yetenek.maliyet;
            }
        }
        return Math.max(0, InfectionData.seviyeHesapla(toplamXp) - 1 - harcanan);
    }

    /** Bu yetenek şu an açılabilir mi, açılamıyorsa neden? Kontrol sırası öncelik sırasıdır. */
    public static Sonuc degerlendir(WolfSkill yetenek, int toplamXp, Set<String> acilanlar, boolean kaosMuhruVar) {
        if (yetenek.acikMi(acilanlar)) {
            return Sonuc.ZATEN_ACIK;
        }
        if (!yetenek.dal.kilitAcikMi(kaosMuhruVar)) {
            return Sonuc.KATALIZOR_GEREKLI;
        }
        for (WolfSkill on : yetenek.getOnKosullar()) {
            if (!on.acikMi(acilanlar)) {
                return Sonuc.ON_KOSUL_EKSIK;
            }
        }
        if (InfectionData.seviyeHesapla(toplamXp) < yetenek.minSeviye) {
            return Sonuc.SEVIYE_YETERSIZ;
        }
        if (kullanilabilirPuan(toplamXp, acilanlar) < yetenek.maliyet) {
            return Sonuc.PUAN_YOK;
        }
        return Sonuc.ACILABILIR;
    }
}